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

import java.nio.file as jnf

import soundness.*

import charEncoders.utf8Encoder
import parsing.trackPositions
import proscenium.List
import strategies.throwUnsafely

// Tests for what `fury check` decides from files alone (fury.md §15, step 1): that every schema
// the tool ships reconstructs and passes stratiform's validity battery, that the repository's
// specimens validate against them, that the specimen build file decodes to the model, and that
// the milestone lint accepts a build in scope and refuses the specimen, which deliberately
// exercises what the milestone leaves out. The repository's `.tel` files are read relative to
// the working directory, which `make test` and CI make the repository root — stratiform is the
// validator here because the `tel` command's own `validate` predates the schema headers these
// files carry.
object Tests extends Suite(m"Fury tests"):
  private def declared(arg: Model.Argument): (Text, Optional[Text]) = (arg.name, arg.default)

  private def document(path: Text): Tel =
    String(jnf.Files.readAllBytes(jnf.Path.of(path.s)), "UTF-8").tt.read[Tel]

  private val milestone: Text = List(
    t"tel 1.0",
    t"",
    t"command build",
    t"  build lira/lira",
    t"",
    t"toolchain tools",
    t"  tool scalac",
    t"    version 3.9",
    t"    set encoding UTF-8",
    t"  tool javac",
    t"",
    t"project lira",
    t"  apply tools",
    t"",
    t"  module lira",
    t"    source src/core/*.scala",
    t"").join(t"\n")

  private val feedback: Text = List(
    t"tel 1.0",
    t"",
    t"command jar",
    t"  build lira/lira",
    t"  extract lira/lira jar dist/lira.jar",
    t"",
    t"project lira",
    t"  module lira",
    t"    source src/core/*.scala",
    t"    source dist/*.jar",
    t"").join(t"\n")

  def run(): Unit =
    suite(m"Shipped schemas"):
      List(t"build", t"tool", t"guarantees", t"local", t"lock", t"registry").each: name =>
        test(m"$name.schema.tel reconstructs and passes the validity battery"):
          Schemas.load(t"$name.schema.tel")

        . assert(_ => true)

    suite(m"Specimens against their schemas"):
      test(m"build.tel validates against build.schema.tel"):
        Check.violations(document(t"build.tel"), Schemas.build)

      . assert(_.isEmpty)

      test(m"local.tel validates against local.schema.tel"):
        Check.violations(document(t"local.tel"), Schemas.load(t"local.schema.tel"))

      . assert(_.isEmpty)

      test(m"scalac.tool.tel validates against tool.schema.tel"):
        Check.violations(document(t"scalac.tool.tel"), Schemas.load(t"tool.schema.tel"))

      . assert(_.isEmpty)

    suite(m"The model"):
      test(m"the specimen build file decodes to its nine modules"):
        Model.decode(document(t"build.tel")).projects.stdlib.map(_.modules.stdlib.length)

      . assert(_ == scala.List(9))

      test(m"artifact names, types and filenames are read positionally"):
        val build = Model.decode(document(t"build.tel"))
        val app = build.projects.stdlib.flatMap(_.modules.stdlib).find(_.name == t"app")

        app.map: module =>
          module.artifacts.stdlib.map: artifact =>
            (artifact.name, artifact.appType, artifact.filename)

      . assert:
          _ == Some(scala.List(
            (t"linux-x86", t"native-exe/x86_64-linux-gnu", t"example"),
            (t"linux-arm", t"native-exe/aarch64-linux-gnu", t"example"),
            (t"windows-x86", t"native-exe/x86_64-windows-msvc", t"example.exe"),
            (t"macos-arm", t"native-exe/aarch64-apple-darwin", t"example")))

      test(m"a declared argument is read with its default"):
        val build = Model.decode(document(t"build.tel"))
        val command = build.commands.stdlib.find(_.name == t"test")
        command.map(_.args.stdlib.map(declared))

      . assert(_ == Some(scala.List((t"only", t"*"))))

    suite(m"The milestone lint"):
      test(m"a build within the milestone's scope validates and lints clean"):
        val tel = milestone.read[Tel]
        val violations = Check.violations(tel, Schemas.build)
        val findings = Lint(Model.decode(tel)).stdlib
        (violations.isEmpty, findings.filter(_.error).map(_.message))

      . assert(_ == (true, scala.Nil))

      test(m"the specimen build file exceeds the milestone"):
        Lint(Model.decode(document(t"build.tel"))).stdlib.exists(_.error)

      . assert(_ == true)

      test(m"an extraction into a source glob is a feedback loop"):
        Lint(Model.decode(feedback.read[Tel])).stdlib.filter(_.error).map(_.message)

      . assert:
          _ == scala.List:
            t"command jar: extract to dist/lira.jar matches source dist/*.jar (a feedback loop)"
