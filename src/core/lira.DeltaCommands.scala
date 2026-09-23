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

import environments.daemonClientEnvironment
import filesystemBackends.javaBaseFilesystem
import logging.silentLogging
import systems.javaBaseSystem

// `lira delta` and `lira add` (design/tool.md §5): producing a delta from two cached releases
// of one module, and adding a file to the store — a whole `.lira` file as it is, or a delta by
// reconstructing the release it carries from the base the store already holds.
private def versionOf(release: Store.Release): Text =
  release.manifest.version.let { version => t"$version" }.or(t"development")

private def deltaCommand(coord: Text, from: Text, to: Text, out: Optional[Text])(using cli: Cli)
:   Exit = command:

  given Stdio = cli.stdio
  val store = Store.default()
  val releases = store.releases().filter(_.manifest.module == coord)

  if releases.nil then
    Out.println(t"lira: no cached release of $coord")
    Exit.Fail(1)
  else
    def find(version: Text): Optional[Store.Release] =
      releases.filter { release => versionOf(release) == version } match
        case release :: Nil => release

        case Nil =>
          Out.println(t"lira: $coord $version is not cached")
          Unset

        case _ =>
          Out.println(t"lira: $coord $version is cached more than once")
          Unset

    find(from).let: base =>
      find(to).let: target =>
        val baseHash = Lira.Hash.text(base.manifest.payload.hash)

        if baseHash == Lira.Hash.text(target.manifest.payload.hash) then
          Out.println(t"lira: $from and $to carry the same payload; there is nothing to delta")
          Exit.Fail(1)
        else
          val baseStore = BlobStream.read(store.blobStream(base.manifest))
          val targetStore = BlobStream.read(store.blobStream(target.manifest))
          val pairing = DeltaFile.pairings(base.manifest, baseStore, target.manifest, targetStore)
          val produced = DeltaFile.produce(baseStore, targetStore, pairing, Priming.Window)
          val body = DeltaFile.encode(produced.commands)
          val compressed = Lira.Payload.compress(body)

          val header =
            DeltaFile.Header
              (baseHash, t"brotli", Priming.Window, Priming.Block, body.length.toLong)

          val head = store.fetch(Store.Tier.Manifest, target.hex)
          val file = DeltaFile.assemble(head, header, compressed)
          val name = coord.cut(t"/").reverse.prim.or(coord)
          val path = resolve(out.or(t"$name-$from-$to.lira"))
          save(path, file)

          facts(scala.List
            ( (t"module", coord),
              (t"versions", t"$from -> $to"),
              (t"base", baseHash),
              (t"kept", t"${produced.kept} records"),
              (t"skipped", t"${produced.skipped}"),
              (t"stored", t"${produced.stored}"),
              (t"updated", t"${produced.updated}"),
              (t"delta", t"${file.length} bytes"),
              (t"release", t"${target.size} bytes") ))

          Out.println(t"")
          Out.println(t"wrote ${path.encode}")
          Exit.Ok
      . or(Exit.Fail(1))
    . or(Exit.Fail(1))

private def addCommand(files: List[Text])(using cli: Cli): Exit = command:
  given Stdio = cli.stdio
  val store = Store.default()
  files.each { file => addFile(store, clientPath(file).read[Data]) }
  Exit.Ok

// Adds one file: a delta is reconstructed against its base and then ingested exactly as a whole
// file is, so verification (spec §16) runs over the reconstruction in full before anything is
// recorded as the release.
private def addFile(store: Store, data: Data)(using Stdio)
:   Unit raises Io.Error raises Lira.Error raises StoreError raises DeltaError =

  def report(ingested: Store.Ingested, origin: Text): Unit =
    val status = if ingested.fresh then t"added" else t"already cached"
    val counts = t"${ingested.blobsAdded} new blobs, ${ingested.blobsShared} shared"
    Out.println(t"${ingested.module}: $status$origin (${ingested.manifestHex.keep(12)}, $counts)")

  if !DeltaFile.isDelta(data) then report(store.ingest(data), t"") else
    val parsed = DeltaFile.parse(data)
    val header = parsed.header

    val manifest =
      store.decodeHead(parsed.head).lest(DeltaError(t"the delta's manifest does not decode"))

    val base = store.releases()
      . seek { release => Lira.Hash.text(release.manifest.payload.hash) == header.base }
      . lest:
          val base = header.base
          DeltaError(t"the base release (payload $base) is not cached; add it first")

    val baseStore = BlobStream.read(store.blobStream(base.manifest))

    val body =
      try parsed.body.decompress[Brotli] catch case error: Exception =>
        abort(DeltaError(t"the body does not decompress"))

    if body.length.toLong != header.length
    then abort(DeltaError(t"the body's length is not the header's"))

    val stream = DeltaFile.apply(header, baseStore, body, manifest.payload.length)

    if stream.length.toLong != manifest.payload.length
        || Blob.compare(Lira.Payload.hash(stream), manifest.payload.hash) != 0
    then abort(DeltaError(t"the reconstruction does not match the manifest's payload"))

    val full = DeltaFile.join(List(parsed.head, Lira.Payload.compress(stream)))
    val origin = t" from ${base.manifest.module} ${versionOf(base)}"
    report(store.ingest(full), origin)
