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
import hyphenations.englishHyphenation
import interpreters.posixInterpreter
import logging.silentLogging
import systems.javaSystem
import textSanitizers.strictSanitizer
import threading.platformThreading

// The `lira` command-line tool (LIRA specification §5.1): the PATH-resolved handler that the
// interpreter directive of every `.lira` file invokes. It runs as an Ethereal daemon — the
// first invocation starts a background JVM; every later one attaches over a socket, giving
// millisecond startup and live tab-completions.
// The three groups of design/tool.md §5, which is how the commands were always described; the
// help output now renders them under these headings rather than as one alphabetical run.
val Artifacts =
  CommandGroup("Artifact commands", "Reading, comparing and versioning `.lira` releases")

val StoreCommands =
  CommandGroup("Store commands", "The content-addressed store: ingest, retention and audit")

val Housekeeping = CommandGroup("Housekeeping", "The daemon and the shell it runs in")

val Verify = Subcommand("verify", "verify a .lira file (install grade)", group = Artifacts)

val Harvest =
  Subcommand
   ("harvest", "harvest a host's surface into tagged .lira contracts", group = Artifacts)

val Jar = Subcommand("jar", "write a section's canonical derivative JAR", group = Artifacts)

val Assign =
  Subcommand
   ("assign", "assign the next derived version to a development release", group = Artifacts)

val Delta =
  Subcommand
   ("delta", "show what changed between two releases, and its grade", group = Artifacts)

val AtomsCmd =
  Subcommand
   ("atoms", "list the atoms of a release, or of a bare artifact", group = Artifacts)

val Id =
  Subcommand("id", "identify a bare artifact by its derivative hash", group = Artifacts)

val Cache =
  Subcommand
   ("cache", "manage the content-addressed store (add/ls/rm/path)", group = StoreCommands)

val Pin =
  Subcommand
   ("pin", "pin a cached release, exempting it from eviction", group = StoreCommands)

val Unpin = Subcommand("unpin", "unpin a cached release", group = StoreCommands)

val Gc =
  Subcommand
   ("gc", "collect unreferenced objects; evict to the byte budget", group = StoreCommands)

val Fsck =
  Subcommand
   ("fsck", "re-verify every store object; quarantine mismatches", group = StoreCommands)

val Install =
  Subcommand("install", "install tab-completions into the shell", group = Housekeeping)

val Help = Subcommand("help", "show usage information", group = Housekeeping)
val Quit = Subcommand("quit", "shut down the background daemon", group = Housekeeping)
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
private def flagText(flag: Flag of Text, label: Text)
    (using cli: Cli, interpreter: Interpreter { type Topic = Commandline })
:   Optional[Text] =

  // The operand's display name in `help` — `--blob <file>` rather than `--blob <value>` — is read
  // from the `Interpretable` when the flag registers, so naming it is a matter of supplying one.
  given (Text is Interpretable) = new Interpretable:
    type Self = Text
    override def placeholder: Optional[Text] = label

    def interpret(arguments: List[Argument]): Optional[Text] =
      arguments.stdlib.headOption.map(_()).getOrElse(Unset)

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
  val cli0 = summon[Cli]

  // The dispatch is a named block because the usage text is derived from it: `Executive.help`
  // re-runs it in tab-completion mode against synthesized argument prefixes, discovering the
  // subcommands and their flags from the same patterns that dispatch them. `execute` takes its
  // body as a context function and evaluates nothing in completion mode, so those runs do no IO
  // and this does not recur into itself.
  //
  // Lazy, because building the tree walks every subcommand: a command that never prints usage
  // never pays for it.
  lazy val help: Optional[Help] =
    executives.completions.help
      (t"lira", cli0.environment, cli0.workingDirectory, cli0.stdio, cli0.login)(dispatch)

  def dispatch(using Cli): Execution = positional match
    case Verify() :: Pathname(file) :: Nil => execute(verify(file))
    case Jar() :: universe :: Pathname(file) :: Nil => execute(storeJar(universe(), file))
    case Cache() :: rest          => execute(cache(rest.map(_())))
    case Pin() :: target :: Nil   => execute(pin(target(), true))
    case Unpin() :: target :: Nil => execute(pin(target(), false))

    case Gc() :: _                =>
      val budget = flagText(Budget, t"bytes")
      execute(gcCommand(budget))

    case Fsck() :: _              => execute(fsck())
    case Id() :: Pathname(file) :: Nil => execute(identify(file))

    // The flags are read against the subcommand itself, not inside a branch that needs every
    // operand present: a flag registers where it is read, so reading it here is what puts it in
    // the completions from `lira assign <TAB>` onwards, and what lets `Executive.help` find it
    // when it probes this subcommand with no operands at all.
    case Assign() :: rest =>
      val major = Major()

      rest match
        case Pathname(file) :: Nil => execute(assign(file, Unset, major.present))

        case Pathname(file) :: Pathname(previous) :: Nil =>
          execute(assign(file, previous, major.present))

        case _ => execute(usage(help, Exit.Fail(1)))

    case Delta() :: rest =>
      val blob = flagText(Blob, t"file")

      rest match
        case Pathname(previous) :: Pathname(next) :: Nil =>
          execute(delta(previous, next, blob))

        case _ => execute(usage(help, Exit.Fail(1)))

    case AtomsCmd() :: rest =>
      val realm = flagText(Realm, t"realm")
      val classpath = flagText(Classpath, t"a:b")
      val discipline = flagText(Only, t"discipline")
      val owner = flagText(Owner, t"prefix")

      rest match
        case Pathname(file) :: Nil =>
          execute(atomsCommand(file, realm, classpath, discipline, owner))

        case _ => execute(usage(help, Exit.Fail(1)))

    case Harvest() :: kind :: Pathname(out) :: rest =>
      execute(harvest(kind(), out, rest.map(_())))

    case Install() :: _           => execute(installCompletions())
    case Help() :: _              => execute(usage(help, Exit.Ok))
    case Quit() :: _              => execute(quit())

    // The interpreter directive's own case (§5.1): `lira <file.lira>` with no subcommand. Since
    // Soundness #1784, `Pathname` composes its suggestions with the subcommands' rather than
    // replacing them, so the first word completes as both.
    case Pathname(file) :: Nil =>
      execute(manifest(file))

    case _ => execute(usage(help, Exit.Fail(1)))

  dispatch

