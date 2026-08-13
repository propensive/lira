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

import charDecoders.utf8Decoder
import logging.silentLogging
import textSanitizers.strictSanitizer

// `lira atoms` (design/tool.md §5.2): the atom listing of a release or of a bare artifact.
//
// The two are the same question asked of different inputs, and the difference matters. A `.lira`
// file *declares* its atoms in the Atoms metadata blobs its `api` records name (§10.4), so the
// listing is read, not computed, and is exactly what a consumer's verifier compares against. A
// bare artifact declares nothing, so its atoms are computed here and now, which answers a
// different question: what this content *would* contribute were it published.
//
// Computing them takes what atomization always takes (§11.2): a realm, since a discipline out of
// its domain claims nothing, and for the membership-keyed disciplines a dependency classpath,
// since a type's presented surface includes what it inherits. Neither can be guessed from the
// bytes, so both are flags, and an unresolvable supertype fails rather than under-reporting.

// The disciplines lira can name, in claiming order (§11.4). `classfile/1` precedes `jsig/1`, which
// also claims `.class`, and both precede `tasty/1`, which holds derived binaries atomless: in the
// other order a jar of classfiles would list nothing at all.
private val knownDisciplines: proscenium.List[Discipline] =
  proscenium.List(ClassfileDiscipline, JsigDiscipline, Tasty, DtsDiscipline, WebIdlDiscipline,
    WitDiscipline, CHeaderDiscipline, KotlinMetadataDiscipline, CapabilityDiscipline)

// How a discipline decomposes its own keys (LIRA §10.4; `Discipline.decompose`), by id. A
// discipline lira cannot name decomposes nothing, and its atoms list flat — which is the same
// outcome as a discipline that states no decomposition, and needs no special case.
private def decomposer(discipline: Text, resources: proscenium.List[LiraManifest.Resource])
:   Text => Optional[Discipline.Decomposition] =

  val implementation: Optional[Discipline] =
    val resource = reliquary.ResourceDiscipline(resources)

    if discipline == resource.id then resource
    else if discipline == OpaqueDiscipline.id then OpaqueDiscipline
    else knownDisciplines.stdlib.find(_.id == discipline).getOrElse(Unset)

  key => implementation.let { discipline => discipline.decompose(key) }

// Atoms under the owners their keys decompose into, owners sorted, and each owner's atoms sorted
// by key: an atom listing's own order is by ascending value hash (§10.4), which is meaningless to
// a reader.
//
// An atom whose key does not decompose is printed at the top level, and where its key is also an
// owner — a type's own atom stands beside the members it owns, under `classfile/1` and `jsig/1`
// alike — it is printed *within* that owner's group, marked, rather than as a second line
// spelled identically to the heading above it.
//
// `prefix` and `suffix` decorate an entry — a change marker, a class note — and deliberately take
// no part in decomposition: only this function holds `split`, since separation checking forbids
// two arguments that capture it.
private def renderGrouped[entry]
    (entries: proscenium.List[entry], key: entry => Text, prefix: entry => Text,
     suffix: entry => Text)
    (split: Text => Optional[Discipline.Decomposition])
    (using Stdio)
:   Unit =

  def line(entry: entry): Text =
    val name: Text = split(key(entry)).lay(key(entry))(_.member)
    t"${prefix(entry)}$name${suffix(entry)}"


  val grouped = entries.stdlib.groupBy { entry => split(key(entry)).let(_.owner).or(t"") }
  val owners: scala.List[Text] = grouped.keySet.toList.filter(_ != t"").sortBy(_.s)
  val loose: scala.List[entry] = grouped.get(t"").getOrElse(scala.Nil)
  val ownerSet = owners.toSet

  def sorted(entries: scala.List[entry]): scala.List[entry] = entries.sortBy { entry => key(entry).s }

  owners.each: owner =>
    Out.println(t"  $owner")

    sorted(loose.filter { entry => key(entry) == owner }).each: entry =>
      Out.println(t"    ${line(entry)}  (itself)")

    sorted(grouped.get(owner).getOrElse(scala.Nil)).each: entry =>
      Out.println(t"    ${line(entry)}")

  val orphans = sorted(loose.filter { entry => !ownerSet.contains(key(entry)) })

  if !orphans.isEmpty then
    orphans.each: entry =>
      Out.println(t"  ${line(entry)}")

private def atomsCommand
    ( arguments: proscenium.List[Text],
      realm:     Optional[Text],
      classpath: Optional[Text],
      only:      Optional[Text],
      owner:     Optional[Text] )
    (using cli: Cli)
:   Exit =

  positional(arguments) match
    case scala.List(file) => atomsOf(file, realm, classpath, only, owner)

    case _ =>
      guard:
        given Stdio = cli.stdio
        Out.println(t"lira: atoms takes one file: lira atoms <file> [--realm <realm>]")
        Exit.Fail(1)

private def atomsOf
    ( file:      Text,
      realm:     Optional[Text],
      classpath: Optional[Text],
      only:      Optional[Text],
      owner:     Optional[Text] )
    (using cli: Cli)
:   Exit = guard:

  given Stdio = cli.stdio
  import strategies.throwUnsafely
  val data = lira.load(file)

  safely(Lira.read(data)).lay(computed(file, data, realm, classpath, only, owner)): release =>
    declared(release, only, owner)

// A release's own listing, as its `api` records carry it: the atoms a consumer's verifier will
// recompute and compare against, not a fresh atomization of the payload.
private def declared(release: Lira, only: Optional[Text], owner: Optional[Text])
    (using Cli, Stdio)
:   Exit =

  import strategies.throwUnsafely
  val report = Verification.install(release)
  val manifest = release.manifest

  Out.println(t"module:  ${manifest.module}")
  manifest.version.let { version => Out.println(t"version: $version") }
  Out.println(t"source:  declared (the release's own atom listings)")
  Out.println(t"")

  listings(report.atomizations, manifest.resource, only, owner)

