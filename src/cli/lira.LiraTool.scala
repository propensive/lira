package lira

import java.nio.file as jnf

import anticipation.*
import contingency.*
import exoskeleton.*
import gossamer.*
import rudiments.*
import reliquary.*
import turbulence.*
import vacuous.*

import strategies.throwUnsafely

// The `lira` command-line tool (LIRA specification §5.1): the PATH-resolved handler that the
// interpreter directive of every `.lira` file invokes. Its default action presents the
// manifest; `verify` performs install-grade verification; `jar` emits a section's canonical
// derivative artifact; `assign` gives a development release its derived version.
object LiraTool extends Application:

  def invoke(using cli: Cli): Exit =
    given Stdio = cli.stdio
    val arguments = cli.arguments.map(_.value)

    try arguments.stdlib match
      case scala.List(file) if !file.s.startsWith("-") => manifest(file)
      case scala.List(t"verify", file)                 => verify(file)
      case scala.List(t"jar", universe, file)          => jar(universe, file)
      case scala.List(t"assign", file)                 => assign(file, Unset, false)
      case scala.List(t"assign", file, t"--major")     => assign(file, Unset, true)
      case scala.List(t"assign", file, previous)       => assign(file, previous, false)

      case scala.List(t"assign", file, previous, t"--major") =>
        assign(file, previous, true)

      case _ => usage()

    catch
      case error: LiraError =>
        Out.println(t"lira: ${error.message}")
        Exit(1)

      case error: Exception =>
        Out.println(t"lira: ${Text(error.toString)}")
        Exit(2)

  private def usage()(using Cli, Stdio): Exit =
    Out.println(t"Usage: lira <file.lira>                    show the manifest")
    Out.println(t"       lira verify <file.lira>             verify the file (install grade)")
    Out.println(t"       lira jar <universe> <file.lira>     write the canonical derivative JAR")
    Out.println(t"       lira assign <file.lira> [<previous.lira>] [--major]")
    Out.println(t"                                           assign the next derived version")
    Exit(1)

  private def load(file: Text): Data =
    Array.unsafeFrozen(jnf.Files.readAllBytes(jnf.Paths.get(file.s)).nn)

  private def save(file: Text, data: Data): Unit =
    jnf.Files.write(jnf.Paths.get(file.s), Array.unsafeJvm(data))

  private def stem(file: Text): Text =
    if file.s.endsWith(".lira") then Text(file.s.dropRight(5)) else file

  // The manifest is everything before the first `##` line; §5.2 fixes the byte layout so this
  // needs no TEL parsing, and the author's formatting is preserved exactly.
  private def manifest(file: Text)(using Cli, Stdio): Exit =
    val data = load(file)
    val separator = scala.List[Byte]('\n', '#', '#', '\n')
    var index = 0
    var found = -1

    while found < 0 && index + 4 <= data.length do
      if data(index) == separator(0) && data(index + 1) == separator(1)
          && data(index + 2) == separator(2) && data(index + 3) == separator(3)
      then found = index
      index += 1

    if found < 0 then
      Out.println(t"lira: the file has no document separator")
      Exit(1)
    else
      val text = String(Array.unsafeJvm(data), 0, found + 1, "UTF-8")
      Out.println(Text(text))
      Exit.Ok

  private def verify(file: Text)(using Cli, Stdio): Exit =
    val lira = Lira.read(load(file))
    val report = Verification.install(lira)
    val manifest = lira.manifest

    Out.println(t"module:    ${manifest.module}")
    manifest.version.let { version => Out.println(t"version:   $version") }
    if manifest.development then Out.println(t"version:   (development release)")
    Out.println(t"snapshot:  ${LiraHash.text(manifest.lineage.stdlib.last)}")
    Out.println(t"payload:   ${LiraHash.text(manifest.payload.hash)}")
    Out.println(t"sections:  ${Text(manifest.section.stdlib.map(_.universe.s).mkString(", "))}")

    report.advisories.stdlib.foreach: advisory =>
      Out.println(t"advisory:  ${Text(advisory.toString)}")

    Out.println(t"verified (install grade)")
    Exit.Ok

  private def jar(universe: Text, file: Text)(using Cli, Stdio): Exit =
    val lira = Lira.read(load(file))
    val report = Verification.install(lira)

    report.materialized.stdlib.find(_(0) == universe) match
      case scala.Some(pair) =>
        val data = Derivative.jar(pair(1), report.blobstore)
        val target = t"${stem(file)}-$universe.jar"
        save(target, data)
        Out.println(t"wrote $target (${LiraHash.text(LiraHash(LiraHash.Domain.Derivative, data))})")
        Exit.Ok

      case _ =>
        Out.println(t"lira: the release has no $universe section")
        Exit(1)

  private def assign(file: Text, previous: Optional[Text], forceMajor: Boolean)
    ( using Cli, Stdio )
  :   Exit =

    val release = Lira.read(load(file))
    val before = previous.let { path => Lira.read(load(path)) }
    val published = before.let { lira => List(lira.manifest) }.or(List())
    val manifest = Publication.assign(release, before, published, forceMajor)

    val stream = LiraPayload.decompress
      (release.compressed, release.manifest.payload.length, release.manifest.payload.hash)

    val blobs = BlobStream.read(stream).blobs.map(_.data)
    val version = manifest.version.let { version => t"$version" }.or(t"unversioned")
    val target = t"${stem(file)}-$version.lira"
    save(target, Lira.assemble(manifest, blobs))
    Out.println(t"assigned $version -> $target")
    Exit.Ok
