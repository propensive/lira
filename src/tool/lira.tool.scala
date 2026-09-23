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

import anthology.Format

// `lira.tool`: the contract lives in an object under the `lira` package, so that plugins name it as
// `lira.tool.Tool` while the package itself stays a single segment.
object tool:

  // The plugin contract (builds.md §14.3; fury.md §6; fever.md §3): what a tool tells the build
  // tool about itself, and what one invocation of it exchanges. The information model is
  // tool.schema.tel's, verbatim — a descriptor is the data `tool.tel` is extracted from at
  // publish — and the invocation and outcome are BinTEL documents, so that the same payload
  // crosses a method call, a UNIX socket to a tool daemon, or the swarm channel unchanged. Forms
  // are anthology's `Format`s: the contract is built on anthology's types rather than
  // duplicating them (fury.md §6), at the accepted cost of a Scala-oriented dependency for now.
  //
  // This is the skeleton the ladder's step 0 establishes; step 5 fills it in with the BinTEL
  // codecs and the first implementation (Fever's `scalac` edges). One evolution rule from day
  // one (builds.md §14.3): plugins IMPLEMENT `Tool`, so an abstract addition is a major under
  // `tasty/1`; the trait grows by defaulted methods or optional side-traits, never by abstract
  // members.

  // Whether a setting affects the output bytes (and so belongs in manifests and in the step's
  // `inputs/1` identity) or has side effects only (and is never recorded).
  enum Effect:
    case Output, Nothing

  // A setting SPECIFICATION — key and classification — never a value; values are configured in
  // build.tel and, where output-affecting, recorded in section-scoped Tool records.
  case class Setting(key: Text, affects: Effect)

  // A source-side form an edge consumes, required unless `optional`.
  case class Input(form: Format, optional: Boolean = false)

  // One invocation kind, named by its output form unless stated (builds.md §14.2): the roles are
  // `inputs` (consumed), `contexts` (dependency cells read, by universe, with the disciplines the
  // edge `employs` over them and the carriers it `emits`), and the output.
  case class Edge
    ( output:     Format,
      name:       Optional[Text]     = Unset,
      parameter:  Optional[Text]     = Unset,
      inputs:     List[Input]        = Nil,
      contexts:   List[Format]       = Nil,
      employs:    List[Text]         = Nil,
      emits:      List[Text]         = Nil,
      components: List[Text]         = Nil,
      settings:   List[Setting]      = Nil ):

    // Edge ids default to the output form: `scalac/jvm`, `scalac/sjsir`.
    def id: Text = name.or(output.id)

  case class Descriptor(name: Text, edges: List[Edge], settings: List[Setting] = Nil)

  // The target cell: universe, integration case and option case.
  case class Cell
    ( universe: Text, integration: Optional[Text] = Unset, option: Optional[Text] = Unset )

  case class Diagnostic(severity: Text, message: Text, path: Optional[Text] = Unset)

  // The accumulated context of one step, exchanged as a BinTEL document: input trees by form and
  // context cells by universe are named by their store hashes — nothing but hashes ever crosses a
  // process boundary (fury.md §1) — with the merged tool and edge settings and the cell.
  case class Invocation
    ( edge:     Text,
      inputs:   Map[Text, Text],
      context:  Map[Text, List[Text]],
      settings: Map[Text, Text],
      cell:     Cell )

  // Outputs by form, as store hashes, and the diagnostics the tool produced.
  case class Outcome(outputs: Map[Text, Text], diagnostics: List[Diagnostic])

  trait Tool:
    def descriptor: Descriptor
    def invoke(invocation: Invocation): Outcome
