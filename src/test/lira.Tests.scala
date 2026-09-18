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

// See the note in `lira.LiraTool.scala`: the collection types come straight from Proscenium.
import proscenium.{List, Nil}

import charEncoders.utf8Encoder
import strategies.throwUnsafely

// Tests for the parts of the tool that are decidable from their arguments alone: the §5.2 byte
// layout every `.lira` file has, the content-addressed store's naming (design/tool.md §2), and
// the command surface the usage text is derived from. Nothing here touches a store on disk or
// reads a real release — those need fixtures the format's own implementation in Soundness
// (`reliquary`) tests for itself. Run with `make test` (fume) or `make test-plain`.
object Tests extends Suite(m"LIRA tool tests"):

  // A minimal file in the §5.2 shape: a TEL manifest, the `##` separator line alone, then the
  // payload. The separator is the first line consisting of exactly `##`, which is what lets a
  // file be split without parsing any TEL.
  val manifestText: Text = t"lira 1.0\n\nname example\nversion 1.0.0\n"
  val payloadText: Text = t"payload bytes\n"

  def file(manifest: Text, payload: Text): Data =
    utf8Encoder.encoded(t"$manifest##\n$payload")

  def run(): Unit =
    suite(m"File layout (spec §5.2)"):
      test(m"The separator line is found"):
        Store.separatorIndex(file(manifestText, payloadText))
      . assert(_ != Unset)

      // `Data` is an immutable array, so `==` on two of them is reference equality; compare the
      // bytes they hold.
      def bytes(data: Optional[Data]): Optional[scala.List[Byte]] = data.let(_.readable.toList)

      test(m"The separator index is the newline that opens the `##` line"):
        val data = file(manifestText, payloadText)
        bytes(Store.separatorIndex(data).let { index => Store.slice(data, 0, index + 1) })
      . assert(_ == bytes(utf8Encoder.encoded(manifestText)))

      test(m"The payload is what follows the separator line"):
        val data = file(manifestText, payloadText)
        bytes(Store.separatorIndex(data).let { index => Store.slice(data, index + 4, data.length) })
      . assert(_ == bytes(utf8Encoder.encoded(payloadText)))

      test(m"A file with no separator line has no separator index"):
        Store.separatorIndex(utf8Encoder.encoded(manifestText))
      . assert(_ == Unset)

      test(m"A `##` that is not alone on its line is not the separator"):
        Store.separatorIndex(utf8Encoder.encoded(t"lira 1.0\n## not a separator\n"))
      . assert(_ == Unset)

      test(m"The first of two separator lines is the one found"):
        Store.separatorIndex(utf8Encoder.encoded(t"a\n##\nb\n##\nc\n"))
      . assert(_ == 1)

    suite(m"Manifest-bytes hashing (spec §7.1)"):
      test(m"Equal bytes hash equally"):
        val once = Store.manifestBytesHash(utf8Encoder.encoded(manifestText))
        val again = Store.manifestBytesHash(utf8Encoder.encoded(manifestText))
        once.readable.toList == again.readable.toList
      . assert(_ == true)

      test(m"Different bytes hash differently"):
        val one = Store.manifestBytesHash(utf8Encoder.encoded(manifestText))
        val other = Store.manifestBytesHash(utf8Encoder.encoded(t"lira 1.0\n\nname other\n"))
        one.readable.toList == other.readable.toList
      . assert(_ == false)

      test(m"The hash is a Blake3 digest, so 32 bytes wide"):
        Store.manifestBytesHash(utf8Encoder.encoded(manifestText)).length
      . assert(_ == 32)

      // The domain prefix is what keeps raw manifest bytes from colliding with the spec's own
      // `lira/1:manifest` domain, which hashes the canonical signing encoding instead.
      test(m"The domain prefix is applied, so this is not a bare Blake3 of the content"):
        val content = utf8Encoder.encoded(manifestText)
        Store.manifestBytesHash(content).readable.toList == Blake3.hashOf(content).readable.toList
      . assert(_ == false)

    suite(m"Store layout (design/tool.md §2)"):
      val store = Store(t"/tmp/lira-store".as[Path on Linux])
      val hex = t"0123456789abcdef"

      test(m"An object's path fans out on its first two hex characters"):
        store.objectPath(Store.Tier.Payload, hex).encode
      . assert(_ == t"/tmp/lira-store/payload/01/0123456789abcdef")

      // Listed rather than derived from `Tier.values`: the point is that these four names, which
      // are directory names in a store on disk, are exactly what §2 fixes — a fifth tier, or a
      // renamed one, should fail here rather than pass by construction.
      val tiers = scala.List
        (Store.Tier.Manifest, Store.Tier.Payload, Store.Tier.Blob, Store.Tier.Derivative)

      test(m"Tier directory names are the ones design/tool.md §2 fixes"):
        tiers.map(_.dirName)
      . assert(_ == scala.List(t"manifest", t"payload", t"blob", t"derivative"))

      test(m"Every tier has a distinct directory"):
        tiers.map(_.dirName).toSet.size
      . assert(_ == tiers.size)

    // `prune` (lira.LiraTool.scala) keeps files out of the help tree by testing `group.present`,
    // so a subcommand declared without a group is silently absent from the usage text.
    suite(m"Command surface (design/tool.md §5)"):
      val subcommands = scala.List
        (lira.Verify, lira.Harvest, lira.Jar, lira.Assign, lira.Delta, lira.AtomsCmd, lira.Id,
         lira.Cache, lira.Pin, lira.Unpin, lira.Gc, lira.Fsck, lira.Install, lira.Help, lira.Quit)

      val flags = scala.List[Flag](lira.Major, lira.Budget, lira.Blob, lira.Realm, lira.Classpath, lira.Only,
        lira.Owner)

      test(m"Every subcommand belongs to a command group"):
        subcommands.count(_.group.absent)
      . assert(_ == 0)

      test(m"Subcommand names are distinct"):
        subcommands.map(_.name).toSet.size
      . assert(_ == subcommands.size)

      test(m"Flag names are distinct"):
        flags.map(_.name).toSet.size
      . assert(_ == flags.size)

      test(m"No flag shares a name with a subcommand"):
        flags.map(_.name).toSet.intersect(subcommands.map(_.name).toSet).size
      . assert(_ == 0)

      test(m"The three groups of design/tool.md §5 are all used"):
        subcommands.flatMap(_.group.option).toSet.size
      . assert(_ == 3)