// `helpTree` builds the tree from the *suggestions* each argument position offers, and a position
// taking a path offers the working directory's contents — indistinguishable, at that point, from
// a subcommand. Left alone the tree lists `Makefile` and `readme.md` among the commands and
// descends into each. Every subcommand lira declares belongs to a `CommandGroup` and no file
// suggestion does, so that is the test.
private def prune(help: Help): Help =
  val children = help.subcommands.stdlib.filter(_.group.present)
  help.copy(subcommands = List.from(children.map(prune)))

// The usage text is the dispatch's own structure, discovered by re-running it in tab-completion
// mode (`Executive.help`), so a subcommand or flag cannot be added without appearing here, and
// the descriptions are the ones the completions already show. What no tree can carry are the
// conventions that are not subcommands or flags — the operands each subcommand takes, and
// `harvest`'s `+<tag>` sanction — so those are stated below it.
private def usage(help: Optional[Help], exit: Exit)(using cli: Cli): Exit =
  given Stdio = cli.stdio

  // Rendered at an explicit width rather than through `Help is Printable`, which takes its width
  // from the termcap: Ethereal's per-invocation `Termcap` (ethereal_core.scala) overrides `ansi`
  // and `color` but not `width`, so it inherits `Int.MaxValue` and nothing ever wraps — even
  // though the launcher detects the terminal's size and forwards `COLUMNS`. `tableWidth` applies
  // the same bound the tables use. The `hyphenations.englishHyphenation` import is what makes the
  // wrapping break words rather than only spaces.
  help.lay(Out.println(t"lira: the command structure could not be determined")):
    help => Out.println(prune(help).teletype(tableWidth))

  Out.println(t"")
  Out.println(t"Operands:")
  Out.println(t"  verify, atoms, id, jar   a .lira file, or for `atoms` and `id` a bare artifact")
  Out.println(t"  delta                    two .lira files, previous first")
  Out.println(t"  assign                   a .lira file, optionally its predecessor")
  Out.println(t"  harvest                  jdk|android, an output directory, then the sources")
  Out.println(t"  cache                    add|ls|rm|path; pin and unpin take a hash prefix")
  Out.println(t"")
  Out.println(t"A `+<tag>` argument to `harvest` sanctions that release as a major — a removal")
  Out.println(t"in the vendor's history — beginning a fresh lineage.")
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

  // The one `try` left in the tool, and deliberately: Contingency tracks `Hazard`s, and what this
  // guards against is everything that is not one — a `NoClassDefFoundError` from a jar rebuilt
  // under a running daemon is the case that motivated it (see `parasite` #1743). Installation
  // classloads late and lazily, in the daemon's long-lived JVM, and a `Throwable` that escapes
  // here reaches the worker's failure path, where it is easy to lose entirely. `InstallError` —
  // an error, and therefore trackable — is recovered, not caught.
  try
    recover:
      case error: InstallError =>
        Out.println(t"lira: could not install tab-completions")
        Exit.Fail(2)

    . protect:
        Completions.ensure(force = true).each(Out.println(_))
        Exit.Ok

  catch case error: Throwable =>
    error.printStackTrace()
    Out.println(t"lira: fatal: ${Text(error.toString)}")
    Exit.Fail(3)

// Every command runs inside this: the errors it can raise are tracked, and each is answered here
// with a message and an exit code. It replaces a `try`/`catch` over `strategies.throwUnsafely`,
// which converted every raised error into an untyped throw — so the compiler could not tell which
// errors a command actually had to answer for, a `case` could name an error no call site raises,
// and an error nobody named was caught by a blanket `case error: Exception` and reported as an
// unexplained string.
//
// The types named below are exactly the errors lira's own code and the libraries it calls can
// raise; adding a call that raises something else is a compile error until it is handled here or
// in the command itself, which is the point.
private def command(using cli: Cli)
    ( block: (Tactic[Lira.Error], Tactic[StoreError], Tactic[IoError], Tactic[Path.Error],
              Tactic[DisciplineError], Tactic[Name.Error], Tactic[StreamError]) ?->{cli} Exit )
