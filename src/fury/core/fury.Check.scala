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
package fury

import scala.collection.immutable as sci

import soundness.*

import errorDiagnostics.emptyDiagnostics
import filesystemBackends.javaBaseFilesystem
import parsing.trackPositions
import systems.javaBaseSystem

object CheckFailed extends Status(1, t"the build file has errors")
object NoBuildFile extends Status(3, t"no build.tel was found at or above the working directory")

// `fury check` (fury.md §15, step 1): find the build file, parse it, validate it against
// build.schema.tel — accruing every violation rather than stopping at the first — decode it to
// the typed model, print what it declares, and apply the milestone's refuse/ignore lint. Nothing
// is resolved and nothing is built.
object Check:
  // The accrual accumulator: each violation with its focus, newest first. An `Error`, because a
  // contingency accrual must be a `Hazard`; the position-tracked parse (`trackPositions`) is
  // what lets `Tel.Type.assign` give each focus a span.
  private case class Accrued(items: sci.List[(Optional[Tel.Focus], Tel.Error)] = sci.Nil)
    ( using Diagnostics )
  extends Error(m"${items.length} TEL errors"):
    def add(focus: Optional[Tel.Focus], error: Tel.Error): Accrued =
      Accrued((focus, error) :: items)

  // The build file at or above `directory`, found by walking up as `.git` is found, so that the
  // command works from anywhere inside a project.
  def locate(directory: Text): Optional[Path on Local] =
    def recur(dir: Text): Optional[Path on Local] =
      val candidate: Optional[Path on Local] =
        safely(t"$dir/build.tel".as[Path on Local]).let: path =>
          if path.existent() then path else Unset

      candidate.or:
        val cut = dir.s.lastIndexOf('/')
        if cut > 0 then recur(dir.s.substring(0, cut).nn.tt) else Unset

    recur(directory)

  type Outcome = Exit | NoBuildFile.type | CheckFailed.type

  def run(directory: Text)(using Stdio): Outcome =
    locate(directory).lay[Outcome]:
      Out.println(t"fury: no build.tel at or above $directory")
      NoBuildFile

    . apply(check(_))

  private def check(file: Path on Local)(using Stdio): Outcome =
    recover:
      case error: Io.Error         => report(error.message)
      case error: Truncation.Error => report(error.message)
      case error: SchemaError      => report(error.message)
      case error: BuildError       => report(error.message)

    . protect:
        val data: Data = file.read[Data]
        val schema: Tels = Schemas.build

        // A parse failure is one error at one position; validation failures accrue below.
        val parsed: Optional[Tel] =
          recover:
            case error: Tel.Error =>
              located(file, position(error.span), error)
              Unset

          . protect(data.read[Tel])

        parsed.lay[Outcome](CheckFailed): document =>
          violations(document, schema) match
            case sci.Nil =>
              val build = Model.decode(document)
              summarize(file, build)

              val findings = Lint(build).stdlib
              findings.foreach(display)

              if findings.exists(_.error) then CheckFailed else Exit.Ok

            case errors =>
              // A focus arrives with a keyword path and, once located against the position-
              // tracked document, the span of the compound it names; the path is printed too,
              // since a validator's rejection says nothing else about which field it was.
              errors.foreach: (focus, error) =>
                val where: Text = focus.let(_.withSpan(document)).lay(position(error.span)):
                  focus =>
                    val path: Text = focus.pointer.components.stdlib.map(_.s).mkString("/").tt
                    t"${position(focus.span)} ($path)"

                located(file, where, error)

              CheckFailed

  // Every violation of `schema` by `document`, oldest first, under Fury's validators: the
  // accrual boundary collects them where fail-fast would stop at the first.
  def violations(document: Tel, schema: Tels): sci.List[(Optional[Tel.Focus], Tel.Error)] =
    val accrued: Accrued =
      validate[Tel.Focus](Accrued()):
        case error: Tel.Error => accrual.add(prior, error)

      . protect:
          venture(Tel.Type.assign(document, schema, Validators.registry))
          ()

    accrued.items.reverse

  private def display(finding: Lint.Finding)(using Stdio): Unit =
    Out.println(t"${finding.severity}: ${finding.message}")

  private def located(file: Path on Local, where: Text, error: Tel.Error)(using Stdio): Unit =
    val number = error.reason.number
    Out.println(t"${file.encode}:$where: E$number ${error.message}")

  private def report(message: Message)(using Stdio): Exit =
    Out.println(t"fury: $message")
    Exit.Fail(2)

  private def position(span: denominative.Span): Text =
    val line: Text = span.startLine.let { line => t"${line.n1}" }.or(t"?")
    val column: Text = span.startColumn.let { column => t"${column.n1}" }.or(t"?")
    t"$line:$column"

  private def summarize(file: Path on Local, build: Model.Build)(using Stdio): Unit =
    Out.println(t"${file.encode}: valid")

    build.commands.stdlib.foreach: (command: Model.Command) =>
      Out.println(t"  command ${command.name}")

    build.toolchains.stdlib.foreach: (toolchain: Model.Toolchain) =>
      val tools: Text = toolchain.tools.stdlib.map(_.name.s).mkString(", ").tt
      Out.println(t"  toolchain ${toolchain.name}: $tools")

    build.projects.stdlib.foreach: (project: Model.Project) =>
      Out.println(t"  project ${project.id}")

      project.modules.stdlib.foreach: (module: Model.Module) =>
        val sources: Int = module.sources.stdlib.length
        val includes: Text = module.includes.stdlib.map(_.module.s).mkString(", ").tt
        Out.println(t"    module ${module.name}: $sources source globs; includes $includes")
