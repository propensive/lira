package lira

import java.nio.file as jnf

import soundness.*

import backstops.silentBackstop
import charDecoders.utf8Decoder
import classloaders.threadContextClassloader
import environments.daemonClientEnvironment
import executives.completions
import interpreters.posixInterpreter
import logging.silentLogging
import systems.javaSystem
import textSanitizers.strictSanitizer
import threading.platformThreading
import workingDirectories.systemWorkingDirectory

// The `lira` command-line tool (LIRA specification §5.1): the PATH-resolved handler that the
// interpreter directive of every `.lira` file invokes. It runs as an Ethereal daemon — the
// first invocation starts a background JVM; every later one attaches over a socket, giving
// millisecond startup and live tab-completions.
val Verify = Subcommand("verify", "verify a .lira file (install grade)")
val Harvest = Subcommand("harvest", "harvest a host's surface into tagged .lira contracts")
val Jar = Subcommand("jar", "write a section's canonical derivative JAR")
val Assign = Subcommand("assign", "assign the next derived version to a development release")
val Install = Subcommand("install", "install tab-completions into the shell")
val Help = Subcommand("help", "show usage information")
val Quit = Subcommand("quit", "shut down the background daemon")
val Major = Flag[Unit]("major", false, Nil, "begin a new major series (a fresh lineage)")

@main
def main(): Unit = cli:
  arguments match
    case Verify() :: file :: Nil  => execute(verify(file()))
    case Jar() :: universe :: file :: Nil => execute(jar(universe(), file()))
    case Assign() :: file :: Nil  => execute(assign(file(), Unset, Major().present))

    case Assign() :: file :: previous :: Nil =>
      execute(assign(file(), previous(), Major().present))

    case Harvest() :: kind :: out :: rest =>
      execute(harvest(kind(), out(), rest.map(_())))

    case Install() :: _           => execute(installCompletions())
    case Help() :: _              => execute(usage(Exit.Ok))
    case Quit() :: _              => execute(quit())

    case file :: Nil if !file().s.startsWith("-") =>
      execute(manifest(file()))

    case _ => execute(usage(Exit.Fail(1)))

private def usage(exit: Exit)(using cli: Cli): Exit =
  given Stdio = cli.stdio
  Out.println(t"Usage: lira <file.lira>                    show the manifest")
  Out.println(t"       lira verify <file.lira>             verify the file (install grade)")
  Out.println(t"       lira jar <universe> <file.lira>     write the canonical derivative JAR")
  Out.println(t"       lira assign <file.lira> [<previous.lira>] [--major]")
  Out.println(t"                                           assign the next derived version")
  Out.println(t"       lira harvest jdk <dir> [<ct.sym>] [+<tag> ...]")
  Out.println(t"                                           harvest the JDK lineage from ct.sym")
  Out.println(t"       lira harvest android <dir> <android.jar ...> [+<tag> ...]")
  Out.println(t"                                           harvest Android API levels")
  Out.println(t"       (a +<tag> argument sanctions that release as a major: a removal in the")
  Out.println(t"        vendor's history, beginning a fresh lineage)")
  Out.println(t"       lira install                        install shell tab-completions")
  Out.println(t"       lira help                           show this usage information")
  Out.println(t"       lira quit                           shut down the background daemon")
  exit

private def quit()(using service: DaemonService[?], cli: Cli): Exit =
  given Stdio = cli.stdio
  Out.println(t"lira: shutting down")
  service.shutdown()
  Exit.Ok

private def installCompletions()(using cli: Cli, service: DaemonService[?])
  ( using erased Effectful )
:   Exit =

  given Stdio = cli.stdio
  import errorDiagnostics.stackTracesDiagnostics
  import workingDirectories.javaWorkingDirectory

  given Entrypoint = summon[DaemonService[?]]

  recover:
    case error: InstallError =>
      Out.println(t"lira: could not install tab-completions")
      Exit.Fail(2)

  . protect:
      Completions.ensure(force = true).each(Out.println(_))
      Exit.Ok