:   Exit =

  given Stdio = cli.stdio

  recover:
    case error: Lira.Error      => report(error.message)
    case error: StoreError      => report(error.message)
    case error: IoError         => report(error.message)
    case error: Path.Error      => report(error.message)
    case error: DisciplineError => report(error.message)
    case error: Name.Error      => report(error.message)
    case error: StreamError     => report(error.message)

  . protect(block)

private def report(message: fulminate.Message)(using Stdio): Exit =
  Out.println(t"lira: $message")
  Exit.Fail(1)

// `Pathname` resolves an argument against the *client's* working directory, which the launcher
// forwards — never the daemon's own — so a path that reaches these helpers is already absolute.
// A path a flag carries as text is not, and resolves the same way here.
// These three raise rather than resolving unsafely: a missing file, an unwritable directory or a
// path a filesystem will not admit are ordinary outcomes of a command, and the command's own
// handler is where they belong. `unsafely` here would throw past every tracked handler, and the
// caller would see nothing at all.
private def resolve(file: Text)(using cli: Cli)(using Tactic[Path.Error]): Path on Local =
  safely(file.as[Path on Local]).or:
    t"${cli.workingDirectory.directory()}/$file".as[Path on Local]

private def load(file: Path on Local)(using Cli)(using Tactic[IoError], Tactic[StreamError])
:   Data =
  import filesystemBackends.virtualMachine
  file.read[Data]

private def save(file: Path on Local, data: Data)(using Cli)(using Tactic[IoError]): Unit =
  import filesystemBackends.virtualMachine
  import filesystemOptions.createNonexistentParents.enabled
  import filesystemOptions.overwritePreexisting.enabled

  file.create[File](CreateFlag.Parents, CreateFlag.Replace): handle ?=>
    handle.write(Chain(data))

private def stem(file: Path on Local): Text =
  val name = file.encode
  if name.s.endsWith(".lira") then Text(name.s.dropRight(5)) else name

// The manifest is everything before the first `##` line; §5.2 fixes the byte layout so this
// needs no TEL parsing, and the author's formatting is preserved exactly.
private def manifest(file: Path on Local)(using cli: Cli): Exit = command:
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

private def verify(file: Path on Local)(using cli: Cli): Exit = command:
  given Stdio = cli.stdio
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
      (t"snapshot", Lira.Hash.text(manifest.lineage.stdlib.last)),
      (t"payload", Lira.Hash.text(manifest.payload.hash)),
      (t"sections", Text(manifest.section.stdlib.map(_.realm.s).mkString(", "))),
      (t"lineage", t"${manifest.lineage.stdlib.size} snapshots") ) ++ advisories)

  Out.println(t"")
  Out.println(t"verified (install grade)")
  Exit.Ok

private def assign(file: Path on Local, previous: Optional[Path on Local], forceMajor: Boolean)
    (using cli: Cli)
:   Exit =

  command:
    given Stdio = cli.stdio
    val release = Lira.read(load(file))
    val before = previous.let { path => Lira.read(load(path)) }
    val published = before.let { lira => proscenium.List(lira.manifest) }.or(proscenium.List())
    val manifest = Publication.assign(release, before, published, forceMajor)

    val stream = Lira.Payload.decompress
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

  command:
    given Stdio = cli.stdio

    val majors = extra.filter(_.s.startsWith("+")).map { tag => Text(tag.s.substring(1).nn) }
    val sources = extra.filter { arg => !arg.s.startsWith("+") }
    val directory = jnf.Paths.get(out.encode.s).nn

    // One module's failure is not the harvest's: a vendor release that removes surface stops that
    // module's lineage and the rest carry on, so `Lira.Error` is recovered here rather than
    // reaching the command's own handler. Recovering it locally also *narrows* what the rest of
    // the loop may raise, which is the property a `catch` could not state.
    def emit(module: Text, releases: proscenium.List[HostRelease]): Boolean =
      val contracts: Optional[proscenium.List[(Text, Data)]] =
        recover:
          case error: Lira.Error =>
            error.reason match
              case Lira.Error.Reason.UngradedSuccessor(tag) =>
                Out.println(t"lira: $module: $tag grades as a major over its predecessor (a")
                Out.println(t"      removal in the vendor's history); sanction it with +$tag")

              case _ => Out.println(t"lira: $module: ${error.message}")

            Unset

        . protect:
            HostContracts.assemble(
              module,
              releases,
              toolchain  = proscenium.List(Lira.Manifest.Tool(t"lira", t"0.1")),
              allowMajor = { tag => majors.stdlib.contains(tag) })

      contracts.lay(false): contracts =>
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
