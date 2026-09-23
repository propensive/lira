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

import scala.caps

import soundness.*

// The priming of an `update` (spec/increment.md §6): the receiver's encoding of the base blob is
// fixed by RFC 7932 itself — a stream header, the base as uncompressed meta-blocks, and the empty
// metadata meta-block a flush emits — so that the decoder state after it is determined by the
// two parameters the delta file carries, `window` and `block`, and by nothing about any encoder.
// The sender's continuation is then a valid tail of that stream: meta-blocks whose backward
// references may reach into the base, ending with ISLAST = 1.
//
// The continuation encoder below is pneumatic's Brotli encoder (Jon Pretty's own, Soundness's
// `pneumatic-brotli`) with one change: the LZ77 window is preloaded with the base before any
// command is emitted, so matches may reach back into it. It belongs in pneumatic as a
// preloaded-window mode (design/tool.md §10); it lives here until pneumatic gains one. Its
// output depends on the window alone — it codes no distance through the ring buffer and uses no
// context modelling — which is exactly what makes its tail valid after the RFC-fixed prefix.

// A little-endian (LSB-first) bit-stream writer, the mirror of a Brotli decoder's bit reader.
private[lira] final class BitWriter extends caps.Mutable:
  private var out: scala.Array[Byte]^ = new scala.Array[Byte](256)
  private var size: Int = 0
  private var accumulator: Long = 0L
  private var bitCount: Int = 0

  private update def ensure(extra: Int): Unit =
    if size + extra > out.length then
      var grown = out.length
      while size + extra > grown do grown <<= 1
      val fresh: scala.Array[Byte]^ = new scala.Array[Byte](grown)
      java.lang.System.arraycopy(out, 0, fresh, 0, size)
      out = fresh

  update def writeBits(value: Int, n: Int): Unit =
    accumulator |= (value.toLong & ((1L << n) - 1)) << bitCount
    bitCount += n

    while bitCount >= 8 do
      ensure(1)
      out(size) = (accumulator & 0xff).toByte
      size += 1
      accumulator >>>= 8
      bitCount -= 8

  update def align(): Unit =
    if bitCount > 0 then
      ensure(1)
      out(size) = (accumulator & 0xff).toByte
      size += 1
      accumulator = 0L
      bitCount = 0

  update def writeBytes(bytes: scala.Array[Byte]^{caps.any.rd}, offset: Int, length: Int): Unit =
    ensure(length)
    java.lang.System.arraycopy(bytes, offset, out, size, length)
    size += length

  def result(): scala.Array[Byte] =
    val array: scala.Array[Byte]^ = new scala.Array[Byte](size)
    java.lang.System.arraycopy(out, 0, array, 0, size)
    array

