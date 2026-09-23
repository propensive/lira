                                                                                                  /*
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                                                                                                  ┃
┃                                 ╭───╮╭───╮                                                       ┃
┃                                 │   ││   │                                                       ┃
┃                                 │   │╰───╯                                                       ┃
┃                                 │   │╭───╮╭───╮╌────╮╭─────────╮                                 ┃
┃                                 │   ││   ││   ╭──╮  ││   ╭─╮   │                                 ┃
┃                                 │   ││   ││   │  ╰──╯│   │ │   │                                 ┃
┃                                 │   ││   ││   │      │   │ │   │                                 ┃
┃                                 │   ││   ││   │      │   ╰─╯   │                                 ┃
┃                                 ╰───╯╰───╯╰───╯      ╰─────╌╰──╯                                 ┃
┃                                                                                                  ┃
┃    LIRA, version 0.1.0.                                                                          ┃
┃    © Copyright 2026 Jon Pretty, Propensive OÜ.                                                   ┃
┃                                                                                                  ┃
┃    The primary distribution site is:                                                             ┃
┃                                                                                                  ┃
┃        https://lira.nexus/                                                                       ┃
┃                                                                                                  ┃
┃    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file     ┃
┃    except in compliance with the License. You may obtain a copy of the License at                ┃
┃                                                                                                  ┃
┃        https://www.apache.org/licenses/LICENSE-2.0                                               ┃
┃                                                                                                  ┃
┃    Unless required by applicable law or agreed to in writing,  software distributed under the    ┃
┃    License is distributed on an "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    ┃
┃    either express or implied. See the License for the specific language governing permissions    ┃
┃    and limitations under the License.                                                            ┃
┃                                                                                                  ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                                                                                                  */
package lira

import soundness.*

import charDecoders.utf8Decoder
import charEncoders.utf8Encoder

// A delta file did not parse, named a base the store does not hold, or did not reconstruct the
// release its manifest describes. Nothing a delta yields is the release until the whole stream
// has verified (increment.md §7, L155), so every failure here rejects the file entire.
case class DeltaError(detail: Text)(using Diagnostics)
extends Error(m"delta: $detail")

