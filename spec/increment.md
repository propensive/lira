# Increments — Specification Draft

## Abstract

Successive releases of a module share most of their content. A release's blob stream is
content-addressed and sorted (LIRA §8.2), so a receiver holding release _n_ already holds every
blob of release _n_+1 that did not change, and needs only the rest. But the rest is not
arbitrary either: a classfile, a TASTy file or a declaration file whose source changed by a line
differs from its predecessor by a few bytes, and a compressor that has just seen the predecessor
encodes the successor almost for free. LIRA transfers a whole release as one Brotli stream
(§8.1), which captures neither saving.

This document specifies the **increment**: a file that carries one release relative to a
**base** release the receiver already holds. An increment carries the release's manifest
verbatim — signed, and therefore carrying every identity of the release — followed by a
command stream that reconstructs the release's blob stream from the base's: unchanged records
are kept by run-length, absent ones skipped, new ones stored, and changed ones **updated** by a
Brotli continuation that the receiver decodes in the context of the predecessor blob. The
receiver produces the base's context by an encoder-free construction the RFC fixes completely,
so nothing about the sender's compressor need be known or matched. What an increment yields is
exactly the decompressed blob stream of §8.2, verified as such (L102, L103, L105); an increment
is a transport, and confers nothing on its own.

## 1. Status

This document is a working draft. It is normative for the increment file format, for the
`lira-increment` schema, and for the application of increments; the labels **L151** through
**L156** are defined here and referred to from the base specification (LIRA §8.1, §16–§18).
"LIRA §_n_" refers to [`lira.md`](lira.md). Brotli is RFC 7932 throughout, and section
references of the form "RFC §_n_" are to it. The `lira` tool calls an increment a **delta
file**: `lira delta` writes one and `lira add` applies one ([`tool.md`](../design/tool.md)
§5); the name `delta` is free at the command line, where the atom-level record of LIRA §12.3
is reached through `lira diff`.

## 2. Motivation

Two savings are available when a receiver holds a release's predecessor, and the base
specification delivers neither.

The first is at the level of the blob: content is stored once, addressed by hash, and sorted
(LIRA §8.2), so the blobs two releases share are identifiable by a merge of two sorted lists,
and need never be sent twice. The store API's want/have exchange
([`tool.md`](../design/tool.md) §7) already exploits this, and it is not this document's
subject, except that an increment obtains the same saving by construction: a `keep` command
copies a run of the base's records without transmitting a byte of them.

The second is _within_ the blob. Two versions of a compiled unit are typically near-identical:
a constant pool with one entry more, a method body with one instruction changed, a signature
table with one row added. A general-purpose compressor that has just consumed the old version
finds almost the whole of the new one as backward references into it, and emits the new
version at a small fraction of its standalone compressed size. Brotli, with its
sixteen-megabyte window, is well suited: the old version fits, and the continuation that
encodes the new version is a valid tail of an ordinary stream.

The transfer therefore goes as follows. The sender compresses the old blob into a fresh
stream, flushes, notes how many bytes the stream has produced, compresses the new blob into
the same stream, finishes, and sends only the bytes after the noted count. The receiver, who
holds the old blob, produces the same leading bytes, appends what was sent, decodes the whole,
and discards the leading output — the old blob — to obtain the new one.

The one thing this requires is that the receiver reach the _same decoder state_ the sender
left behind after the old blob. A Brotli decoder's state across a meta-block boundary is its
sliding window, its ring of recent distances, and its two context bytes (RFC §4, §7.1), and a
compressed encoding of the old blob leaves all three at values that depend on the encoder's
choices — which no two implementations, versions or settings need share. The base
specification deliberately makes no encoder normative (LIRA §8.1), and this document does not
change that. Instead it fixes the receiver's side in the only way RFC 7932 fixes anything: the
receiver's encoding of the old blob is **uncompressed**, a form the RFC determines completely
once two parameters are chosen — the window size and the meta-block size — and the increment
carries those two (§4, §6). The sender is then obliged to produce a continuation that decodes
from _that_ state, which the receiver verifies by decoding it and hashing the result. No
encoder is needed on the receiving side at all, and no encoder is named anywhere.

## 3. File Structure

An increment file consists of, in order:

1. the **magic**: the four bytes `B4 B5 BB C4` — the characters `δελτ` under BASE-256,
   distinct from the LIRA magic of LIRA §5;
