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
        (lira.Verify, lira.Harvest, lira.Jar, lira.Assign, lira.Diff, lira.Delta, lira.AtomsCmd,
         lira.Id, lira.Add, lira.Cache, lira.Pin, lira.Unpin, lira.Gc, lira.Fsck, lira.Install,
         lira.Help, lira.Quit)

      val flags = scala.List[Flag](lira.Major, lira.Budget, lira.Blob, lira.Output, lira.Realm,
        lira.Classpath, lira.Only, lira.Owner)

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

    // Deterministic byte patterns with enough structure to match against, and no reliance on any
    // compressor's behaviour beyond RFC 7932 validity.
    def list(data: Data): List[Byte] = data.to[List]

    def pattern(length: Int, seed: Int): Data =
      val bytes = Array.allocate[Byte](length)
      var state = seed
      var i = 0

      while i < length do
        state = state*1103515245 + 12345
        bytes(i) = (if i % 7 == 0 then (state >>> 16) & 0xff else (i*31 + seed) & 0x7f).toByte
        i += 1

      Array.freeze(bytes)

    def edit(data: Data, changes: List[(Int, Byte)]): Data =
      val bytes = Array.allocate[Byte](data.length)
      bytes.place(data)
      changes.each { (index, value) => bytes(index) = value }
      Array.freeze(bytes)

    def splice(data: Data, at: Int, insert: Data): Data =
      val bytes = Array.allocate[Byte](data.length + insert.length)
      bytes.place(data, 0, 0, at)
      bytes.place(insert, 0, at, insert.length)
      bytes.place(data, at, at + insert.length, data.length - at)
      Array.freeze(bytes)

    suite(m"Priming (spec increment.md §6)"):
      val base = pattern(4096, 7)
      val prefix = Priming.prefix(base, Priming.Window, Priming.Block)

      // Appendix A's arithmetic: three header bytes, the base, the metadata meta-block's `06`.
      test(m"A 4,096-byte base under the recommended parameters primes in 3 + 4096 + 1 bytes"):
        prefix.length
      . assert(_ == 4100)

      test(m"The prefix opens with WBITS 24, ISLAST 0, MNIBBLES 4 and the low bit of MLEN − 1"):
        prefix.readable(0) & 0xff
      . assert(_ == 0x8f)

      test(m"The prefix ends with the empty metadata meta-block"):
        prefix.readable(prefix.length - 1) & 0xff
      . assert(_ == 0x06)

      test(m"An empty base primes to two bytes"):
        list(Priming.prefix(Data(), Priming.Window, Priming.Block)).map(_ & 0xff)
      . assert(_ == List(0x6f, 0x00))

      // The prefix is not a stream of its own — it ends with ISLAST 0, waiting for a
      // continuation — so it is closed here with the empty last meta-block (ISLAST 1,
      // ISLASTEMPTY 1: the byte `03`) to check what it decodes to.
      test(m"The prefix, closed by an empty last meta-block, decodes to the base"):
        val closed = splice(prefix, prefix.length, Array[Byte](3.toByte))
        list(closed.decompress[Brotli]) == list(base)
      . assert(_ == true)

      // A successor differing from its predecessor by two edits and an insertion.
      val edited = edit(base, List((100, 42.toByte), (2000, 43.toByte)))
      val next = splice(edited, 1500, pattern(64, 99))
      val continuation = Priming.continuation(base, next, Priming.Window)

      test(m"A continuation decodes against its base to the successor"):
        Priming.decode(base, continuation, Priming.Window, Priming.Block, next.length).let(list(_))
      . assert(_ == list(next))

      test(m"A continuation is far smaller than the successor it carries"):
        continuation.length*8 < next.length
      . assert(_ == true)

      // RFC 7932 cannot tell a wrong base from the right one — the copies land at the same
      // distances — which is why the result is verified by hash (increment.md §7, L155).
      test(m"A continuation against the wrong base decodes to the wrong bytes"):
        Priming.decode(pattern(4096, 8), continuation, Priming.Window, Priming.Block, next.length)
          . let(list(_)) != list(next)
      . assert(_ == true)

    suite(m"Delta files (spec increment.md §3–§5, §7)"):
      val a = pattern(300, 1)
      val b = pattern(2048, 2)
      val c = pattern(700, 3)
      val d = pattern(100, 4)
      val e = pattern(90, 5)
      val edited = edit(b, List((500, 1.toByte), (1500, 2.toByte)))

      val baseStream = BlobStream.write(List(a, b, c, d))
      val targetStream = BlobStream.write(List(a, edited, c, e))
      val base = BlobStream.read(baseStream)
      val target = BlobStream.read(targetStream)

      val ordinalOfB = base.blobs.where { blob => Blob.compare(blob.hash, Lira.Hash(Lira.Hash.Domain.Blob, b)) == 0 }
        . let(_.n0).or(-1)

      val pairing = Map(Lira.Hash.text(Lira.Hash(Lira.Hash.Domain.Blob, edited)) -> ordinalOfB)

      val produced = DeltaFile.produce(base, target, pairing, Priming.Window)
      val body = DeltaFile.encode(produced.commands)
      val header =
        DeltaFile.Header(t"base", t"brotli", Priming.Window, Priming.Block, body.length.toLong)

      test(m"The walk keeps the shared records, skips the base's own, and accounts for the rest"):
        (produced.kept, produced.skipped, produced.stored, produced.updated)
      . assert(_ == (2, 2, 1, 1))

      test(m"Applying the commands reproduces the target stream exactly"):
        list(DeltaFile.apply(header, base, body, targetStream.length.toLong))
      . assert(_ == list(targetStream))

      test(m"A budget below the target's length rejects the reconstruction"):
        safely(DeltaFile.apply(header, base, body, targetStream.length.toLong - 1))
      . assert(_ == Unset)

      test(m"A truncated body is rejected"):
        val truncated = Store.slice(body, 0, body.length - 1)
        safely(DeltaFile.apply(header, base, truncated, targetStream.length.toLong))
      . assert(_ == Unset)

      test(m"The header renders and parses back"):
        val rendered = DeltaFile.Header(t"Ab12", t"brotli", 24, 16777216, 1234L).render
        DeltaFile.Header.parse(rendered)
      . assert(_ == DeltaFile.Header(t"Ab12", t"brotli", 24, 16777216, 1234L))

      val head = utf8Encoder.encoded(t"#!/usr/bin/env lira\nlira 1.0\n\nname example\n##\n")
      val delta = DeltaFile.assemble(head, header, utf8Encoder.encoded(t"body"))

      test(m"A delta file is told from a whole file by its pragma line"):
        (DeltaFile.isDelta(delta), DeltaFile.isDelta(file(manifestText, payloadText)))
      . assert(_ == (true, false))

      test(m"A delta file parses into its head, header and body"):
        val parsed = DeltaFile.parse(delta)
        (list(parsed.head) == list(head), parsed.header, parsed.body.utf8)
      . assert(_ == (true, header, t"body"))
