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

// The `lira` command-line tool (LIRA specification §5.1): the PATH-resolved handler that the
// interpreter directive of every `.lira` file invokes. It runs as an Ethereal daemon — the
// first invocation starts a background JVM; every later one attaches over a socket, giving
// millisecond startup and live tab-completions.
val Verify = Subcommand("verify", "verify a .lira file (install grade)")
val Harvest = Subcommand("harvest", "harvest a host's surface into tagged .lira contracts")
val Jar = Subcommand("jar", "write a section's canonical derivative JAR")
val Assign = Subcommand("assign", "assign the next derived version to a development release")
val Delta = Subcommand("delta", "show what changed between two releases, and its grade")
val AtomsCmd = Subcommand("atoms", "list the atoms of a release, or of a bare artifact")
val Cache = Subcommand("cache", "manage the content-addressed store (add/ls/rm/path)")
val Pin = Subcommand("pin", "pin a cached release, exempting it from eviction")
val Unpin = Subcommand("unpin", "unpin a cached release")
val Gc = Subcommand("gc", "collect unreferenced objects; evict to the byte budget")
val Fsck = Subcommand("fsck", "re-verify every store object; quarantine mismatches")
val Id = Subcommand("id", "identify a bare artifact by its derivative hash")
val Install = Subcommand("install", "install tab-completions into the shell")
val Help = Subcommand("help", "show usage information")
val Quit = Subcommand("quit", "shut down the background daemon")
val Major = Flag[Unit]("major", false, Nil, "begin a new major series (a fresh lineage)")
val Budget = Flag[Text]("budget", false, Nil, "byte budget for unpinned cached releases")
val Blob = Flag[Text]("blob", false, Nil, "also write the delta blob to this path")
val Realm = Flag[Text]("realm", false, Nil, "the realm to atomize a bare artifact in")
val Classpath = Flag[Text]("classpath", false, Nil, "dependency classpath for membership keying")
val Only = Flag[Text]("discipline", false, Nil, "restrict the listing to one discipline")
val Owner = Flag[Text]("owner", false, Nil, "restrict the listing to keys with this prefix")

// Every flag lira declares that takes a value, and takes exactly one.
private val valueFlags: scala.List[Flag] =
  scala.List(Budget, Blob, Realm, Classpath, Only, Owner)

// The POSIX interpreter's own reading of the commandline. `arguments` is the raw list, flags and
// all, so a command matching an exact arity would fail the moment a flag appeared among them;
// `Commandline.positional` is what remains once the flags are accounted for.
//
// The interpreter gives a flag *every* argument that follows it until the next flag, since in
// general a flag may be repeatable or variadic. None of lira's are: each takes one operand, so
// the surplus is not the flag's — it is the command's, and returning it (in the order it was
// typed) is what lets `delta a.lira --blob out b.lira` mean what it plainly says.
private def positional(using cli: Cli, interpreter: Interpreter { type Topic = Commandline })
:   List[Argument] =

  val commandline = interpreter.interpret(cli.arguments)

  val surplus = valueFlags.flatMap: flag =>
    interpreter.find(commandline, flag).stdlib.toList.drop(1)

  List.from((commandline.positional.stdlib.toList ++ surplus).sortBy(_.position))

// A value-taking flag's operand. `flag()` is called for its side effect as much as its result: it
// registers the flag with the `Cli`, which is what puts it in this subcommand's completions, and
// it is where an `Interpretable` richer than `Text` would do its work. It reads `Unset` when the
// interpreter handed the flag more operands than it takes, so the first operand stands in.
private def flagText(flag: Flag of Text)
    (using cli: Cli, interpreter: Interpreter { type Topic = Commandline })
:   Optional[Text] =

  flag().or:
    val commandline = interpreter.interpret(cli.arguments)
    interpreter.find(commandline, flag).stdlib.toList.headOption.map(_()).getOrElse(Unset)

