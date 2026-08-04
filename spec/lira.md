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
change. The Scala discipline (Appendix A) and the JVM ecosystem profile (Appendix C) will each be
specified normatively in a companion document.

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
- **Universe**: a compilation target family identifying the kind of executable representation a
  section holds (e.g. `jvm`, `js`, `native`). The universe vocabulary is open (§9.4).
- **Section**: one compiled view of the release, keyed by universe and optionally by a
  dependency vector (§9.5), stored as a tree of blobs.
- **Blob**: a byte string in the payload, identified by its hash (§7).
- **Atom**: the unit of API compatibility: a hash over the canonical encoding of one indivisible
  fragment of a module's public interface (§10).
- **Discipline**: a named, versioned canonicalization procedure that converts content into atoms
  (§11).
- **Guarantee level**: what a compatibility claim certifies — linkage, recompilation, or
  behavior (§11.4).
- **Profile**: a named, versioned set of predicates an ecosystem imposes over releases and
  buildpaths in addition to those of this specification (§11.5).
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

`<discipline>` is the full discipline identifier including its version (§11.1), e.g.
`tasty/1`. Because the discipline identifier participates in the domain, atoms produced by
different disciplines — or by different versions of one discipline — can never collide or alias.

The `lira/1` prefix is the **format epoch**. Any future revision of this specification that
changes the meaning of any hashed encoding MUST change the epoch, invalidating no existing hash
but guaranteeing that old and new hashes never mix silently.

## 8. Payload

### 8.1 Compression Envelope

The payload is a single Brotli stream. Producers MUST use quality 11, window size lgwin 24, and
generic mode, so that payload bytes are a deterministic function of the blob stream (§17).
Readers MUST enforce `payload.length` (the declared decompressed size, §14) as a hard limit
during decompression and MUST reject a payload that exceeds it (**L102**) — this bounds
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

A section is one compiled view of the release: a mapping from paths to blobs. Exactly one
section per universe is designated the **root section** implicitly by the overlay rules below;
for the motivating ecosystem the `jvm` section is the root, holding the representation that is
also valid as a conventional artifact of its ecosystem.

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
once, by the root. A `delete` path not present in the root, or an overlay entry whose path and
blob both equal a root entry, is invalid (**L107**): overlays are minimal by construction, which
makes divergence between platforms _visible_ in the manifest rather than buried in the payload.

### 9.4 Universes

The base schema (§14) defines universe variants `jvm`, `js`, and `native`. The vocabulary is
open: new universes are introduced by TEL schema layers, which may append variants to a select
but never remove them. A consumer knowing only the base schema can still parse a layered
manifest (TEL §8.2); it MUST treat sections of unknown universes as opaque and MUST NOT attempt
to materialize them.

### 9.5 Variant Sections

A section MAY carry `against` entries: the API snapshot hashes of dependency releases it was
compiled against, when these differ from the release's declared dependency list. This supports
carrying compilations against multiple major versions of a dependency in one file. Sections
without `against` are compiled against the manifest's own `dependency` list. Producers SHOULD
prefer proving that a single compilation spans multiple dependency majors (§13.4) over emitting
variant sections; variants are the fallback for genuine incompatibility.

### 9.6 Cross-Universe API Invariant

For each discipline, the atom set MUST be identical across every universe in that discipline's
domain (§11.2) for which the release carries a section (**L108**): a release presents one API on
every universe it supports. Implementations may differ per universe; interfaces may not.
Producers MUST verify this at assembly time by atomizing each such universe's content
independently and comparing. (A library whose API genuinely differs by platform is two modules.)

The qualification to the discipline's domain is load-bearing. A discipline may be **universal** —
`tasty/1` atomizes a representation carried in every Scala universe, so the invariant binds all of
them, and enforcing it is the whole point — or **universe-specific**, atomizing a representation
that exists in one universe only. A bytecode-level discipline over classfiles has no counterpart
in `js` or `native`; for it the invariant is vacuous, not violated. Without this qualification a
universe-specific discipline could never be declared, since atomizing the universes outside its
domain yields the empty set and no release could satisfy an unqualified L108.

A release MUST NOT declare an `api` record whose discipline's domain is disjoint from the
universes it carries sections for (**L117**): an atomization of nothing is not a claim about
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
   discipline whose domain holds more than one universe is bound by the cross-universe invariant
   (§9.6) across the whole domain; one whose domain is a single universe is not bound by it at
   all.
2. **Atomization**: content → a set of atoms, each with key, class, value hash, obeying the
   folding principle (§10.3). Atomization MUST be a pure function of the content's semantic
   model: independent of file ordering, compilation timestamps, tool version strings, fresh-name
   generation, and any other artifact of a particular compilation run. Producers MUST be able to
   reproduce identical atom sets from identical sources (§17).
