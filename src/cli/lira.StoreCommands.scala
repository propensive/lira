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

import alphabets.hexLowerCase
import charDecoders.utf8Decoder
import charEncoders.utf8Encoder
import columnAttenuation.ignoreAttenuation
import environments.daemonClientEnvironment
import filesystemBackends.virtualMachine
import filesystemOptions.createNonexistentParents.enabled
import filesystemOptions.deleteRecursively.enabled
import filesystemOptions.dereferenceSymlinks.disabled
import filesystemOptions.moveAtomically.enabled
import filesystemOptions.overwritePreexisting.enabled
import logging.silentLogging
import systems.javaSystem
import tableStyles.thinRoundedTableStyle
import textMetrics.uniformMetric
import textSanitizers.strictSanitizer

// Command handlers for the store group (design/tool.md §5). Relative paths resolve against
// the client's forwarded working directory, exactly as the artifact commands do.
private def clientPath(file: Text)(using cli: Cli): Path on Linux =
  unsafely:
    safely(file.as[Path on Linux]).or:
      t"${cli.workingDirectory.directory()}/$file".as[Path on Linux]

private def stemOf(file: Text): Text =
  if file.ends(t".lira") then file.keep(file.length - 5) else file

private def single(store: Store, prefix: Text)(using Stdio)
:   Optional[Store.Release] raises IoError =

  store.releases().filter(_.hex.starts(prefix)).stdlib match
    case scala.List(release) => release

    case scala.List() =>
      Out.println(t"lira: no cached release matches $prefix")
      Unset

    case _ =>
      Out.println(t"lira: $prefix is ambiguous")
      Unset