// Each branch reads its flags *before* `execute`: reading a flag registers it (`Cli.register`),
// which is what puts it in the completions for the subcommand it belongs to, and what lets a
// value-taking flag be understood rather than mistaken for a positional argument. Reading them
// inside the `execute` block would register them too late to appear, and reading every flag up
// front would offer `--major` to `verify`.
@main
def main(): Unit = cli:
  // `Pathname` resolves a relative argument against the ambient `WorkingDirectory`, and under the
  // daemon the ambient one is the daemon's — which is wherever it happened to be started. The
  // client's is forwarded on the `Cli`, and is the only one a user's relative path can mean.
  given WorkingDirectory = summon[Cli].workingDirectory

  positional match
    case Verify() :: Pathname(file) :: Nil => execute(verify(file))
    case Jar() :: universe :: Pathname(file) :: Nil => execute(storeJar(universe(), file))
    case Cache() :: rest          => execute(cache(rest.map(_())))
    case Pin() :: target :: Nil   => execute(pin(target(), true))
    case Unpin() :: target :: Nil => execute(pin(target(), false))

    case Gc() :: _                =>
      val budget = flagText(Budget)
      execute(gcCommand(budget))

    case Fsck() :: _              => execute(fsck())
    case Id() :: Pathname(file) :: Nil => execute(identify(file))

    case Assign() :: Pathname(file) :: Nil =>
      val major = Major()
      execute(assign(file, Unset, major.present))

    case Assign() :: Pathname(file) :: Pathname(previous) :: Nil =>
      val major = Major()
      execute(assign(file, previous, major.present))

    case Delta() :: Pathname(previous) :: Pathname(next) :: Nil =>
      val blob = flagText(Blob)
      execute(delta(previous, next, blob))

    case AtomsCmd() :: Pathname(file) :: Nil =>
      val realm = flagText(Realm)
      val classpath = flagText(Classpath)
      val discipline = flagText(Only)
      val owner = flagText(Owner)
      execute(atomsCommand(file, realm, classpath, discipline, owner))

    case Harvest() :: kind :: Pathname(out) :: rest =>
      execute(harvest(kind(), out, rest.map(_())))

    case Install() :: _           => execute(installCompletions())
    case Help() :: _              => execute(usage(Exit.Ok))
    case Quit() :: _              => execute(quit())

    // The interpreter directive's own case (§5.1): `lira <file.lira>` with no subcommand.
    case Pathname(file) :: Nil =>
      execute(manifest(file))

    case _ => execute(usage(Exit.Fail(1)))

private def usage(exit: Exit)(using cli: Cli): Exit =
  given Stdio = cli.stdio
  Out.println(t"Usage: lira <file.lira>                    show the manifest")
  Out.println(t"       lira verify <file.lira>             verify the file (install grade)")
  Out.println(t"       lira jar <universe> <file.lira>     write the canonical derivative JAR")
  Out.println(t"       lira assign <file.lira> [<previous.lira>] [--major]")
  Out.println(t"                                           assign the next derived version")
  Out.println(t"       lira delta <previous.lira> <next.lira> [--blob <file>]")
  Out.println(t"                                           show what changed, and its grade")
  Out.println(t"       lira atoms <file.lira>              list the atoms the release declares")
  Out.println(t"       lira atoms <artifact> [--realm <realm>] [--classpath <a:b>]")
  Out.println(t"                                           atomize a bare artifact and list it")
  Out.println(t"       (both take --discipline <id> and --owner <prefix> to narrow the listing)")
  Out.println(t"       lira harvest jdk <dir> [<ct.sym>] [+<tag> ...]")
  Out.println(t"                                           harvest the JDK lineage from ct.sym")
  Out.println(t"       lira harvest android <dir> <android.jar ...> [+<tag> ...]")
  Out.println(t"                                           harvest Android API levels")
  Out.println(t"       (a +<tag> argument sanctions that release as a major: a removal in the")
  Out.println(t"        vendor's history, beginning a fresh lineage)")
  Out.println(t"       lira cache add <file.lira> ...      ingest files into the store")
  Out.println(t"       lira cache ls                       list cached releases")
  Out.println(t"       lira cache rm <hash-prefix>         remove a cached release")
  Out.println(t"       lira cache path <hash-prefix>       print a store object's path")
  Out.println(t"       lira pin <hash-prefix>              exempt a release from eviction")
  Out.println(t"       lira unpin <hash-prefix>            allow eviction again")
  Out.println(t"       lira gc [--budget <bytes>]          collect garbage; evict to budget")
  Out.println(t"       lira fsck                           re-verify the store; quarantine")
  Out.println(t"       lira id <artifact>                  identify a bare artifact")
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

  given entrypoint: (Entrypoint^{service}) = service

  try
    recover:
      case error: InstallError =>
        Out.println(t"lira: could not install tab-completions")
        Exit.Fail(2)

    . protect:
        Completions.ensure(force = true).each(Out.println(_))
        Exit.Ok

  catch case error: Throwable =>
    // This handler is outside `guard`, so surface fatal errors the same way it does.
    error.printStackTrace()
    Out.println(t"lira: fatal: ${Text(error.toString)}")
    Exit.Fail(3)

private def guard(using cli: Cli)(block: ->{cli} Exit): Exit =
  given Stdio = cli.stdio
  import strategies.throwUnsafely

  try block catch
    case error: LiraError =>
      Out.println(t"lira: ${error.message}")
      Exit.Fail(1)

    case error: StoreError =>
      Out.println(t"lira: ${error.message}")
      Exit.Fail(1)

    case error: Exception =>
      Out.println(t"lira: ${Text(error.toString)}")
      Exit.Fail(2)

    // Non-Exception throwables would otherwise propagate into the worker's failure path,
    // where they are easy to lose; print the stack to the daemon log and fail loudly.
    case error: Throwable =>
      error.printStackTrace()
      Out.println(t"lira: fatal: ${Text(error.toString)}")
      Exit.Fail(3)