2. the **manifest**: the manifest of the release the increment carries — the **target** —
   verbatim: a BinTEL document in external-schema mode conforming to the `lira` schema,
   obtained and decoded exactly as LIRA §5.1–§5.2 prescribe, its signatures included;
3. the **header**: a BinTEL document in external-schema mode conforming to the
   `lira-increment` schema (§4), beginning at the byte immediately following the manifest;
4. the **body**: a Brotli stream beginning at the byte immediately following the header and
   extending to the end of the file, whose decompressed form is the command stream (§5).

Because the manifest opens with BinTEL's external-schema magic, the first eight bytes of every
increment file are fixed: `B4 B5 BB C4 B2 C4 B5 BB` — `δελτβτελ` under BASE-256. A file that
does not begin with these eight bytes, whose manifest does not decode as one BinTEL document
conforming to the `lira` schema, whose header does not decode as one BinTEL document
conforming to the `lira-increment` schema, or that ends at or before the end of its header,
is invalid (**L151**). Both documents are self-delimiting on the terms of LIRA §5.1, so no
length and no separator frames them, and the first byte after the header is the body's. The
manifest's schema signature is resolved as LIRA §5.2: an increment whose composed schema the
reader does not hold is unreadable to that reader, not invalid.

An increment is a binary file; producers MUST NOT set the executable permission bit. The
RECOMMENDED file extension is `.increment`; the media type is `application/lira-increment`,
whose registration is anticipated rather than yet granted. A tool that presents a `.lira`
file's manifest (LIRA §5.3) SHOULD present an increment's embedded manifest and its header on
the same terms — the canonical TEL rendering of each.

## 4. The `lira-increment` Schema

```text
tel 1.0

name lira-increment

scalar Hash
  validate base-256-hash

scalar Natural
  validate natural

document
  field base         Hash        # payload.hash of the base release (LIRA §8.4)
  field compression  Identifier  # brotli: the body's envelope and every update's continuation
  field window       Natural     # WBITS of every priming prefix: 10 to 24 (§6)
  field block        Natural     # greatest uncompressed meta-block length in a prefix: 1 to 2^24 (§6)
  field length       Natural     # decompressed length of the body (§7)
```

The schema is a published constant of this document, held by every conforming reader as the
four metadata-blob schemas of LIRA §14 are; a header whose schema signature is not its
signature fails L151. The scalar validators are those of LIRA §14, and `Identifier` is the
type of the `Payload` record's `compression` field there.

`base` names the base release by its implementation identity: the blob-domain hash of its
decompressed blob stream (LIRA §8.4). That stream — its records, in their order — is what the
commands of §5 walk, and a receiver that does not hold it cannot apply the increment (§7).
`compression` names the algorithm of the body and of every `update` continuation; `brotli` is
the only value this document defines. `window` and `block` are the two parameters of priming,
defined in §6. `length` is the decompressed length of the body, a consistency check on the
terms of §7 — not a bound, since the header is unsigned.

## 5. The Command Stream

The decompressed body is the **command stream**: a sequence of commands, each a `uvarint` tag
followed by its operands, where `uvarint` is unsigned LEB128 as in LIRA §8.2. The receiver
applies the commands in order to the base's records, maintaining a **cursor** — the ordinal
of the next base record not yet kept or skipped, initially zero — and emits the target's
records in order:

```text
0  keep    n                                   emit the next n base records; cursor += n
1  skip    n                                   discard the next n base records; cursor += n
2  store   length ++ bytes                     emit a record of exactly these bytes
3  update  ordinal ++ length ++ clength ++ cbytes
                                               emit the record of length bytes obtained by
                                               decoding cbytes primed with base record
                                               ordinal (§6)
```

Ordinals count the base's records from zero, in stream order. `keep` and `skip` are
run-length coded and consume the base; `store` and `update` consume nothing of it. An
`update` may name any ordinal — a record already kept, one skipped, one not yet reached, or
the same record as another `update` — because naming a record as priming context is
independent of whether it survives into the target: a blob that is edited in one place and
retained in another is the ordinary case, not a special one. The priming context is the
record's bytes alone, without its length prefix.

The command stream is **well-formed** iff (**L153**):

- every tag is one of 0–3, every `n` is at least 1, and the cursor never exceeds the base's
  record count;