private def cache(args: List[Text])(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val store = Store.default()

  def cacheUsage(): Exit =
    Out.println(t"Usage: lira cache add <file.lira> ...")
    Out.println(t"       lira cache ls")
    Out.println(t"       lira cache rm <hash-prefix>")
    Out.println(t"       lira cache path <hash-prefix>")
    Exit.Fail(1)

  args match
    case verb :: rest => verb.s match
      case "add" if !rest.stdlib.isEmpty =>
        rest.each: file =>
          val ingested = store.ingest(clientPath(file).read[Data])
          val status = if ingested.fresh then t"added" else t"already cached"
          val counts = t"${ingested.blobsAdded} new blobs, ${ingested.blobsShared} shared"
          Out.println(t"${ingested.module}: $status (${ingested.manifestHex.keep(12)}, $counts)")

        Exit.Ok

      case "ls" if rest.stdlib.isEmpty =>
        val rows = store.releases().stdlib.sortBy(_.manifest.module.s)

        if rows.isEmpty then Out.println(t"the store is empty") else
          val scaffold = Scaffold[Store.Release, Text]
            ( Column(t"Release")(_.hex.keep(12)),
              Column(t"Module")(_.manifest.module),
              Column(t"Version"): release =>
                release.manifest.version.let { version => t"$version" }.or(t"dev"),
              Column(t"Bytes")(_.size),
              Column(t"")(release => if release.pinned then t"pinned" else t"") )

          scaffold.tabulate(List.from(rows)).grid(110).render.stdlib.each(Out.println(_))

        Exit.Ok

      case "rm" => rest.stdlib match
        case scala.List(prefix) =>
          single(store, prefix).let: release =>
            if release.pinned then
              Out.println(t"lira: ${release.hex.keep(12)} is pinned; unpin it first")
              Exit.Fail(1)
            else
              store.remove(release.hex)
              val sweep = store.gc(Unset)
              val note = t"${sweep.blobsRemoved} blobs collected"
              Out.println(t"removed ${release.manifest.module} (${release.hex.keep(12)}, $note)")
              Exit.Ok
          . or(Exit.Fail(1))

        case _ => cacheUsage()

      case "path" => rest.stdlib match
        case scala.List(prefix) =>
          val matches = store.locate(prefix).stdlib

          if matches.isEmpty then
            Out.println(t"lira: no object matches $prefix")
            Exit.Fail(1)
          else
            matches.each { (tier, path) => Out.println(path.encode) }
            Exit.Ok

        case _ => cacheUsage()

      case _ => cacheUsage()

    case _ => cacheUsage()

private def pin(prefix: Text, add: Boolean)(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val store = Store.default()

  single(store, prefix).let: release =>
    if add then
      store.pin(release.hex, release.manifest.module)
      Out.println(t"pinned ${release.manifest.module} (${release.hex.keep(12)})")
    else
      store.unpin(release.hex)
      Out.println(t"unpinned ${release.manifest.module} (${release.hex.keep(12)})")

    Exit.Ok
  . or(Exit.Fail(1))

private def gcCommand(budget: Optional[Text])(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val store = Store.default()

  def sweep(limit: Optional[Long]): Exit =
    val swept = store.gc(limit)
    val evicted = swept.evicted.stdlib.size
    val collected = t"${swept.payloadsRemoved} payloads, ${swept.blobsRemoved} blobs"
    Out.println(t"evicted $evicted releases; collected $collected, ${swept.derivativesRemoved} derivatives")
    Exit.Ok

  budget match
    case text: Text => safely(text.as[Long]) match
      case limit: Long => sweep(limit)

      case _ =>
        Out.println(t"lira: '$text' is not a byte count")
        Exit.Fail(1)

    case _ => sweep(Unset)

private def fsck()(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val store = Store.default()
  val audit = store.fsck()

  Out.println(t"audited ${audit.objects} objects")
  audit.corrupted.each { name => Out.println(t"quarantined: $name") }
  audit.orphanPayloads.each { name => Out.println(t"orphan payload: $name (gc collects it)") }

  if audit.corrupted.stdlib.isEmpty then Exit.Ok else Exit.Fail(1)

private def identify(file: Text)(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val store = Store.default()

  store.identify(clientPath(file).read[Data]).let: (release, section) =>
    val version = release.manifest.version.let { version => t"$version" }.or(t"development")
    val integration = section.integration.let { id => t", integration $id" }.or(t"")
    Out.println(t"${release.manifest.module} $version (${section.realm}$integration)")
    Exit.Ok

  . or:
      Out.println(t"lira: unknown artifact (no cached release declares it)")
      Exit.Fail(1)

// `lira jar` via the store (design/tool.md §2.5): the derivative tier is the materialization
// cache, so a declared derivative already cached is linked out without decompressing
// anything; otherwise the JAR is built, cached, and linked out.
private def storeJar(universe: Text, file: Text)(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val store = Store.default()
  val data = clientPath(file).read[Data]
  val lira = Lira.read(data)
  store.ingest(data)

  val declared: Optional[Data] = lira.manifest.section.stdlib
    . find { section => section.realm == universe }
    . map(_.derivative)
    . getOrElse(Unset)

  val cached: Optional[Data] =
    declared.let { hash => safely(store.fetch(Store.Tier.Derivative, hash.serialize[Hex])) }

  val jarData: Optional[Data] = cached.or:
    val report = Verification.install(lira)

    report.materialized.stdlib.find { pair => pair(0).realm == universe } match
      case scala.Some(pair) =>
        val built = Derivative.jar(pair(1), report.blobstore)
        val hex = LiraHash(LiraHash.Domain.Derivative, built).serialize[Hex]
        store.put(Store.Tier.Derivative, hex, built)
        built

      case _ => Unset

  jarData.let: bytes =>
    val hex = LiraHash(LiraHash.Domain.Derivative, bytes).serialize[Hex]
    store.journal(t"use", hex)
    val source = store.objectPath(Store.Tier.Derivative, hex)
    val target = clientPath(t"${stemOf(file)}-$universe.jar")
    target.wipe()
    safely(source.hardLinkTo(target)).or(source.copyTo(target))
    Out.println(t"wrote ${target.encode} (${LiraHash.text(LiraHash(LiraHash.Domain.Derivative, bytes))})")
    Exit.Ok

  . or:
      Out.println(t"lira: the release has no $universe section")
      Exit.Fail(1)