// A bare artifact's atoms, computed now: which discipline claims what is decided by the registry
// exactly as it is at publish time (§11.4), so an item no language discipline claims falls to
// `opaque/1` and is listed as such rather than silently dropped.
private def computed
    ( file:      Text,
      data:      Data,
      realm:     Optional[Text],
      classpath: Optional[Text],
      only:      Optional[Text],
      owner:     Optional[Text] )
    (using Cli, Stdio)
:   Exit =

  import strategies.throwUnsafely
  val where: Text = realm.or(t"jvm")

  val paths: proscenium.List[Text] =
    classpath.lay(proscenium.List[Text]()): text =>
      val entries: scala.List[Text] = text.cut(t":").stdlib.toList.filter(_.s.nonEmpty)
      proscenium.List.from(entries)

  val content = expand(file, data)

  val selected: proscenium.List[Discipline] =
    only.lay(knownDisciplines): id =>
      proscenium.List.from(knownDisciplines.stdlib.filter(_.id == id))

  if only.present && selected.stdlib.isEmpty then
    Out.println(t"lira: no discipline named ${only.or(t"")}; lira knows:")
    knownDisciplines.stdlib.each { discipline => Out.println(t"      ${discipline.id}") }
    Exit.Fail(1)
  else
    val registry = Discipline.Registry(selected)
    val context = Discipline.Context(where, Unset, paths)
    val atomizations = registry.atomize(content, context)

    Out.println(t"artifact: $file")
    Out.println(t"source:   computed (realm $where, ${content.stdlib.size} items)")
    Out.println(t"")

    val exit = listings(atomizations, proscenium.List(), only, owner)
    elsewhere(content, where, selected)
    exit

// A discipline out of its domain claims nothing *ahead of* any question of what it would have
// claimed (§11.2), so content that looks unclaimed under one realm may be a discipline's whole
// subject under another — `webidl/1` and `cheader/1` live in `{host}` alone. Saying so is the
// difference between a listing a reader can trust and one that quietly under-reports.
private def elsewhere
    (content: proscenium.List[(TreePath, Data)], realm: Text, selected: proscenium.List[Discipline])
    (using Stdio)
:   Unit =

  val realms: scala.List[Text] = scala.List(t"jvm", t"sjsir", t"nir", t"host")

  val hints = selected.stdlib.flatMap: discipline =>
    if discipline.domain.covers(realm) then scala.Nil
    else
      val claims = content.stdlib.exists { (path, data) => discipline.claims(path, data) }

      if !claims then scala.Nil else
        val others = realms.filter(discipline.domain.covers)
        if others.isEmpty then scala.Nil
        else
          val realms: Text = Text(others.map(_.s).mkString(", "))
          scala.List(t"${discipline.id} would claim content here in $realms")

  if !hints.isEmpty then
    Out.println(t"")

    hints.each: hint =>
      val text: Text = hint
      Out.println(t"note: $text")

    Out.println(t"      (atomization is per-realm; pass --realm to choose one)")

// One block per discipline, since an atom's identity is domain-separated by the discipline that
// minted it (§7.1) and two disciplines' keys are not comparable.
private def listings
    ( atomizations: proscenium.List[Atomization],
      resources:    proscenium.List[LiraManifest.Resource],
      only:         Optional[Text],
      owner:        Optional[Text] )
    (using Stdio)
:   Exit =

  val chosen = atomizations.stdlib.filter: atomization =>
    only.lay(true) { id => atomization.discipline == id }

  if chosen.isEmpty then
    Out.println(t"no atoms")
    Exit.Ok
  else
    var total = 0

    chosen.sortBy(_.discipline.s).each: atomization =>
      val split = decomposer(atomization.discipline, resources)

      val atoms = atomization.atoms.stdlib.filter: atom =>
        owner.lay(true) { prefix => atom.key.s.startsWith(prefix.s) }

      val rigid = atoms.count(_.atomClass == AtomClass.Rigid)
      val replaceable = atoms.length - rigid
      total += atoms.length

      Out.println(t"${atomization.discipline}  ($rigid rigid, $replaceable replaceable)")

      def note(atom: Atom): Text =
        if atom.atomClass == AtomClass.Replaceable then t" (replaceable)" else t""

      renderGrouped(proscenium.List.from(atoms), _.key, _ => t"", note(_))(split)

      Out.println(t"")

    Out.println(t"$total atoms")
    Exit.Ok

// A jar, an APK, a `ct.sym` — anything the zip magic identifies — is a tree of items, and its
// entries are the content atomization sees; anything else is one item, named by its own filename
// so that a discipline claiming by extension still claims it.
private def expand(file: Text, data: Data)
:   proscenium.List[(TreePath, Data)] raises LiraError =
  val bytes = data.readable

  val zipped =
    data.length > 4 && bytes(0) == 'P' && bytes(1) == 'K' && bytes(2) == 3 && bytes(3) == 4

  if !zipped then
    val name = Text(jnf.Paths.get(file.s).nn.getFileName.nn.toString)
    proscenium.List((TreePath(name), data))
  else
    val zip = java.util.zip.ZipFile(file.s)

    try
      val entries = scala.collection.mutable.ListBuffer[(TreePath, Data)]()

      zip.entries.nn.asScala.foreach: entry =>
        val item = entry.nn

        if !item.isDirectory then
          val stream = zip.getInputStream(item).nn
          val content = stream.readAllBytes().nn
          stream.close()
          entries += ((TreePath(Text(item.getName.nn)), Array.unsafeFrozen(content)))

      proscenium.List.from(entries.toList)

    finally zip.close()