3. **Keying**: whether atom keys denote **declarations** — each member atomized once, under the
   type that declares it — or **memberships** — each member atomized under every type that
   presents it, after inheritance and linearization. The choice is constrained: if consumers of a
   type can, in any universe of the domain, depend on a member that type _inherits_ rather than
   declares, that member MUST be atomized under the inheriting type's key. Declaration keying is
   sound only where every reference to an inherited member resolves through the declaring type in
   every universe of the domain. The choice determines whether a change to a supertype in another
   module registers in this module's own atom set or only in its used-set (§13.4), and disciplines
   MUST state it.
4. **Replaceability soundness**: for every replaceable atom, everything its content refers to
   that consumers will need at _their_ compile time must itself be rigid-atomized, so that
   replacing the atom within a lineage can never produce a linkage failure.
5. **References**: for each replaceable atom, the set of atoms (own-module or cross-module) that
   its content depends on — the input to used-set closure (§13.4).
6. **Certified guarantees**: which of the guarantee levels of §11.4 its rigid atoms certify, for
   each universe in its domain. A discipline MUST NOT claim a level it does not enforce. Where an
   ecosystem's linkage model imposes obligations the discipline's canonical encoding does not
   cover, those obligations belong to a profile (§11.5) and MUST NOT be claimed here.

### 11.3 Registered Disciplines

This specification registers two disciplines:

- **`opaque/1`** (normative): the entire content item is a single **rigid** atom whose key is
  its path and whose canonical encoding is its bytes. Any change is therefore a removal plus an
  addition — a major event. `opaque/1` is the REQUIRED default for content no other discipline
  claims: nothing in a LIRA file is ever outside the compatibility algebra; unknown content is
  merely maximally conservative. Its domain is every universe; its keying is by declaration
  (paths); it certifies linkage and recompilation trivially, since it admits no change at all
  below the major grade.
- **`tasty/1`** (informative here; normative specification in a companion document): the
  Scala discipline sketched in Appendix A. Its domain is every universe whose section carries
  TASTy — `jvm`, `js` and `native` in the motivating ecosystem — and the cross-universe invariant
  over that domain is what makes "one API on every platform" checkable. Its keying is by
  membership, for the reason given in Appendix A. It certifies **recompilation** on its whole
  domain, and linkage only at the TASTy level; the classfile-level linkage obligations of the
  `jvm` universe are not its and belong to the JVM profile (Appendix C).

Anticipated future disciplines include declaration-surface disciplines for TypeScript (`dts`),
Java classfile signatures, and Kotlin metadata. Foreign content — JavaScript modules resolved at
link time, C sources compiled by a downstream linker, and so on — is admissible in any section
today under `opaque/1`.

### 11.4 Guarantee Levels

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

### 11.5 Ecosystem Profiles

An **ecosystem profile** is a named, versioned set of predicates that an ecosystem imposes over
releases and buildpaths in addition to those of this specification. It is identified as
`<name>/<version>` on the same terms as a discipline (§11.1), and a profile MUST increment its
version on any change to a predicate. A profile may:

1. impose predicates over `toolchain` records — for example, mutual readability of a metadata
   format version across a buildpath (§13.3);
2. impose structural invariants over a universe's content that are not expressible as atoms,
   checked by tooling that reads the representation directly — the bytecode-level checks a JVM
   ecosystem needs are the motivating case;
3. certify a guarantee level (§11.4) that the release's disciplines do not.

Profiles MUST NOT weaken any requirement of this specification (**L119**). They add predicates
and they add guarantees; they never subtract. A file rejected by the core is rejected under every
profile, and a grade forbidden by §12.3 is forbidden under every profile.

A release declares the profiles it claims to satisfy in its `profile` records (§14). The claim is
verifiable, not decorative: a release that declares a profile whose predicates it violates is
invalid (**L118**), and a registry MUST check declared profiles before accepting a release
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

with hashes sorted ascending bytewise and concatenated as raw 32-byte values.

### 12.2 Lineage

The manifest's `lineage` field lists the snapshots of the module's releases in one major series,
oldest first; the final entry MUST equal the release's own snapshot (**L109**). The lineage is
the module's verifiable version history: every compatibility question in this specification
reduces to membership in, or relations between, lineages.

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

### 12.4 Grades and Guarantee Levels

The grades above are computed from atom sets and are therefore claims at the guarantee levels the
release's disciplines certify (§11.2, §11.4) — for a discipline over source-level metadata, the
recompilation level. A profile (§11.5) may certify a further level, and a step may satisfy the
algebra at one level while failing a profile predicate at another. Such a step is **not**
promoted to major by the core: rigid monotonicity is a statement about atoms, and the atoms are
intact.