// `Pathname` resolves an argument against the *client's* working directory, which the launcher
// forwards — never the daemon's own — so a path that reaches these helpers is already absolute.
// A path a flag carries as text is not, and resolves the same way here.
private def resolve(file: Text)(using cli: Cli): Path on Local =
  unsafely:
    safely(file.as[Path on Local]).or:
      t"${cli.workingDirectory.directory()}/$file".as[Path on Local]

private def load(file: Path on Local)(using Cli): Data =
  import filesystemBackends.virtualMachine
  unsafely(file.read[Data])

private def save(file: Path on Local, data: Data)(using Cli): Unit =
  import filesystemBackends.virtualMachine
  import filesystemOptions.createNonexistentParents.enabled
  import filesystemOptions.overwritePreexisting.enabled

  unsafely:
    file.create[File](CreateFlag.Parents, CreateFlag.Replace): handle ?=>
      handle.write(Chain(data))

private def stem(file: Path on Local): Text =
  val name = file.encode
  if name.s.endsWith(".lira") then Text(name.s.dropRight(5)) else name

// The manifest is everything before the first `##` line; §5.2 fixes the byte layout so this
// needs no TEL parsing, and the author's formatting is preserved exactly.
private def manifest(file: Path on Local)(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  val data = load(file)
  val bytes = data.readable
  var index = 0
  var found = -1

  while found < 0 && index + 4 <= data.length do
    if bytes(index) == '\n' && bytes(index + 1) == '#' && bytes(index + 2) == '#'
        && bytes(index + 3) == '\n'
    then found = index
    index += 1

  if found < 0 then
    Out.println(t"lira: the file has no document separator")
    Exit.Fail(1)
  else
    Out.println(Text(String(Array.unsafeJvm(data), 0, found + 1, "UTF-8")))
    Exit.Ok

private def verify(file: Path on Local)(using cli: Cli): Exit = guard:
  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val lira = Lira.read(load(file))
  val report = Verification.install(lira)
  val manifest = lira.manifest

  val version: Text =
    if manifest.development then t"development release"
    else manifest.version.let { version => t"$version" }.or(t"unversioned")

  val advisories: scala.List[(Text, Text)] =
    report.advisories.stdlib.toList.map { advisory => (t"advisory", Text(advisory.toString)) }

  facts(scala.List
    ( (t"module", manifest.module),
      (t"version", version),
      (t"snapshot", LiraHash.text(manifest.lineage.stdlib.last)),
      (t"payload", LiraHash.text(manifest.payload.hash)),
      (t"sections", Text(manifest.section.stdlib.map(_.realm.s).mkString(", "))),
      (t"lineage", t"${manifest.lineage.stdlib.size} snapshots") ) ++ advisories)

  Out.println(t"")
  Out.println(t"verified (install grade)")
  Exit.Ok

private def assign(file: Path on Local, previous: Optional[Path on Local], forceMajor: Boolean)
    (using cli: Cli)
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
    val target = resolve(t"${stem(file)}-$version.lira")
    save(target, Lira.assemble(manifest, blobs))
    Out.println(t"assigned $version -> ${target.encode}")
    Exit.Ok

// Harvests a host's surface into its contract lineages (LIRA hosts.md, jsig.md): where the
// vendor modularizes — the JDK — one contract module per platform module, coordinated across a
// vendor release by a shared tag (hosts.md §3, "Granularity"); for a flat platform — Android —
// one contract module. Majors — the removals in a vendor's history — require explicit `+<tag>`
// sanction (L110), applied per module wherever that vendor release removed surface. Emitted
// files carry the executable bit, as §5.1 requires of producers.
private def harvest(kind: Text, out: Path on Local, extra: proscenium.List[Text])
    (using cli: Cli)
:   Exit =

  guard:
    given Stdio = cli.stdio
    import strategies.throwUnsafely

    val majors = extra.filter(_.s.startsWith("+")).map { tag => Text(tag.s.substring(1).nn) }
    val sources = extra.filter { arg => !arg.s.startsWith("+") }
    val directory = jnf.Paths.get(out.encode.s).nn

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
              val name = resolve(jar).name
              Text(name.s.stripSuffix(".jar").nn)

          val surface = HostArchive.surface(Text(resolve(jar).toString))
          Out.println(t"harvested $tag (${surface.stdlib.size} classes)")
          (tag, surface)

        val releases = proscenium.List.from:
          parsed.sortBy { pair => pair(0).s }.map { pair => HostRelease(pair(0), pair(1)) }

        if emit(t"android", releases) then Exit.Ok else Exit.Fail(1)

      case other =>
        Out.println(t"lira: unknown host kind '$other' (expected jdk or android)")
        Exit.Fail(1)
