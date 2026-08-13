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

import logging.silentLogging
import textSanitizers.strictSanitizer

// `lira delta` (design/tool.md §2.6): what changed between two releases of one module, and what
// grade the change carries. The grade is the whole of the compatibility question — `Grade.between`
// is set arithmetic over atoms and needs no listing to reach its verdict (LIRA §10.3) — so the
// listing below exists purely to answer the reader's next question, which is *what* changed.
//
// The atom-level record `LiraDelta.compute` writes is deliberately not the source of the listing:
// it carries value hashes alone, because a verifier checking a lineage step has both releases in
// hand and needs no keys. The listing therefore works from the two atomizations directly, and the
// blob is written only when asked for.

// One change to one atom. `Replaced` is the replaceable-atom case of §10.2 — same key, new value,
// which leaves compiled consumers behaviorally stale — while a rigid atom whose value moves is a
// removal plus an addition, and reads as both.
private enum Change:
  case Added, Removed, Replaced

  def marker: Text = this match
    case Added    => t"+"
    case Removed  => t"-"
    case Replaced => t"~"

private case class Entry(key: Text, atomClass: AtomClass, change: Change)

// Every discipline lira can name, for `decompose` alone: an unrecognised discipline id is not an
// error here, since the listing degrades to ungrouped keys and nothing else depends on it.
private val disciplines: proscenium.List[Discipline] =
  proscenium.List(Tasty, ClassfileDiscipline, JsigDiscipline, DtsDiscipline, WebIdlDiscipline,
    WitDiscipline, CHeaderDiscipline, KotlinMetadataDiscipline, CapabilityDiscipline,
    OpaqueDiscipline)

// `--blob <file>` occupies two argument slots, so the two releases are the positional arguments
// that remain once the flag and the value it takes are set aside.
private def delta(arguments: proscenium.List[Text], blob: Optional[Text])(using cli: Cli)
:   Exit =

  var skip = false

  val positional = arguments.stdlib.filter: argument =>
    val flag = argument.s.startsWith("-")
    val value = skip
    skip = flag
    !flag && !value

  positional match
    case scala.List(previous, next) => compareReleases(previous, next, blob)

    case _ =>
      guard:
        given Stdio = cli.stdio
        Out.println(t"lira: delta takes two files: lira delta <previous.lira> <next.lira>")
        Exit.Fail(1)

private def compareReleases(previous: Text, next: Text, blob: Optional[Text])(using cli: Cli)
:   Exit = guard:

  given Stdio = cli.stdio
  import strategies.throwUnsafely

  val before = Lira.read(lira.load(previous))
  val after = Lira.read(lira.load(next))

  if before.manifest.module != after.manifest.module then
    Out.println(t"lira: ${before.manifest.module} and ${after.manifest.module} are different")
    Out.println(t"      modules; a delta compares two releases of one module")
    Exit.Fail(1)
  else
    val beforeReport = Verification.install(before)
    val afterReport = Verification.install(after)
    val grade = Grade.between(beforeReport.atomizations, afterReport.atomizations)

    val versions =
      def version(lira: Lira): Text = lira.manifest.version.let { v => t"$v" }.or(t"development")
      t"${version(before)} -> ${version(after)}"

    Out.println(t"module:  ${after.manifest.module}")
    Out.println(t"grade:   ${gradeText(grade)} ($versions)")
    sections(before, after)
    Out.println(t"")

    val disciplineIds =
      (beforeReport.atomizations.stdlib.map(_.discipline)
        ++ afterReport.atomizations.stdlib.map(_.discipline)).distinct.sorted

    var changes = 0

    disciplineIds.each: id =>
      val entries = compare(atoms(beforeReport, id), atoms(afterReport, id))
      changes = changes + entries.stdlib.size

      if !entries.stdlib.isEmpty then
        Out.println(t"$id")
        render(id, after.manifest, entries)
        Out.println(t"")

    if changes == 0 then Out.println(t"no atom changed")

    blob.let: target =>
      val record = LiraDelta.compute(beforeReport.atomizations, afterReport.atomizations)
      lira.save(target, record.encode)
      Out.println(t"delta blob -> $target")

    Exit.Ok

