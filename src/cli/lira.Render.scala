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

import columnAttenuation.ignoreAttenuation
import textMetrics.uniformMetric
import hyphenations.englishHyphenation
import treeStyles.roundedTreeStyle

// Shared rendering for the tool's output (design/tool.md §5): the two shapes every command's
// output takes, so that no command invents a third.
//
// Every table is laid out against the *client's* terminal width, forwarded to the daemon in its
// `Stdio`, and capped so that a pipe — whose termcap reports no bound at all — gets a width a
// reader can follow rather than one line per atom stretched to `Int.MaxValue`.
private def tableWidth(using stdio: Stdio): Int =
  val reported = stdio.termcap.width
  if reported < 40 || reported > 160 then 120 else reported

// The facts a command establishes before its listing — module, version, grade, source — as a
// borderless two-column table, so that values line up however long the labels are.
// A rule that is `Blank` is *drawn* blank, not omitted, so a facts table styled from the
// stock givens would carry an empty band where its header would be. `Unset` omits, and these
// facts want no rules at all: two columns of label and value, aligned, and nothing else.
private val plainStyle: TableStyle =
  TableStyle(1, Unset, Unset, Unset, BoxLine.Blank, BoxLine.Blank, LineCharset.Default)

private def facts(rows: scala.List[(Text, Text)])(using Stdio): Unit =
  // `Tabulation`'s titles are a list of rows, so an empty one is headless; `Scaffold.tabulate`
  // always supplies exactly one, hence the re-wrap.
  given TableStyle = plainStyle

  if !rows.isEmpty then
    val scaffold = Scaffold[(Text, Text), Text](Column(t"")(_(0)), Column(t"")(_(1)))
    val table = scaffold.tabulate(List.from(rows))

    val headless = new Tabulation[Text]:
      type Row = (Text, Text)
      val columns: Array[Column[Row, Text]]^{} = table.columns
      val titles: List[Array[Array[Text]^{}]^{}] = List()
      val rows: List[Array[Array[Text]^{}]^{}] = table.rows
      val dataLength: Int = table.dataLength

    // The empty title section still renders one blank row; it heads nothing, so drop it.
    headless.grid(tableWidth).render.stdlib.filter(_.trim.length > 0).each(Out.println(_))

// An atom listing as a tree whose glyphs live in the first column of a table, so the shape of the
// API and the per-atom facts — class, value hash, what changed — are read together. Dendrology
// draws the tiles; `TreeStyle.serialize` turns them into the cell's text, and escritoire sizes
// what remains.
private enum Node[entry]:
  case Owner(label: Text)
  case Leaf(entry: entry, label: Text)

private case class TreeRow(name: Text, atomClass: Text, detail: Text, note: Text)

// Grouped by the owner each discipline decomposes its own keys into (§10.4 is explicit that the
// key text is diagnostic, so this grouping is too). An atom whose key does not decompose sits at
// the root; where its key is *also* an owner — a type's own atom beside the members it owns —
// it becomes that owner's first child, marked, rather than a second root spelled identically.
private def treeRows[entry]
    ( entries:   proscenium.List[entry],
      key:       entry => Text,
      atomClass: entry => Text,
      detail:    entry => Text,
      marker:    entry => Text )
    ( split: Text => Optional[Discipline.Decomposition] )
:   scala.List[TreeRow] =

  val grouped = entries.stdlib.groupBy { entry => split(key(entry)).let(_.owner).or(t"") }
  val owners: scala.List[Text] = grouped.keySet.toList.filter(_ != t"").sortBy(_.s)
  val loose: scala.List[entry] = grouped.get(t"").getOrElse(scala.Nil)
  val ownerSet = owners.toSet

  def sorted(entries: scala.List[entry]): scala.List[entry] =
    entries.sortBy { entry => key(entry).s }

  def leaf(entry: entry, whole: Boolean): Node[entry] =
    val name: Text = if whole then key(entry) else split(key(entry)).lay(key(entry))(_.member)
    Node.Leaf(entry, t"${marker(entry)}$name")

  val roots: scala.List[Node[entry]] =
    owners.map(Node.Owner(_))
      ++ sorted(loose.filter { entry => !ownerSet.contains(key(entry)) }).map(leaf(_, true))

  def children(node: Node[entry]): proscenium.List[Node[entry]] = node match
    case Node.Leaf(_, _) => proscenium.List()

    case Node.Owner(label) =>
      val own = sorted(loose.filter { entry => key(entry) == label }).map: entry =>
        val name: Text = t"${marker(entry)}${key(entry)}"
        Node.Leaf(entry, t"$name  (itself)")

      val members = sorted(grouped.get(label).getOrElse(scala.Nil)).map(leaf(_, false))
      proscenium.List.from(own ++ members)

  val diagram = TreeDiagram.by[Node[entry]](children(_))(roots*)

  val chain = diagram.map: tiles => node =>
    val name: Text = roundedTreeStyle.serialize(tiles, nodeLabel(node))

    node match
      case Node.Owner(_)       => TreeRow(name, t"", t"", t"")
      case Node.Leaf(entry, _) => TreeRow(name, atomClass(entry), detail(entry), t"")

  chain.stdlib.toList

private def nodeLabel[entry](node: Node[entry]): Text = node match
  case Node.Owner(label)   => label
  case Node.Leaf(_, label) => label

// The listing itself. `detail` is whatever the command has to say about each atom beyond its
// class — a value hash for `atoms`, an old-to-new hash pair for a replaced atom in `delta`.
private def treeTable(rows: scala.List[TreeRow], detail: Text)(using Stdio): Unit =
  import tableStyles.thinRoundedTableStyle

  if !rows.isEmpty then
    val scaffold = Scaffold[TreeRow, Text]
      ( Column(t"Atom")(_.name),
        Column(t"Class")(_.atomClass),
        Column(detail)(_.detail) )

    scaffold.tabulate(List.from(rows)).grid(tableWidth).render.stdlib.each(Out.println(_))
