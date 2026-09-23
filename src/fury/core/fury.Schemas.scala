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

import soundness.*

import charEncoders.utf8Encoder
import errorDiagnostics.emptyDiagnostics

// A schema Fury ships could not be read: a defect of the build, never of a user's file.
case class SchemaError(detail: Text)(using Diagnostics) extends Error(m"schema: $detail")

// The TEL schemas Fury validates against (fury.md §14): copied by the build from the repository
// root into the `schemas/` resource directory, and reconstructed into stratiform's `Tels` — the
// document's shape by `Reconstructor`, then the schema-validity battery by `Validation` — on
// demand. Read through the thread-context classloader, never the system one, which under Burdock
// sees only the slim pre-repackage jar (fume's `Suites` carries the same note).
object Schemas:
  private def resource(name: Text): Optional[Text] =
    val loader = Thread.currentThread.nn.getContextClassLoader.nn

    Optional(loader.getResourceAsStream(name.s)).let: stream =>
      String(stream.readAllBytes(), "UTF-8").tt

  def load(name: Text): Tels raises SchemaError =
    val text: Text =
      resource(t"schemas/$name").or(abort(SchemaError(t"$name is not among the shipped schemas")))

    mitigate:
      case error: Tel.Error => SchemaError(t"$name is malformed: ${error.message}")

    . protect:
        Tels.Validation.validate(Tels.Reconstructor.fromTel(text.read[Tel]))

  def build: Tels raises SchemaError = load(t"build.schema.tel")
