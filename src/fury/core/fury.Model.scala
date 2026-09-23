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

case class BuildError(detail: Text)(using Diagnostics) extends Error(m"build file: $detail")

// The typed model of a build.tel (build.schema.tel), hand-decoded from the validated document in
// the style of reliquary's `Manifest.decode` — ladder step 1 tests whether that is tolerable at
// this schema's size (fury.md §15). Positional atoms are read in the schema's field order and
// child fields by keyword. Nothing here resolves anything: forms, tools and coordinates stay
// texts until `fury resolve`.
object Model:
  case class Build
    ( registries:   List[Registry],
       commands:     List[Command],
       toolchains:   List[Toolchain],
       repositories: List[Repository],
       projects:     List[Project],
       topologies:   List[Text] )

  case class Registry(coordinate: Text, selector: Optional[Text])
  case class Argument(name: Text, default: Optional[Text], option: Optional[Text])
  case class Selection(option: Text, choice: Text)
  case class BuildStep(module: Text, artifacts: List[Text], selections: List[Selection])
  case class RunStep(module: Text, arguments: List[Text], selections: List[Selection])
  case class Extract(module: Text, entry: Text, path: Text)

  case class Command
    ( name:     Text,
       args:     List[Argument],
       builds:   List[BuildStep],
       runs:     List[RunStep],
       watches:  List[Text],
       extracts: List[Extract] )

  case class Setting(key: Text, value: Text)
  case class Component(name: Text, selector: Optional[Text])
  case class Edge(name: Text, components: List[Component], settings: List[Setting])

  case class Tool
    ( name:     Text,
       module:   Optional[Text],
       selector: Optional[Text],
       version:  Optional[Text],
       flags:    List[Text],
       settings: List[Setting],
       edges:    List[Edge] )

  case class Toolchain(name: Text, tools: List[Tool], children: List[Toolchain])
  case class Repository(name: Text, origin: Text, scope: Optional[Text], track: Optional[Text])
  case class Host(domain: Optional[Text], tag: Text)
  case class Presumption(kind: Text, name: Text, predicate: Optional[Text])
  case class Source(glob: Text, form: Optional[Text])
  case class Include(module: Text, selector: Optional[Text])
  case class Assembly(module: Text, path: Optional[Text])
  case class Product(name: Text, appType: Text, filename: Optional[Text], platforms: List[Text])
  case class Axis(name: Text, cases: List[Text], ephemeral: Boolean)
  case class Universe(name: Text, ephemeral: Boolean)

  case class Module
    ( name:         Text,
       apply:        Optional[Text],
       sources:      List[Source],
       requires:     List[Text],
       presumes:     List[Presumption],
       guarantees:   List[Presumption],
       includes:     List[Include],
       assemble:     Optional[Assembly],
       artifacts:    List[Product],
       generates:    List[Text],
       integrations: List[Axis],
       options:      List[Axis],
       universes:    List[Universe],
       host:         Optional[Host] )

  case class Project
    ( id:          Text,
       name:        Optional[Text],
       description: Optional[Text],
       apply:       Optional[Text],
       host:        Optional[Host],
       modules:     List[Module] )

  def decode(tel: Tel): Build raises BuildError =
    val top = tel.childCompounds.readable.toVector

    Build
      ( registries   = named(top, t"registry").map(registry).to(List),
        commands     = named(top, t"command").map(command).to(List),
        toolchains   = named(top, t"toolchain").map(toolchain).to(List),
        repositories = named(top, t"repository").map(repository).to(List),
        projects     = named(top, t"project").map(project).to(List),
        topologies   = named(top, t"topology").map(first(_, t"a topology")).to(List) )

  private def bad(detail: Text): BuildError =
    import errorDiagnostics.emptyDiagnostics
    BuildError(detail)

  private def texts(compound: Tel.Compound): sci.Vector[Text] =
    compound.atoms.readable.collect:
      case Tel.Atom.Inline(text, _)  => text
      case Tel.Atom.Source(text)     => text
      case Tel.Atom.Literal(_, text) => text

    . toVector

  private def children(compound: Tel.Compound): sci.Vector[Tel.Compound] =
    compound.children.readable.flatMap(_.compounds.readable).toVector

  private def named(compounds: sci.Vector[Tel.Compound], keyword: Text): sci.Vector[Tel.Compound] =
    compounds.filter(_.keyword == keyword)

  private def atom(compound: Tel.Compound, index: Int): Optional[Text] =
    texts(compound).lift(index).getOrElse(Unset)

  private def first(compound: Tel.Compound, what: Text): Text raises BuildError =
    atom(compound, 0).or(abort(bad(t"$what needs a name")))

  // A child field with exactly one atom, absent or present at most once.
  private def field(compounds: sci.Vector[Tel.Compound], keyword: Text)
  :   Optional[Text] raises BuildError =

    named(compounds, keyword) match
      case sci.Vector()         => Unset
      case sci.Vector(compound) => single(compound, keyword)
      case _                    => abort(bad(t"the $keyword field is repeated"))

  private def single(compound: Tel.Compound, keyword: Text): Text raises BuildError =
    texts(compound) match
      case sci.Vector(value) => value
      case _                 => abort(bad(t"the $keyword field needs exactly one value"))

  private def required(compounds: sci.Vector[Tel.Compound], keyword: Text)
  :   Text raises BuildError =

    field(compounds, keyword).or(abort(bad(t"the $keyword field is missing")))

  // Every atom of every occurrence of a repeatable single-value field.
  private def repeated(compounds: sci.Vector[Tel.Compound], keyword: Text)
  :   sci.Vector[Text] raises BuildError =

    named(compounds, keyword).flatMap: compound =>
      val atoms = texts(compound)
      if atoms.isEmpty then abort(bad(t"the $keyword field needs at least one value"))
      atoms

  private def flag(compounds: sci.Vector[Tel.Compound], keyword: Text): Boolean =
    named(compounds, keyword).nonEmpty

  private def optionally[value](compounds: sci.Vector[Tel.Compound])
    ( decode: Tel.Compound => value )
  :   Optional[value] =

    compounds.headOption.map(decode).getOrElse(Unset)

  private def registry(compound: Tel.Compound): Registry raises BuildError =
    Registry(first(compound, t"a registry layer"), atom(compound, 1))

  private def selections(compounds: sci.Vector[Tel.Compound]): List[Selection] raises BuildError =
    named(compounds, t"set").map: set =>
      texts(set) match
        case sci.Vector(option, choice) => Selection(option, choice)
        case _                          => abort(bad(t"a selection is `set <option> <case>`"))

    . to(List)

  private def settings(compounds: sci.Vector[Tel.Compound]): List[Setting] raises BuildError =
    named(compounds, t"set").map: set =>
      texts(set) match
        case sci.Vector(key, value) => Setting(key, value)
        case _                      => abort(bad(t"a setting is `set <key> <value>`"))

    . to(List)

  private def command(compound: Tel.Compound): Command raises BuildError =
    val fields = children(compound)

    val args = named(fields, t"arg").map: arg =>
      Argument
        ( first(arg, t"an argument"),
          atom(arg, 1),
          atom(arg, 2).or(field(children(arg), t"option")) )

    val builds = named(fields, t"build").map: build =>
      val sub = children(build)
      BuildStep(first(build, t"a build step"), repeated(sub, t"artifact").to(List), selections(sub))

    val runs = named(fields, t"run").map: run =>
      RunStep(first(run, t"a run step"), texts(run).drop(1).to(List), selections(children(run)))

    val extracts = named(fields, t"extract").map: extract =>
      texts(extract) match
        case sci.Vector(module, entry, path) => Extract(module, entry, path)
        case _                               => abort(bad(t"an extract needs three values"))

    Command
      ( name     = first(compound, t"a command"),
        args     = args.to(List),
        builds   = builds.to(List),
        runs     = runs.to(List),
        watches  = named(fields, t"watch").map(first(_, t"a watch")).to(List),
        extracts = extracts.to(List) )

  private def tool(compound: Tel.Compound): Tool raises BuildError =
    val fields = children(compound)

    val edges = named(fields, t"edge").map: edge =>
      val sub = children(edge)

      val components = named(sub, t"component").map: component =>
        Component(first(component, t"a component"), atom(component, 1))

      Edge(first(edge, t"an edge"), components.to(List), settings(sub))

    Tool
      ( name     = first(compound, t"a tool"),
        module   = atom(compound, 1),
        selector = atom(compound, 2),
        version  = field(fields, t"version"),
        flags    = repeated(fields, t"flag").to(List),
        settings = settings(fields),
        edges    = edges.to(List) )

  private def toolchain(compound: Tel.Compound): Toolchain raises BuildError =
    val fields = children(compound)

    Toolchain
      ( first(compound, t"a toolchain"),
        named(fields, t"tool").map(tool).to(List),
        named(fields, t"toolchain").map(toolchain).to(List) )

  private def repository(compound: Tel.Compound): Repository raises BuildError =
    val fields = children(compound)

    Repository
      ( first(compound, t"a repository"),
        required(fields, t"origin"),
        field(fields, t"scope"),
        field(fields, t"track") )

  private def host(compound: Tel.Compound): Host raises BuildError = texts(compound) match
    case sci.Vector(tag)         => Host(Unset, tag)
    case sci.Vector(domain, tag) => Host(domain, tag)
    case _                       => abort(bad(t"a host is `host [<domain>] <tag>`"))

  private def presumption(compound: Tel.Compound): Presumption raises BuildError =
    texts(compound) match
      case sci.Vector(kind, name)            => Presumption(kind, name, Unset)
      case sci.Vector(kind, name, predicate) => Presumption(kind, name, predicate)
      case _                                 => abort(bad(t"a presumption needs a kind and a name"))

  private def axis(compound: Tel.Compound): Axis raises BuildError =
    val fields = children(compound)

    Axis
      ( first(compound, t"an axis"),
        named(fields, t"case").map(first(_, t"a case")).to(List),
        flag(fields, t"ephemeral") )

  private def module(compound: Tel.Compound): Module raises BuildError =
    val fields = children(compound)

    val sources = named(fields, t"source").map: source =>
      Source(first(source, t"a source"), field(children(source), t"form"))

    val includes = named(fields, t"include").map: include =>
      Include(first(include, t"an include"), atom(include, 1))

    val artifacts = named(fields, t"artifact").map: artifact =>
      val appType = atom(artifact, 1).or:
        abort(bad(t"an artifact is `artifact <name> <app-type> [<filename>]`"))

      Product
        ( first(artifact, t"an artifact"),
          appType,
          atom(artifact, 2),
          repeated(children(artifact), t"platform").to(List) )

    val universes = named(fields, t"universe").map: universe =>
      Universe(first(universe, t"a universe"), flag(children(universe), t"ephemeral"))

    val assemble = optionally(named(fields, t"assemble")): assembly =>
      val path: Optional[Text] = atom(assembly, 1).or(field(children(assembly), t"path"))
      Assembly(first(assembly, t"an assembly"), path)

    Module
      ( name         = first(compound, t"a module"),
        apply        = field(fields, t"apply"),
        sources      = sources.to(List),
        requires     = repeated(fields, t"require").to(List),
        presumes     = named(fields, t"presume").map(presumption).to(List),
        guarantees   = named(fields, t"guarantee").map(presumption).to(List),
        includes     = includes.to(List),
        assemble     = assemble,
        artifacts    = artifacts.to(List),
        generates    = repeated(fields, t"generate").to(List),
        integrations = named(fields, t"integration").map(axis).to(List),
        options      = named(fields, t"option").map(axis).to(List),
        universes    = universes.to(List),
        host         = optionally(named(fields, t"host"))(host) )

  private def project(compound: Tel.Compound): Project raises BuildError =
    val fields = children(compound)

    Project
      ( id          = first(compound, t"a project"),
        name        = field(fields, t"name"),
        description = field(fields, t"description"),
        apply       = field(fields, t"apply"),
        host        = optionally(named(fields, t"host"))(host),
        modules     = named(fields, t"module").map(module).to(List) )