Instead the release records the shortfall. A release whose lineage step satisfies §12.3 but whose
predecessor's guarantees are not preserved at some level a declared profile certifies MUST list
that level in the profile record's `breaks` field (**L120**). The reading of

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

Each lineage step SHOULD be accompanied by a **Delta metadata blob** recording the added atoms
and the replaced replaceable atoms of that step. Deltas make staleness computable (§13.4) and
allow a verifier holding consecutive releases to check a lineage step exactly.

### 12.5 The Decorative Version

The manifest's `version` field (`x.y.z`) is a human-readable projection: `x` names the lineage
(major series), `y` SHOULD equal the number of minor steps in the lineage, `z` counts patches
since the last snapshot change. The projection carries no authority; every consumer decision is
made on hashes. A mismatch between `version` and the lineage structure is a warning, not an
error.

## 13. The Buildpath

### 13.1 Definition

A buildpath is a set of `.lira` files intended for joint use. It is unordered: the coherence
rules below make ordering irrelevant, unlike traditional classpaths.

### 13.2 Dependency Requirements

Each `dependency` record in a manifest names a module and a **required snapshot** — an API
identity the depending module was compiled against. A candidate release **satisfies** the
requirement iff the required snapshot appears in the candidate's `lineage`.

### 13.3 Validity

A buildpath is valid iff all of the following hold, and each is decidable from manifests alone:

1. **Uniqueness**: at most one release per module name (**L111**).
2. **Namespace disjointness**: the `owns` claims (namespaces such as packages, per-ecosystem
   interpretation) of distinct modules are pairwise disjoint (**L112**).
3. **Closure**: every module named by any `dependency` record is present (**L113**).
4. **Compatibility**: every dependency requirement is satisfied per §13.2 (**L114**). Diamond
   dependencies resolve by construction: requirements on two snapshots of one module are jointly
   satisfiable iff some published lineage contains both — the incompatible-major case is exactly
   the case where none does.
5. **Profile coherence**: every ecosystem profile (§11.5) declared by any release on the
   buildpath imposes its predicates over the whole buildpath, including any predicate over
   `toolchain` records — for example, mutual readability of metadata format versions. The base
   specification imposes none of its own, and a buildpath whose releases declare no profile is
   subject to rules 1–4 alone. Profile predicates, like the rules above, MUST be decidable from
   manifests; a profile predicate requiring payload inspection is a publish-time check (§16), not
   a buildpath rule.

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
runtime the universe requires. Reconstruction of a standalone per-platform archive (e.g. a
platform jar) is the same materialization serialized to the ecosystem's container format.

## 14. Manifest Schema

The `lira` TEL schema. (The companion schemas `lira-tree`, `lira-atoms`, `lira-uses`, and
`lira-delta` for metadata blobs are structured per §9.2, §10.4, §13.4 and §12.3; their full
definitions follow the same conventions and are elided in this draft.)

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
  description  A guarantee level (§11.4): linkage or recompilation.
  validate     guarantee

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

record Dependency
  field module ModuleName
  field api Hash                        # required snapshot (satisfied by lineage membership)
  field version Semver optional         # human-readable hint; no authority
  field uses Hash optional              # Uses metadata blob
  field spans Hash optional repeatable  # snapshots provably spanned (§13.4)

record Section
  select  Universe
  field   against  Hash  optional  repeatable    # variant dependency snapshots (§9.5)
  field   tree     Hash                          # Tree metadata blob
  field   delete   String  optional  repeatable  # root paths removed in this overlay

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
  variant  jvm     Flag
  variant  js      Flag
  variant  native  Flag

document
  field module ModuleName
  field version Semver
  field lineage Hash repeatable         # snapshots, oldest first; last = this release
  field toolchain Tool repeatable
  field owns Namespace optional repeatable
  field api Api repeatable
  field profile Profile optional repeatable
  field dependency Dependency optional repeatable
  field delta Hash optional             # Delta metadata blob for this lineage step
  field section Section repeatable
  field payload Payload
  field signature Signature optional repeatable
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

