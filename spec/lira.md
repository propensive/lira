# LIRA Specification Draft

## Abstract

LIRA (Library IR Archive) is a language-agnostic artifact format for distributing compiled
libraries. A single `.lira` file carries every compiled representation of one library release —
for example, JVM classfiles, TASTy, Scala.js IR and Scala Native IR — deduplicated within one
container, together with a human-readable manifest, machine-verifiable API-derived version
metadata, and quantum-safe signatures.

LIRA defines:

- a **container format**: a TEL manifest, a document separator, and a Brotli-compressed
  content-addressed payload;
- a **sectioning model** by which platform-specific views of a library are stored as overlays on a
  shared root, and from which conventional per-platform artifacts (such as classpath entries) can
  be reconstructed;
- a **compatibility algebra** in which a library's public API is a set of hashed **atoms**, API
  evolution is expressed as set relations, and version-compatibility claims are verifiable by
  recomputation rather than trusted by convention;
- a **discipline mechanism** by which the atomization of content is delegated to pluggable,
  language-specific canonicalizers, while everything above the atom level remains language-blind;
- **ecosystem profiles**, by which an ecosystem adds the checks its own linkage model requires —
  bytecode-level invariants, toolchain coherence — without any of that entering the core;
- the **buildpath**: a composition of LIRA files whose coherence — including diamond-dependency
  resolution — is decidable from manifests alone, without reading any payload.

The primary motivating ecosystem is Scala (JVM, Scala.js, Scala Native), but no normative part of
this specification is specific to Scala. Language-specific material appears only in informative
appendices.

## 1. Status

This document is a working draft. Numbered requirements and the schema in §14 are expected to
change. The Scala discipline is specified normatively in the companion document
[`tasty.md`](tasty.md); the JVM ecosystem profile (Appendix D) awaits one.

## 2. Conformance Language

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD
NOT**, **RECOMMENDED**, **MAY**, and **OPTIONAL** in this document are to be interpreted as
described in RFC 2119 and RFC 8174 when, and only when, they appear in all capitals.

## 3. Normative Dependencies

