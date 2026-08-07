package lira

import soundness.*

import alphabets.hexLowerCase
import charDecoders.utf8Decoder
import charEncoders.utf8Encoder
import filesystemBackends.virtualMachine
import filesystemOptions.createNonexistentParents.enabled
import filesystemOptions.deleteRecursively.enabled
import filesystemOptions.dereferenceSymlinks.disabled
import filesystemOptions.moveAtomically.enabled
import filesystemOptions.overwritePreexisting.enabled
import logging.silentLogging
import textSanitizers.strictSanitizer

// A store object failed re-verification, or an operation addressed an object the store does
// not hold. Corruption is never repaired silently: `fsck` quarantines, and everything else
// aborts loudly.
case class StoreError(detail: Text)(using Diagnostics)
extends Error(m"store: $detail")

// The content-addressed store of design/tool.md §2: `.lira` files are decomposed on ingest
// into manifest bytes, the whole compressed payload, and loose blobs; canonical derivative
// JARs (spec §13.6) occupy a fourth tier. Object names are lowercase-hex hashes with a
// two-character fan-out; every object is verified as it is written and again as it is read.
object Store:

  // Raw manifest bytes need their own domain (spec §7.1, companion domains): the spec's
  // `lira/1:manifest` domain hashes the canonical signing encoding, so it identifies content
  // rather than bytes, and two formattings of one manifest would collide under it.
  private val manifestBytesDomain: Text = t"lira/1:manifest-bytes"

  def manifestBytesHash(content: Data): Data =
    val prefix: Data = charEncoders.utf8Encoder.encoded(manifestBytesDomain)
    val buffer = Array[Byte](prefix.length + 1 + content.length)
    buffer.copyFrom(prefix, 0, 0, prefix.length)
    buffer.copyFrom(content, 0, prefix.length + 1, content.length)
    Blake3.hashOf(Array.freeze(buffer))

  // §5.2 fixes the byte layout, so splitting a file needs no TEL parsing: the separator is
  // the first line consisting of exactly `##`.
  def separatorIndex(data: Data): Optional[Int] =
    val bytes = data.readable
    var index = 0

    while index + 4 <= data.length do
      if bytes(index) == '\n' && bytes(index + 1) == '#' && bytes(index + 2) == '#'
          && bytes(index + 3) == '\n'
      then return index
      index += 1

    Unset

  def slice(data: Data, from: Int, until: Int): Data =
    val buffer = Array[Byte](until - from)
    buffer.copyFrom(data, from, 0, until - from)
    Array.freeze(buffer)

  enum Tier:
    case Manifest, Payload, Blob, Derivative

    def dirName: Text = this match
      case Manifest   => t"manifest"
      case Payload    => t"payload"
      case Blob       => t"blob"
      case Derivative => t"derivative"

  case class Ingested
    ( manifestHex: Text,
      payloadHex:  Text,
      module:      Text,
      fresh:       Boolean,
      blobsAdded:  Int,
      blobsShared: Int )

  case class Release(hex: Text, manifest: LiraManifest, size: Long, pinned: Boolean)

  case class Sweep
    ( evicted:            List[Text],
      payloadsRemoved:    Int,
      blobsRemoved:       Int,
      derivativesRemoved: Int )
  case class Audit(objects: Int, corrupted: List[Text], orphanPayloads: List[Text])

  // `$LIRA_STORE` overrides the root; otherwise the store lives under the XDG data home —
  // deliberately not the cache home, since pinned content must survive cache cleaning
  // (design/tool.md §2.1).
  def default()(using Environment, System): Store raises PathError =
    val custom = safely(Environment[Text](t"LIRA_STORE"))

    val root = custom.let(_.as[Path on Linux]).or:
      Directories.dataHome[Path on Linux] / "lira" / "store"

    Store(root)