`key` is `hash("lira/1:key", public-key-bytes)` using the algorithm's standard public-key
encoding. Public-key distribution is out of band (a registry of authorized signers, analogous to
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
3. checks every tree's path rules and every overlay's minimality (§9.2–§9.3);
4. re-atomizes content under each declared discipline and compares against the Atoms blobs,
   checks the cross-universe invariant over each discipline's domain (§9.6) and that no declared
   discipline is inapplicable (**L117**) — requires an implementation of each discipline;
5. recomputes the snapshot and checks it equals the last lineage entry (§12.1, **L109**);
6. given the predecessor release, checks the lineage step's grade and delta (§12.3);
7. checks the predicates of each declared profile, and — given the predecessor release — that
   every guarantee level the step fails to preserve is listed in that profile's `breaks` field
   (§11.5, §12.4, **L118**, **L120**) — requires an implementation of each profile;
8. recomputes the signing domain and verifies each signature (§15).

Steps 0–3, 5 (given the Atoms blobs) and 8 require no language knowledge and SHOULD be performed
at installation. Steps 4, 6 and 7 are publish-time checks: a registry MUST perform them before
accepting a release, since they are what make manifests trustworthy at use-time. A registry that
cannot implement a declared discipline or profile MUST reject the release rather than accept it
unchecked — an unverifiable claim is worse than an absent one, because consumers cannot tell the
two apart from the manifest. Every claim in a manifest is thus either recomputable locally or
attested by signature over recomputable claims; nothing is trusted testimony.

## 17. Determinism

Producing a release twice from identical inputs MUST yield byte-identical `.lira` files. To that
end: all orderings in this specification are total (blobs by hash; tree entries by path; atoms
by value hash; lineage by history); the interpreter directive is a fixed string (§5.1); Brotli
parameters are pinned (§8.1); no timestamps exist anywhere in the format; atomization is
required to be run-independent (§11.2); and manifests generated by tools MUST use LF endings and
canonical TEL formatting. Determinism is what makes
the implementation identity meaningful and allows independent parties to reproduce and attest a
release.

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
  profiles certify (§11.4), and at no others. Tools presenting a grade to a user SHOULD present
  the level with it; a "minor" reported without its level invites a consumer relying on linkage
  to act on a claim that was only ever about recompilation.
- **Behavioral compatibility**: no hash scheme certifies that unchanged signatures have
  unchanged behavior. Patch and minor grades bound _interface_ and _copied-content_ change;
  behavior remains the publisher's promise, mitigated by signatures and (out of scope here)
  attestation of test evidence.

## Appendix A (Informative): The Scala Discipline `tasty/1`

Atomization is performed over TASTy — never over raw TASTy bytes, which embed tool version
strings, but over a canonical re-encoding of the semantic model (fully-qualified references,
erased-signature overload disambiguators, alpha-normalized local names, API-relevant flags and
annotations only, members sorted). Illustrative decomposition, applying the folding principle:

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
  the JVM profile's (Appendix C), not the discipline's.

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

### A.3 Keying: Why `tasty/1` Keys by Membership

Under §11.2's keying requirement `tasty/1` MUST key by membership: an inherited concrete member
is atomized under every type that presents it, not only under the type that declares it. The
reason is a JVM fact rather than a Scala one. A consumer's call to `c.foo()`, where `C` inherits
`foo` from `T`, compiles to an invocation naming `C` (or an interface in `C`'s hierarchy) — so
`C`'s _linkage_ surface includes members `C` does not declare, and a declaration-keyed atom set
would let `C`'s presented interface change while every atom keyed to `C` stayed fixed.

Membership keying costs redundancy — a member of a widely-inherited trait appears in many atoms
— and buys a genuine property: the atom set of a type is the interface of that type, closed. It
also interacts well with §13.4: a consumer's used-set names the atom under the type it actually
referenced, so staleness and spanning are computed against the type the consumer named, not the
type an implementation detail happened to place the member on.

## Appendix B (Informative): Worked Example

```text
#!/usr/bin/env lira
tel 1.0 <lira schema signature>

module gossamer-core
version 0.64.2
lineage Kx3f…  Lm81…  Pq44…

toolchain
  name scala
  version 3.9.0-RC4-p6

owns gossamer

api
  discipline tasty/1
  atoms Vw12…

profile
  id jvm/1
  breaks linkage

# module              # api     # version
dependency anticipation-core      Ab12…     0.64.0
dependency rudiments-core         Cd34…     0.64.1

delta Xy56…

section jvm
  tree Ef56…
section js
  tree Gh78…
section native
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
universes it supports; that the native view omits one root file; that the last step, though a
minor by the atom algebra, did not preserve JVM linkage, so consumers holding compiled bytecode
against `Lm81…` must recompile while consumers who build from source need do nothing; and
everything needed to verify the file's integrity and authorship — all without decompressing a
byte of the payload.

## Appendix C (Informative): The JVM Ecosystem Profile `jvm/1`

The `jvm` universe is the case that motivates profiles, because it is the case where linkage and
recompilation most visibly come apart. This appendix sketches what `jvm/1` must cover; like
Appendix A it is informative, and a companion document will state it normatively.

### C.1 The Two Levels Diverge in Both Directions

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

Neither level subsumes the other, which is why `jvm/1` is a profile predicate (§11.5) rather
than extra atoms: the two claims have different audiences and must be reportable separately.

### C.2 What `jvm/1` Checks

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

### C.3 Can Classfiles Be Regenerated From TASTy?

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
distinction §11.4 draws and precisely what `breaks linkage` records — so the honest treatment of
regeneration is not to attempt it, but to grade with it in mind.