- **TEL** ([tel.md](https://github.com/propensive/tel/blob/main/spec/tel.md)) — the manifest is a TEL document; metadata blobs are TEL documents.
- **BinTEL** ([bintel.md](https://github.com/propensive/tel/blob/main/spec/bintel.md)) — canonical binary encoding, used as the signing domain.
- **BASE-256** ([base256.md](https://github.com/propensive/tel/blob/main/spec/base256.md)) — textual encoding of hashes and signatures.
- **BLAKE3** — the sole hash function of this specification, used with 256-bit output.
- **Brotli** (RFC 7932) — payload compression.
- **ML-DSA** (FIPS 204) — the default signature algorithm.

## 4. Terminology

- **Module**: a named library. A module has one API lineage and many releases.
- **Release**: one published `.lira` file for a module.
- **Universe**: a library-composition world identifying the kind of representation a section
  holds (e.g. `jvm`, `sjsir`, `nir`). The universe vocabulary is open (§9.4).
- **Section**: one compiled view of the release, keyed by universe and integration, stored as a
  tree of blobs.
- **Integration**: one alternative dependency vector a release was built against (§9.5).
- **Assignment**: a choice of one integration per release on a buildpath, under which that
  buildpath's validity is decided (§13.3).
- **Blob**: a byte string in the payload, identified by its hash (§7).
- **Atom**: the unit of API compatibility: a hash over the canonical encoding of one indivisible
  fragment of a module's public interface (§10).
- **Discipline**: a named, versioned canonicalization procedure that converts content into atoms
  (§11).
- **Guarantee level**: what a compatibility claim certifies — linkage, recompilation, or
  behavior (§11.5).
- **Profile**: a named, versioned set of predicates an ecosystem imposes over releases and
  buildpaths in addition to those of this specification (§11.6).
- **Snapshot**: the hash identifying a release's complete API — the hash of its sorted atom set
  (§12.1).
- **Lineage**: the ordered list of snapshots of a module's releases within one major series
  (§12.2).
- **Buildpath**: a set of `.lira` files intended to be used together (§13).

## 5. File Structure

A `.lira` file consists of, in order:

1. the **interpreter directive** line (§5.1), whose content is fixed;
2. a **manifest**: a TEL document conforming to the `lira` schema (§14), encoded per the TEL
   specification (UTF-8, LF line endings REQUIRED for generated files), of which the interpreter
   directive is the first line (TEL §7);
3. a **document separator** line (`##`, per §5.2);
4. the **payload**: the Brotli-compressed blob stream (§8), extending to the end of the file.

A reader MUST obtain the manifest by TEL single-document parsing (TEL §6.1), which terminates at
the separator; the remainder of the file is the payload and is not TEL. A `.lira` file whose
prefix is not a valid TEL document conforming to the `lira` schema is invalid (**L101**).

The manifest is intentionally human-readable: opening a `.lira` file in a text editor shows the
complete metadata of the release. Tools that modify manifests MUST follow TEL's
formatting-preservation rules.

The RECOMMENDED file extension is `.lira`.

### 5.1 Interpreter Directive

Every `.lira` file MUST begin with an interpreter directive line (TEL §7) whose bytes are
exactly:

```text
#!/usr/bin/env lira
```

A file whose first line is absent, or differs in any byte from this string, is invalid
(**L115**). The directive is part of the TEL presentation model but outside the semantic model:
it participates in no hash and no signature, and needs neither — its byte-exactness is a
validity condition checked before anything else (§16), so no conforming file can carry any other
directive.

The directive makes every `.lira` file directly executable on POSIX systems: executing it
invokes the PATH-resolved `lira` tool with the file's path as its argument. Users can therefore
_expect_ to invoke a lira file. The `lira` tool's behavior is out of scope for this
specification, except that its default action when invoked on a `.lira` file MUST at minimum
present the manifest, and MAY additionally verify or analyse the file. Where `lira` is not
installed, execution fails with the operating system's ordinary command-not-found error; the
file remains fully readable as data.

Producers MUST set the executable permission bit on emitted `.lira` files where the filesystem
supports one. Transports that do not preserve permissions (e.g. HTTP) lose the bit, not the
directive; installers SHOULD restore it.

### 5.2 Sigil

The `lira` schema declares no sigil, and a lira manifest MUST NOT specify a sigil in its pragma
(**L116**): the resolved sigil is always the TEL default `#`, and the document separator is
therefore always `##`. This fixes the byte layout of the file for non-TEL tooling — anything
that can find the first line matching `##` exactly can split manifest from payload.

## 6. Identity

A release has two identities, serving different purposes:

- **API identity**: the current snapshot hash — the last entry of `lineage` (§12). Two releases
  with equal snapshots present byte-for-byte identical public APIs. Dependency requirements
  (§13.2) refer to API identity.
- **Implementation identity**: `payload.hash` — the hash of the decompressed payload (§8.4). This
  identifies the exact bits of the release and is the correct key for lockfiles, caches,
  reproducibility claims, and attestation.

A **patch** relationship holds between two releases iff their API identities are equal and their
implementation identities differ (§12.3).

## 7. Hashing

All hashes in this specification are 256-bit BLAKE3. Wherever a hash appears in TEL text (the
manifest or a metadata blob), it is BASE-256 encoded (32 characters). Wherever a hash appears in
a binary context (snapshot computation, signing input), it is the raw 32 bytes.

### 7.1 Domain Separation

Every hash is computed over a **domain-separated** input:

```text
hash(domain, content) = BLAKE3-256( utf8(domain) ++ 0x00 ++ content )
```

The domain strings of this specification are:

| Domain                     | Content hashed                                           |
| -------------------------- | -------------------------------------------------------- |
| `lira/1:blob`              | the bytes of a blob (§8)                                 |
| `lira/1:atom:<discipline>` | the canonical encoding of one atom (§10)                 |
| `lira/1:snapshot`          | the concatenated sorted atom hashes of a release (§12.1) |
| `lira/1:manifest`          | the canonical manifest encoding for signing (§15.2)      |
| `lira/1:key`               | the encoded public key, for fingerprints (§15.3)         |
| `lira/1:derivative`        | the bytes of a canonical derivative artifact (§13.6)     |

`<discipline>` is the full discipline identifier including its version (§11.1), e.g.
`tasty/1`. Because the discipline identifier participates in the domain, atoms produced by
different disciplines — or by different versions of one discipline — can never collide or alias.

The `lira/1` prefix is the **format epoch**. Any future revision of this specification that
changes the meaning of any hashed encoding MUST change the epoch, invalidating no existing hash
but guaranteeing that old and new hashes never mix silently.

## 8. Payload

### 8.1 Compression Envelope

The payload is a single Brotli stream. The compressed bytes participate in **no identity**:
`payload.hash` (§8.4), the snapshot (§12.1) and every signature (§15.2) cover the
*decompressed* blob stream, directly or transitively, so two files differing only in
compressor output are the same release. A producer MUST be **self-deterministic** — the same
toolchain over the same blob stream MUST emit the same compressed bytes, with the toolchain
recorded in the manifest (§14) — but no particular encoder's output is normative across
producers: cross-implementation bit-reproduction of a `.lira` file requires the same producer
toolchain (§17). Readers MUST enforce `payload.length` (the declared decompressed size, §14)
as a hard limit during decompression and MUST reject a payload whose decompressed length is
not exactly the declared value (**L102**) — the upper bound is what bounds
decompression-bomb exposure.

### 8.2 Blob Stream

The decompressed payload is the **blob stream**: a sequence of records, each of the form

```text
record = uvarint(length) ++ bytes
```

where `uvarint` is unsigned LEB128. Each record's bytes constitute one blob. The blob's identity
is `hash("lira/1:blob", bytes)`. Records MUST be sorted in ascending bytewise order of their
blob hashes, and no two records may have equal hashes (**L103**). Blob hashes are not stored in
the stream: a reader recomputes them while scanning, and this recomputation is the integrity
check. A blob referenced anywhere in the manifest or in a metadata blob that is absent from the
stream renders the file invalid (**L104**); unreferenced blobs are permitted but producers
SHOULD NOT emit them.

Content occurring in multiple sections is therefore stored exactly once, addressed by hash.

### 8.3 Metadata Blobs

Certain blobs are **metadata blobs**: TEL documents (parsed in single-document mode) conforming
to small schemas defined alongside the `lira` schema. This specification defines four:

- **Tree** (§9.2) — an entry table mapping paths to blobs for one section.
- **Atoms** (§10.4) — the atom listing of the release for one discipline.
- **Uses** (§13.4) — a used-atom set with respect to one dependency.
- **Delta** (§12.3) — the atom-level change record for one lineage step.

Metadata blobs are ordinary blobs: content-addressed, deduplicated, and hashed under
`lira/1:blob`. Being TEL, they remain human-inspectable after decompression.

### 8.4 Payload Hash

`payload.hash` is `hash("lira/1:blob", decompressed-payload)` — the blob-domain hash of the
entire decompressed blob stream. A reader MUST verify it after decompression (**L105**).

## 9. Sections

### 9.1 Model

A section is one compiled view of the release: a mapping from paths to blobs. The **root
section** is the *first* `section` record of the manifest; the root is per-file, not fixed by
this specification to any universe. For the motivating ecosystem the `jvm` section is
conventionally first, holding the representation that is also valid as a conventional artifact
of its ecosystem; a TypeScript release's root would be its `js` section.

Where a release offers several integrations (§9.5) the sections form a matrix, and the root is
still one section of it: every other section, of whatever universe or integration, is an overlay
on that one (§9.3). Producers SHOULD make the root the section of the most widely applicable
integration, since overlays are minimal with respect to it.

### 9.2 Trees

Each section's `tree` field references a **Tree metadata blob**: a TEL document whose rows map
paths to blob hashes:

```tel
tel 1.0 <lira-tree schema signature>

# path                        # blob
entry gossamer/Text.class       Ab12…
entry gossamer/Text.tasty       Cd34…
```

Paths MUST be relative, `/`-separated, contain no empty, `.` or `..` segments, and be unique
within a tree; rows MUST be sorted in ascending bytewise UTF-8 order of path (**L106**). Readers
MUST reject trees violating these rules — the path rules exclude directory-traversal attacks by
construction.

### 9.3 Overlay Semantics

A non-root section is an **overlay**: its materialized form is computed from the root section as

```text
materialize(overlay) = (root − overlay.delete) ⊕ overlay.tree
```

where `delete` is the section's list of removed root paths and `⊕` replaces or adds entries by
path. An overlay's tree therefore contains only content that is absent from, or differs from,
the root — platform-specific IR and divergent files. Content identical to the root is carried
once, by the root. A `delete` path not present in the root; an overlay entry whose path and
blob both equal a root entry; or a path appearing in both `delete` and the overlay's tree (a
replacement spelled redundantly) — each is invalid (**L107**): overlays are minimal by
construction, which makes divergence between platforms _visible_ in the manifest rather than
buried in the payload.

### 9.4 Universes

The base schema (§14) defines universe variants `jvm`, `sjsir`, and `nir` — the three
library-composition worlds of the motivating ecosystem. (The names `js`, `klib`, `component`
and their kin are reserved for the universes proper of other ecosystems, arriving as schema
layers; a universe names the world in which independently-published libraries compose, not a
language's view of a target.) The vocabulary is open: new universes are introduced by TEL
schema layers, which may append variants to a select but never remove them. A consumer knowing
only the base schema can still parse a layered manifest (TEL §8.2); it MUST treat sections of
unknown universes as opaque and MUST NOT attempt to materialize them.

### 9.5 Integrations

A release MAY have been built against more than one dependency vector: against two majors of one
dependency, for consumers who cannot move together, or against alternative dependencies
altogether, where a consumer chooses a backend. Each such alternative is an **integration**,
declared by an `integration` record (§14) with an identifier unique within the release.

Sections are keyed by universe **and** integration: a section names the integration it realizes,
and no two sections may share a (universe, integration) pair (**L131**). A release therefore
carries a matrix of sections, one per universe per integration, though it need not be full — a
universe may be offered under only some integrations. Every declared integration MUST have at
least one section (**L133**); an integration realized by nothing is a dependency vector no
content was ever built against.

Dependency records are scoped to integrations exactly as they are scoped to universes (§13.2), so
dependencies common to every integration are declared once and unscoped. A release declaring no
integrations has exactly one, implicitly, and every section and dependency belongs to it; such a
manifest is identical to one written before this mechanism existed.

Integrations do not weaken the API guarantee. Every cell of the matrix presents the same
interface (§9.6), so a release still has exactly one API identity and integrations are invisible
to consumers' compatibility reasoning: which integration a buildpath selects (§13.3) changes what
else must be present, never what the module offers.

Producers SHOULD prefer proving that a single compilation spans multiple dependency majors
(§13.4) over emitting integrations. Spanning is a proof about used-sets that costs nothing at
consumption time; an integration is a second compilation that must be built, stored, verified and
chosen between. Integrations are the fallback for genuine incompatibility, not the first answer
to a version difference.

### 9.6 Cross-Section API Invariant

For each discipline, atomizing the **claimed** content (§11.2) of every section MUST yield an
identical atom set (**L108**): a release presents one API on every universe it supports and under
every integration it offers. Implementations may differ per universe and per integration;
interfaces may not. Producers MUST verify this at assembly time by atomizing each section's
materialized tree independently and comparing; the
`api` records (§14) list this section-invariant atomization, computed from the root section's
materialized tree. (A library whose API genuinely differs by platform, or by which dependencies
it was built against, is two modules.)

Holding the invariant across integrations is what keeps them cheap: because every integration
presents one interface, the snapshot (§12.1), the lineage, dependency satisfaction (§13.2) and
diamond resolution all remain single-valued and need no notion of integrations at all. The
constraint bites on replaceable atoms, and publishers should know where: a public `inline` or
macro body that splices integration-differing content has an integration-differing value hash,
fails this invariant, and forces the module to be published as two. Rigid atoms are usually
unaffected, since a signature naming a dependency's type names it identically whichever release
of that dependency was on the compile classpath.

The invariant is scoped per discipline to the content that discipline claims. Content that a
discipline claims **atomless** (§11.2) — derived binaries such as classfiles, whose interface
the discipline already carries through other files, or scanned resource directories (§11.4),
whose contents are deliberately non-contractual — contributes nothing to any universe's
atom set, which is what permits universes to diverge in implementation without violating the
invariant. Content claimed by no discipline falls to `opaque/1` and must therefore be
byte-identical across the universes that carry it.

The invariant is also scoped to the discipline's **domain** (§11.2) — the universes it atomizes
at all. A discipline may be **universal**, atomizing a representation carried in every universe
the release supports (`tasty/1` is the motivating case, and enforcing L108 across its whole
domain is the point of it), or **universe-specific**, atomizing a representation that exists in
one universe only. A bytecode-level discipline over classfiles has no counterpart in `sjsir` or
`nir`; for it the invariant is vacuous, not violated. Claiming nothing and claiming atomless are
distinct: the latter is a discipline's decision about content it covers, the former means the
universe is outside its domain entirely. Without this scoping a universe-specific discipline
could never be declared at all, since it would atomize to the empty set everywhere outside its
domain and no release could satisfy an unqualified L108.

A release MUST NOT declare an `api` record whose discipline's domain is disjoint from the
universes it carries sections for (**L127**): an atomization of nothing is not a claim about
anything, and admitting one would let a release appear to be checked under a discipline that
never examined it.

## 10. Atoms

### 10.1 Definition

An atom is the unit of API compatibility: a pair of

- a **key** — a stable identifier for _what_ is declared (for example, a fully-qualified name
  plus a disambiguator), and
- a **value hash** — `hash("lira/1:atom:<discipline>", canonical-encoding)`, where the canonical
  encoding is a deterministic, discipline-defined serialization of _everything about the
  declaration on which consumers can depend_.

Two releases share an atom iff they agree on that fragment of API, byte for byte, under the
discipline's canonicalization.

### 10.2 Atom Classes

Every atom belongs to one of two classes:

- **rigid** — describes interface shape on which compiled consumers depend for _linkage_. Within
  a major series, the rigid atom set is monotonic: rigid atoms may be added, never removed or
  changed (§12).
- **replaceable** — describes content that consumers _copy_ at their own compile time (the
  canonical example is an inline-expanded function body). A replaceable atom may be **replaced**
  (same key, new value) within a minor release, and removed only when its introducing
  declaration is removed (a major event). Replacing it never breaks linkage — the discipline is
  required to guarantee this (§11.2) — but leaves already-compiled consumers _behaviorally
  stale_ until recompiled; the delta record (§12.3) makes staleness computable.

### 10.3 The Folding Principle

Disciplines MUST atomize such that the compatibility rules of their language are _encoded in
the decomposition itself_: any declaration whose **addition** is safe for consumers becomes a
standalone atom, and any fragment whose addition would break consumers is **folded into the
value of its enclosing atom**, so that adding it changes the enclosing atom and therefore
registers as a removal-plus-addition. Under this principle, "compatible extension" is exactly
the subset relation on atom sets, and no rule engine ever inspects a diff: the entire
compatibility check, for every language, is set arithmetic (§12).

### 10.4 Atom Listings

Each `api` record in the manifest (§14) references an **Atoms metadata blob**: a TEL document
listing, for one discipline, every atom of the release — sorted by ascending value hash — with
its class, its value hash, and its key in human-readable form (for diagnostics; the key text
does not participate in any hash):

```text
tel 1.0 <lira-atoms schema signature>

discipline tasty/1

# class        # hash    # key
atom rigid       Ef56…     gossamer.Text.length():Int
atom replaceable Gh78…     gossamer.Text.trim():gossamer.Text [inline]
```

## 11. Disciplines

### 11.1 Identification

A discipline is identified as `<name>/<version>`, where `<name>` is a kebab-case identifier and
`<version>` is a positive integer. Any change to a discipline's canonicalization — however small
— MUST increment its version, since atom hashes are domain-separated by the full identifier and
a silent change would fracture hash stability.

Names SHOULD identify the interface carrier a discipline canonicalizes rather than the language
that produces it — `tasty`, `dts`, `wit` — since one carrier may be shared by several languages
and one language may present several carriers.

### 11.2 Requirements

A discipline defines, deterministically:

1. **Domain**: the set of universes (§9.4) whose content it atomizes. The domain is a property of
   the discipline, fixed when the discipline is specified, not a property of any release. A
   discipline whose domain holds more than one universe is bound by the cross-section invariant
   (§9.6) across the whole domain; one whose domain is a single universe is not bound by it at
   all. Domain is distinct from claiming: a discipline claims nothing in a universe outside its
   domain, and claiming nothing there is not the same as claiming content atomless.
2. **Claiming**: which content items of a materialized tree the discipline atomizes. A
   discipline MAY claim content **atomless** — covered by the discipline but contributing no
   atoms — for content that carries no independent contract: derived representations whose
   interface it already carries through other claimed content (a Scala discipline claims
   `.class`, `.sjsir` and `.nir` files atomless, since the TASTy it atomizes is their
   interface), or content deliberately outside the API surface (scanned resource directories,
   §11.4). Content claimed by no discipline falls to `opaque/1` (§11.3).
3. **Atomization**: claimed content → a set of atoms, each with key, class, value hash, obeying
   the folding principle (§10.3). Atomization MUST be a pure function of the content's semantic
   model: independent of file ordering, compilation timestamps, tool version strings, fresh-name
   generation, and any other artifact of a particular compilation run. Producers MUST be able to
   reproduce identical atom sets from identical sources (§17).
4. **Keying**: whether atom keys denote **declarations** — each member atomized once, under the
   type that declares it — or **memberships** — each member atomized under every type that
   presents it, after inheritance and linearization. The choice is constrained by the guarantee
   levels the discipline certifies (§11.5): declaration keying is sound exactly where every
   reference a certified consumer can make resolves through the declaring type. It holds for a
   discipline certifying recompilation over a metadata format whose references name the declaring
   owner — `tasty/1` is such a case, and keys by declaration ([`tasty.md`](tasty.md) §6). It fails
   for a discipline certifying linkage in a universe whose call sites name the receiver, since a
   type's linkage surface then includes members it does not declare. Disciplines MUST state their
   choice, because it determines whether a change to a supertype in another module registers in
   this module's own atom set or only in its used-set (§13.4).
5. **Replaceability soundness**: for every replaceable atom, everything its content refers to
   that consumers will need at _their_ compile time must itself be rigid-atomized, so that
   replacing the atom within a lineage can never produce a linkage failure.
6. **References**: for each replaceable atom, the atoms (own-module or cross-module) that its
   content depends on — the input to used-set closure (§13.4). References are emitted
   **symbolically, by atom key**: a cross-module value hash is not computable from one module's
   content alone, so disciplines name what they reference, and assembly-time tooling resolves
   names to value hashes against the dependencies' Atoms listings (§10.4) by exact key match.
7. **Certified guarantees**: which of the guarantee levels of §11.5 its rigid atoms certify, for
   each universe in its domain. A discipline MUST NOT claim a level it does not enforce. Where an
   ecosystem's linkage model imposes obligations the discipline's canonical encoding does not
   cover, those obligations belong to an ecosystem profile (§11.6) and MUST NOT be claimed here.

### 11.3 Registered Disciplines

This specification registers three disciplines:

- **`opaque/1`** (normative): the entire content item is a single **rigid** atom whose key is
  its path and whose canonical encoding is its bytes. Any change is therefore a removal plus an
  addition — a major event. `opaque/1` is the REQUIRED default for content no other discipline
  claims: nothing in a LIRA file is ever outside the compatibility algebra; unknown content is
  merely maximally conservative. Its domain is every universe; its keying is by declaration
  (paths); it certifies linkage and recompilation trivially, since it admits no change at all
  merely maximally conservative. Its domain is every universe; its keying is by declaration
  (paths); it certifies linkage and recompilation trivially, since it admits no change at all
  below the major grade.
- **`resource/1`** (normative; §11.4): resources declared in the manifest — presence-guaranteed
  exports, content-tracked resources, and scanned directories claimed atomless. Its domain is
  every universe; it certifies presence, which is the recompilation level for content addressed
  by name.
- **`tasty/1`** (informative here; normative specification in [`tasty.md`](tasty.md)): the Scala
  discipline sketched in Appendix A. Its domain is every universe whose section carries TASTy —
  `jvm`, `sjsir` and `nir` — and the cross-section invariant over that domain is what makes "one
  API on every platform" checkable. Its keying is by declaration ([`tasty.md`](tasty.md) §6). It
  certifies **recompilation** and TASTy-level linkage on its whole domain; the classfile-level
  linkage obligations of the `jvm` universe are not its and belong to the JVM ecosystem profile
  (Appendix D).

Anticipated future disciplines include declaration-surface disciplines for TypeScript (`dts`),
Java classfile signatures, and Kotlin metadata. Foreign content — JavaScript modules resolved at
link time, C sources compiled by a downstream linker, and so on — is admissible in any section
today under `opaque/1`.

### 11.4 The `resource/1` Discipline

Code does not only link against declarations; it also loads **resources** — non-code content
addressed by classpath-style name. A resource's name may be part of a module's contract even
though its bytes are not. `resource/1` expresses this inside the atom algebra, parameterized by
the manifest's `resource` records (§14) — an authorial claim, like `owns`. It is the one
registered discipline whose claiming takes input beyond the tree itself, which is unproblematic
because atomization runs only where the manifest is in hand (§16, step 4).

Each `resource` record declares one path in one of three modes:

- **`export`** — the named tree item is claimed and yields one **rigid** atom whose key is the
  path and whose canonical encoding is the path's UTF-8 bytes. The value hash is therefore a
  function of the name alone: the atom asserts *presence*, not content. Within a lineage,
  adding an export is a minor event and removing one is major (§12.3); editing the content is
  invisible to the algebra — a patch — because resource content is behavior, and no discipline
  certifies behavior (§18). Because the atom is content-independent, the cross-section
  invariant (**L108**) permits an exported resource's bytes to differ per universe while
  automatically requiring the *path* to be present in every universe: a universe lacking it
  atomizes to a smaller set and fails L108.
- **`track`** — as `export`, but the item yields one **replaceable** atom whose canonical
  encoding is the item's bytes, with an empty reference list (resources create no linkage, so
  replaceability soundness is trivial). Tracking is for resources consumers read at *their*
  compile time — a schema a macro bakes into generated code, say — where a content change is
  exactly replaceable churn: a minor event that marks consumers whose used-sets contain the
  atom as stale (§13.4). L108 consequently requires tracked content to be byte-identical
  across universes.
- **`scan`** — every tree item whose path has the declared path plus `/` as a prefix is claimed
  **atomless**. Scanned directories hold content that consumers *enumerate* rather than name —
  plugin registrations, discovered templates — so no individual name is contractual: additions,
  removals and edits under a scanned directory are patch-grade, and content may diverge freely
  per universe (§9.6). A scanned directory may be empty, in any or all universes.

Declarations MUST be well-formed (**L124**): no path may be declared twice, and an `export` or
`track` path MUST NOT lie under a declared `scan` directory, so the partition of §11.2 has a
single claimant by construction. In the claiming order, `resource/1` follows every language
discipline and precedes the `opaque/1` fallback: an item under a `scan` directory that a
language discipline claims goes to that discipline, and the remainder are atomless. An
`export` or `track` declaration, by contrast, MUST be effective (**L125**): a declared path
that another discipline claims, or that resolves to no item in any universe's materialized
tree, is an assembly-time error — a presence guarantee over nothing, or over content whose
contract another discipline already carries, is never what the author meant.

Resource atoms are ordinary atoms: they appear in an Atoms blob under `resource/1`, enter the
snapshot (§12.1), and may appear in consumers' used-sets — so "this resource is available on
the buildpath" is checkable, and spans majors, exactly like a symbol reference (§13.4).
### 11.5 Guarantee Levels

A compatibility claim is worthless without saying what it certifies. Three levels are
distinguished, and every discipline states which of them its rigid atoms carry (§11.2):

- **Linkage**: already-compiled consumers continue to resolve and load against the new release,
  with no recompilation. Meaningful where linking is late and by name — JVM classloading, native
  symbol resolution.
- **Recompilation**: consumers' sources still compile against the new release, and any content
  copied into them at compile time still re-expands. Meaningful in every ecosystem, and the only
  meaningful level where there is no late linking to protect.
- **Behavior**: that unchanged interfaces compute unchanged results. No hash scheme certifies
  this and this specification does not attempt to (§18).

The levels are independent, and in both directions. A change may preserve linkage while breaking
recompilation: tightening a type bound, changing an implicit's specificity, or altering a type
alias can leave every compiled descriptor identical and still fail every consumer's next
compile. A change may preserve recompilation while breaking linkage: adding a member can force
new bridges or forwarders into classfiles that consumers already hold copies of the old shape
of. Neither direction implies the other, so a format that certifies only one level and calls it
"compatible" is making an equivocation, not a claim.

The base algebra of §10 and §12 is a **recompilation-level** algebra: rigid atoms describe the
interface a consumer compiles against. A discipline certifies linkage additionally only where
its canonical encoding demonstrably covers the universe's whole linkage surface — a strong claim
that most disciplines over source-level metadata cannot make, because the linkage surface is a
property of a _lower_ representation than the one they atomize. What such a discipline does not
cover is the business of a profile.

### 11.6 Ecosystem Profiles

An **ecosystem profile** is a named, versioned set of predicates that an ecosystem imposes over
releases and buildpaths in addition to those of this specification. It is identified as
`<name>/<version>` on the same terms as a discipline (§11.1), and a profile MUST increment its
version on any change to a predicate. A profile may:

1. impose predicates over `toolchain` records — for example, mutual readability of a metadata
   format version across a buildpath (§13.3);
2. impose structural invariants over a universe's content that are not expressible as atoms,
   checked by tooling that reads the representation directly — the bytecode-level checks a JVM
   ecosystem needs are the motivating case;
3. certify a guarantee level (§11.5) that the release's disciplines do not.

Profiles MUST NOT weaken any requirement of this specification (**L129**). They add predicates
and they add guarantees; they never subtract. A file rejected by the core is rejected under every
profile, and a grade forbidden by §12.3 is forbidden under every profile.

A release declares the profiles it claims to satisfy in its `profile` records (§14). The claim is
verifiable, not decorative: a release that declares a profile whose predicates it violates is
invalid (**L128**), and a registry MUST check declared profiles before accepting a release
(§16), exactly as it checks disciplines. A release declaring no profile makes no
ecosystem-specific claim and is graded by the core algebra alone.

**Why profile predicates are not simply more atoms.** An ecosystem could instead define a
universe-specific discipline — a `classfile/1` whose domain is `{jvm}` — and let bytecode surface
enter the atom set directly. This specification permits that and, for an ecosystem whose primary
contract genuinely _is_ linkage, it is the better choice. But it is the wrong default, because
atoms feed the snapshot (§12.1), and the snapshot is API identity: fusing the two levels into one
identity means that a release whose source-level interface is unchanged but whose bridge methods
moved acquires a different API identity, breaking dependency satisfaction (§13.2) for every
consumer, including those who only ever recompile. Keeping linkage predicates in a profile keeps
the snapshot at the recompilation level, where it is the useful identity, and records
linkage-level breakage separately, where it can be acted on by the consumers it actually affects.

## 12. Snapshots, Lineage, and Versioning

### 12.1 Snapshot

A release's **atom set** is the union of the atoms of all its `api` records (well-defined across
disciplines because atom hashes are domain-separated). Its **snapshot** is

```text
snapshot = hash("lira/1:snapshot", concat(sorted atom value hashes))
```

with the **distinct** value hashes sorted ascending bytewise and concatenated as raw 32-byte
values: the input is a set, so two atoms sharing a value hash (for example, identical opaque
content at two paths) contribute one 32-byte term.

### 12.2 Lineage

The manifest's `lineage` field lists the **distinct** snapshots of the module's releases in one
major series, oldest first; the final entry MUST equal the release's own snapshot (**L109**). A
patch release (below) shares its predecessor's snapshot and therefore appends nothing: the
lineage is the sequence of the series' *API states*, not of its releases. The lineage is the
module's verifiable version history: every compatibility question in this specification reduces
to membership in, or relations between, lineages.

A new major series begins a fresh lineage with no shared prefix. There are no compatibility
guarantees across majors except those separately provable via used-sets (§13.4).

### 12.3 Release Grades

Between a release `A` and its immediate successor `B` in a lineage:

- **patch**: `atoms(B) = atoms(A)` — API identity; only non-API content changed.
- **minor**: `rigid(A) ⊆ rigid(B)`, and every replaceable atom of `A` is present in `B` either
  unchanged or replaced (same key, new value). Pure extension plus replaceable churn.
- Anything else — any rigid removal or change, any replaceable removal whose key survives
  nothing — MUST NOT be published into the same lineage; it is a **major** event beginning a new
  lineage. Publishing tools MUST refuse to extend a lineage with a non-conforming successor
  (**L110**) unless the operator explicitly requests a major.

Each lineage step SHOULD be accompanied by a **Delta metadata blob** recording the added atoms
and the replaced replaceable atoms of that step. Deltas make staleness computable (§13.4) and
allow a verifier holding consecutive releases to check a lineage step exactly.

### 12.4 Grades and Guarantee Levels

The grades above are computed from atom sets and are therefore claims at the guarantee levels the
release's disciplines certify (§11.2, §11.5) — for a discipline over source-level metadata, the
recompilation level. A profile (§11.6) may certify a further level, and a step may satisfy the
algebra at one level while failing a profile predicate at another. Such a step is **not**
promoted to major by the core: rigid monotonicity is a statement about atoms, and the atoms are
intact.

Instead the release records the shortfall. A release whose lineage step satisfies §12.3 but whose
predecessor's guarantees are not preserved at some level a declared profile certifies MUST list
that level in the profile record's `breaks` field (**L130**). The reading of

```text
profile
  id      jvm/1
  breaks  linkage
```

is precise: *by the atom algebra this is a minor, and consumers who recompile may take it as one;
consumers relying on already-compiled linkage must recompile against it.* A release that silently
omits a `breaks` level it does not preserve is invalid — the whole value of the record is that
its absence means something.

This split is what lets one release serve both populations honestly. Collapsing it — forcing a
major for every bridge-affecting change — would fracture lineages for changes that no recompiling
consumer can observe; ignoring it would promise linkage the release does not deliver. Note also
what `breaks` cannot do: it never licenses a rigid atom removal, and it never permits a step the
core forbids. It records a weaker claim about a step the core already allows.

### 12.5 The Derived Version

The manifest's `version` field is OPTIONAL, and strictly numeric: exactly `x.y.z` with each
component a decimal natural. Prerelease and build suffixes are forbidden by the schema —
development state is expressed by the version's *absence*, never by a suffix.

**Development releases.** A release without a `version` is a **development release**,
identified purely by its hashes: the snapshot is its API identity and `payload.hash` its
implementation identity. Development releases are complete, verifiable `.lira` files; what they
lack is only the assignment below. Other modules may depend on them during development,
optionally pinning the exact build (§13.2); such dependents are themselves unpublishable until
the pin is lifted (**L118**).

**Version assignment.** Publishing a release is the act of assigning it the next version
number the algebra dictates, and signing the result:

1. compute the grade (§12.3) of the release against the module's previous published release
   (a first release is assigned `0.1.0` with a fresh single-entry lineage);
2. derive the version — patch increments `z`, minor increments `y` and zeroes `z`, and an
   explicitly-requested major increments `x` and zeroes both (in the `0` series, where the
   minor conventionally carries breaking steps, a major increments `y`);
3. extend the lineage per §12.2 and §12.3 (**L110**);
4. re-sign the manifest (§15).

The payload is untouched, so assignment never rebuilds and the implementation identity is
unchanged. The signed manifest — binding module, version, lineage, snapshot and payload hash
under one signature — **is** the assignment record; a distribution index's release record is a
projection of it.

**Publication rules.** A publishing tool MUST refuse to publish a manifest that:

- carries no version, or a non-numeric one (**L117**);
- pins any dependency to a `build` (**L118**);
- requires a snapshot that appears in no *published* release's lineage for that module
  (**L119**);
- for a stable series (`x ≥ 1`), carries a minor number that is not the count of minor steps
  in its lineage (**L120**; the `0` series is exempt, since there the minor also carries
  breaking steps and is not a projection of lineage length).

Consumers still make every decision on hashes: to a consumer the version remains a
human-readable projection, and any disagreement it observes (for example, a dependency's
`version` hint against the resolved release) is a warning, never an error.

## 13. The Buildpath

### 13.1 Definition

A buildpath is a set of `.lira` files intended for joint use. It is unordered: the coherence
rules below make ordering irrelevant, unlike traditional classpaths.

### 13.2 Dependency Requirements

Each `dependency` record in a manifest names a module and a **required snapshot** — an API
identity the depending module was compiled against. A candidate release **satisfies** the
requirement iff the required snapshot appears in the candidate's `lineage`.

A dependency record MAY additionally carry:

- **`universe`** entries, scoping the dependency to the named universes: sections of a release
  may have genuinely different implementations per universe, and correspondingly different
  dependencies (a DOM facade needed only by the `sjsir` implementation; a C-binding wrapper
  only by `nir`). A dependency without `universe` entries applies to every universe.
- **`integration`** entries, scoping the dependency to the named integrations (§9.5): the
  dependency vectors that distinguish integrations are expressed exactly here. A dependency
  without `integration` entries applies to every integration, which is how dependencies common to
  all of them are declared once. The two scopes are independent and conjunctive: a dependency
  applies to a (universe, integration) pair iff it applies to that universe and to that
  integration.
- **`build`**, a development-time pin to an exact implementation identity (§6): the candidate
  must additionally have exactly that `payload.hash`. Build pins express "this exact unpublished
  build" during development; a manifest carrying one is itself unpublishable (**L118**, §12.4).

Dependency requirements are per-release facts: a module's dependency graph — which modules it
names, at which snapshots, in which universes — may change freely between releases at any
grade. Grades (§12.3) constrain only the module's own atom set; consumer safety under a changed
graph comes from re-validating the buildpath (§13.3), never from the grade.

### 13.3 Validity

A buildpath is valid **for a universe under an assignment** — a map from each release to one of
its integrations (§9.5) — iff all of the following hold, and each is decidable from manifests
alone. Closure and compatibility quantify over the dependency records *applicable to that
universe and to that release's assigned integration* (§13.2); uniqueness, namespace disjointness
and resource disjointness are global. A buildpath is **valid for a universe** iff some assignment
makes it so (**L132**).

1. **Uniqueness**: at most one release per module name (**L111**).
2. **Namespace disjointness**: the `owns` claims (namespaces such as packages, per-ecosystem
   interpretation) of distinct modules are pairwise disjoint — a namespace and any dotted
   extension of it clash (**L112**).
3. **Resource disjointness**: the `export` and `track` resource paths (§11.4) of distinct
   modules are pairwise disjoint (**L126**), so a classpath-style resource reference resolves
   to exactly one module. `scan` directories are exempt — cross-module aggregation under a
   shared directory is their purpose.
4. **Closure**: every module named by an applicable `dependency` record is present (**L113**).
5. **Compatibility**: every applicable dependency requirement is satisfied per §13.2,
   including any build pin (**L114**). Diamond dependencies resolve by construction:
   requirements on two snapshots of one module are jointly satisfiable iff some published
   lineage contains both — the incompatible-major case is exactly the case where none does.
6. **Profile coherence**: every ecosystem profile (§11.6) declared by any release on the
   buildpath imposes its predicates over the whole buildpath, including any predicate over the
   `toolchain` records — for example, mutual readability of metadata format versions. The base
   specification imposes none of its own, and a buildpath whose releases declare no profile is
   subject to rules 1–5 alone. Profile predicates, like the rules above, MUST be decidable from
   manifests; a profile predicate requiring payload inspection is a publish-time check (§16), not
   a buildpath rule.

Where no release declares an integration, every release has one and the assignment is unique:
the rules read exactly as they did before this mechanism, and validity is decided by one pass.

Note what the quantifier does *not* add. No rule above mentions integrations, and none needs to:
the rules that decide between them are the ones already there. An assignment whose integration
requires a snapshot the present release of that module does not carry in its lineage fails rule
5; one that would need a second release of a module already on the path fails rule 1; and one
requiring a module absent altogether fails rule 4. So the version-alternative case resolves out
of rules that predate integrations entirely, and all the quantifier adds is the search for an
assignment that survives them.

Rule 4 is also how a consumer expresses a backend choice without pinning: an integration naming a
module the buildpath does not carry fails closure, so putting exactly one backend on the
buildpath selects the integration that uses it. Pinning (below) is for the case where the
buildpath carries both and the choice is genuinely free.

**The canonical assignment.** More than one assignment may be valid — the case where a release
offers alternative backends and the buildpath carries both. Resolution must still be
reproducible, so among the
valid assignments the **canonical** one is the lexicographically least sequence, taken over
releases in ascending module-name order, of each assigned integration's `rank` then `id` (§14).
Tools MUST select the canonical assignment unless the consumer pins otherwise, and a consumer MAY
pin any release to a named integration, the search then ranging over the releases left free.
Pinning is how a consumer states a preference the manifests cannot imply; `rank` is how a
publisher states a default so that the unpinned case is deterministic rather than arbitrary.

**The cost.** Finding an assignment is a search, and in the general case an intractable one —
this is ordinary dependency resolution, which the rest of this specification avoids by requiring
exact snapshots and deciding satisfaction by lineage membership. Four things bound it: the
search ranges over
integrations only and never over versions; the branching factor is the number of integrations a
release declares, typically two or three; closure and uniqueness prune early, since a wrong
choice usually contradicts a release already fixed; and §13.4's spanning often removes the need
for an integration altogether. Tools SHOULD report an unsatisfiable buildpath by naming the
releases whose integrations could not be reconciled, since "no valid assignment" is otherwise an
unactionable diagnosis.

### 13.4 Used-Sets, Spanning, and Staleness

A manifest MAY attach to each dependency a **Uses metadata blob**: the set of that dependency's
atom value hashes the module actually depends on. The used-set is computed as the module's own
direct references **transitively closed over the reference lists (§11.2) of its dependencies'
replaceable atoms** — capturing content copied into the module at compile time through any depth
of inline expansion, with no compiler cooperation required beyond the archives themselves.

Used-sets enable two derived judgements:

- **Spanning**: a module compiled against release `A` of a dependency is also valid against any
  release `B` (including across majors) whenever `used ⊆ atoms(B)`. Publishing tools MAY record
  proven spans, and buildpath tools MAY validate against them, eliminating recompilation for
  dependency upgrades that provably do not touch what the module uses.
- **Staleness**: after a minor upgrade of a dependency, the modules that SHOULD be recompiled
  are exactly those whose used-set intersects the union of replaced atoms in the traversed
  deltas (§12.3). Staleness is advisory — linkage is guaranteed by §10.2 — but tools SHOULD
  surface it.

### 13.5 Derivation of Conventional Artifacts

From a valid buildpath, a chosen universe and the assignment that validated it (§13.3), a
consumer derives a conventional artifact set (e.g. a classpath) by, per release: selecting the
section for that universe and that release's assigned integration (a release lacking one is a
validation-time error, not a link-time surprise), materializing it per §9.3 into a
cache keyed by implementation identity, and appending whatever ecosystem-supplied platform
runtime the universe requires. Reconstruction of a standalone per-platform archive is the
canonical derivative artifact of §13.6.

### 13.6 Canonical Derivative Artifacts

Each section deterministically derives one **canonical derivative artifact**: the section's
materialized tree serialized to the ecosystem's container format under the canonical profile of
Appendix C (for the motivating ecosystem, a JAR). Derivation is byte-deterministic — a pure
function of the materialized tree — so the artifact's identity,

```text
derivative = hash("lira/1:derivative", artifact bytes)
```

is a stable fact about the section, which the section's OPTIONAL `derivative` field declares
(§14) and verifiers recompute (§16, step 3).

The declared derivative hashes make releases **findable from conventional artifacts alone**: a
tool holding only a classpath of ordinary JARs hashes each under the derivative domain and
looks the result up — against a buildpath's manifests, or a distribution index — recovering
the release, its API identity, and its whole compatibility context. Since a derivative belongs to
one section, and a section to one (universe, integration) pair, the lookup also recovers *which
integration* the artifact is, which no coordinate-mangling convention can tell it.
Materialization caches
(§13.5) SHOULD store sections in exactly this form, so the cache entry *is* the canonical
artifact.

## 14. Manifest Schema

The `lira` TEL schema, and the four companion schemas for metadata blobs. The scalar
validators are normative: `base-256-hash` is exactly 32 BASE-256 characters; `module-name` is
kebab-case segments joined by `/` or `.`; `namespace` is dotted package-style segments
(letters, digits, `_`; no leading digit); `semver` is exactly `major.minor.patch`, each a
decimal natural with no superfluous leading zero; `natural` is such a natural; `discipline-id`
is `<kebab-name>/<positive integer>`; `tree-path` is a relative `/`-separated path with no
empty, `.` or `..` segments; `atom-class` is `rigid` or `replaceable`.

```text
tel 1.0

name lira

scalar Hash
  description  A 256-bit BLAKE3 hash, BASE-256 encoded (32 characters).
  validate     base-256-hash

scalar ModuleName
  validate module-name

scalar Namespace
  validate namespace

scalar Semver
  validate semver

scalar Natural
  validate natural

scalar DisciplineId
  description  A discipline identifier, e.g. tasty/1.
  validate     discipline-id

scalar ProfileId
  description  An ecosystem profile identifier, e.g. jvm/1.
  validate     profile-id

scalar Guarantee
  description  A guarantee level (§11.5): linkage or recompilation.
  validate     guarantee

scalar TreePath
  description  A relative tree path (§9.2).
  validate     tree-path

record Tool
  description  One tool that produced content in this release.

  field name     Identifier
  field version  String
  field flag     Identifier optional repeatable

record Api
  description  One discipline's atomization of this release's public interface.

  field discipline DisciplineId
  field atoms Hash                      # Atoms metadata blob

record Profile
  description  An ecosystem profile whose predicates this release claims to satisfy.

  field id      ProfileId
  field breaks  Guarantee optional repeatable   # levels not preserved vs the predecessor (§12.4)

record Resource
  description  One resource claim for the resource/1 discipline (§11.4).

  select  ResourceMode
  field   path  TreePath

record Dependency
  field module ModuleName
  field api Hash                        # required snapshot (satisfied by lineage membership)
  field version Semver optional         # human-readable hint; no authority
  field build Hash optional             # development-time implementation-identity pin (§13.2)
  field universe Identifier optional repeatable  # universes this dependency applies to (§13.2)
  field integration Identifier optional repeatable  # integrations it applies to (§9.5, §13.2)
  field uses Hash optional              # Uses metadata blob
  field spans Hash optional repeatable  # snapshots provably spanned (§13.4)

record Integration
  description  One alternative dependency vector this release was built against (§9.5).

  field  id     Identifier
  field  rank   Natural optional    # canonical-assignment preference, lower first (§13.3)
  field  label  String optional     # human-readable note; no authority

record Section
  select  Universe
  field   integration Identifier optional            # the integration realized (§9.5)
  field   tree        Hash                           # Tree metadata blob
  field   delete      String  optional  repeatable  # root paths removed in this overlay
  field   derivative  Hash  optional                # canonical derivative artifact (§13.6)

record Payload
  field  compression  Identifier          # brotli
  field  length       Natural             # decompressed byte length (enforced)
  field  hash         Hash                # implementation identity (§8.4)

record Signature
  field  signer     String
  field  algorithm  Identifier          # e.g. ml-dsa-65
  field  key        Hash                # public-key fingerprint (§15.3)
  field  value      String              # BASE-256 signature

select Universe
  variant  jvm    Flag
  variant  sjsir  Flag
  variant  nir    Flag

select ResourceMode
  variant  export  Flag
  variant  track   Flag
  variant  scan    Flag

document
  field module ModuleName
  field version Semver optional         # absent on development releases (§12.4)
  field lineage Hash repeatable         # distinct snapshots, oldest first; last = this release
  field toolchain Tool repeatable
  field owns Namespace optional repeatable
  field resource Resource optional repeatable  # resource/1 claims (§11.4)
  field api Api repeatable
  field profile Profile optional repeatable
  field integration Integration optional repeatable  # alternative dependency vectors (§9.5)
  field dependency Dependency optional repeatable
  field delta Hash optional             # Delta metadata blob for this lineage step
  field section Section repeatable     # first section = root (§9.1); keyed (universe, integration)
  field payload Payload
  field signature Signature optional repeatable
```

The four metadata-blob schemas:

```text
tel 1.0

name lira-tree

record Entry
  field path TreePath
  field blob Hash

scalar Hash
  validate base-256-hash

scalar TreePath
  validate tree-path

document
  field entry Entry optional repeatable
```

```text
tel 1.0

name lira-atoms

record Atom
  field class AtomClass
  field hash Hash
  field key String

scalar Hash
  validate base-256-hash

scalar DisciplineId
  validate discipline-id

scalar AtomClass
  validate atom-class

document
  field discipline DisciplineId
  field atom Atom optional repeatable   # sorted by ascending value hash (§10.4)
```

```text
tel 1.0

name lira-uses

scalar Hash
  validate base-256-hash

scalar ModuleName
  validate module-name

document
  field module ModuleName
  field atom Hash optional repeatable   # sorted ascending bytewise
```

```text
tel 1.0

name lira-delta

record Replacement
  field old Hash
  field new Hash

scalar Hash
  validate base-256-hash

document
  field add Hash optional repeatable          # sorted ascending bytewise
  field replace Replacement optional repeatable  # sorted by ascending old hash
```

New universes, disciplines with schema-level needs, and future fields are introduced as TEL
schema layers; the manifest's pragma signature encodes exactly which extensions a file uses.

## 15. Signatures

### 15.1 Algorithms

The default and RECOMMENDED algorithm is **ML-DSA-65** (FIPS 204), identified as `ml-dsa-65`.
The `algorithm` field provides agility; verifiers MUST reject signatures whose algorithm they do
not implement rather than ignore them silently, and MAY be configured to require particular
algorithms. A release MAY carry multiple signatures (co-signing, algorithm diversity).

### 15.2 Signing Domain

The signed message is:

```text
hash("lira/1:manifest", BinTEL(manifest with all signature fields removed))
```

where `BinTEL(…)` is the canonical BinTEL encoding of the manifest's semantic model under the
`lira` schema. Signing the canonical encoding — never the source text — makes signatures immune
to reformatting, and removing `signature` fields first means signing and counter-signing never
perturb the signed bytes. The payload is covered transitively through `payload.hash`; every
metadata blob and section is covered through the hash tower. The interpreter directive (§5.1) is
not covered and needs no coverage: its bytes are fixed by **L115**, so any substitution renders
the file invalid before signatures are considered.

### 15.3 Keys

`key` is `hash("lira/1:key", public-key-bytes)` over the algorithm's standard interchange
encoding of the public key — for ML-DSA, the X.509 `SubjectPublicKeyInfo` (DER) form. Public-key distribution is out of band (a registry of authorized signers, analogous to
SSH `allowed_signers`, is RECOMMENDED); a future schema layer MAY permit embedding public keys
for trust-on-first-use deployments.

## 16. Verification

Verification is re-execution of the construction, bottom-up. A full verifier, given a `.lira`
file (and, where noted, additional artifacts):

0. checks, before parsing anything, that the first line is byte-exactly the canonical
   interpreter directive (§5.1, **L115**) and that the pragma specifies no sigil (§5.2,
   **L116**);
1. decompresses the payload within `payload.length` and checks `payload.hash` (§8.4);
2. recomputes every blob hash while scanning the stream and checks sortedness and uniqueness
   (§8.2), and resolves every referenced blob (§8.3);
3. checks every tree's path rules and every overlay's minimality (§9.2–§9.3), and recomputes
   every declared derivative hash from the materialized section (§13.6);
4. re-atomizes content under each declared discipline and compares against the Atoms blobs,
   checks the cross-section invariant over each discipline's domain for every section of the
   (universe × integration) matrix (§9.6), that integration declarations are well-formed
   (**L131**) and each is realized (**L133**), and that no declared discipline is inapplicable
   (**L127**) — requires an implementation of each discipline (though
   `opaque/1` and `resource/1` are language-blind and implementable by every verifier);
5. recomputes the snapshot and checks it equals the last lineage entry (§12.1, **L109**);
6. given the predecessor release, checks the lineage step's grade and delta (§12.3);
7. checks the predicates of each declared profile, and — given the predecessor release — that
   every guarantee level the step fails to preserve is listed in that profile's `breaks` field
   (§11.6, §12.4, **L128**, **L130**) — requires an implementation of each profile;
8. recomputes the signing domain and verifies each signature (§15): a signature that does not
   verify (**L121**), whose algorithm the verifier does not implement (**L122**), or whose key
   fingerprint matches no trusted key (**L123**) fails the file.

Steps 0–3, 5 (given the Atoms blobs) and 8 require no language knowledge and SHOULD be performed
at installation. Steps 4, 6 and 7 are publish-time checks: a registry MUST perform them before
accepting a release, since they are what make manifests trustworthy at use-time. A registry that
cannot implement a declared discipline or profile MUST reject the release rather than accept it
unchecked — an unverifiable claim is worse than an absent one, because consumers cannot tell the
two apart from the manifest. Every claim in a manifest is thus either recomputable locally or
attested by signature over recomputable claims; nothing is trusted testimony.

## 17. Determinism

Producing a release twice from identical inputs **with the same producer toolchain** MUST yield
byte-identical unsigned `.lira` files. To that end: all orderings in this specification are
total (blobs by hash; tree entries by path; atoms by value hash; lineage by history); the
interpreter directive is a fixed string (§5.1); no timestamps exist anywhere in the format;
atomization is required to be run-independent (§11.2); producers are required to be
self-deterministic in their compression (§8.1); and manifests generated by tools MUST use LF
endings and canonical TEL formatting.

Two qualifications bound the claim precisely. *Across* producer toolchains, what is reproducible
is the manifest's semantic model and the decompressed blob stream — every identity of §6 and
§12 — while compressed bytes may differ (§8.1); implementation identity is defined over the
decompressed stream for exactly this reason. And signing is excluded: the default ML-DSA
signing mode is hedged (randomized), so re-signing yields different signature values over the
same signed message; determinism claims apply to the file with its `signature` fields removed,
which is also precisely the signing domain (§15.2).

Determinism is what makes the implementation identity meaningful and allows independent parties
to reproduce and attest a release.

## 18. Security Considerations

- **Decompression bombs**: bounded by mandatory enforcement of `payload.length` (§8.1).
- **Path traversal**: excluded by tree path rules (§9.2).
- **Hash agility**: deliberately absent within an epoch; BLAKE3-256 is the only hash, and any
  future change is an epoch change (§7.1). Signature agility is present but explicit (§15.1).
- **Substitution attacks**: dependency requirements are by snapshot hash and satisfaction is by
  lineage membership; a registry that verifies lineage steps at publish time (§16) prevents an
  attacker from grafting a hostile "compatible" release onto another module's lineage without
  the signing keys of that module's publishers.
- **Executable artifacts**: every `.lira` file is executable by design (§5.1), but the
  execution surface is limited by construction: the directive is byte-fixed by **L115** — a
  verifier or installer rejects any deviation before other processing — so the only code that
  can run is whatever `lira` executable the _user's own_ PATH resolves. The residual risks are
  those of running any local tool against untrusted input: users SHOULD verify files before
  invoking them, and the `lira` tool itself MUST treat the file as untrusted data (enforcing
  §8.1, §9.2 et al.).
- **Guarantee scope**: a grade is a claim at the levels the release's disciplines and declared
  profiles certify (§11.5), and at no others. Tools presenting a grade to a user SHOULD present
  the level with it; a "minor" reported without its level invites a consumer relying on linkage
  to act on a claim that was only ever about recompilation.
- **Behavioral compatibility**: no hash scheme certifies that unchanged signatures have
  unchanged behavior. Patch and minor grades bound _interface_ and _copied-content_ change;
  behavior remains the publisher's promise, mitigated by signatures and (out of scope here)
  attestation of test evidence.

## Appendix A (Informative): The Scala Discipline `tasty/1`

The Scala discipline is specified normatively in the companion document
[`tasty.md`](tasty.md); this appendix is an orientation. Atomization is performed
over TASTy — never over raw TASTy bytes, which embed tool version strings, but over the
semantic model as the compiler unpickles it (fully-qualified references, erased-signature
overload disambiguators, alpha-normalized local names, API-relevant flags and annotations only,
members sorted). Illustrative decomposition, applying the folding principle:

- Standalone **rigid** atoms: concrete methods and fields; each overload (key includes erased
  signature); each default-argument's _existence_ (its body is excluded — defaults resolve at
  runtime in the callee); compiler-generated `inline$` accessors (real public surface reached by
  inline expansion).
- Folded into the parent's atom (so their addition is a **major** event): abstract members of
  open templates; the child list of sealed types and enums (exhaustivity); parents, self-types,
  variance, bounds, opacity, and modifier changes.
- **Replaceable** atoms: bodies of `inline`/`transparent inline`/macro definitions, keyed by
  their declaration; their reference lists (§11.2) enumerate everything they splice into
  consumers, enabling used-set closure and staleness computation. Replaceability soundness holds
  because the compiler guarantees everything an inline body reaches is public or accessor-wrapped
  — all of which is rigid-atomized.
- TASTy-level and classfile-level compatibility diverge in both directions; `tasty/1`
  atomizes the TASTy level and certifies **recompilation**. The classfile-level invariants are
  the JVM ecosystem profile's (Appendix D), not the discipline's.

### A.1 Type Hierarchies and Variance

Nothing in the algebra reasons about subtyping, and nothing needs to. A class's parent list,
self-type, type-parameter variances and bounds are not atoms of their own: they are constituents
of the canonical encoding of the class's _own_ atom. So if `class C` stops extending `trait T`,
`C`'s canonical encoding changes, its value hash changes, and the atom that was in the previous
release is simply absent from the new one. Under §10.2 rigid atoms are monotonic within a
lineage, and under §12.3 an absent rigid atom is not expressible below the major grade. The
hierarchy change is caught as a set-membership fact, by a checker that has never heard of
inheritance. Variance (`Box[+A]` → `Box[A]`), bounds, and opacity behave identically.

This is deliberately conservative in the additive direction, and the cost should be stated
plainly. Because parents fold rather than standing alone, _adding_ a parent is also a major
event, though it is frequently harmless to consumers. Scala has real cases where it is not
harmless — implicit ambiguity, sealed-hierarchy exhaustivity, inherited-member conflicts — and
the folding principle resolves an unclear case to the sound side by construction. Where a
language offers an explicit marker distinguishing the two (Rust's `#[non_exhaustive]` is the
clearest instance), a discipline SHOULD read it and fold accordingly; Scala offers no such
marker for parent lists, so `tasty/1` pays the cost in false majors rather than risk unsound
minors. A future `tasty/2` could introduce an annotation to opt a type into addition-friendly
atomization — a discipline version bump, exactly as §11.1 requires.

### A.2 Hierarchies That Cross Module Boundaries

An atom's canonical encoding names its references by fully-qualified name, not by their atom
hashes. A module's own atom set therefore cannot, by itself, detect that a _supertype in another
module_ has changed underneath it: if `C` in module M extends `T` from module N, nothing in M's
manifest changes when N changes `T`.

That consistency is a buildpath property, and it is enforced by §13.3 and §13.4 rather than by
the discipline. M's Uses blob records `T`'s atom hash among M's used-set; N changing `T` changes
that atom; and spanning (`used ⊆ atoms(B)`) then fails for M against the new N, as does
dependency satisfaction (§13.2) if N's change was major. The invariant "every module's view of
every hierarchy it participates in agrees" is thus decided across the whole buildpath, from
manifests alone — which is the correct place for it, since no single module can hold the
information needed to check it.

### A.3 Keying, and Where Inherited Members Live

Under §11.2's keying requirement `tasty/1` keys by **declaration**: a member is atomized once,
under the type that declares it, with the erased-signature disambiguator of
[`tasty.md`](tasty.md) §6. Inherited members are not re-atomized under each type that presents
them.

This is sound *because* of what the discipline certifies (§11.3): recompilation and TASTy-level
linkage. A consumer's TASTy reference to `c.foo()`, where `C` inherits `foo` from `T`, names the
symbol `T.foo` — the declaring owner — so the atom the consumer's used-set records is the one
that changes if `foo` changes, wherever `foo` was declared. Cross-module hierarchy consistency
then follows from A.2 rather than from redundant keys.

Membership keying would be required for a discipline certifying *classfile* linkage, where a
call site names the receiver and a type's linkage surface therefore includes members it does not
declare. That is exactly the surface `tasty/1` scopes out to the JVM ecosystem profile
(Appendix D), so the keying choice and the guarantee claim stay consistent: the discipline keys
the way the representation it atomizes actually references things, and the profile covers the
representation that references them differently.

## Appendix B (Informative): Worked Example

```text
#!/usr/bin/env lira
tel 1.0 <lira schema signature>

module gossamer-core
version 0.64.2
lineage Kx3f…
lineage Lm81…
lineage Pq44…

toolchain
  name scala
  version 3.9.0-RC4-p6

owns gossamer

# mode     # path
resource export     gossamer/text-tables.conf
resource scan       gossamer/templates

api
  discipline tasty/1
  atoms Vw12…

api
  discipline resource/1
  atoms Wz34…

profile
  id jvm/1
  breaks linkage

integration
  id rudiments1
  rank 0
integration
  id rudiments0
  rank 1
  label built against the rudiments 0.x line

# module              # api     # version
dependency anticipation-core      Ab12…     0.64.0
dependency rudiments-core         Cd34…     0.64.1
  integration rudiments1
dependency rudiments-core         Ef90…     0.63.8
  integration rudiments0

delta Xy56…

section jvm
  integration rudiments1
  tree Ef56…
  derivative Tu78…
section sjsir
  integration rudiments1
  tree Gh78…
section nir
  integration rudiments1
  tree Ij90…
  delete gossamer/JvmOnly.class
section jvm
  integration rudiments0
  tree Kl12…
  derivative Vw90…

payload
  compression brotli
  length 2580480
  hash Qr12…

signature
  signer jon.pretty@propensive.com
  algorithm ml-dsa-65
  key St34…
  value <BASE-256 signature>
##
<Brotli-compressed blob stream>
```

Reading this manifest alone, a tool can determine: the module's API history (three snapshots,
two minor steps); that it satisfies any dependent requiring `Kx3f…`, `Lm81…` or `Pq44…`; which
universes it supports; that it offers a `jvm` build against the `rudiments` 0.x line as well as
the preferred one, so a buildpath pinned to `Ef90…` resolves without a second artifact, while
`sjsir` and `nir` are offered only under the preferred integration; that the `nir` view omits one
root file; that the resource `gossamer/text-tables.conf` is contractually present on every
universe's classpath; the hash of the classpath JAR each `jvm` section derives, which identifies
which integration a bare JAR is; that the last step, though a minor by the atom
algebra, did not preserve JVM linkage, so consumers holding compiled bytecode against `Lm81…`
must recompile while consumers who build from source need do nothing; and everything needed to
verify the file's integrity and authorship — all without decompressing a byte of the payload.

## Appendix C (Normative): The Canonical Derivative Profile

The canonical derivative artifact of a `jvm`, `sjsir` or `nir` section is a ZIP archive (a
JAR) with exactly this layout:

- one file entry per row of the materialized tree, in tree order (ascending bytewise UTF-8
  path order); no directory entries;
- entry names are the tree paths, UTF-8 encoded, with no transformation;
- every entry uses the **Stored** method (no compression), with the CRC-32 and sizes of the
  content bytes;
- all timestamps are the DOS epoch (00:00:00, 1 January 1980); no extra fields, no entry or
  archive comments, no archive prefix; ZIP64 structures only where entry counts or sizes make
  them unavoidable;
- the archive is local file headers with entry data in order, then the central directory,
  then the end-of-central-directory record.

Entries are Stored deliberately: a compression method would make every declared derivative
hash depend on one encoder implementation's exact output forever, whereas the Stored profile
depends only on the content itself. The artifact is nevertheless a fully conventional JAR,
readable by any ZIP tooling.

## Appendix D (Informative): The JVM Ecosystem Profile `jvm/1`

The `jvm` universe is the case that motivates profiles, because it is the case where linkage and
recompilation most visibly come apart. This appendix sketches what `jvm/1` must cover; like
Appendix A it is informative, and a companion document will state it normatively. It is
unrelated to Appendix C, which uses "profile" in the narrower sense of a canonical encoding.

### D.1 The Two Levels Diverge in Both Directions

`tasty/1` certifies recompilation (§11.3). It does not certify JVM linkage, and cannot, because
the linkage surface is a property of the classfiles — a representation below the one it
atomizes. Concretely:

- **Recompilation-safe, linkage-breaking.** Adding a concrete method to a trait is a standalone
  rigid atom and hence a clean minor: no consumer's source stops compiling. But it changes mixin
  forwarder generation in every subclass of that trait, including subclasses compiled earlier and
  held as bytecode elsewhere. Adding an overload can cause an existing method to acquire a
  bridge; changing a supertype's generic signature changes bridge generation in subclasses whose
  own TASTy is untouched. In each case the atoms are monotonic and the bytecode contract is not.
- **Linkage-safe, recompilation-breaking.** Tightening a type bound where erasure is unchanged,
  changing a given's specificity, retargeting a type alias, altering variance — all leave every
  descriptor in every classfile identical, and all can fail a consumer's next compile. Here the
  atoms correctly register a major and the bytecode would have permitted the change.

Neither level subsumes the other, which is why `jvm/1` is a profile predicate (§11.6) rather
than extra atoms: the two claims have different audiences and must be reportable separately.

### D.2 What `jvm/1` Checks

The predicates are bytecode-level and follow the model of existing binary-compatibility tooling
(MiMa and the JLS binary-compatibility chapter are the rule table). Against the predecessor
release's `jvm` section: no public method, field or class disappears or changes descriptor; no
bridge or mixin forwarder that a compiled consumer could have bound to is removed; `static
final` constant values that javac may have inlined are tracked as replaceable-equivalent;
accessibility never narrows. A failure of any of these, in a step the core algebra grades as a
minor, is exactly the situation §12.4 exists for: the release publishes `breaks linkage` and
remains a minor for recompiling consumers.

`jvm/1` also carries the natural toolchain predicate for the ecosystem (§13.3): every release on
a buildpath must carry TASTy that the consumer's compiler can read, since TASTy readability is
versioned and not universally backward-compatible.

### D.3 Can Classfiles Be Regenerated From TASTy?

The question is natural — TASTy is the full typed tree, `scalac -from-tasty` really does compile
TASTy to classfiles, and if the classfiles are derivable then a linkage break might seem
repairable without a major. Three things make this less useful than it appears, and they are
worth recording so that implementations do not attempt it:

1. **Regeneration cannot restore a broken linkage contract.** Recompiling the _new_ TASTy
   produces the new classfile surface — the bridge that a compiled consumer needs is missing
   precisely because the new TASTy no longer implies it. Only regenerating from the _predecessor's_
   TASTy would reproduce the old surface, and that is just the predecessor release. Derivability
   moves no compatibility question.
2. **Regeneration changes the bytes.** Classfiles emitted by a different compiler build differ
   from those shipped, so every blob hash in the `jvm` section changes, and with it the payload
   hash and the release's implementation identity (§6). A section whose contents are derived at
   consumption time is a different kind of object from one recorded in the payload, and §17's
   determinism guarantee does not extend to it.
3. **`-from-tasty` is a compiler-testing path**, not a distribution mechanism, and it requires a
   compiler able to read the TASTy version in question.

What the derivability of classfiles genuinely does buy is worth stating positively, because it
is the reason a linkage break need not be fatal: **while TASTy is intact, a linkage break is a
recompilation cost, not a wall.** Any consumer willing to rebuild from source can consume the
release; only consumers pinned to prebuilt bytecode are blocked. That is precisely the
distinction §11.5 draws and precisely what `breaks linkage` records — so the honest treatment of
regeneration is not to attempt it, but to grade with it in mind.