private def guard(block: => Exit)(using cli: Cli): Exit =
  given Stdio = cli.stdio
  import strategies.throwUnsafely

  try block catch
    case error: LiraError =>
      Out.println(t"lira: ${error.message}")
      Exit.Fail(1)

    case error: Exception =>
      Out.println(t"lira: ${Text(error.toString)}")
      Exit.Fail(2)

// Relative paths arrive from the client and must resolve against the *client's* working
// directory, which the launcher forwards — never the daemon's own.
private def resolve(file: Text)(using cli: Cli): jnf.Path =
  val path = jnf.Paths.get(file.s).nn
  if path.isAbsolute then path
  else jnf.Paths.get(cli.workingDirectory.directory().s).nn.resolve(path).nn

private def load(file: Text)(using Cli): Data =
  Array.unsafeFrozen(jnf.Files.readAllBytes(resolve(file)).nn)

private def save(file: Text, data: Data)(using Cli): Unit =
  jnf.Files.write(resolve(file), Array.unsafeJvm(data))

private def stem(file: Text): Text =
  if file.s.endsWith(".lira") then Text(file.s.dropRight(5)) else file

// The manifest is everything before the first `##` line; §5.2 fixes the byte layout so this
// needs no TEL parsing, and the author's formatting is preserved exactly.
private def manifest(file: Text)(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  val data = load(file)
  var index = 0
  var found = -1

  while found < 0 && index + 4 <= data.length do
    if data(index) == '\n' && data(index + 1) == '#' && data(index + 2) == '#'
        && data(index + 3) == '\n'
    then found = index
    index += 1

  if found < 0 then
    Out.println(t"lira: the file has no document separator")
    Exit.Fail(1)
  else
    Out.println(Text(String(Array.unsafeJvm(data), 0, found + 1, "UTF-8")))
    Exit.Ok

private def verify(file: Text)(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val lira = Lira.read(load(file))
  val report = Verification.install(lira)
  val manifest = lira.manifest

  Out.println(t"module:    ${manifest.module}")
  manifest.version.let { version => Out.println(t"version:   $version") }
  if manifest.development then Out.println(t"version:   (development release)")
  Out.println(t"snapshot:  ${LiraHash.text(manifest.lineage.stdlib.last)}")
  Out.println(t"payload:   ${LiraHash.text(manifest.payload.hash)}")
  Out.println(t"sections:  ${Text(manifest.section.stdlib.map(_.world.s).mkString(", "))}")

  report.advisories.stdlib.each: advisory =>
    Out.println(t"advisory:  ${Text(advisory.toString)}")

  Out.println(t"verified (install grade)")
  Exit.Ok

private def jar(universe: Text, file: Text)(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val lira = Lira.read(load(file))
  val report = Verification.install(lira)

  report.materialized.stdlib.find { pair => pair(0).world == universe } match
    case scala.Some(pair) =>
      val data = Derivative.jar(pair(1), report.blobstore)
      val target = t"${stem(file)}-$universe.jar"
      save(target, data)
      Out.println(t"wrote $target (${LiraHash.text(LiraHash(LiraHash.Domain.Derivative, data))})")
      Exit.Ok

    case _ =>
      Out.println(t"lira: the release has no $universe section")
      Exit.Fail(1)

private def assign(file: Text, previous: Optional[Text], forceMajor: Boolean)(using cli: Cli)
:   Exit =

  guard:
    given Stdio = cli.stdio
    import strategies.throwUnsafely
    val release = Lira.read(load(file))
    val before = previous.let { path => Lira.read(load(path)) }
    val published = before.let { lira => proscenium.List(lira.manifest) }.or(proscenium.List())
    val manifest = Publication.assign(release, before, published, forceMajor)

    val stream = LiraPayload.decompress
      (release.compressed, release.manifest.payload.length, release.manifest.payload.hash)

    val blobs = BlobStream.read(stream).blobs.map(_.data)
    val version = manifest.version.let { version => t"$version" }.or(t"unversioned")
    val target = t"${stem(file)}-$version.lira"
    save(target, Lira.assemble(manifest, blobs))
    Out.println(t"assigned $version -> $target")
    Exit.Ok

// Harvests a host's surface into its contract lineages (LIRA hosts.md, jsig.md): where the
// vendor modularizes — the JDK — one contract module per platform module, coordinated across a
// vendor release by a shared tag (hosts.md §3, "Granularity"); for a flat platform — Android —
// one contract module. Majors — the removals in a vendor's history — require explicit `+<tag>`
// sanction (L110), applied per module wherever that vendor release removed surface. Emitted
// files carry the executable bit, as §5.1 requires of producers.
private def harvest(kind: Text, out: Text, extra: proscenium.List[Text])(using cli: Cli)
:   Exit =

  guard:
    given Stdio = cli.stdio
    import strategies.throwUnsafely

    val majors = extra.filter(_.s.startsWith("+")).map { tag => Text(tag.s.substring(1).nn) }
    val sources = extra.filter { arg => !arg.s.startsWith("+") }
    val directory = resolve(out)

    def emit(module: Text, releases: proscenium.List[HostRelease]): Boolean =
      val contracts =
        try
          HostContracts.assemble(
            module,
            releases,
            toolchain  = proscenium.List(LiraManifest.Tool(t"lira", t"0.1")),
            allowMajor = { tag => majors.stdlib.contains(tag) })
        catch case error: LiraError =>
          error.reason match
            case LiraError.Reason.UngradedSuccessor(tag) =>
              Out.println(t"lira: $module: $tag grades as a major over its predecessor (a")
              Out.println(t"      removal in the vendor's history); sanction it with +$tag")

            case _ => Out.println(t"lira: $module: ${error.message}")

          return false

      val target = directory.resolve(module.s).nn
      jnf.Files.createDirectories(target)

      contracts.stdlib.foreach: (tag, bytes) =>
        val file = target.resolve(s"$tag.lira").nn
        jnf.Files.write(file, Array.unsafeJvm(bytes))
        file.toFile.nn.setExecutable(true, false)

        val manifest = Lira.read(bytes).manifest
        val version = manifest.version.let { v => t"$v" }.or(t"unversioned")

        Out.println(t"$module $tag -> ${Text(file.toString)} ($version, lineage of ${manifest
            .lineage.stdlib.size})")

      true

    kind.s match
      case "jdk" =>
        val path = sources.stdlib.headOption.getOrElse:
          CtSym.location().or:
            Out.println(t"lira: no ct.sym found; pass its path explicitly")
            return Exit.Fail(1)

        val resolved = Text(resolve(path).toString)

        val perModule = scala.collection.mutable.LinkedHashMap
            [Text, scala.collection.mutable.ListBuffer[HostRelease]]()

        CtSym.releases(resolved).stdlib.foreach: release =>
          val tag = Text(s"jdk-$release")
          val modules = CtSym.modules(resolved, release)
          Out.println(t"harvested $tag (${modules.stdlib.size} modules)")

          modules.stdlib.foreach: (module, content) =>
            perModule.getOrElseUpdate(module, scala.collection.mutable.ListBuffer())
              += HostRelease(tag, content)

        var good = true

        perModule.foreach: (module, releases) =>
          if !emit(module, proscenium.List.from(releases.toList)) then good = false

        if good then Exit.Ok else Exit.Fail(1)

      case "android" =>
        if sources.stdlib.isEmpty then
          Out.println(t"lira: pass one android.jar per API level")
          return Exit.Fail(1)

        val level = java.util.regex.Pattern.compile("android-([0-9]+)").nn

        val parsed = sources.stdlib.map: jar =>
          val matcher = level.matcher(jar.s).nn

          val tag =
            if matcher.find() then Text(s"android-${matcher.group(1)}")
            else
              val name = resolve(jar).getFileName.nn.toString
              Text(name.stripSuffix(".jar").nn)

          val surface = HostArchive.surface(Text(resolve(jar).toString))
          Out.println(t"harvested $tag (${surface.stdlib.size} classes)")
          (tag, surface)

        val releases = proscenium.List.from:
          parsed.sortBy { pair => pair(0).s }.map { pair => HostRelease(pair(0), pair(1)) }

        if emit(t"android", releases) then Exit.Ok else Exit.Fail(1)

      case other =>
        Out.println(t"lira: unknown host kind '$other' (expected jdk or android)")
        Exit.Fail(1)
