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

// The first milestone's refuse/ignore lint (fury.md §9): what v1 refuses because it would affect
// identity or cannot yet be implemented is an error; what it parses and ignores is a warning.
// Every finding names the construct, so a build file's author knows what the milestone leaves
// out. Includes that no source satisfies are `fury resolve`'s finding, not this one's.
object Lint:
  case class Finding(error: Boolean, message: Text):
    def severity: Text = if error then t"error" else t"warning"

  // The hardcoded registry's knowledge of the two built-in tools (fury.md §9): their settings
  // and each one's classification; no flags are known, so every flag is refused.
  private val outputSettings: sci.Map[Text, sci.Set[Text]] =
    sci.Map(t"scalac" -> sci.Set(t"encoding", t"release"), t"javac" -> sci.Set())

  private val sideEffectSettings: sci.Map[Text, sci.Set[Text]] =
    sci.Map(t"scalac" -> sci.Set(t"verbose"), t"javac" -> sci.Set(t"verbose"))

  def apply(build: Model.Build): List[Finding] =
    val registries = build.registries.stdlib.map: registry =>
      refuse(t"registry layer ${registry.coordinate}: registry layers are not supported")

    val repositories = build.repositories.stdlib.map: repository =>
      ignore(t"repository ${repository.name}: repositories are ignored")

    val topologies = build.topologies.stdlib.map: name =>
      ignore(t"topology $name: topologies are ignored")

    val all: sci.List[Finding] =
      registries ++
        build.commands.stdlib.flatMap(command) ++
        build.toolchains.stdlib.flatMap(toolchain) ++
        repositories ++
        build.projects.stdlib.flatMap(project) ++
        topologies ++
        extractions(build)

    List.from(all)

  private def refuse(message: Text): Finding = Finding(true, message)
  private def ignore(message: Text): Finding = Finding(false, message)

  private def command(command: Model.Command): sci.List[Finding] =
    val at = t"command ${command.name}"

    val builds = command.builds.stdlib.flatMap: build =>
      val selections = build.selections.stdlib.map: selection =>
        refuse(t"$at: set ${selection.option} ${selection.choice}: selections are not supported")

      val artifacts = build.artifacts.stdlib.map: artifact =>
        refuse(t"$at: artifact $artifact: artifacts are not supported")

      selections ++ artifacts

    val args = command.args.stdlib.map: arg => ignore(t"$at: argument ${arg.name} is ignored")

    val runs = command.runs.stdlib.map: run =>
      refuse(t"$at: run ${run.module}: running is not supported")

    val watches = command.watches.stdlib.map { _ => refuse(t"$at: watch is not supported") }

    args ++ runs ++ watches ++ builds

  private def toolchain(toolchain: Model.Toolchain): sci.List[Finding] =
    val at = t"toolchain ${toolchain.name}"

    val children = toolchain.children.stdlib.map: child =>
      refuse(t"$at: toolchain ${child.name}: child toolchains are not supported")

    val tools = toolchain.tools.stdlib.flatMap: tool =>
      val here = t"$at: tool ${tool.name}"

      if !outputSettings.contains(tool.name)
      then sci.List(refuse(t"$here: only scalac and javac exist"))
      else
        val distributed =
          tool.module.let(_ => sci.List(refuse(t"$here: LIRA-distributed tools are not supported")))
          . or(sci.Nil)

        val edges = tool.edges.stdlib.map: edge =>
          refuse(t"$here: edge ${edge.name}: edge blocks are not supported")

        val flags = tool.flags.stdlib.map: flag => refuse(t"$here: flag $flag is unknown")

        val settings = tool.settings.stdlib.flatMap: setting =>
          if outputSettings(tool.name).contains(setting.key) then sci.Nil
          else if sideEffectSettings(tool.name).contains(setting.key)
          then sci.List(ignore(t"$here: set ${setting.key}: side-effect settings are ignored"))
          else sci.List(refuse(t"$here: set ${setting.key} is unknown"))

        distributed ++ edges ++ flags ++ settings

    children ++ tools

  private def project(project: Model.Project): sci.List[Finding] =
    val at = t"project ${project.id}"

    val host = project.host.let(_ => sci.List(ignore(t"$at: host is ignored"))).or(sci.Nil)

    val modules = project.modules.stdlib.flatMap: module =>
      val here = t"$at: module ${module.name}"

      val universes = module.universes.stdlib.filter(_.name != t"jvm").map: universe =>
        refuse(t"$here: universe ${universe.name}: only jvm is supported")

      val integrations = module.integrations.stdlib.map: axis =>
        refuse(t"$here: integration ${axis.name}: axes are not supported")

      val options = module.options.stdlib.map: axis =>
        refuse(t"$here: option ${axis.name}: axes are not supported")

      val assemble =
        module.assemble.let(_ => sci.List(refuse(t"$here: assemble is not supported"))).or(sci.Nil)

      val artifacts = module.artifacts.stdlib.map: artifact =>
        refuse(t"$here: artifact ${artifact.name}: artifacts are not supported")

      val generates = module.generates.stdlib.map: tool =>
        refuse(t"$here: generate $tool: codegen is not supported")

      val presumes = module.presumes.stdlib.map: presumption =>
        ignore(t"$here: presume ${presumption.kind} ${presumption.name} is ignored")

      val guarantees = module.guarantees.stdlib.map: guarantee =>
        ignore(t"$here: guarantee ${guarantee.kind} ${guarantee.name} is ignored")

      val requires = module.requires.stdlib.map: requirement =>
        ignore(t"$here: require $requirement is ignored")

      val moduleHost = module.host.let(_ => sci.List(ignore(t"$here: host is ignored"))).or(sci.Nil)

      universes ++ integrations ++ options ++ assemble ++ artifacts ++ generates ++ presumes ++
        guarantees ++ requires ++ moduleHost

    host ++ modules

  // The feedback-loop lint (builds.md §14.5): an extraction destination matched by any source
  // glob would feed the next build's inputs.
  private def extractions(build: Model.Build): sci.List[Finding] =
    val globs: sci.List[Text] =
      build.projects.stdlib.flatMap(_.modules.stdlib.flatMap(_.sources.stdlib.map(_.glob)))

    build.commands.stdlib.flatMap: command =>
      command.extracts.stdlib.flatMap: extract =>
        globs.filter { glob => globMatches(glob, extract.path) }.map: glob =>
          val at = t"command ${command.name}: extract to ${extract.path}"
          refuse(t"$at matches source $glob (a feedback loop)")

  // `**` spans directories, `*` a name, `?` a character; everything else is literal.
  private def globMatches(glob: Text, path: Text): Boolean = path.s.matches(pattern(glob.s))

  private def pattern(glob: String): String =
    if glob.isEmpty then ""
    else if glob.startsWith("**") then ".*" + pattern(glob.substring(2).nn)
    else if glob.startsWith("*") then "[^/]*" + pattern(glob.substring(1).nn)
    else if glob.startsWith("?") then "[^/]" + pattern(glob.substring(1).nn)
    else
      val char: Char = glob.charAt(0)
      val special: Boolean = "\\.[]{}()+-^$|".indexOf(char.toInt) >= 0
      val literal: String = if special then "\\" + char else char.toString
      literal + pattern(glob.substring(1).nn)