object Priming:
  // The recommended parameters (increment.md §6): the largest window and meta-block the RFC
  // allows, since the prefix is never transmitted and a wider window only widens what a
  // continuation may reference.
  val Window: Int = 24
  val Block: Int = 1 << 24

  private final val MinMatch = 4
  private final val HashBits = 17
  private final val HashSize = 1 << HashBits
  private final val RingSize = 1 << 18
  private final val MaxChain = 64
  private final val NiceLength = 128

  private def writeWindowBits(writer: BitWriter^, windowBits: Int): Unit =
    if windowBits == 16 then writer.writeBits(0, 1)
    else if windowBits == 17 then
      writer.writeBits(1, 1); writer.writeBits(0, 3); writer.writeBits(0, 3)
    else if windowBits >= 18 then
      writer.writeBits(1, 1); writer.writeBits(windowBits - 17, 3)
    else
      writer.writeBits(1, 1); writer.writeBits(0, 3); writer.writeBits(windowBits - 8, 3)

  private def writeLength(writer: BitWriter^, length: Int): Unit =
    val value = length - 1
    val nibbles = if value < (1 << 16) then 4 else if value < (1 << 20) then 5 else 6
    writer.writeBits(nibbles - 4, 2)
    var i = 0
    while i < nibbles do { writer.writeBits((value >>> (i*4)) & 0xf, 4); i += 1 }

  // The priming prefix `P(base, window, block)` of increment.md §6: never transmitted, built by
  // both sides from the base record alone.
  def prefix(base: Data, window: Int, block: Int): Data =
    val bytes = Array.unsafeJvm(base)
    val writer: BitWriter^ = BitWriter()
    writeWindowBits(writer, window)
    var pos = 0

    while pos < bytes.length do
      val chunk = Math.min(bytes.length - pos, block)
      writer.writeBits(0, 1) // ISLAST = 0
      writeLength(writer, chunk)
      writer.writeBits(1, 1) // ISUNCOMPRESSED = 1
      writer.align()
      writer.writeBytes(bytes, pos, chunk)
      pos += chunk

    // The empty metadata meta-block: ISLAST = 0, MNIBBLES = 0 (`11`), reserved 0, MSKIPBYTES = 0,
    // padded — the byte 0x06, which is what a flush emits when its output is not byte-aligned.
    writer.writeBits(0, 1)
    writer.writeBits(3, 2)
    writer.writeBits(0, 1)
    writer.writeBits(0, 2)
    writer.align()
    Array.unsafeFrozen(writer.result())

  // Decodes a continuation against its base: the prefix and the continuation are one stream,
  // whose output is the base followed by the new record. `Unset` if the stream does not decode,
  // or decodes to the wrong length: the caller reports both as a malformed delta.
  def decode(base: Data, continuation: Data, window: Int, block: Int, length: Int)
  :   Optional[Data] =

    val head = prefix(base, window, block)
    val whole = Array.allocate[Byte](head.length + continuation.length)
    whole.place(head, 0, 0, head.length)
    whole.place(continuation, 0, head.length, continuation.length)
    val stream = Array.freeze(whole)

    val decoded: Optional[Data] =
      try stream.decompress[Brotli] catch case error: Exception => Unset

    decoded.let: output =>
      if output.length != base.length + length then Unset else
        val tail = Array.allocate[Byte](length)
        tail.place(output, base.length, 0, length)
        Array.freeze(tail)

  // --- Huffman construction, ported from the reference C encoder (entropy_encode.c) via pneumatic
  private def reverseBits(nBits: Int, value: Int): Int =
    var retval = 0
    var v = value
    var i = 0
    while i < nBits do { retval = (retval << 1) | (v & 1); v >>= 1; i += 1 }
    retval

  private def convertBitDepthsToSymbols
    ( depth: scala.Array[Byte], len: Int, bits: scala.Array[Int]^ )
  :   Unit =

    val blCount: scala.Array[Int]^ = new scala.Array[Int](16)
    val nextCode: scala.Array[Int]^ = new scala.Array[Int](16)
    var i = 0
    while i < len do { blCount(depth(i) & 0xff) += 1; i += 1 }
    blCount(0) = 0
    var code = 0
    nextCode(0) = 0
    i = 1
    while i < 16 do { code = (code + blCount(i - 1)) << 1; nextCode(i) = code; i += 1 }
    i = 0

    while i < len do
      val d = depth(i) & 0xff
      if d != 0 then { bits(i) = reverseBits(d, nextCode(d)); nextCode(d) += 1 }
      i += 1

  private def setDepth
    ( p0:       Int,
      total:    scala.Array[Int],
      left:     scala.Array[Int],
      right:    scala.Array[Int],
      depth:    scala.Array[Byte]^,
      maxDepth: Int )
  :   Boolean =

    val stack: scala.Array[Int]^ = new scala.Array[Int](16)
    var level = 0
    var p = p0
    stack(0) = -1
    var result = 0

    while result == 0 do
      if left(p) >= 0 then
        level += 1

        if level > maxDepth then result = 2 else { stack(level) = right(p); p = left(p) }
      else
        depth(right(p)) = level.toByte
        while level >= 0 && stack(level) == -1 do level -= 1

        if level < 0 then result = 1 else { p = stack(level); stack(level) = -1 }

    result == 1

  private def sortLeaves(total: scala.Array[Int]^, right: scala.Array[Int]^, n: Int): Unit =
    var i = 1

    while i < n do
      val t = total(i)
      val r = right(i)
      var j = i - 1

      while j >= 0 && (total(j) > t || (total(j) == t && right(j) < r)) do
        total(j + 1) = total(j)
        right(j + 1) = right(j)
        j -= 1

      total(j + 1) = t
      right(j + 1) = r
      i += 1

  private def createHuffmanTree
    ( data: scala.Array[Int], length: Int, treeLimit: Int, depth: scala.Array[Byte]^ )
  :   Unit =

    val total: scala.Array[Int]^ = new scala.Array[Int](2*length + 1)
    val left: scala.Array[Int]^ = new scala.Array[Int](2*length + 1)
    val right: scala.Array[Int]^ = new scala.Array[Int](2*length + 1)
    var i = 0
    while i < length do { depth(i) = 0; i += 1 }

    var countLimit = 1
    var done = false

    while !done do
      var n = 0
      i = length

      while i != 0 do
        i -= 1

        if data(i) != 0 then
          val count = if data(i) > countLimit then data(i) else countLimit
          total(n) = count; left(n) = -1; right(n) = i; n += 1

      if n == 1 then
        depth(right(0)) = 1
        done = true
      else
        sortLeaves(total, right, n)
        var s = 0
        while s < n do { left(s) = -1; s += 1 }

        total(n) = Int.MaxValue; left(n) = -1; right(n) = -1
        total(n + 1) = Int.MaxValue; left(n + 1) = -1; right(n + 1) = -1

        var ii = 0
        var jj = n + 1
        var k = n - 1

        while k != 0 do
          val l = if total(ii) <= total(jj) then ii else jj
          if l == ii then ii += 1 else jj += 1
          val r = if total(ii) <= total(jj) then ii else jj
          if r == ii then ii += 1 else jj += 1
          val jEnd = 2*n - k
          total(jEnd) = total(l) + total(r)
          left(jEnd) = l
          right(jEnd) = r
          total(jEnd + 1) = Int.MaxValue; left(jEnd + 1) = -1; right(jEnd + 1) = -1
          k -= 1

        if setDepth(2*n - 1, total, left, right, depth, treeLimit) then done = true
        else countLimit <<= 1

  private val codeLengthCodeOrder: Array[Int]^{} =
    Array.unsafeFrozen:
      scala.Array(1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15)

  private def writeCodeLengthCodeLength(writer: BitWriter^, v: Int): Unit = v match
    case 0 => writer.writeBits(0, 2)
    case 1 => writer.writeBits(7, 4)
    case 2 => writer.writeBits(3, 3)
    case 3 => writer.writeBits(2, 2)
    case 4 => writer.writeBits(1, 2)
    case _ => writer.writeBits(15, 4)

  private def bitsForCount(count: Int): Int =
    var v = count
    var bits = 0
    while v != 0 do { v >>= 1; bits += 1 }
    bits

  private def storeHuffmanTree
    ( writer: BitWriter^, depth: scala.Array[Byte]^, codes: scala.Array[Int], alphabetSize: Int )
  :   Unit =

    var used = 0
    var lastNonZero = -1
    var onlySymbol = 0
    var i = 0

    while i < alphabetSize do
      if depth(i) != 0 then { used += 1; lastNonZero = i; onlySymbol = i }
      i += 1

    if used <= 1 then
      writer.writeBits(1, 2) // simple code
      writer.writeBits(0, 2) // NSYM - 1 = 0
      writer.writeBits(onlySymbol, bitsForCount(alphabetSize - 1))
      depth(onlySymbol) = 0
    else
      val streamLen = lastNonZero + 1
      val histogram: scala.Array[Int]^ = new scala.Array[Int](18)
      i = 0
      while i < streamLen do { histogram(depth(i) & 0xff) += 1; i += 1 }
      val clcDepth: scala.Array[Byte]^ = new scala.Array[Byte](18)
      createHuffmanTree(histogram, 18, 5, clcDepth)
      val clcCodes: scala.Array[Int]^ = new scala.Array[Int](18)
      convertBitDepthsToSymbols(clcDepth, 18, clcCodes)

      var clcUsed = 0
      var s = 0
      while s < 18 do { if clcDepth(s) != 0 then clcUsed += 1; s += 1 }
      val singleClc = clcUsed == 1

      writer.writeBits(0, 2) // HSKIP = 0
      var space = 32
      i = 0

      while i < 18 && space > 0 do
        val sym = codeLengthCodeOrder.readable(i)
        val v = clcDepth(sym) & 0xff
        writeCodeLengthCodeLength(writer, v)
        if v != 0 then space -= 32 >> v
        i += 1

      i = 0

      while i < streamLen do
        val len = depth(i) & 0xff
        if !singleClc then writer.writeBits(clcCodes(len), clcDepth(len) & 0xff)
        i += 1

  // --- Length/distance prefix codes (RFC 7932 §5), with NPOSTFIX = 0 and NDIRECT = 0 -----------
  private val insertLengthOffset: Array[Int]^{} =
    Array.unsafeFrozen:
      scala.Array(0, 1, 2, 3, 4, 5, 6, 8, 10, 14, 18, 26, 34, 50, 66, 98, 130, 194, 322, 578,
                  1090, 2114, 6210, 22594)

  private val insertLengthNBits: Array[Int]^{} =
    Array.unsafeFrozen:
      scala.Array(0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 12, 14, 24)

  private val copyLengthOffset: Array[Int]^{} =
    Array.unsafeFrozen:
      scala.Array(2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 18, 22, 30, 38, 54, 70, 102, 134, 198, 326,
                  582, 1094, 2118)

  private val copyLengthNBits: Array[Int]^{} =
    Array.unsafeFrozen:
      scala.Array(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 24)

  private def lengthCode(offsets: Array[Int]^{}, length: Int): Int =
    var i = 0
    while i + 1 < offsets.length && offsets.readable(i + 1) <= length do i += 1
    i

  private def distanceCode(distance: Int): Long =
    val x = distance + 3
    val n = 30 - Integer.numberOfLeadingZeros(x)
    val b = if x >= (3 << n) then 1 else 0
    val j = ((n - 1) << 1) + b
    val low = ((2 + b) << n) - 3
    val extra = distance - low
    ((16 + j).toLong << 40) | (n.toLong << 32) | (extra.toLong & 0xffffffffL)

  private val rangeIndex: Array[Int]^{} =
    Array.unsafeFrozen(scala.Array(0, 1, 4, 2, 3, 6, 5, 7, 8))

  private def commandCode(insertCode: Int, copyCode: Int): Int =
    val insGroup = insertCode / 8
    val copGroup = copyCode / 8
    val base = rangeIndex.readable(insGroup*3 + copGroup)
    ((base + 2) << 6) | ((insertCode & 7) << 3) | (copyCode & 7)

  private def isAllZero(histogram: scala.Array[Int]): Boolean =
    var i = 0
    while i < histogram.length do { if histogram(i) != 0 then return false; i += 1 }
    true

  // The continuation for `next` against `base`: one compressed meta-block with ISLAST = 1,
  // no stream header, whose backward references may reach `window` bytes back — into the base.
  def continuation(base: Data, next: Data, window: Int): Data =
    if next.length == 0 then
      val writer: BitWriter^ = BitWriter()
      writer.writeBits(1, 1) // ISLAST = 1
      writer.writeBits(1, 1) // ISLASTEMPTY = 1
      writer.align()
      Array.unsafeFrozen(writer.result())
    else Array.unsafeFrozen(compressed(Array.unsafeJvm(base), Array.unsafeJvm(next), window))

  private def compressed(base: scala.Array[Byte], next: scala.Array[Byte], window: Int)
  :   scala.Array[Byte] =

    val writer: BitWriter^ = BitWriter()
    val start = base.length
    val length = start + next.length
    val input: scala.Array[Byte]^ = new scala.Array[Byte](length)
    java.lang.System.arraycopy(base, 0, input, 0, start)
    java.lang.System.arraycopy(next, 0, input, start, next.length)
    val maxDistance = (1 << window) - 16

    val head: scala.Array[Int]^ = new scala.Array[Int](HashSize)
    var h = 0
    while h < HashSize do { head(h) = -1; h += 1 }

    val ringMask = RingSize - 1
    val chain: scala.Array[Int]^ = new scala.Array[Int](Math.min(length, RingSize))

    var capacity = 1024
    var cmdInsert = new scala.Array[Int](capacity)
    var cmdLitPos = new scala.Array[Int](capacity)
    var cmdCopy = new scala.Array[Int](capacity)
    var cmdDist = new scala.Array[Int](capacity)
    var commands = 0

    def push(insert: Int, litPos: Int, copy: Int, dist: Int): Unit =
      if commands == capacity then
        capacity <<= 1
        scala.caps.unsafe.unsafeAssumeSeparate:
          val ni = new scala.Array[Int](capacity)
          val nl = new scala.Array[Int](capacity)
          val nc = new scala.Array[Int](capacity)
          val nd = new scala.Array[Int](capacity)
          java.lang.System.arraycopy(cmdInsert, 0, ni, 0, commands)
          java.lang.System.arraycopy(cmdLitPos, 0, nl, 0, commands)
          java.lang.System.arraycopy(cmdCopy, 0, nc, 0, commands)
          java.lang.System.arraycopy(cmdDist, 0, nd, 0, commands)
          cmdInsert = ni; cmdLitPos = nl; cmdCopy = nc; cmdDist = nd

      cmdInsert(commands) = insert
      cmdLitPos(commands) = litPos
      cmdCopy(commands) = copy
      cmdDist(commands) = dist
      commands += 1

    def hashAt(p: Int): Int =
      val v = (input(p) & 0xff) | ((input(p + 1) & 0xff) << 8) | ((input(p + 2) & 0xff) << 16) |
        ((input(p + 3) & 0xff) << 24)

      (v*0x1e35a7bd) >>> (32 - HashBits)

    // Preload the window: every position of the base enters the hash chains, and none emits a
    // command. This is the whole of the difference from a fresh encoder.
    var pos = 0

    while pos < start do
      if pos + MinMatch <= length then
        val hv = hashAt(pos)
        chain(pos & ringMask) = head(hv)
        head(hv) = pos

      pos += 1

    var literalStart = start

    while pos < length do
      var matched = false

      if pos + MinMatch <= length then
        val hv = hashAt(pos)
        var candidate = head(hv)
        chain(pos & ringMask) = candidate
        head(hv) = pos

        var bestLen = 0
        var bestDist = 0
        var depth = MaxChain

        while candidate >= 0 && depth > 0 && pos - candidate <= maxDistance do
          if bestLen == 0 ||
            (pos + bestLen < length && input(candidate + bestLen) == input(pos + bestLen))
          then
            var len = 0
            while pos + len < length && input(candidate + len) == input(pos + len) do len += 1

            if len > bestLen then
              bestLen = len
              bestDist = pos - candidate
              if len >= NiceLength then depth = 0

          val next = chain(candidate & ringMask)
          candidate = if next >= candidate then -1 else next
          depth -= 1

        if bestLen >= MinMatch then
          push(pos - literalStart, literalStart, bestLen, bestDist)
          var q = pos + 1
          val end = pos + bestLen

          while q < end do
            if q + MinMatch <= length then
              val qh = hashAt(q)
              chain(q & ringMask) = head(qh)
              head(qh) = q

            q += 1

          pos = end
          literalStart = pos
          matched = true

      if !matched then pos += 1

    if literalStart < length then push(length - literalStart, literalStart, 0, 0)

    val litHist: scala.Array[Int]^ = new scala.Array[Int](256)
    val cmdHist: scala.Array[Int]^ = new scala.Array[Int](704)
    val distHist: scala.Array[Int]^ = new scala.Array[Int](64)
    var c = 0

    while c < commands do
      val insert = cmdInsert(c)
      val litPos = cmdLitPos(c)
      var t = 0
      while t < insert do { litHist(input(litPos + t) & 0xff) += 1; t += 1 }
      val copy = cmdCopy(c)
      val insertCode = lengthCode(insertLengthOffset, insert)
      val copyCode = if copy > 0 then lengthCode(copyLengthOffset, copy) else 0
      cmdHist(commandCode(insertCode, copyCode)) += 1

      if copy > 0 then
        val dc = distanceCode(cmdDist(c))
        distHist((dc >>> 40).toInt) += 1

      c += 1

    if isAllZero(litHist) then litHist(0) = 1
    if isAllZero(distHist) then distHist(0) = 1

    val litDepth: scala.Array[Byte]^ = new scala.Array[Byte](256)
    val litCodes = new scala.Array[Int](256)
    val cmdDepth: scala.Array[Byte]^ = new scala.Array[Byte](704)
    val cmdCodes = new scala.Array[Int](704)
    val distDepth: scala.Array[Byte]^ = new scala.Array[Byte](64)
    val distCodes = new scala.Array[Int](64)
    createHuffmanTree(litHist, 256, 15, litDepth)
    convertBitDepthsToSymbols(litDepth, 256, litCodes)
    createHuffmanTree(cmdHist, 704, 15, cmdDepth)
    convertBitDepthsToSymbols(cmdDepth, 704, cmdCodes)
    createHuffmanTree(distHist, 64, 15, distDepth)
    convertBitDepthsToSymbols(distDepth, 64, distCodes)

    // The meta-block: no stream header, since the prefix carries it.
    writer.writeBits(1, 1) // ISLAST = 1
    writer.writeBits(0, 1) // ISLASTEMPTY = 0
    writeLength(writer, next.length)
    writer.writeBits(0, 1) // NBLTYPESL = 1
    writer.writeBits(0, 1) // NBLTYPESI = 1
    writer.writeBits(0, 1) // NBLTYPESD = 1
    writer.writeBits(0, 2) // NPOSTFIX = 0
    writer.writeBits(0, 4) // NDIRECT = 0
    writer.writeBits(0, 2) // literal context mode 0
    writer.writeBits(0, 1) // literal context map: 1 tree
    writer.writeBits(0, 1) // distance context map: 1 tree
    storeHuffmanTree(writer, litDepth, litCodes, 256)
    storeHuffmanTree(writer, cmdDepth, cmdCodes, 704)
    storeHuffmanTree(writer, distDepth, distCodes, 64)

    c = 0

    while c < commands do
      val insert = cmdInsert(c)
      val litPos = cmdLitPos(c)
      val copy = cmdCopy(c)
      val insertCode = lengthCode(insertLengthOffset, insert)
      val copyCode = if copy > 0 then lengthCode(copyLengthOffset, copy) else 0
      val cmd = commandCode(insertCode, copyCode)
      writer.writeBits(cmdCodes(cmd), cmdDepth(cmd) & 0xff)
      val insertBits = insertLengthNBits.readable(insertCode)
      val copyBits = copyLengthNBits.readable(copyCode)
      writer.writeBits(insert - insertLengthOffset.readable(insertCode), insertBits)

      if copy > 0 then writer.writeBits(copy - copyLengthOffset.readable(copyCode), copyBits)
      else writer.writeBits(0, copyBits)

      var t = 0

      while t < insert do
        val b = input(litPos + t) & 0xff
        writer.writeBits(litCodes(b), litDepth(b) & 0xff)
        t += 1

      if copy > 0 then
        val dc = distanceCode(cmdDist(c))
        val sym = (dc >>> 40).toInt
        val n = ((dc >>> 32) & 0xff).toInt
        val extra = (dc & 0xffffffffL).toInt
        writer.writeBits(distCodes(sym), distDepth(sym) & 0xff)
        writer.writeBits(extra, n)

      c += 1

    writer.align()
    writer.result()
