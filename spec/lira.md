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
- the **buildpath**: a composition of LIRA files whose coherence — including diamond-dependency
  resolution — is decidable from manifests alone, without reading any payload.

The primary motivating ecosystem is Scala (JVM, Scala.js, Scala Native), but no normative part of
this specification is specific to Scala. Language-specific material appears only in informative
appendices.

## 1. Status

This document is a working draft. Numbered requirements and the schema in §14 are expected to
change. The Scala discipline is specified normatively in the companion document
[`scala-tasty.md`](scala-tasty.md).

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
- **Section**: one compiled view of the release, keyed by universe and optionally by a
  dependency vector (§9.5), stored as a tree of blobs.
- **Blob**: a byte string in the payload, identified by its hash (§7).
- **Atom**: the unit of API compatibility: a hash over the canonical encoding of one indivisible
  fragment of a module's public interface (§10).
- **Discipline**: a named, versioned canonicalization procedure that converts content into atoms
  (§11).
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
implementation identities differ (§12.4).

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
`scala-tasty/1`. Because the discipline identifier participates in the domain, atoms produced by
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

### 9.5 Variant Sections

A section MAY carry `against` entries: the API snapshot hashes of dependency releases it was
compiled against, when these differ from the release's declared dependency list. This supports
carrying compilations against multiple major versions of a dependency in one file. Sections
without `against` are compiled against the manifest's own `dependency` list. Producers SHOULD
prefer proving that a single compilation spans multiple dependency majors (§13.4) over emitting
variant sections; variants are the fallback for genuine incompatibility.

### 9.6 Cross-Universe API Invariant

For each discipline, atomizing each universe's **claimed** content (§11.2) MUST yield an
identical atom set (**L108**): a release presents one API on every universe it supports.
Implementations may differ per universe; interfaces may not. Producers MUST verify this at
assembly time by atomizing each universe's materialized tree independently and comparing; the
`api` records (§14) list this universe-invariant atomization, computed from the root section's
materialized tree. (A library whose API genuinely differs by platform is two modules.)

The invariant is scoped per discipline to the content that discipline claims. Content that a
discipline claims **atomless** (§11.2) — derived binaries such as classfiles, whose interface
the discipline already carries through other files, or scanned resource directories (§11.4),
whose contents are deliberately non-contractual — contributes nothing to any universe's
atom set, which is what permits universes to diverge in implementation without violating the
invariant. Content claimed by no discipline falls to `opaque/1` and must therefore be
byte-identical across the universes that carry it.

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

discipline scala-tasty/1

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

### 11.2 Requirements

A discipline defines, deterministically:

1. **Claiming**: which content items of a materialized tree the discipline atomizes. A
   discipline MAY claim content **atomless** — covered by the discipline but contributing no
   atoms — for content that carries no independent contract: derived representations whose
   interface it already carries through other claimed content (a Scala discipline claims
   `.class`, `.sjsir` and `.nir` files atomless, since the TASTy it atomizes is their
   interface), or content deliberately outside the API surface (scanned resource directories,
   §11.4). Content claimed by no discipline falls to `opaque/1` (§11.3).
2. **Atomization**: claimed content → a set of atoms, each with key, class, value hash, obeying
   the folding principle (§10.3). Atomization MUST be a pure function of the content's semantic
   model: independent of file ordering, compilation timestamps, tool version strings, fresh-name
   generation, and any other artifact of a particular compilation run. Producers MUST be able to
   reproduce identical atom sets from identical sources (§17).
3. **Replaceability soundness**: for every replaceable atom, everything its content refers to
   that consumers will need at _their_ compile time must itself be rigid-atomized, so that
   replacing the atom within a lineage can never produce a linkage failure.
4. **References**: for each replaceable atom, the atoms (own-module or cross-module) that its
   content depends on — the input to used-set closure (§13.4). References are emitted
   **symbolically, by atom key**: a cross-module value hash is not computable from one module's
   content alone, so disciplines name what they reference, and assembly-time tooling resolves
   names to value hashes against the dependencies' Atoms listings (§10.4) by exact key match.

### 11.3 Registered Disciplines

This specification registers three disciplines:

- **`opaque/1`** (normative): the entire content item is a single **rigid** atom whose key is
  its path and whose canonical encoding is its bytes. Any change is therefore a removal plus an
  addition — a major event. `opaque/1` is the REQUIRED default for content no other discipline
  claims: nothing in a LIRA file is ever outside the compatibility algebra; unknown content is
  merely maximally conservative.
- **`resource/1`** (normative; §11.4): resources declared in the manifest — presence-guaranteed
  exports, content-tracked resources, and scanned directories claimed atomless.