class Store(val root: Path on Linux):
  import Store.*

  // Dynamic segments (hex names, tier names) are appended textually and re-decoded: the
  // decoder runs the same admissibility rules as `/`, and the segments are hex digests and
  // fixed names, admissible on any filesystem.
  private def child(parent: Path on Linux, name: Text): Path on Linux =
    unsafely(t"${parent.encode}/$name".as[Path on Linux])

  private def tierDir(tier: Tier): Path on Linux = child(root, tier.dirName)
  private def pinDir: Path on Linux = child(root, t"pin")
  private def quarantineDir: Path on Linux = child(root, t"quarantine")
  private def journalPath: Path on Linux = child(root, t"journal")

  def objectPath(tier: Tier, hex: Text): Path on Linux =
    child(child(tierDir(tier), hex.keep(2)), hex)

  // Writes stage through a `.part` sibling and land by rename, so no partial object is ever
  // visible; an object that already exists is never rewritten (content-addressed names make
  // rewriting meaningless). Returns whether the object was new.
  def put(tier: Tier, hex: Text, data: Data): Boolean raises IoError =
    val target = objectPath(tier, hex)

    if target.existent() then false else
      target.create[File](CreateFlag.Parents, CreateFlag.Replace): handle ?=>
        handle.write(Chain(data))

      true

  // Verify-on-read (design/tool.md §2.4): the object's bytes must recompute the name they
  // are stored under. Payloads are not fetched here: their key is over the decompressed
  // stream, so they verify through the owning manifest (`blobStream`).
  def fetch(tier: Tier, hex: Text): Data raises IoError raises StoreError =
    val target = objectPath(tier, hex)
    if !target.existent() then abort(StoreError(t"no $hex in ${tier.dirName}"))
    val data = target.read[Data]

    val actual = tier match
      case Tier.Manifest   => manifestBytesHash(data)
      case Tier.Payload    => abort(StoreError(t"payloads verify via their manifest"))
      case Tier.Blob       => LiraHash(LiraHash.Domain.Blob, data)
      case Tier.Derivative => LiraHash(LiraHash.Domain.Derivative, data)

    if actual.serialize[Hex] != hex
    then abort(StoreError(t"object $hex in ${tier.dirName} fails verification"))

    data

  // The decompressed blob stream of a release, verified against the manifest's declared
  // length and hash on every read.
  def blobStream(manifest: LiraManifest): Data raises IoError raises LiraError raises StoreError =
    val hex = manifest.payload.hash.serialize[Hex]
    val target = objectPath(Tier.Payload, hex)
    if !target.existent() then abort(StoreError(t"no $hex in payload"))
    LiraPayload.decompress(target.read[Data], manifest.payload.length, manifest.payload.hash)

  // Ingest (design/tool.md §2.2): verify eagerly at install grade, then decompose. The
  // manifest tier keeps the head bytes exactly as they arrived — directive, manifest, and
  // `##` separator — so the original file is a concatenation away.
  def ingest(data: Data): Ingested raises IoError raises LiraError raises StoreError =
    val lira = Lira.read(data)
    val report = Verification.install(lira)

    val separator = separatorIndex(data).lest(StoreError(t"the separator vanished"))
    val head = slice(data, 0, separator + 4)
    val manifestHex = manifestBytesHash(head).serialize[Hex]
    val payloadHex = lira.manifest.payload.hash.serialize[Hex]

    val fresh = put(Tier.Manifest, manifestHex, head)
    put(Tier.Payload, payloadHex, lira.compressed)

    var added = 0
    var shared = 0

    report.blobstore.blobs.stdlib.each: blob =>
      if put(Tier.Blob, blob.hash.serialize[Hex], blob.data) then added += 1 else shared += 1

    journal(t"add", manifestHex)
    Ingested(manifestHex, payloadHex, lira.manifest.module, fresh, added, shared)

  // The journal is advisory (design/tool.md §3): append order is recency, torn lines are
  // skipped at read, and no lock is taken.
  def journal(verb: Text, hex: Text): Unit raises IoError =
    if !journalPath.existent() then journalPath.create[File](CreateFlag.Parents): handle ?=> ()

    Eof(journalPath).open(Write): handle ?=>
      handle.write(Chain(t"$verb $hex\n".in[Data]))

  def recency(): Map[Text, Int] raises IoError =
    if !journalPath.existent() then Map() else
      val entries = journalPath.read[Data].utf8.cut(t"\n").stdlib.zipWithIndex.flatMap:
        (line, index) =>
          line.cut(t" ").stdlib match
            case scala.List(_, hex) => scala.List((hex, index))
            case _                  => scala.List()

      Map.from(entries)

  def pin(hex: Text, label: Text): Unit raises IoError =
    val target = child(pinDir, hex)

    target.create[File](CreateFlag.Parents, CreateFlag.Replace): handle ?=>
      handle.write(Chain(label.in[Data]))

  def unpin(hex: Text): Unit raises IoError =
    child(pinDir, hex).wipe()

  def pins(): List[Text] raises IoError =
    if !pinDir.existent() then List()
    else List.from(pinDir.children.stdlib.map(_.name))

  private def objects(tier: Tier): List[Path on Linux] raises IoError =
    val dir = tierDir(tier)

    if !dir.existent() then List() else
      List.from:
        dir.children.stdlib.flatMap { fan => fan.children.stdlib }

  private def decodeHead(head: Data): Optional[LiraManifest] =
    separatorIndex(head).let: separator =>
      safely[TelError | LiraError]:
        val document = slice(head, 0, separator + 1).utf8.load[Tel]
        LiraManifest.decode(document.root)

  // Every release in the store, sized by its payload object plus its manifest head. Blob
  // and derivative bytes are shared between releases, so they are accounted at sweep time,
  // not per release.
  def releases(): List[Release] raises IoError =
    val pinned = pins().stdlib.to(scala.collection.immutable.Set)
    val backend = summon[FilesystemBackend on Linux]

    objects(Tier.Manifest).flatMap: path =>
      val hex = path.name

      decodeHead(safely(path.read[Data]).or(Data())).option.toList.map: manifest =>
        val payloadPath = objectPath(Tier.Payload, manifest.payload.hash.serialize[Hex])

        val size = safely(backend.stat(path, false).size).or(0L)
          + safely(backend.stat(payloadPath, false).size).or(0L)

        Release(hex, manifest, size, pinned.contains(hex))

  // GC (design/tool.md §3): pins are roots; the eviction unit is the release closure; the
  // budget bounds the bytes held by *unpinned* releases, least recently used first. Blobs
  // and derivatives survive if any retained release references them.
  def gc(budget: Optional[Long]): Sweep raises IoError raises LiraError raises StoreError =
    val all = releases().stdlib
    val order = recency().stdlib

    val unpinned = all.filter(!_.pinned)
    val lru = unpinned.sortBy { release => order.getOrElse(release.hex, -1) }
    val unpinnedTotal = unpinned.map(_.size).sum

    var excess = budget.let { limit => unpinnedTotal - limit }.or(0L)

    val evicted = lru.takeWhile: release =>
      if excess <= 0L then false else
        excess -= release.size
        true

    val evictedSet = evicted.map(_.hex).to(scala.collection.immutable.Set)
    val retained = all.filter { release => !evictedSet.contains(release.hex) }

    val retainedPayloads = retained.map(_.manifest.payload.hash.serialize[Hex]).to:
      scala.collection.immutable.Set

    evicted.each: release =>
      objectPath(Tier.Manifest, release.hex).wipe()

    // Payloads survive only while some retained manifest owns them; this also collects
    // payloads orphaned by `remove`, whose manifest is already gone.
    var payloadsRemoved = 0

    objects(Tier.Payload).each: path =>
      if !retainedPayloads.contains(path.name) then
        path.wipe()
        payloadsRemoved += 1

    // Reference sets come from the retained payloads themselves — GC is rare enough that
    // decompression here beats maintaining a second index that could drift.
    val referencedBlobs = retained.flatMap: release =>
      safely(BlobStream.read(blobStream(release.manifest)).blobs.stdlib).or(scala.List())
        . map(_.hash.serialize[Hex])
    . to(scala.collection.immutable.Set)

    val referencedDerivatives = retained.flatMap: release =>
      release.manifest.section.stdlib.flatMap { section => section.derivative.option.toList }
        . map(_.serialize[Hex])
    . to(scala.collection.immutable.Set)

    var blobsRemoved = 0
    var derivativesRemoved = 0

    objects(Tier.Blob).each: path =>
      if !referencedBlobs.contains(path.name) then
        path.wipe()
        blobsRemoved += 1

    objects(Tier.Derivative).each: path =>
      if !referencedDerivatives.contains(path.name) then
        path.wipe()
        derivativesRemoved += 1

    // Compact the journal to one line per retained release, preserving recency order.
    if journalPath.existent() then
      val lines = retained.sortBy { release => order.getOrElse(release.hex, -1) }
        . map { release => t"add ${release.hex}" }

      journalPath.create[File](CreateFlag.Parents, CreateFlag.Replace): handle ?=>
        handle.write(Chain(Text(lines.map(_.s).mkString("", "\n", "\n")).in[Data]))

    Sweep(List.from(evicted.map(_.hex)), payloadsRemoved, blobsRemoved, derivativesRemoved)

  // Removes one release's manifest; the rest of its closure — payload, blobs, derivatives
  // not shared with a survivor — falls to the next `gc`.
  def remove(hex: Text): Unit raises IoError raises StoreError =
    val target = objectPath(Tier.Manifest, hex)
    if !target.existent() then abort(StoreError(t"no $hex in manifest"))
    target.wipe()

  def locate(prefix: Text): List[(Tier, Path on Linux)] raises IoError =
    List.from:
      scala.List(Tier.Manifest, Tier.Payload, Tier.Blob, Tier.Derivative).flatMap: tier =>
        objects(tier).stdlib.filter(_.name.starts(prefix)).map { path => (tier, path) }

  // Full re-audit (design/tool.md §2.4): every object is rehashed — including those never
  // read — and mismatches are quarantined, never deleted. Payloads verify through their
  // owning manifest; a payload no manifest owns is an orphan, not corruption.
  def fsck(): Audit raises IoError raises StoreError =
    var count = 0
    var corrupted: List[Text] = List()
    var orphans: List[Text] = List()

    def quarantine(tier: Tier, path: Path on Linux): Unit =
      val target = child(quarantineDir, t"${tier.dirName}.${path.name}")
      path.moveTo(target)
      corrupted = t"${tier.dirName}/${path.name}" :: corrupted

    List(Tier.Manifest, Tier.Blob, Tier.Derivative).each: tier =>
      objects(tier).each: path =>
        count += 1
        val good = safely(fetch(tier, path.name)).present
        if !good then quarantine(tier, path)

    val owned = releases().stdlib.map { release => release.manifest }

    val lengths = owned.map { manifest => (manifest.payload.hash.serialize[Hex], manifest) }.toMap

    objects(Tier.Payload).each: path =>
      count += 1

      lengths.get(path.name) match
        case scala.Some(owner) =>
          val good = safely(blobStream(owner)).present
          if !good then quarantine(Tier.Payload, path)

        case _ =>
          orphans = path.name :: orphans

    Audit(count, corrupted, orphans)

  // The bare-artifact lookup behind `lira id` (spec §13.6): hash the candidate under the
  // derivative domain and search stored manifests for a section that declares it.
  def identify(data: Data): Optional[(Release, Section)] raises IoError =
    val hex = LiraHash(LiraHash.Domain.Derivative, data).serialize[Hex]

    val matches = releases().flatMap: release =>
      release.manifest.section.stdlib.flatMap: section =>
        section.derivative.option.toList.filter(_.serialize[Hex] == hex)
          . map { _ => (release, section) }

    matches match
      case pair :: _ => pair
      case _         => Unset