private def gradeText(grade: Grade): Text = grade match
  case Grade.Patch => t"patch (the two releases present the same API)"
  case Grade.Minor => t"minor (extension, and replaceable churn)"
  case Grade.Major => t"major (a rigid removal or change; a fresh lineage)"

// Sections are keyed by (realm, integration) and every section of a release presents the same
// atoms (L108), so a per-section atom listing would repeat itself; what a section *can* differ
// in, and what a reader wants to know, is whether the release still carries the cell at all.
private def sections(before: Lira, after: Lira)(using Stdio): Unit =
  def cells(release: Lira): scala.List[Text] =
    release.manifest.section.stdlib.map: section =>
      val realm: Text = section.realm
      val integration: Optional[Text] = section.integration
      integration.lay(realm) { id => t"$realm/$id" }

  val old = cells(before)
  val current = cells(after)
  val added = current.filter { cell => !old.contains(cell) }
  val removed = old.filter { cell => !current.contains(cell) }

  Out.println(t"sections: ${Text(current.mkString(", "))}")
  if !added.isEmpty then Out.println(t"          added: ${Text(added.mkString(", "))}")
  if !removed.isEmpty then Out.println(t"          removed: ${Text(removed.mkString(", "))}")

private def atoms(report: Verification.Report, discipline: Text): proscenium.List[Atom] =
  report.atomizations.stdlib.find(_.discipline == discipline).map(_.atoms).getOrElse:
    proscenium.List()

// Atoms compare by key: a key present on one side alone is an addition or a removal, and a key on
// both whose value hash moved is a replacement — which for a rigid atom is a removal plus an
// addition (§10.2), reported as both so the listing never understates a break.
private def compare(before: proscenium.List[Atom], next: proscenium.List[Atom])
:   proscenium.List[Entry] =

  def index(atoms: proscenium.List[Atom]): scala.collection.immutable.Map[Text, Atom] =
    scala.collection.immutable.Map.from(atoms.stdlib.map { atom => (atom.key, atom) })

  val old = index(before)
  val current = index(next)

  val entries = (old.keySet ++ current.keySet).toList.sorted.flatMap: key =>
    (old.get(key), current.get(key)) match
      case (scala.None, scala.Some(atom)) => scala.List(Entry(key, atom.atomClass, Change.Added))
      case (scala.Some(atom), scala.None) => scala.List(Entry(key, atom.atomClass, Change.Removed))

      case (scala.Some(atom), scala.Some(successor)) =>
        if LiraHash.text(atom.valueHash) == LiraHash.text(successor.valueHash) then scala.Nil
        else if successor.atomClass == AtomClass.Replaceable
        then scala.List(Entry(key, successor.atomClass, Change.Replaced))
        else
          scala.List
            ( Entry(key, atom.atomClass, Change.Removed),
              Entry(key, successor.atomClass, Change.Added) )

      case _ => scala.Nil

  proscenium.List.from(entries)

// Grouped by owner, as the discipline that minted the keys decomposes them: a discipline whose
// keys have no owner — `cheader/1`'s C identifiers, `capability/1`'s names — states no
// decomposition, and its atoms list flat.
private def render(discipline: Text, manifest: LiraManifest, entries: proscenium.List[Entry])
    (using Stdio)
:   Unit =

  val implementation: Optional[Discipline] =
    val resource = reliquary.ResourceDiscipline(manifest.resource)
    if discipline == resource.id then resource
    else disciplines.stdlib.find(_.id == discipline).getOrElse(Unset)

  def split(key: Text): Optional[Discipline.Decomposition] =
    implementation.let { discipline => discipline.decompose(key) }

  val grouped = entries.stdlib.groupBy { entry => split(entry.key).let(_.owner).or(t"") }

  grouped.toList.sortBy(_(0).s).each: (label, changed) =>
    if label != t"" then Out.println(t"  $label")

    changed.each: entry =>
      val name: Text = if label == t"" then entry.key else split(entry.key).lay(entry.key)(_.member)
      val note: Text = if entry.atomClass == AtomClass.Replaceable then t" (replaceable)" else t""
      val indent: Text = if label == t"" then t"  " else t"    "
      Out.println(t"$indent${entry.change.marker} $name$note")