// The delta file (spec/increment.md, which calls it an increment): a release carried relative to
// a base the receiver holds. In the byte layout reliquary implements today — directive, TEL
// manifest, `##` separator, payload — a delta is the target's manifest head verbatim, then a
// second document, the delta header, then a second separator, then the Brotli-compressed command
// stream. A file whose first payload byte would begin a Brotli stream instead begins the header's
// pragma line, `delta 1.0`, which is how `lira add` tells the two apart without a magic number of
// its own.
object DeltaFile:
  val pragma: Text = t"delta 1.0"
  private val pragmaBytes: Data = utf8Encoder.encoded(t"$pragma\n")
  private val separatorBytes: Data = utf8Encoder.encoded(t"##\n")

  // The one byte-assembly primitive of this file: chunks are collected as an immutable list
  // and laid into a single fresh buffer once their total is known.
  def join(chunks: List[Data]): Data =
    val total = chunks.fold(0) { (sum, chunk) => sum + chunk.length }
    val buffer = Array.allocate[Byte](total)
    var offset = 0

    chunks.each: chunk =>
      buffer.place(chunk, 0, offset, chunk.length)
      offset += chunk.length

    Array.freeze(buffer)

  object Header:
    def parse(text: Text): Header raises DeltaError =
      val lines = text.lines.map(_.trim).filter(_.length > 0)

      lines match
        case first :: rest if first == pragma =>
          def collect(lines: List[Text], fields: Map[Text, Text]): Map[Text, Text] = lines match
            case line :: rest => line.cut(t" ", 2) match
              case key :: value :: Nil => collect(rest, fields.define(key, value.trim))
              case _                   => abort(DeltaError(t"the header line '$line' is malformed"))

            case _ => fields

          val fields = collect(rest, Map.empty[Text, Text])

          def field(name: Text): Text =
            fields.at(name).lest(DeltaError(t"the header lacks a `$name` field"))

          def number(name: Text): Long =
            safely(field(name).as[Long]).lest(DeltaError(t"the header's `$name` is not a number"))

          val window = number(t"window")
          val block = number(t"block")

          if window < 10 || window > 24
          then abort(DeltaError(t"the window must be between 10 and 24 bits"))

          if block < 1 || block > (1L << 24)
          then abort(DeltaError(t"the block must be between 1 and 2^24 bytes"))

          val compression = field(t"compression")

          if compression != t"brotli"
          then abort(DeltaError(t"unknown compression '$compression'"))

          Header(field(t"base"), compression, window.toInt, block.toInt, number(t"length"))

        case _ => abort(DeltaError(t"the file is not a delta"))

  // The header (increment.md §4): the base by payload hash, the body's compression, the two
  // priming parameters, and the body's decompressed length — a consistency check, never a bound,
  // since the header is unsigned.
  case class Header(base: Text, compression: Text, window: Int, block: Int, length: Long):
    def render: Text =
      val fields = t"base $base\ncompression $compression\nwindow $window\nblock $block"
      t"$pragma\n\n$fields\nlength $length\n"

  // A parsed delta: the target's manifest head — directive through separator, verbatim — the
  // header, and the compressed body.
  case class Parsed(head: Data, header: Header, body: Data)

  // Locates `\n##\n` at or after `from`; the index of the newline that opens the line.
  private def separatorFrom(data: Data, from: Int): Optional[Int] =
    var index = from

    while index + 4 <= data.length do
      val bytes = data.readable

      if bytes(index) == '\n' && bytes(index + 1) == '#' && bytes(index + 2) == '#'
          && bytes(index + 3) == '\n'
      then return index
      index += 1

    Unset

  private def startsWith(data: Data, offset: Int, prefix: Data): Boolean =
    if offset + prefix.length > data.length then false else
      var i = 0
      while i < prefix.length && data.readable(offset + i) == prefix.readable(i) do i += 1
      i == prefix.length

  def isDelta(data: Data): Boolean =
    Store.separatorIndex(data).let { separator => startsWith(data, separator + 4, pragmaBytes) }
      . or(false)

  def parse(data: Data): Parsed raises DeltaError =
    val first = Store.separatorIndex(data).lest(DeltaError(t"the file has no document separator"))
    val head = Store.slice(data, 0, first + 4)

    if !startsWith(data, first + 4, pragmaBytes) then abort(DeltaError(t"the file is not a delta"))

    val second = separatorFrom(data, first + 4).lest:
      DeltaError(t"the delta header has no separator")

    val header = Header.parse(Store.slice(data, first + 4, second + 1).utf8)
    Parsed(head, header, Store.slice(data, second + 4, data.length))

  def assemble(head: Data, header: Header, body: Data): Data =
    join(List(head, utf8Encoder.encoded(header.render), separatorBytes, body))

  // The command stream (increment.md §5), in target order: a merge walk of two hash-sorted record
  // lists, with `keep` and `skip` run-length coded over the base and `store`/`update` carrying
  // what the base lacks.
  enum Command:
    case Keep(count: Int)
    case Skip(count: Int)
    case Store(bytes: Data)
    case Update(record: Int, length: Int, continuation: Data)

  case class Produced(commands: List[Command], kept: Int, skipped: Int, stored: Int, updated: Int)

  // The producer's pairing of target records with base records, by name (increment.md §8): the
  // record at the same path in the corresponding section's tree, failing that the base's root
  // tree; a section's tree blob with the base section's; an atoms blob with the base's for the
  // same discipline. Keyed by the target blob's hash text; values are base ordinals.
  def pairings
    ( baseManifest:   Lira.Manifest,
      base:           Blobstore,
      targetManifest: Lira.Manifest,
      target:         Blobstore )
  :   Map[Text, Int] raises Lira.Error =

    val ordinals: Map[Text, Int] =
      base.blobs.fold((Map.empty[Text, Int], 0)): (state, blob) =>
        (state(0).define(Lira.Hash.text(blob.hash), state(1)), state(1) + 1)
      . apply(0)

    var paired: Map[Text, Int] = Map.empty

    def pair(targetHash: Data, baseHash: Data): Unit =
      val key = Lira.Hash.text(targetHash)
      val baseKey = Lira.Hash.text(baseHash)

      if key != baseKey && !paired.defines(key) then
        ordinals.at(baseKey).let { ordinal => paired = paired.define(key, ordinal) }

    val baseSections = baseManifest.section
    val root = baseSections.prim

    targetManifest.section.each: section =>
      val same = baseSections.seek(_.key == section.key)

      val candidates: List[Section] = (same, root) match
        case (same: Section, root: Section) =>
          if same.key == root.key then List(same) else List(same, root)

        case (same: Section, _) => List(same)
        case (_, root: Section) => List(root)
        case _                  => List()

      candidates.prim.let { counterpart => pair(section.tree, counterpart.tree) }
      val tree = Lira.Tree.decode(target.resolve(section.tree))

      val baseTrees = candidates.map: counterpart =>
        Lira.Tree.decode(base.resolve(counterpart.tree))

      tree.entries.each: entry =>
        val found = baseTrees.fold[Optional[TreeEntry]](Unset): (found, tree) =>
          found.or(tree.get(entry.path))

        found.let { counterpart => pair(entry.blob, counterpart.blob) }

    targetManifest.api.each: api =>
      baseManifest.api.seek(_.discipline == api.discipline).let: counterpart =>
        pair(api.atoms, counterpart.atoms)

    paired

  def produce(base: Blobstore, target: Blobstore, pairing: Map[Text, Int], window: Int)
  :   Produced =

    val baseBlobs = base.blobs.to[Array].readable
    val count = baseBlobs.length
    var commands: List[Command] = List()
    var cursor = 0
    var keeping = 0
    var skipping = 0
    var kept = 0
    var skipped = 0
    var stored = 0
    var updated = 0

    def flush(): Unit =
      if keeping > 0 then
        commands = Command.Keep(keeping) :: commands
        kept += keeping
        keeping = 0

      if skipping > 0 then
        commands = Command.Skip(skipping) :: commands
        skipped += skipping
        skipping = 0

    target.blobs.each: blob =>
      while cursor < count && Blob.compare(baseBlobs(cursor).hash, blob.hash) < 0 do
        if keeping > 0 then flush()
        skipping += 1
        cursor += 1

      if cursor < count && Blob.compare(baseBlobs(cursor).hash, blob.hash) == 0 then
        if skipping > 0 then flush()
        keeping += 1
        cursor += 1
      else
        flush()

        val update: Optional[Command] =
          pairing.at(Lira.Hash.text(blob.hash)).let: ordinal =>
            val counterpart = baseBlobs(ordinal).data

            if counterpart.length == 0 || blob.data.length == 0 then Unset else
              val continuation = Priming.continuation(counterpart, blob.data, window)

              if continuation.length < blob.data.length
              then Command.Update(ordinal, blob.data.length, continuation)
              else Unset

        update match
          case cmd: Command =>
            commands = cmd :: commands
            updated += 1

          case _ =>
            commands = Command.Store(blob.data) :: commands
            stored += 1

    if cursor < count then
      if keeping > 0 then flush()
      skipping += count - cursor
      cursor = count

    flush()
    Produced(commands.reverse, kept, skipped, stored, updated)

  def encode(commands: List[Command]): Data =
    def varint(value: Long): Data = Varint.encode(value)

    def chunks(command: Command): List[Data] = command match
      case Command.Keep(count) => List(varint(0), varint(count))
      case Command.Skip(count) => List(varint(1), varint(count))
      case Command.Store(bytes) => List(varint(2), varint(bytes.length), bytes)

      case Command.Update(record, length, continuation) =>
        List(varint(3), varint(record), varint(length), varint(continuation.length), continuation)

    join(commands.flatMap(chunks))

  // Application (increment.md §7): the commands are read streaming and every declared length is
  // checked against `budget` — the target manifest's signed `payload.length` — before any bytes
  // are decoded for it. The result is the target's blob stream, which the caller verifies as one.
  def apply(header: Header, base: Blobstore, body: Data, budget: Long): Data raises DeltaError =
    val baseBlobs = base.blobs.to[Array].readable
    var chunks: List[Data] = List()
    var offset = 0
    var cursor = 0
    var emitted = 0L

    def malformed(detail: Text): Nothing = abort(DeltaError(detail))

    def varint(): Long =
      val decoded =
        import errorDiagnostics.emptyDiagnostics

        mitigate:
          case _: Varint.Error => DeltaError(t"a command is malformed")

        . protect(Varint.decode(body, offset))

      offset = decoded.next
      decoded.value

    def count(): Int =
      val value = varint()
      if value < 1L || value > Int.MaxValue.toLong then malformed(t"a run count is out of range")
      value.toInt

    def emit(bytes: Data): Unit =
      val prefix = Varint.encode(bytes.length.toLong)
      emitted += prefix.length + bytes.length
      if emitted > budget then malformed(t"the reconstruction exceeds the declared payload length")
      chunks = bytes :: prefix :: chunks

    def length(): Int =
      val value = varint()
      if value < 0L || value > budget - emitted
      then malformed(t"a record length exceeds the budget")
      value.toInt

    while offset < body.length do
      varint() match
        case 0L =>
          val n = count()
          if cursor + n > baseBlobs.length then malformed(t"a keep overruns the base")
          var i = 0
          while i < n do { emit(baseBlobs(cursor + i).data); i += 1 }
          cursor += n

        case 1L =>
          val n = count()
          if cursor + n > baseBlobs.length then malformed(t"a skip overruns the base")
          cursor += n

        case 2L =>
          val size = length()
          if offset + size > body.length then malformed(t"a stored record overruns the body")
          emit(Store.slice(body, offset, offset + size))
          offset += size

        case 3L =>
          val ordinal = varint()

          if ordinal < 0L || ordinal >= baseBlobs.length.toLong
          then malformed(t"an update names no base record")

          val size = length()
          val continuationLength = varint()

          if continuationLength > 2L*size + 64L
          then malformed(t"a continuation is longer than its bound")

          if offset + continuationLength > body.length.toLong
          then malformed(t"a continuation overruns the body")

          val continuation = Store.slice(body, offset, offset + continuationLength.toInt)
          offset += continuationLength.toInt
          val counterpart = baseBlobs(ordinal.toInt).data
          val decoded = Priming.decode(counterpart, continuation, header.window, header.block, size)
          emit(decoded.lest(DeltaError(t"a continuation does not decode against its base record")))

        case other => malformed(t"unknown command $other")

    if cursor != baseBlobs.length
    then malformed(t"the delta does not account for every base record")

    join(chunks.reverse)