- the stream ends exactly at a command boundary, with the cursor equal to the base's record
  count — no trailing bytes, and no implicit final `skip`;
- every `ordinal` is less than the base's record count;
- in every `update`, `clength` is at most `2 × length + 64`;
- `base` is not the target's `payload.hash`.

A command stream that is not well-formed renders the increment malformed, on the terms of
LIRA **L139**. The bound on `clength` is a coarse one, chosen so that no continuation can be
worse than a stored encoding by more than a constant: an encoder that cannot do better than
that has a stored fallback, and a receiver need not read further to know something is wrong.

Two consequences of the content-addressed model are worth stating. First, the target's
stream is reproduced in full and in order — unreferenced blobs (LIRA §8.2) included, since
`payload.hash` covers them — so the commands describe the target _set_, not an edit script,
and "deletion" is simply a base record the cursor passes with `skip`. Second, the target's
records are emitted in ascending hash order because the commands are laid out in that order;
the receiver does not sort, and a `store` or `update` whose hash is out of place, or equal to
a kept record's, fails L103 at verification (§7) exactly as a mis-sorted payload would.

## 6. Priming

An `update`'s `cbytes` is not a Brotli stream: it is the **tail** of one, the part that
follows the encoding of the base record. The receiver supplies the **priming prefix**, the
encoding of the base record that the tail follows, and decodes prefix and tail as one stream.

The prefix `P(base, window, block)` is fixed by RFC 7932 given the header's two parameters
(**L154**). It consists of:

1. the stream header, encoding WBITS = `window` by the RFC's variable-length code (RFC
   §9.1): one bit for 16, four bits for 18–24, seven bits for 10–15 and 17;
2. the base record's bytes, divided into consecutive chunks of `block` bytes with a final
   chunk of whatever remains, each chunk as an **uncompressed meta-block** (RFC §9.2):
   ISLAST = 0, MNIBBLES the fewest that hold MLEN − 1, MLEN the chunk's length,
   ISUNCOMPRESSED = 1, zero bits to the next byte boundary, then the chunk's bytes;
   no meta-block at all for an empty base record;
3. one **empty metadata meta-block** (RFC §9.2): ISLAST = 0, MNIBBLES = 0, the reserved
   bit 0, MSKIPBYTES = 0, and zero bits to the next byte boundary — the six bits `0`, `11`,
   `0`, `00` in stream order, which pad to the byte `06`.

The third element is what an encoder's flush emits when its output is not byte-aligned, and
is included unconditionally so that the prefix ends at a byte boundary whatever the base
record's length, an empty record included; the stream header alone ends mid-byte, and an
uncompressed meta-block ends aligned. The prefix is therefore a whole number of bytes, and
`cbytes` begins at a byte boundary, which is what makes the splice a concatenation.

The decoder's state after `P` is then determined entirely by the RFC: its window holds the
base record's last 2^`window` − 16 bytes; its distance ring buffer holds its initial values
(RFC §4), no meta-block of the prefix having coded a distance; and its two context bytes
(RFC §7.1) are the base record's last two, or zero where the record is shorter. The
continuation `cbytes` MUST be such that `P ++ cbytes` is a valid RFC 7932 stream: its
meta-blocks MUST begin at that state, the last MUST have ISLAST = 1, the stream MUST end
exactly at the end of `cbytes` — no byte of `cbytes` unconsumed, none wanting — and its
decoded output beyond the base record MUST be exactly `length` bytes, which are the emitted
record. Backward references may reach into the base record; the static dictionary is
available as always; and large-window extensions to the RFC are excluded, WBITS being at
most 24. A continuation violating any of these renders the increment malformed (**L154**).

Producers SHOULD emit `window` 24 and `block` 2^24, the greatest the RFC allows, since the
prefix is never transmitted and a larger window only widens what a continuation may
reference. A receiver MUST honour the declared values whatever they are; the parameters exist
so that the receiver's construction is stated in the file rather than assumed, and so that a
producer whose encoder is bound to a smaller window can declare it. A receiver MAY establish
the decoder state by any means — attaching the base record to its decoder directly, if its
implementation offers that — provided the state is the one `P` produces; the prefix is the
definition, not a required implementation.