- **`scala-tasty/1`** (informative here; normative specification in a companion document): the
  Scala discipline sketched in Appendix A.

Anticipated future disciplines include declaration-surface disciplines for TypeScript (`js-dts`),
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
  certifies behavior (§18). Because the atom is content-independent, the cross-universe
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

### 12.4 The Derived Version

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
- **`build`**, a development-time pin to an exact implementation identity (§6): the candidate
  must additionally have exactly that `payload.hash`. Build pins express "this exact unpublished
  build" during development; a manifest carrying one is itself unpublishable (**L118**, §12.4).

Dependency requirements are per-release facts: a module's dependency graph — which modules it
names, at which snapshots, in which universes — may change freely between releases at any
grade. Grades (§12.3) constrain only the module's own atom set; consumer safety under a changed
graph comes from re-validating the buildpath (§13.3), never from the grade.

### 13.3 Validity

A buildpath is valid **for a universe** iff all of the following hold, and each is decidable
from manifests alone. Closure and compatibility quantify over the dependency records
*applicable to that universe* (§13.2); uniqueness, namespace disjointness and resource
disjointness are global.

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
6. **Toolchain coherence**: ecosystem profiles MAY impose additional predicates over the
   `toolchain` records (for example, mutual readability of metadata format versions). The base
   specification imposes none.

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

From a valid buildpath and a chosen universe, a consumer derives a conventional artifact set
(e.g. a classpath) by, per release: selecting the section for that universe (a release lacking
one is a validation-time error, not a link-time surprise), materializing it per §9.3 into a
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
the release, its API identity, and its whole compatibility context. Materialization caches
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
  description  A discipline identifier, e.g. scala-tasty/1.
  validate     discipline-id

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
  field uses Hash optional              # Uses metadata blob
  field spans Hash optional repeatable  # snapshots provably spanned (§13.4)

record Section
  select  Universe
  field   against     Hash  optional  repeatable    # variant dependency snapshots (§9.5)
  field   tree        Hash                          # Tree metadata blob
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
  field dependency Dependency optional repeatable
  field delta Hash optional             # Delta metadata blob for this lineage step
  field section Section repeatable     # first section = root (§9.1)
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
4. re-atomizes content under each declared discipline and compares against the Atoms blobs, and
   checks the cross-universe invariant (§9.6) — requires an implementation of each discipline
   (though `opaque/1` and `resource/1` are language-blind and implementable by every verifier);
5. recomputes the snapshot and checks it equals the last lineage entry (§12.1, **L109**);
6. given the predecessor release, checks the lineage step's grade and delta (§12.3);
7. recomputes the signing domain and verifies each signature (§15): a signature that does not
   verify (**L121**), whose algorithm the verifier does not implement (**L122**), or whose key
   fingerprint matches no trusted key (**L123**) fails the file.

Steps 0–3, 5 (given the Atoms blobs) and 7 require no language knowledge and SHOULD be performed
at installation. Steps 4 and 6 are publish-time checks: a registry MUST perform them before
accepting a release, since they are what make manifests trustworthy at use-time. Every claim in
a manifest is thus either recomputable locally or attested by signature over recomputable
claims; nothing is trusted testimony.

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
- **Behavioral compatibility**: no hash scheme certifies that unchanged signatures have
  unchanged behavior. Patch and minor grades bound _interface_ and _copied-content_ change;
  behavior remains the publisher's promise, mitigated by signatures and (out of scope here)
  attestation of test evidence.

## Appendix A (Informative): The Scala Discipline `scala-tasty/1`

The Scala discipline is specified normatively in the companion document
[`scala-tasty.md`](scala-tasty.md); this appendix is an orientation. Atomization is performed
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
- TASTy-level and classfile-level compatibility diverge in both directions; `scala-tasty/1`
  atomizes the TASTy level, and the companion specification will state the additional
  classfile-level invariants a publisher must preserve (bridge/forwarder-affecting changes) that
  the JVM ecosystem profile checks with bytecode-level tooling.

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
  discipline scala-tasty/1
  atoms Vw12…

api
  discipline resource/1
  atoms Wz34…

# module              # api     # version
dependency anticipation-core      Ab12…     0.64.0
dependency rudiments-core         Cd34…     0.64.1

delta Xy56…

section jvm
  tree Ef56…
  derivative Tu78…
section sjsir
  tree Gh78…
section nir
  tree Ij90…
  delete gossamer/JvmOnly.class

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
universes it supports; that the `nir` view omits one root file; that the resource
`gossamer/text-tables.conf` is contractually present on every universe's classpath; the hash of
the classpath JAR its `jvm` section derives; and everything needed to verify the file's
integrity and authorship — all without decompressing a byte of the payload.

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
