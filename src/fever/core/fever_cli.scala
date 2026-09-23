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
package fever

import soundness.*

// `pyrocosm.Tool` is imported explicitly, so that it outranks the `Tool` the `soundness.*`
// wildcard exports (anthology's).
import backstops.silentBackstop
import executives.completionsExecutive
import interpreters.posixInterpreter
import pyrocosm.Tool
import systems.javaBaseSystem
import threading.platformThreading

// Fever as a Pyrocosm tool (fever.md): the Scala compiler service — a resident daemon that
// performs compilations and other source-code operations, `scalac` but faster, and in time most
// of an LSP server's work. Everything that knows what a `.scala` file is lives here, never in
// Fury, which reaches Fever only through the `lira.tool` contract. One Fever is released per
// Scala version, against that compiler's API. This is the skeleton of ladder step 0; the first
// edge — `scalac/jvm` through the contract — arrives with step 5.
val Fever: Tool =
  Tool
    ( t"fever",
      prose = t"Fever is the Scala compiler service for Fury and for editors: a resident daemon " +
        t"that compiles Scala through the lira.tool contract, one release per Scala " +
        t"version." )

object UsageError extends Status(2, t"the command line was not understood")
object Unimplemented extends Status(10, t"this subcommand is not yet implemented")

object ui:
  val Compile = Subcommand("compile", "compile Scala sources once, as scalac would")
  val Lsp = Subcommand("lsp", "run the language server over stdio")

def run(): Unit =
  cli:
    Fever.standard:
      arguments match
        case ui.Compile() :: _ =>
          execute:
            given Stdio = summon[Invocation].stdio
            Out.println(t"fever compile is not yet implemented")
            Unimplemented

        case ui.Lsp() :: _ =>
          execute:
            given Stdio = summon[Invocation].stdio
            Out.println(t"fever lsp is not yet implemented")
            Unimplemented

        case _ =>
          execute:
            given Stdio = summon[Invocation].stdio
            Out.println(t"Usage: fever <subcommand>")
            Out.println(t"")
            Out.println(t"  compile    compile Scala sources once, as scalac would")
            Out.println(t"  lsp        run the language server over stdio")
            Out.println(t"  about      show this tool's name, version and daemon")
            Out.println(t"  install    install shell tab-completions and the manpage")
            Out.println(t"  quit       stop the background daemon")
            UsageError