For the sender the obligation is the converse: its encoder, having consumed the base record
and flushed, must be in a state from which the RFC-defined decoder state follows. An encoder
that codes no distance through the ring buffer and uses no context modelling — pneumatic's,
in the `lira` tool's case — satisfies this by construction, since its continuation depends on
the window alone, and the window's contents are the base record however it was encoded. An
encoder that does use either must begin from the same prefix, or reset the corresponding
state, before encoding the new record; how it does so is its own affair, and the receiver's
decode is the test.

## 7. Application and Verification

Application is the receiver's reconstruction of the target's blob stream; verification of
the result is LIRA's, unchanged. A receiver applying an increment (**L152**):

1. checks the eight-byte prefix and decodes the manifest and header (L151);
2. verifies the embedded manifest as LIRA §16 step 0 and step 8 verify a file's — schema,
   conformance, and every signature against trusted keys — **before** decoding a byte of
   the body, so that every bound below is taken from a signed value;
3. requires that it hold the base stream `base` names — the decompressed blob stream whose
   hash it is — and obtains from it the base's records, in order, and their count;
4. decodes the body **streaming**, applying commands as they are read, and bounds the work by
   the target's signed `payload.length`: before decoding any bytes for a `store` or an
   `update`, it checks that the command's `length`, added to the lengths already emitted,
   does not exceed `payload.length` — `clength` needs no bound of its own, being bounded by
   `length` under L153; a command exceeding the bound ends application with the increment
   malformed (L153). The header's `length` is checked for equality with the body's actual decompressed
   length once the stream ends, and is never itself a bound: the header is unsigned, and a
   tampered value can cost the receiver at most the work of `payload.length`;
5. emits the target's records — copied from the base, stored, or decoded by §6 — and
   recomputes each record's blob hash as it is emitted.

The result is a blob stream, and it is verified as one (**L155**): its decompressed length
MUST equal `payload.length` (LIRA **L102**), its records MUST be in ascending hash order and
unique (**L103**), and its hash MUST equal `payload.hash` (**L105**). Verification then
continues from LIRA §16 step 2 exactly as for a downloaded file. An increment that fails at
any point — framing, well-formedness, a bound, a continuation, or the stream checks — is
rejected as a whole; nothing it produced is the release. An increment confers nothing on its
own: every claim it makes is either the signed manifest's or recomputed, and an increment
obtained from an untrusted source is as safe to apply as a `.lira` file from one is to open.

What the receiver holds afterwards is the target's manifest and its decompressed blob stream:
the release, in every sense of LIRA §6. Writing it as a `.lira` file means compressing the
stream with the receiver's own toolchain, which yields a file differing bytewise from the
publisher's and identical to it in every identity (LIRA §8.1, §17) — the publisher's
envelope is not transmitted and cannot be reconstructed.

## 8. Producing Increments

Producing an increment is a merge walk over two sorted record lists, the base's and the
target's: a record in both is kept, one in the base alone is skipped, and one in the target
alone is stored or updated. Runs of kept and skipped records are coalesced. Only the last
choice — store or update, and against which base record — is the producer's, and this
section's guidance on it is informative.

The natural priming context for a new record is its predecessor by name: the record at the
same path in the same section's tree of the base release (LIRA §9.2), the sections
corresponding by realm and integration; failing that, the record at the same path in the
base's root section; failing both — a new path, or a renamed one — `store`. A producer with a
better pairing, from a rename detector, say, is free to use it, since the format constrains
nothing about which ordinal an `update` names. Where a target record has several candidate
predecessors, one is chosen, deterministically. A producer SHOULD `store` where an `update`
would not be smaller: an empty predecessor, a predecessor sharing nothing with the successor,
or a new record already so small that the continuation's framing costs more than it saves.
Metadata blobs benefit from `update` as much as content does: a section's tree changes
whenever an entry does, and the new tree is the old one with a few rows altered.

An increment is a transport artifact and has no identity (**L156**): no hash domain is
defined for it, no signed structure refers to one, and it is designated only by the pair of
payload hashes it connects — the base's, in its header, and the target's, in the manifest it
carries. Producers MUST be self-deterministic over increments as LIRA §8.1 requires of
payloads — the same toolchain, over the same base and target, MUST emit the same increment —
but no cross-toolchain reproduction is claimed, and LIRA §17's determinism guarantee does not
extend to increment files: two producers may pair differently, choose differently between
store and update, and compress differently, and be equally correct.

Increments **chain**: applying one yields the exact canonical stream of its target, which is
a base for the next. A receiver holding release _n_ and offered increments _n_→_n_+1 and
_n_+1→_n_+2 applies them in turn; nothing in the format distinguishes an applied stream from
a downloaded one. Which pairs a publisher or a serving node produces — the lineage
predecessor, the last few releases, the releases a consumer population is known to hold — is
policy, and belongs to the tool and distribution designs
([`tool.md`](../design/tool.md) §7, [`distribution.md`](../design/distribution.md)).

## 9. Security Considerations

- **Decompression bombs**: the body and every continuation are decoded under bounds derived
  from the signed `payload.length` (L152), checked before decoding, with the body read
  streaming; an adversary controlling an increment can waste at most work proportional to the
  release's declared size, which is the bound the base specification already accepts (LIRA
  §8.1).
- **Header tampering**: the header is unsigned. Its `base` misdirected yields a stream that
  fails L105; its `window` or `block` altered yields continuations that fail L154 or a stream
  that fails L105; its `length` altered fails the consistency check of §7 and, by L152,
  bounds nothing. No alteration of the header can produce a stream that verifies as a release
  other than the one whose signed manifest the file carries.
- **Substitution**: the manifest is verbatim and signed, so an increment cannot present a
  target its publisher did not sign, and the reconstructed stream must hash to the manifest's
  `payload.hash`; the substitution protections of LIRA §18 apply unchanged.
- **Partial application**: a receiver MUST NOT treat any part of a reconstruction as the
  release, or as the base for a further increment, until L155 has passed in full. A store
  that ingests records as they are emitted (tool.md §2.4) may do so as content-addressed
  objects — each is hashed before it is written, and an object that verifies is an object —
  but MUST NOT record the release, or write its payload object, before the whole stream has
  verified.
- **Envelope provenance**: a receiver that reconstructs a release from an increment holds no
  copy of the publisher's compressed envelope. Where a distribution record pins that envelope
  by length or by bytes, a locally re-encoded file does not match it and MUST NOT be served
  as it (tool.md §2.3); the release itself is unaffected, its identities being over the
  decompressed stream.

## Appendix A (Informative): Worked Example

A base release with four records and a target with four, shown by their hashes in stream
order — that is, ascending — with their lengths:

```text
base                         target
0  Ab12…  (2,310 bytes)      Ab12…  (2,310 bytes)   unchanged
1  Cd34…  (4,096 bytes)      Ef56…  (4,120 bytes)   Cd34… edited: same path, new content
2  Gh78…  (  512 bytes)      Gh78…  (  512 bytes)   unchanged
3  Kl90…  (1,024 bytes)      Mn12…  (  300 bytes)   a new path
                                                    Kl90… no longer present
```

The producer's merge walk emits, in target order:

```text
keep   1                       Ab12…
update 1  4120  cbytes         Ef56…, primed with base record 1 (Cd34…)
skip   1                       Cd34… is not in the target
keep   1                       Gh78…
store  300  bytes              Mn12…
skip   1                       Kl90… is not in the target
```

The cursor ends at 4, the base's record count; four records are emitted, in ascending order;
their lengths sum to 7,242, which must be the target manifest's `payload.length` less the
four `uvarint` prefixes the records carry in the stream — the stream is what is hashed, so the
receiver hashes prefixes and bytes together, exactly as LIRA §8.4 does.

With the recommended `window` 24 and `block` 2^24, the priming prefix for the `update` is:

```text
bits, in stream order          bytes
1 111                          WBITS = 24 (RFC §9.1)
0  00  4095 (16 bits)  1       ISLAST = 0; MNIBBLES = 4 (code 00); MLEN − 1 = 4095; ISUNCOMPRESSED = 1
(padding: none needed)         4 + 1 + 2 + 16 + 1 = 24 bits: three bytes exactly
…                              the 4,096 bytes of Cd34…
0 11 0 00  (padding 00)        the empty metadata meta-block: the byte 06
```

so `|P| = 3 + 4096 + 1 = 4,100` bytes, and the sender's count of bytes to omit — had it
begun from the same prefix — is 4,100. Any base record of at most 65,536 bytes gives a
three-byte header under these parameters; records up to 2^20 bytes take one nibble more, and
larger ones a further nibble, for a four-byte header either way. An empty base record gives
the two-byte prefix `1 111 0 11 0 00` padded, and a continuation that references nothing.
