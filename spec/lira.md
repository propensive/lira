# LIRA Specification Draft

## Abstract

LIRA (Library IR Archive) is a language-agnostic artifact format for distributing compiled
software, and an algebra for answering — from metadata alone — the question that haunts every
composition of independently-published parts: _will these work together?_ The question arises
at two moments. At **build time**, libraries meet on a buildpath and must present compatible
interfaces. At **deploy time**, running artifacts meet in an environment and must honor
compatible contracts. LIRA gives both occasions a single answer: they are one question asked
with two polarities: a release **provides** an interface, expressed as a set of hashed atoms,
and **requires** capabilities of its surroundings, satisfied by the same set relations that
grade the interface's evolution.

A single `.lira` file carries the compiled representations of one release — for a library,
every platform's view of it (for example, JVM classfiles, TASTy, Scala.js IR and Scala Native
IR), deduplicated within one container; for a deployable service, its closed artifact or a pin
to one — together with a manifest — stored in canonical binary form, rendered as readable TEL
text by any conforming tool (§5) — machine-verifiable API-derived version metadata, and
quantum-safe signatures.

LIRA defines:

- a **container format**: a fixed four-byte header, a BinTEL manifest under the published
  `lira` schema — the base, or the base plus published layers — and a Brotli-compressed
  content-addressed payload, each beginning where the last ends;
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
- **host contracts**, by which the capability interface of a runtime environment — a JDK, a
  browser, Node, an Android API level, a WASI world — is published as a release of its own, and
  a release's per-section `requires` — a library's on its host, a deployable's on its
  environment — is satisfied by the same algebra as any dependency ([`hosts.md`](hosts.md));
- **deployable releases**, by which a closed artifact — an executable JAR, a container image —
  is published in the same container format, carrying the interface it serves and the
  capabilities it requires ([`services.md`](services.md));
- the **buildpath**: a composition of LIRA files whose coherence — including diamond-dependency
  resolution — is decidable from manifests alone, without reading any payload;
- the **environment**: the buildpath's runtime counterpart — an operator-signed release of
  the `env` realm whose manifest states a desired state: the platform contracts granted, the
  releases deployed, and the addresses each provider is bound to — with coherence, including
  the deployability of a new release into it, decidable from manifests on the same terms
  (§13.7, [`services.md`](services.md), [`environments.md`](environments.md)).

A `.lira` file thus has three consumers: the **compiler**, which composes it on a buildpath;
the **deployer**, which composes it into an environment; and the **runtime**, for which the
same manifests stay live for as long as anything runs — every subsequent deploy into the
environment, every readiness probe, and every drift report is judged against them (§13.7,
[`hosts.md`](hosts.md) §9).

The primary motivating ecosystem is Scala (JVM, Scala.js, Scala Native), and the motivating
deployment case is the microservice environment; but no normative part of this specification is
specific to either. Language-specific material appears only in informative appendices.

## 1. Status

This document is a working draft. Numbered requirements and the schema in §14 are expected to
change. The companion documents are normative for their subjects: the Scala discipline
([`tasty.md`](tasty.md)), the JVM bytecode discipline ([`classfile.md`](classfile.md)), the
TypeScript declaration discipline ([`dts.md`](dts.md)), the OpenAPI discipline
([`openapi.md`](openapi.md)), the JVM ecosystem profile ([`jvm.md`](jvm.md)), host contracts
and requirements ([`hosts.md`](hosts.md)), deployable releases and environment validity
([`services.md`](services.md)), and environment releases, bindings and provisioning
([`environments.md`](environments.md)).

## 2. Conformance Language

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD
NOT**, **RECOMMENDED**, **MAY**, and **OPTIONAL** in this document are to be interpreted as
described in RFC 2119 and RFC 8174 when, and only when, they appear in all capitals.

## 3. Normative Dependencies

- **TEL** ([tel.md](https://github.com/propensive/tel/blob/main/spec/tel.md)) — the manifest's semantic model, schema language, and canonical text rendering (TEL §22.3).
- **BinTEL** ([bintel.md](https://github.com/propensive/tel/blob/main/spec/bintel.md)) — the stored form of the manifest and of every metadata blob, and the signing domain.
- **BASE-256** ([base256.md](https://github.com/propensive/tel/blob/main/spec/base256.md)) — textual encoding of hashes and signatures; also names the byte values of the magic numbers (§5.1).
- **BLAKE3** — the sole hash function of this specification, used with 256-bit output.
- **Brotli** (RFC 7932) — payload compression.
- **ML-DSA** (FIPS 204) — the default signature algorithm.

## 4. Terminology

### 4.1 The Taxonomy

The terms of this subsection categorize what a `.lira` file is _about_: representations, the
places where they compose, and the environments they run in. Common usage conflates them under
words like "platform" and "target"; this specification separates them, because the overlaps that
make a flat vocabulary feel impossible (one representation serving several runtimes; one runtime
executing several representations) dissolve once each thing is named by its **role**.

- **Format**: a concrete byte-level encoding of compiled or declared content — classfile, TASTy,
  SJSIR, NIR, Kotlin metadata, `.d.ts`, ES module, WASM component, WIT. A format may occupy
  whole files (a classfile, a `.d.ts`) or ride within another format's files (Kotlin metadata
  travels in classfile annotations); either way it is the encoding, wherever its bytes are
  carried. A format has no intrinsic
  role: the same format can appear at different points of a pipeline with different meanings, so
  nothing in this specification is keyed by format.
- **Universe**: an axis along which independently-published libraries meet and compose,
  characterized by an **interface convention** (how a library's API is expressed to other
  libraries), a **linkage mechanism** (how library artifacts are later combined), and a
  **capability model** (what composed code may assume of its surroundings). The litmus test for
  universe-hood: _do independently-published libraries compose there?_ `jvm` passes — classloading
  composes arbitrary classfile sets; DEX fails — Android libraries ship classfiles, and dexing
  happens after the library phase closes; LLVM bitcode fails — no ecosystem publishes open-world
  libraries as bitcode. The universe vocabulary is open (§9.4).
- **Realm**: what a release's **sections** — its stored views of the release's
  content (§4.2, §9) — are keyed by. Every universe is a realm, and three realms are not
  universes: the `host` realm ([`hosts.md`](hosts.md)), which holds a host
  contract's content rather than a composable library representation; the `app` realm
  ([`services.md`](services.md)), which holds a closed, runnable artifact — content that is
  past composition rather than awaiting it; and the `env` realm
  ([`environments.md`](environments.md)), whose releases state environments. The non-universe
  realms divide the runtime world between them: `host` describes what an environment
  provides, `app` holds what runs on one, and `env` says which is where. The term avoids
  _world_, which this specification must use in WIT's own sense ([`wit.md`](wit.md)) and
  whose cosmological intuition inverts the containment needed here; a realm carries no
  instinct about what contains what.

- **Host**: a runtime environment that executes _closed_ artifacts — closed meaning past
  composition: the product of an egress (below), awaiting linking with nothing — exposing a
  versioned capability interface: the JVM at a JDK version, a browser with its Web APIs, Node with its
  builtins, the Android runtime at an API level, a WASI runtime with its world, an operating
  system with its libc and shell. A host is not a universe: nothing composes _in_ it; artifacts
  run _on_ it. A **running service is a host to its consumers**: its versioned capability
  interface is its network API, which its own release publishes as its own atoms
  ([`services.md`](services.md)) — the observation from which the whole deployment story of
  this specification follows.

- **Host contract**: the published, verifiable statement of a host's capability interface — an
  ordinary release whose atoms are capabilities ([`hosts.md`](hosts.md)). Host contracts bring
  hosts under the compatibility algebra — versioned by lineage, required by `requires`, satisfied
  by lineage membership — while remaining a distinct axis from dependencies (hosts.md §8). Why a
  grown contract still satisfies a requirement and a shrunk one stops — and how position flips
  the rule — is the polarity structure of §10.5.

- **Deliverable**: a pair of a closed artifact format and a host contract — an executable
  JAR on JDK ≥ 21, an APK on Android API ≥ 26, an ES-module bundle in a baseline browser, a WASM
  component in a WASI 0.2 world, that component packaged as a Wasm OCI Artifact for a runtime
  that schedules it from a registry, a native executable for one target triple. Two deliverables
  may share a format and differ only in how it is packaged, since what distinguishes them
  is the host they reach. Deliverables are what _builds_ produce; the word names a
  classification, not a thing, and in particular not a **deployable release** (§4.2), which is
  a `.lira` file. A deliverable is never stored as
  composable content; a deployable release stores or pins exactly one, in its `app`
  section, as closed content (§9.4, [`services.md`](services.md)).
- **Egress**: a linking edge from a universe to a deliverable: it closes over a buildpath's
  artifacts in that universe and produces the deliverable's artifact. One universe may have many
  egresses (`sjsir` egresses to JS bundles, to browser WASM, and to WASI components) — which is
  why a library stores its representation once and never chooses its deliverable. An egress is
  an edge of this taxonomy, not a tool: invoking the compilers and linkers that realize one is
  the build's business (§13.5). And what a
  build then does to a deliverable's artifact — repackaging it as an OCI artifact, appending it to
  a launcher stub — is not an egress: it reads no `.lira` file and closes over nothing, though
  its product may be exactly what a deployable release stores or pins (§9.4).
- **Join**: the point where two universes' contributions merge into one application — a bundler
  linking Scala.js output with TypeScript-compiled modules; a system linker combining native
  objects with C libraries. Joins are what make cross-universe dependencies meaningful (§13.2).

- **Ecosystem**: the community and toolchain conventions surrounding one or more universes —
  the Scala ecosystem surrounds `jvm`, `sjsir` and `nir`; the web ecosystem, with npm and its
  bundlers, surrounds `js`; Kotlin multiplatform surrounds `jvm` and a klib universe of its
  own. An
  ecosystem is not a formal object of this specification; its formal projections are the
  ecosystem profile (§11.6) and the canonical container format of its derivative artifacts
  (§13.6).
- **Language**: a producer of formats. Languages deliberately have no formal role here: one
  language may present several interface carriers (Kotlin presents classfiles with their
  `@Metadata`, klib metadata, and JS output) and one carrier may serve several languages
  (classfiles are produced by Java, Scala and Kotlin alike; `.d.ts` declarations by TypeScript
  and by any compiler that targets JS and declares its types),
  which is why disciplines are named for the carrier they canonicalize, never for a language
  (§11.1).
- **Platform**: not a term of this specification. Common usage conflates a universe with a host
  ("the JVM platform" names both the `jvm` universe and the JVM host) and sometimes with an
  deliverable; wherever this specification's prose says "platform" informally, one of the
  precise terms above is meant and recoverable from context.
- **Compatible**: meaningful only relative to a **guarantee level** (§11.5) — linkage,
  recompilation, or behavior. Every compatibility claim in this specification names its level; a
  claim without one is an equivocation, not a claim.

The taxonomy's edges are now all in view: dependency edges compose libraries within a universe
(§13.2), joins merge universes, an egress closes a universe into a deliverable, and requirement
edges point from content — open or closed — to the providers it runs against (§9.4, §13.3). No
edge leads back out of a deliverable: closure is terminal, and nothing in this specification
turns a deliverable into composable content again.

### 4.2 Terms of the Format

- **Module**: a named library, a named host contract ([`hosts.md`](hosts.md)), or a named
  deployable service ([`services.md`](services.md)). A module has one API lineage and many
  releases.
- **Release**: one published `.lira` file for a module.
- **Section**: one compiled view of the release, keyed by realm and integration, stored as a
  tree of blobs.
- **Integration**: one alternative build context a release was built in — a dependency
  vector, a host target, or both (§9.5).
- **Assignment**: a choice of one integration per release on a buildpath, under which that
  buildpath's validity is decided (§13.3).
- **Blob**: a byte string in the payload, identified by its hash (§7).
- **Atom**: the unit of API compatibility: a hash over the canonical encoding of one indivisible
  fragment of a module's public interface (§10).
- **Discipline**: a named, versioned canonicalization procedure that converts content into atoms
  (§11).

- **Guarantee level**: what a compatibility claim certifies — linkage, recompilation, or
  behavior. The three are independent, not a hierarchy (§11.5).
- **Profile**: a named, versioned set of predicates an ecosystem imposes over releases and
  buildpaths in addition to those of this specification (§11.6).
- **Snapshot**: the hash identifying a release's complete API — the hash of its sorted atom set
  (§12.1).
- **Lineage**: the ordered list of snapshots of a module's releases within one major series
  (§12.2).
- **Buildpath**: a set of `.lira` files intended to be used together (§13).

- **Deployable release**: a release carrying `app` sections: a closed artifact, or a pin to
  one held natively in a foreign content-addressed store (the `artifact` pin, §9.4), together
  with the interface it serves and the capabilities it requires (§9.4,
  [`services.md`](services.md)).

- **Environment**: the runtime counterpart of the buildpath: a set of deployable releases and
  host contracts intended to run together, stated as the desired state of an environment
  release in the `env` realm (§9.4, §13.7, [`environments.md`](environments.md)).

- **Deploy**: a transition of an environment; a release is deployable into an environment iff
  the transition preserves the environment's validity (§13.7).

- **Address**: an environment's own kind of name — a DNS name or URL prefix — at which a
  provider answers, compared as authored on the `owns` precedent
  ([`environments.md`](environments.md)). Other kinds of name — a filesystem path, a queue
  name — are deliberately not admitted by the base schema; an ecosystem needing one can
  introduce it as a schema layer, on the same compared-as-authored terms.
- **Binding**: an environment release's association of an address with a provider module and
  a release selection: the record that disambiguates at run time what uniqueness (§13.3 rule 1)
  disambiguates at build time ([`environments.md`](environments.md)). How a rebinding reaches
  running consumers is deliberately not abstracted here: validity re-judges the new desired
  state, provisioning re-evaluates its table, and reconciliation is the orchestrator's
  business (§13.7).
- **Provisioning**: evaluating a valid environment's satisfaction relation into a table from
  each requirement to the binding that answers it (§13.7,
  [`environments.md`](environments.md) §6) — the runtime analog of materializing a valid
  buildpath's sections onto artifact paths (§9.3, §13.5).
- **Operator**: the party who authors and signs an environment release — the third signing
  role of the format, beside the publisher (§15) and the index
  ([`distribution.md`](../design/distribution.md)).
- **Composition**: the genus of the buildpath and the environment: a set of releases together
  with granted contracts, under a validity judgment decidable from manifests alone (§13.3,
  §13.7) — the environment species being itself published, as an environment release
  ([`environments.md`](environments.md)). The term names the shared judgment and nothing more — universes compose artifacts,
  environments compose processes ([`services.md`](services.md) §2.1) — and the two species
  differ in their edges and their ends: dependency edges compose content that an egress
  **closes over**; requirement edges cohere a state that nothing ever closes over, whose
  coherence is closure (§13.3 rule 4, §13.7) sustained. A definition language may present one
  surface syntax for both edge kinds; the compiled records must remain distinct, since their
  difference (hosts.md §8) is what the deployment algebra stands on.

## 5. File Structure

A `.lira` file consists of, in order:

1. the **header**: the four bytes `B2 B9 B2 BB` — the LIRA magic number, the characters
   `βιβλ` under BASE-256: the Greek root of _library_, which is what the L stands for;
2. the **manifest**: a BinTEL document (BinTEL §6) in **external-schema mode**, conforming
   to the `lira` schema (§14) — the base schema, or the base composed with published layers;
3. the **payload**: the Brotli-compressed blob stream (§8), beginning at the byte immediately
   following the manifest and extending to the end of the file.

### 5.1 Layout and Framing

Because the manifest opens with BinTEL's own external-schema magic `βτελ`, the first eight
bytes of every `.lira` file are fixed: `B2 B9 B2 BB B2 C4 B5 BB` — `βιβλβτελ` under
BASE-256. A file that does not begin with these eight bytes is invalid (**L115**) — a
self-contained-mode manifest (`βτεμ`) included, since the composed schema is a published
constant, never the file's to supply (§5.2). Every byte of the header is at or above `0x80`,
so a `.lira` file cannot be mistaken for ASCII or UTF-8 text; and the LIRA magic, distinct
from BinTEL's, is what file-type detection keys on — a bare BinTEL document is not a `.lira`
file. Nothing precedes the header — no interpreter directive, no byte-order mark. A `.lira`
file is a binary file; producers MUST NOT set the executable permission bit.

No length and no separator delimit the manifest, and none is needed: a BinTEL document is
sized at every level of its structure (BinTEL §6, §7.7), so a reader holding the composed
schema decodes it to its exact extent — and a reader not holding the composed schema can do
nothing with the file at all (§5.2), so there is nobody left to serve with a
schema-independent boundary. A reader MUST obtain the manifest by decoding one BinTEL
document from the fifth byte, treating the first unconsumed byte as the start of the
payload rather than as a framing error: BinTEL's trailing-bytes rule is LIRA's to apply,
and LIRA applies the payload's own rules to it (§8). A file whose manifest does not decode,
or decodes to a document not conforming to the `lira` schema, is invalid (**L101**); a file
that ends at or before the end of its manifest carries no payload and is invalid (**L116**).

### 5.2 Schema Resolution

The manifest carries its schema **signature**, never a schema: the composed schema is not
the file's to define. It is the published `lira` base — normative in §14 and compiled into
every conforming implementation, exactly as the four metadata-blob schemas are (§8.3) —
alone, or composed with **published layers**, each a signed, lineage-versioned `tels/1`
release (§14, [`tels.md`](tels.md)) obtained on the same terms as any release, typically
from the registry that served the file. A reader resolves the signature against the schemas
it holds; a signature it cannot resolve makes the file **unreadable to that reader**, who
MUST fail, naming the signature, rather than guess. Unreadable is not invalid: the file may
conform perfectly to a schema the reader has yet to obtain. A registry MUST NOT accept a
release whose composed schema it does not hold (§16, **L140**), so the registry that serves
a file can always serve, or name, the layers reading it needs.

### 5.3 Presentation and Media Type

The stored manifest is binary, but the format remains inspectable by construction: a tool
that can read a `.lira` file holds its composed schema (§5.2), and the canonical text
serialization (TEL §22.3) of the decoded semantic model is a complete TEL rendering of it.
The `lira` tool's behavior is out of scope for this specification, except that its default
action when invoked on a `.lira` file MUST at minimum present the manifest, and any TEL text
that it — or any conforming tool — emits as the manifest's rendering MUST be the canonical
serialization, so that two tools' renderings of one manifest are byte-identical. Throughout
this specification, manifests and metadata blobs are _shown_ in TEL text; the stored form is
always BinTEL.

The RECOMMENDED file extension is `.lira`; the media type is `application/lira`, whose
registration is anticipated rather than yet granted. Neither the extension nor the media
type distinguishes layered from unlayered manifests — the schema signature in the BinTEL
header does.

## 6. Identity

A release has two identities, serving different purposes:

- **API identity**: the current snapshot hash — the last entry of `lineage` (§12). Two releases
  with equal snapshots present byte-for-byte identical public APIs. Dependency requirements
  (§13.2) and provider requirements (§9.4, §13.3 rule 7) refer to API identity.
- **Implementation identity**: `payload.hash` — the hash of the decompressed payload (§8.4). This
  identifies the exact bits of the release and is the correct key for lockfiles, caches,
  reproducibility claims, and attestation.

A **patch** relationship holds between two releases iff their API identities are equal and their
implementation identities differ (§12.3).

## 7. Hashing

All hashes in this specification are 256-bit BLAKE3. A hash takes one of two forms, by
context: in TEL text — this specification's examples, and any tool's rendering (§5.3) — it is
BASE-256 encoded, 32 characters; in every binary context — the stored manifest and metadata
blobs (via the `base-256-hash` codec, §14), snapshot computation (§12.1), and the signing
input (§15.2) — it is the raw 32 bytes. The two forms are one value: BASE-256 is
character-for-byte, the codec is its strict inverse, and no context ever re-encodes another's
output.

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

Companion documents define further domains under the same epoch, reserved here so they can
never collide: `lira/1:leaf` and `lira/1:node` (the transparency log, distribution.md §4);
`lira/1:set-leaf` and `lira/1:set-node` (a node's published-set commitment, tool.md §7); and
`lira/1:manifest-bytes` (the raw bytes of a manifest as stored, served, and pinned by
distribution records, tool.md §2.2 — distinct from `lira/1:manifest`, which hashes the
canonical signing encoding and so identifies content rather than bytes). One companion
deliberately reserves none: an increment ([`increment.md`](increment.md)) has no identity
(**L156**) and is designated by the two payload hashes it connects.

## 8. Payload

### 8.1 Compression Envelope

The payload is a single Brotli stream. The compressed bytes contribute to **no identity** of
§6:
`payload.hash` (§8.4), the snapshot (§12.1) and every signature (§15.2) cover the
_decompressed_ blob stream, directly or transitively, so two files differing only in
compressor output are the same release. A producer MUST be **self-deterministic** — the same
toolchain over the same blob stream MUST emit the same compressed bytes, with the toolchain
recorded in the manifest (§14) — but no particular encoder's output is normative across
producers: cross-implementation bit-reproduction of a `.lira` file requires the same producer
toolchain (§17). Readers MUST enforce `payload.length` (the declared decompressed size, §14)
as a hard limit during decompression and MUST reject a payload whose decompressed length is
not exactly the declared value (**L102**) — the upper bound is what bounds
decompression-bomb exposure. A payload whose compressed bytes do not decode as a Brotli stream
is malformed (**L139**).

A release MAY also be transferred as an **increment** relative to a release the receiver
already holds ([`increment.md`](increment.md)): a file carrying this manifest verbatim and a
command stream that reconstructs this blob stream from the other's, changed blobs compressed
in the context of their predecessors. What it yields is the decompressed blob stream, verified
under this section and §8.2–§8.4 as any payload is (**L155**); the receiver's own envelope
over it is as good as the publisher's, which is exactly what this section's indifference to
compressed bytes provides for.

### 8.2 Blob Stream

The decompressed payload is the **blob stream**: a sequence of records, each of the form

```text
record = uvarint(length) ++ bytes
```

where `uvarint` is unsigned LEB128. Each record's bytes constitute one blob. The blob's identity
is `hash("lira/1:blob", bytes)`. A record whose length prefix is not a well-formed uvarint, or
which overruns the end of the stream, renders the payload malformed (**L139**). Records MUST be
sorted in ascending bytewise order of their blob hashes, and no two records may have equal
hashes (**L103**). Blob hashes are not stored in
the stream: a reader recomputes them while scanning, and this recomputation is the integrity
check. A blob referenced anywhere in the manifest or in a metadata blob that is absent from the
stream renders the file invalid (**L104**); unreferenced blobs are permitted but producers
SHOULD NOT emit them.

Content occurring in multiple sections is therefore stored exactly once, addressed by hash.

### 8.3 Metadata Blobs

Certain blobs are **metadata blobs**: BinTEL documents in **external-schema mode** (BinTEL
§6.1) conforming to small schemas defined alongside the `lira` schema. This specification
defines four:

- **Tree** (§9.2) — an entry table mapping paths to blobs for one section.
- **Atoms** (§10.4) — the atom listing of the release for one discipline.
- **Uses** (§13.4) — a used-atom set with respect to one dependency.
- **Delta** (§12.3) — the atom-level change record for one lineage step.

Metadata blobs are ordinary blobs: content-addressed, deduplicated, and hashed under
`lira/1:blob`. External-schema mode is the format's only mode (§5.1), and for metadata blobs
resolution is even simpler than §5.2's: the four schemas are normative constants of §14, so
a reader compares a blob's schema signature against four known values; a blob whose
signature matches none of them is malformed (**L139**). Pinning one mode is also what keeps
blob identity canonical — the two modes differ in bytes, and one document with two hashes
would defeat deduplication (§8.2) and the determinism of §17. Blobs remain inspectable on
§5.3's terms: any conforming tool renders one as canonical TEL text.

### 8.4 Payload Hash

`payload.hash` is `hash("lira/1:blob", decompressed-payload)` — the blob-domain hash of the
entire decompressed blob stream. A reader MUST verify it after decompression (**L105**).

## 9. Sections

### 9.1 Model

A section is one compiled view of the release: a mapping from paths to blobs. The **root
section** is the _first_ `section` record of the manifest; the root is per-file, not fixed by
this specification to any universe. For the motivating ecosystem the `jvm` section is
conventionally first, holding the representation that is also valid as a conventional artifact
of its ecosystem; a TypeScript release's root would be its `js` section.

Where a release offers several integrations (§9.5) the sections form a matrix, and the root is
still one section of it: every other section, of whatever universe or integration, is an overlay
on that one (§9.3). Producers SHOULD make the root the section of the most widely applicable
integration, since overlays are minimal with respect to it. The choice of root is the
producer's own, recorded structurally as section order rather than declared anywhere: it
affects how much the overlays carry — bytes, never meaning — and no judgment in this
specification reads it. §17 asks only that a given toolchain choose deterministically.

### 9.2 Trees

Each section's `tree` field references a **Tree metadata blob** — stored as BinTEL (§8.3),
shown here as its TEL rendering — whose rows map paths to blob hashes:

```tel
tel 1.0  <lira-tree schema signature>

# path                        # blob
entry gossamer/Text.class       Ab12…
entry gossamer/Text.tasty       Cd34…
```

Paths MUST be relative, `/`-separated, contain no empty, `.` or `..` segments, and be unique
within a tree; no entry's path may extend another entry's path by a further `/`-separated
segment — one name cannot be both a content item and a directory; and rows MUST be sorted in
ascending bytewise UTF-8 order of path (**L106**). A tree lists content items only:
directories exist exactly insofar as paths pass through them, and have no entries of their
own (compare Appendix C, whose archives likewise carry none). Readers
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

A tree maps paths to content and to nothing else: no permission bits, no timestamps, no
per-item attributes exist anywhere in the format. Where a derivative container format needs
such metadata, the canonical profile supplies constants (Appendix C); where an application
needs it at run time, it belongs in the content, not the container.

### 9.4 Realms: Universes, `host`, `app`, and `env`

Sections are keyed by **realm** (§4.1), and the base schema (§14) expresses the realm axis
structurally, in two places. The document's **kind** — a select whose `library`, `host`, `app`
and `env` variants carry the fields and section shapes lawful for each kind of release —
distinguishes the three realms that are not universes; within the `library` variant, a
universe select keys each section's realm, with `jvm`, `sjsir`, and `nir` — the three
universes of the motivating ecosystem — as its base variants. (The names `js`, `klib`,
`wasmc` and their kin are
reserved for the universes proper of other ecosystems, arriving as schema layers; a universe
names the realm in which independently-published libraries compose, not a language's view of a
target.) The
universe vocabulary is open: new universes are introduced by TEL schema layers, which may append
variants to a select but never remove them. Reading a layered manifest requires holding the
composed schema (§5.2) — published layers, held on the same terms as the base — and holding
a universe's schema is deliberately a lower bar than supporting the universe: a consumer
MUST treat sections of universes it decodes but does not implement as opaque and MUST NOT
attempt to materialize them.

The first anticipated layer is the web layer, appending the two reserved universes this
specification's disciplines already reach for: **`js`** — interface convention `.d.ts`
([`dts.md`](dts.md), whose domain SHOULD narrow to it in a `dts/2`), linkage by bundler and ESM
resolution, capability model that of the host the bundle lands on — and **`wasmc`** — the WASM
component universe: interface convention WIT ([`wit.md`](wit.md)), linkage by component
composition, capabilities declared by world imports. Each is a TEL schema layer appending a
variant to the library-section universe select (§14, TEL §8.2).

A `host` section holds a **host contract**'s content — the carrier of a runtime environment's
capability interface — and a release carrying one is a host contract rather than a library.
The shape is the schema's (**L135**, discharged by construction under §14): the `host` kind
admits exactly one section and no integration, dependency or `requires` records, and no other
kind admits a `host` section — a violating manifest is not a strange release but a
non-conforming document (L101). Host sections
are never materialized onto any artifact path (§13.5). The full treatment — what a host contract
is, how libraries require one, and how requirements are satisfied — is the companion document
[`hosts.md`](hosts.md).

An `app` section holds a **deployable release**'s closed artifact — the product of an egress —
either stored as ordinary tree content, or pinned by external content address where the
artifact lives natively in another content-addressed store, as a container image lives in an
OCI registry (the `artifact` field, §14). A pinned section's tree still carries the release's
ancillary content — its interface descriptions (below) and probe metadata — never a copy of
the artifact itself. A release carrying an `app` section is a deployable release, and its
shape too is the schema's (**L143**, discharged by construction): the `app` kind admits only
`app` sections and no `dependency` records. It MAY declare integrations — one `app`
section per integration, naming the alternative build contexts of the egresses that produced the
artifacts — one deliverable per operating system is the canonical case, the per-OS `app`
sections overlaid on a shared root so that common content is stored once — labels for
alternative closed builds, not dependency declarations. Its
composition already happened, at the egress that produced it, and what it retains of that
history divides: its `source` records (§17) may name the sources it was built from, while the
_buildpath_ it was closed over remains a question of provenance attestation, deliberately out
of scope (§18). Its `app`
sections carry `requires` records freely — indeed those records are much of its point, since
what a closed artifact asks of its environment is the whole of its remaining need. And,
symmetrically, other modules' `requires` records may name a deployable module exactly as they
name a host contract (§13.3, **L137**): host contracts and deployable releases are this
specification's two kinds of **provider** — the capability an environment is _granted_, and the
capability _deployed into_ it — recognizable by their `host` and `app` sections respectively.

Content in an `app` or `env` section that no declared discipline claims is claimed
**atomless** rather than falling to `opaque/1` (**L144**). The asymmetry with universe
sections is principled: a universe section's content is interface-bearing by default, because
consumers compile against it; an `app` section's content is closed by default, because
nothing consumes its bytes as interface, and those bytes are already covered by
implementation identity (§6) — and an `env` section's content (probe metadata, operator
notes) is ancillary by construction, its release's interface being its manifest records
(below). A deployable
release's interface is instead **the surface it serves**, and it publishes that surface as its
own atoms: `api` records over the interface descriptions its tree carries — an OpenAPI
document, Protobuf descriptors — under a discipline whose domain includes `app`. A
self-described deployable is a module like any other: its atoms are what it serves, its
lineage is that surface's history, its evolution is graded by §12, and a requirement on it is
satisfied by lineage membership exactly as a dependency is (§13.2). A deployable declaring no
`api` records has an empty atom set and a degenerate API identity — legitimate for a leaf
application nothing requires, useless for a service, so publishers SHOULD self-describe any
module others are to require: an empty atom set can satisfy no requirement. (A command-line
tool is today the honest degenerate case: its invocation surface is a contract no registered
discipline yet atomizes, §11.3.) (Such a release's
derived version, per §12.5, advances only in the patch position — the algebra's honest report
that nothing it can see has changed.) The full treatment
— deployable releases, requirements on services, environments, and deployment — is the
companion document [`services.md`](services.md).

An `env` section marks an **environment release**: the operator-signed statement of one
environment's desired state (§13.7, [`environments.md`](environments.md)). Its shape is
likewise the schema's (**L148**, discharged by construction): the `env` kind admits exactly
one section, no integration, dependency or `requires` records — a `grant` record (§14) states
provision, not requirement, and is read by environment closure from the provision side — and
alone admits the `grant`, `deploy`
and `binding` records that are the
environment's substance, which is not section content at all: the
judgments that read those records (§13.7) read manifests, never payloads, so the records live
where every judgment input in this specification lives. An `env` section's tree carries only
ancillary content — probe metadata, operator notes — and MAY be empty. An environment
release declares `environment/1` (§11.3), whose atoms are its bindings and grants; and an
environment module is _neither kind of provider_ — it carries no `host` and no `app` section
— so no `requires` record can name one (**L137**) and no environment is ever a dependency
(**L147**): environments are judged and consulted, never composed against.

### 9.5 Integrations

A release MAY have been built in more than one **build context**: against two majors of one
dependency, for consumers who cannot move together; against alternative dependencies
altogether, where a consumer chooses a backend; or against different **host targets** — a
POSIX build and a Windows build of one library, same dependencies, different content and
different `requires` (§9.4, hosts.md §6). Each such alternative is an **integration**,
declared by an `integration` record (§14) with an identifier unique within the release.

Sections are keyed by realm **and** integration: a section names the integration it realizes,
and no two sections may share a (realm, integration) pair (**L131**). Where a release
declares any integration, _every_ section MUST name one (also **L131**): an unlabelled section
beside declared integrations belongs to no cell of the matrix and is ambiguous, not implicit. A release therefore
carries a matrix of sections, one per universe per integration, though it need not be full — a
universe may be offered under only some integrations. Every declared integration MUST have at
least one section (**L133**); an integration realized by nothing is a build context no
content was ever built in.

Dependency records are scoped to integrations exactly as they are scoped to universes (§13.2), so
dependencies common to every integration are declared once and unscoped. A release declaring no
integrations has exactly one, implicitly, and every section and dependency belongs to it; such a
manifest is identical to one written before this mechanism existed. Tools MAY normalize the
implicit integration internally — absent is a singleton — but it never appears in any stored
form: requiring a declaration would force a synthetic identifier into every single-integration
manifest, and that name would leak outward, into `deploy` records (§13.7) and canonical
ordering (§13.3), becoming a compatibility surface nobody chose.

Integrations do not weaken the API guarantee. Every cell of the matrix presents the same
interface (§9.6), so a release still has exactly one API identity and integrations are invisible
to consumers' compatibility reasoning: which integration a buildpath selects (§13.3) changes what
else must be present, never what the module offers. Host-target integrations are chosen exactly
as dependency-vector ones are: §13.3's rule 7 rejects, for a target's host contracts, any
integration whose selected section carries a requirement they do not satisfy, so naming the
Windows contract in the target selects the Windows integration, with no mechanism beyond the
rules already there.

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
`api` records (§14) list this section-invariant atomization, computed from the materialized
tree of a section in the discipline's domain — the root's, where the root lies in it. (A
library whose API genuinely differs by platform, or by which dependencies
it was built against, is two modules.)

Holding the invariant across integrations is what keeps them cheap: because every integration
presents one interface, the snapshot (§12.1), the lineage, dependency satisfaction (§13.2) and
diamond resolution all remain single-valued and need no notion of integrations at all.

What makes this sound is that atoms carry _names_, not the structures behind them. Where a
module X exposes a dependency Y's types in its own API — as parameter or return types — X's
atoms encode those types by fully-qualified name (§11.2, A.2), and the name is the same
whichever release of Y sat on the compile classpath; so X's atoms, and with them its API
identity, are integration-invariant — X _appears_ compatible across integrations because it
is: what its interface promises is the names. Whether the structures behind those names agree
is Y's own question, answered by Y's atoms, and policed at the point of composition: X's
used-set against Y (§13.4) and the buildpath rules (§13.3) decide whether the Y actually
present matches what X compiled against. The constraint bites exactly where content, not
names, crosses the module boundary: a public `inline` or
macro body that splices integration-differing content has an integration-differing value hash,
fails this invariant, and forces the module to be published as two. Rigid atoms are usually
unaffected, for the reason above.

The invariant is scoped per discipline to the content that discipline claims. Content that a
discipline claims **atomless** (§11.2) — derived binaries such as classfiles, whose interface
the discipline already carries through other files, or scanned resource directories (§11.4),
whose contents are deliberately non-contractual — contributes nothing to any universe's
atom set, which is what permits universes to diverge in implementation without violating the
invariant. Content claimed by no discipline falls to `opaque/1` in universe
sections — and must therefore be byte-identical across the universes that carry it — and is
atomless in `app` sections (**L144**, §9.4), where it may diverge freely across integrations.

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
realms it carries sections for (**L127**): an atomization of nothing is not a claim about
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

The two classes are the atom-level image of the first two guarantee levels (§11.5): rigid
atoms carry what linkage and recompilation alike depend on, while replaceable atoms exist
precisely for content whose replacement every discipline must keep linkage-safe yet
recompilation cannot ignore — the copied content goes stale, which is a recompilation-level
fact, and the reason staleness (§13.4) is computed rather than feared.

### 10.3 The Folding Principle

Disciplines MUST atomize such that the compatibility rules of their language are _encoded in
the decomposition itself_: any declaration whose **addition** is safe for consumers becomes a
standalone atom, and any fragment whose addition would break consumers is **folded into the
value of its enclosing atom**, so that adding it changes the enclosing atom and therefore
registers as a removal-plus-addition. Under this principle, "compatible extension" is exactly
the subset relation on atom sets, and no rule engine ever inspects a diff: the entire
compatibility check, for every language, is set arithmetic (§12).

An example makes the principle concrete. Adding a new public method to a class is safe for
every consumer, so a method is a standalone atom, and the addition registers as one new atom —
a minor. Narrowing an existing method's parameter type is not safe, so a method's parameter
types are folded into its atom's value — the change replaces one atom with another, which
registers as a removal, and no minor grade admits a removal. The engine that catches both has
never heard of methods or parameters; it computes `⊆`.

### 10.4 Atom Listings

Each `api` record in the manifest (§14) references an **Atoms metadata blob** — stored as
BinTEL (§8.3), shown here as its TEL rendering — listing, for one discipline, every atom of
the release — sorted by ascending value hash — with
its class, its value hash, and its key in human-readable form (for diagnostics; the key text
does not participate in any hash):

```text
tel 1.0  <lira-atoms schema signature>

discipline tasty/1

# class        # hash    # key
atom rigid       Ef56…     gossamer.Text.length():Int
atom replaceable Gh78…     gossamer.Text.trim():gossamer.Text [inline]
```

### 10.5 Polarity (Informative)

The algebra has two polarities, and they are formal duals. What a release **provides** is its
atom set, and provider evolution is safe when the set grows. What a consumer **needs** is its
used-set, and consumer evolution is safe when the set shrinks. Every judgement in this
specification is an inclusion `used ⊆ atoms` read from one side or the other — monotone in its
right argument, antitone in its left — which is why one mechanism serves dependencies (§13.2),
host requirements ([`hosts.md`](hosts.md)) and deployed services
([`services.md`](services.md)): the machinery never cares which side it stands on, only which
side is which. Getting that wrong is precisely the failure diagnosed in hosts.md §2, where
encoding requirements as the requirer's own atoms inverts the polarity and turns monotonicity
against itself.

The folding principle (§10.3) is the same duality applied _within_ one carrier. A declaration's
positions alternate polarity with nesting, exactly as function types alternate variance: what a
consumer receives (a return type, a response field) sits covariantly — additions are safe, so
the fragment stands alone as an atom — while what a consumer must supply (a parameter, a
required request field) sits contravariantly — additions break, so the fragment folds into its
parent's value. A discipline's folding decisions are, in this light, a variance computation
over its carrier's grammar, and the instructive cases are those where one syntax serves both
polarities and folds differently in each: `webidl/1`'s dictionaries ([`webidl.md`](webidl.md))
against `dts/1`'s interfaces, and `openapi/1`'s enumerations ([`openapi.md`](openapi.md)),
which stand alone in request position and fold in response position.

The formal statement is deliberately modest — the subset lattice, providers ascending,
requirements descending — and it is not load-bearing: no
verifier computes with the duality. It is recorded because it explains why one small algebra
keeps sufficing as the specification's scope grows, and because it predicts where it will stop:
content whose readers and writers _both_ evolve against retained data — a message topic's
history (services.md §11) — carries both polarities at once, and is exactly where a single
lineage stops being the right structure.

## 11. Disciplines

### 11.1 Identification

A discipline is identified as `<name>/<version>`, where `<name>` is a kebab-case identifier and
`<version>` is a positive integer. Any change to a discipline's canonicalization — however small
— MUST increment its version, since atom hashes are domain-separated by the full identifier and
a silent change would fracture hash stability.

Names SHOULD identify the interface carrier a discipline canonicalizes rather than the language
that produces it — `tasty`, `dts`, `wit` — since one carrier may be shared by several languages
and one language may present several carriers.

> **The dual-declaration bridge** _(non-normative)_. Two versions of one discipline stand in
> no formal relationship: each is an immutable canonicalization, and domain separation keeps
> their atoms disjoint even over identical content. Migration is nonetheless graded, because
> it happens at the release level: a release may declare both `foo/1` and `foo/2`, and since
> the grade is computed over the union of its atoms, the bridging release is a **minor**
> (pure addition), the later release dropping `foo/1` is a **major**, and in between the
> grade of the union is the maximum of the per-discipline grades — a theorem of §12.3, not a
> rule of this section. The maximum has a consequence worth knowing: while both versions are
> declared, the stricter canonicalization governs, so a successor discipline's greater
> precision pays off only after the off-ramp. Used-sets are per-canonicalization, so
> consumers recording uses against `foo/2` atoms during the bridge make the eventual drop of
> `foo/1` a non-event for spanning — and published used-sets make "who still depends on
> `foo/1`?" a manifests-only query.

### 11.2 Requirements

A discipline defines, deterministically:

1. **Domain**: the set of realms (§4) — universes, and possibly the non-universe realms
   `host` ([`hosts.md`](hosts.md)) and `app` ([`services.md`](services.md)) — whose content it
   atomizes. The domain is a property of
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
   §11.4). Claiming partitions each section's materialized tree: every content item has exactly
   one claimant, determined by **first match** over the release's `api` records in manifest
   order, then `resource/1`, then the `opaque/1` fallback (**L134**) — except in an `app`
   section, where the final fallback is atomless rather than `opaque/1` (**L144**, §9.4). The
   order of `api` records
   is therefore semantic, and producers MUST order them so that each item is claimed by the
   discipline that carries its contract — [`classfile.md`](classfile.md) §4 is the case that
   makes the order load-bearing. Content claimed by no discipline falls to `opaque/1` (§11.3).
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

This specification and its companion documents register the following disciplines:

- **`opaque/1`** (normative): the entire content item is a single **rigid** atom whose key is
  its path and whose canonical encoding is its bytes. Any change is therefore a removal plus an
  addition — a major event. `opaque/1` is the REQUIRED default for content no other discipline
  claims: nothing in a LIRA file is ever outside the compatibility algebra; unknown content is
  merely maximally conservative. Its domain is every universe; its keying is by declaration
  (paths); it certifies linkage and recompilation trivially, since it admits no change at all
  below the major grade.
- **`resource/1`** (normative; §11.4): resources declared in the manifest — presence-guaranteed
  exports, content-tracked resources, and scanned directories claimed atomless. Its domain is
  every universe; it certifies presence, which is the recompilation level for content addressed
  by name.
- **`tasty/1`** (informative here; normative specification in [`tasty.md`](tasty.md)): the Scala
  discipline sketched in Appendix A. Its domain is the fixed set `{jvm, sjsir, nir}` — the
  universes whose sections carry TASTy — and the cross-section invariant over that domain is what
  makes "one API on every platform" checkable. Its keying is by declaration ([`tasty.md`](tasty.md) §6). It
  certifies **recompilation** and TASTy-level linkage on its whole domain; the classfile-level
  linkage obligations of the `jvm` universe are not its and belong to the JVM ecosystem profile
  (Appendix D).
- **`classfile/1`** (informative here; normative specification in
  [`classfile.md`](classfile.md)): the JVM bytecode discipline. Its domain is the single universe
  `{jvm}`, so the cross-section invariant is vacuous for it; its keying is by **membership**,
  since a JVM call site names the receiver and a type's linkage surface therefore includes
  members it does not declare. It certifies **linkage** and only linkage. Registering it is a
  deliberate choice with a cost — see §11.6 and [`classfile.md`](classfile.md) §14 — and for most
  JVM ecosystems the `jvm/1` profile ([`jvm.md`](jvm.md)) is the better instrument.
- **`dts/1`** (informative here; normative specification in [`dts.md`](dts.md)): the TypeScript
  declaration discipline. Its domain is every universe and `host`, for want of a universe to
  name — `js` is
  reserved (§9.4) but not yet defined — which usefully brings a declared TypeScript surface under
  the cross-section invariant, and makes it the natural discipline for host contracts carried as
  `.d.ts` (a Node-builtins contract, [`hosts.md`](hosts.md)). Its keying is by declaration. It
  certifies **recompilation** and not linkage: `.d.ts` declarations are erased before anything
  runs, so there is no late linking for them to protect.
- **`capability/1`** (informative here; normative specification in [`hosts.md`](hosts.md)): the
  plain host-capability discipline. Its domain is the single realm `{host}`; its keying is by
  declaration; one rigid atom per declared capability, the value hash covering the capability's
  name and optional version predicate. It is the discipline of host contracts with no formal
  carrier — POSIX commands, tool availability; a contract with a formal grammar (Web IDL, `.d.ts`)
  uses a discipline over that carrier instead. It certifies presence, on the same terms as
  `resource/1`.

- **`wit/1`** (informative here; normative specification in [`wit.md`](wit.md)): the WIT
  discipline of the WebAssembly Component Model. Its domain is `{host, wasmc}` — WASI-world
  host contracts today, `wasmc`-universe libraries when that reserved universe's schema
  layer lands. Keying by declaration; certifies **recompilation**.
- **`webidl/1`** (informative here; normative specification in [`webidl.md`](webidl.md)): the
  Web IDL discipline, for browser host contracts. Its domain is `{host}`; keying by
  declaration; certifies **recompilation**. Its folding decisions differ instructively from
  `dts/1`'s, because the IDL declares the usage direction TypeScript cannot: interface members
  are additive, and only required dictionary members fold.
- **`cheader/1`** (informative here; normative specification in [`cheader.md`](cheader.md)):
  the C header discipline, for host contracts over environment-supplied shared libraries — a
  libc, `libcrypto`, any `dlopen`ed dependency. Its domain is `{host}`; keying by declaration;
  certifies **recompilation** and symbol presence.
- **`jsig/1`** (informative here; normative specification in [`jsig.md`](jsig.md)): the Java
  signature-surface discipline, over the same canonical encoding as `classfile/1` but claiming
  what stubs can promise — **recompilation** and presence, never linkage. Its domain is
  `{jvm, host}`; keying by membership. It is the discipline of the `jdk` and `android` host
  contracts, and of `scalajs-javalib`, whose shared discipline is what lets cross-contract
  spanning decide portability.

- **`kmeta/1`** (informative here; normative specification in
  [`kotlin.md`](kotlin.md)): the Kotlin declaration-surface discipline over the `@Metadata`
  annotation. Its domain is `{jvm, host}`; keying by **membership**; certifies
  **recompilation**. It covers what `classfile/1` cannot see — nullability, properties,
  default-parameter existence, suspend coloring — and its claiming order beside `classfile/1`
  is load-bearing, exactly as [`classfile.md`](classfile.md) §4.
- **`openapi/1`** (informative here; normative specification in [`openapi.md`](openapi.md)):
  the OpenAPI discipline, for HTTP interfaces — standard contracts published as host
  contracts, and the served surfaces of deployable releases. Its domain is `{host, app}` — the
  first registered discipline to include the `app` realm, which is what lets a service
  release's own atoms be the surface it serves (§9.4, [`services.md`](services.md) §3). Keying
  is by declaration; atomization is **directional**: every position in a description has a
  polarity — request or response — the folding principle resolves by variance (§10.5) rather
  than by conservatism, and a schema reachable in both polarities atomizes once per direction.
  It certifies **recompilation** — regeneration, in the transposition of
  [`services.md`](services.md) §7; a wire-level linkage claim is deliberately left to an
  ecosystem profile, on exactly the division of labor between `tasty/1` and `jvm/1`.
- **`environment/1`** (informative here; normative specification in
  [`environments.md`](environments.md)): the environment-topology discipline. Its domain is
  the single realm `{env}`; its keying is by declaration; it emits only rigid atoms and
  certifies **presence**, on `capability/1`'s terms. Like `resource/1`, its input reaches
  beyond the tree (§11.4's precedent): it atomizes the manifest's `binding` and `grant`
  records — one atom per binding, the value covering the address and provider module and
  deliberately **not** the selection (the `probe`-field precedent, hosts.md §5), and one per
  grant, covering the module name only — so an environment's lineage is the history of its
  _topology_: additions are minors, and removing an address, retargeting it to a different
  module, or withdrawing a grant is a major, behind **L110**'s explicit-major gate. Deploys,
  selections and routes enter no atom and change at patch grade.
- **`tels/1`** (informative here; normative specification in [`tels.md`](tels.md)): the TEL
  schema discipline, for releases whose content is a TEL schema document — including the
  layers of this specification's own extensibility seam (§14). Its domain is every universe
  and `host`, on `dts/1`'s reasoning; keying by declaration; it emits only rigid atoms — one
  per component of the schema's composed sequence, one per ordered component pair — so that
  grade computation coincides, by construction, with TEL's signature-subsequence
  compatibility relation, and schema versions are derived from TEL's own subtype relation.
  It certifies **recompilation** in its schema transposition: revalidation. It also enforces
  publish-time name binding: a schema's declared `name` binds its module name, and its layer
  names are the names a TEL pragma's `+` selections address.

Anticipated future disciplines include one for Java source signatures where no `.class` files
are shipped; a klib-metadata sibling of `kmeta/1` for Kotlin multiplatform;
`proto/1` over Protobuf descriptors — the `classfile/1` of the network family, since Protobuf's
tag numbers and unknown-field rules are properties of the wire format itself, licensing a
stronger linkage claim than `openapi/1` makes; and a command-line invocation discipline over
a command's parameter grammar, without which a CLI deliverable's release is honestly
degenerate (§9.4) — its invocation surface is a contract nothing yet atomizes.
Foreign content — JavaScript modules resolved at link time, C sources compiled by a downstream
linker, and so on — is admissible in any section today under `opaque/1`.

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
  function of the name alone: the atom asserts _presence_, not content. Within a lineage,
  adding an export is a minor event and removing one is major (§12.3); editing the content is
  invisible to the algebra — a patch — because resource content is behavior, and no discipline
  certifies behavior (§18). Because the atom is content-independent, the cross-section
  invariant (**L108**) permits an exported resource's bytes to differ per universe while
  automatically requiring the _path_ to be present in every universe: a universe lacking it
  atomizes to a smaller set and fails L108.
- **`track`** — as `export`, but the item yields one **replaceable** atom whose canonical
  encoding is the item's bytes, with an empty reference list (resources create no linkage, so
  replaceability soundness is trivial). Tracking is for resources consumers read at _their_
  compile time — a schema a macro bakes into generated code, say — where a content change is
  exactly replaceable churn: a minor event that marks consumers whose used-sets contain the
  atom as stale (§13.4). L108 consequently requires tracked content to be byte-identical
  across universes.
- **`scan`** — every tree item whose path has the declared path plus `/` as a prefix is claimed
  **atomless**. Scanned directories hold content that consumers _enumerate_ rather than name —
  plugin registrations, discovered templates — so no individual name is contractual: additions,
  removals and edits under a scanned directory are patch-grade, and content may diverge freely
  per universe (§9.6). A scanned directory may be empty, in any or all universes.

Declarations MUST be well-formed (**L124**): no path may be declared twice, and an `export` or
`track` path MUST NOT lie under a declared `scan` directory, so the partition of §11.2 has a
single claimant by construction. In the claiming order (**L134**, §11.2), `resource/1` follows
every `api`-record discipline and precedes the `opaque/1` fallback: an item under a `scan`
directory that a language discipline claims goes to that discipline, and the remainder are
atomless. An
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
  symbol resolution, and network calls resolved by service discovery: for a service contract,
  linkage is **wire compatibility**, and "no recompilation" reads "no redeployment"
  ([`services.md`](services.md) §7).
- **Recompilation**: consumers' sources still compile against the new release, and any content
  copied into them at compile time still re-expands. Meaningful in every ecosystem, and the only
  meaningful level where there is no late linking to protect. For a service contract,
  recompilation is **regeneration**: clients regenerated from the new interface description
  still build.
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
universe-specific discipline — `classfile/1`, whose domain is `{jvm}`
([`classfile.md`](classfile.md)) — and let bytecode surface enter the atom set directly. This specification permits that and, for an ecosystem whose primary
contract genuinely _is_ linkage, it is the better choice. But it is the wrong default, because
atoms feed the snapshot (§12.1), and the snapshot is API identity: fusing the two levels into one
identity means that a release whose source-level interface is unchanged but whose bridge methods
moved acquires a different API identity, breaking dependency satisfaction (§13.2) for every
consumer, including those who only ever recompile. Keeping linkage predicates in a profile keeps
the snapshot at the recompilation level, where it is the useful identity, and records
linkage-level breakage separately, where it can be acted on by the consumers it actually
affects. The disparity between TASTy-level and classfile-level compatibility in the motivating
ecosystem is exactly this split — one level the discipline's, one the profile's — worked
through in §12.4 and Appendix D.

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
lineage is the sequence of the series' _API states_, not of its releases. The lineage is the
module's verifiable version history: every compatibility question in this specification reduces
to membership in, or relations between, lineages.

A new major series begins a fresh lineage with no shared prefix. There are no compatibility
guarantees across majors except those separately provable via used-sets (§13.4).

### 12.3 Release Grades

Between a release `A` and its immediate successor `B` in a lineage:

- **patch**: `atoms(B) = atoms(A)` — API identity; only non-API content changed.
- **minor**: `rigid(A) ⊆ rigid(B)`, and every replaceable atom of `A` is present in `B` either
  unchanged or replaced (same key, new value). Pure extension plus replaceable churn.
- Anything else — any rigid removal or change, any replaceable removal whose introducing
  declaration survives — MUST NOT be published into the same lineage; it is a **major** event
  beginning a new lineage. Publishing tools MUST refuse to extend a lineage with a non-conforming successor
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

is precise: _by the atom algebra this is a minor, and consumers who recompile may take it as one;
consumers relying on already-compiled linkage must recompile against it._ A release that silently
omits a `breaks` level it does not preserve is invalid — the whole value of the record is that
its absence means something.

This split is what lets one release serve both populations honestly. Collapsing it — forcing a
major for every bridge-affecting change — would fracture lineages for changes that no recompiling
consumer can observe; ignoring it would promise linkage the release does not deliver. Note also
what `breaks` cannot do: it never licenses a rigid atom removal, and it never permits a step the
core forbids. It records a weaker claim about a step the core already allows.

Over a service contract, the same record schedules operations. A contract step that `breaks
linkage` is exactly the change that is safe for consumers who redeploy against the new
description and unsafe for consumers already running: a **coordinated deploy**, named in the
manifest rather than discovered in an incident — and the consumers that must move are
computable, being those whose used-sets intersect the step's delta
([`services.md`](services.md) §7).

### 12.5 The Derived Version

The manifest's `version` field is OPTIONAL, and strictly numeric: exactly `x.y.z` with each
component a decimal natural. Prerelease and build suffixes are forbidden by the schema —
development state is expressed by the version's _absence_, never by a suffix.

**Development releases.** A release without a `version` is a **development release**,
identified purely by its hashes: the snapshot is its API identity and `payload.hash` its
implementation identity. Development releases are complete, verifiable `.lira` files; what they
lack is only the assignment below. Other modules may depend on them during development,
optionally pinning the exact build (§13.2); such dependents are themselves unpublishable until
the pin is lifted (**L118**).

**Version assignment.** Publishing a release is the act of assigning it the next version
number the algebra dictates, and signing the result:

1. compute the grade (§12.3) of the release against the module's previous published release
   (a first release is assigned `1.0.0` with a fresh single-entry lineage);
2. derive the version — patch increments `z`, minor increments `y` and zeroes `z`, and an
   explicitly-requested major increments `x` and zeroes both;
3. extend the lineage per §12.2 and §12.3 (**L110**);
4. re-sign the manifest (§15).

The payload is untouched, so assignment never rebuilds and the implementation identity is
unchanged. The signed manifest — binding module, version, lineage, snapshot and payload hash
under one signature — **is** the assignment record; a distribution index's release record is a
projection of it.

**Publication rules.** A publishing tool MUST refuse to publish a manifest that:

- carries no version, a non-numeric one, or one with major `0` (**L117**): versions begin at
  `1.0.0`, so every published version carries semantic versioning's stable-series guarantees —
  the 0-series convention, in which the minor also carries breaking steps, is a semver
  exception this specification does not admit, development state being already expressed by
  the version's absence;
- pins any dependency to a `build` (**L118**);
- requires a snapshot that appears in no _published_ release's lineage for that module
  (**L119**);
- carries a minor number that is not the count of minor steps in its lineage (**L120**).

Consumers still make every decision on hashes: to a consumer the version remains a
human-readable projection, and any disagreement it observes (for example, a dependency's
`version` hint against the resolved release) is a warning, never an error. The asymmetry is
verifiability, not distrust: a hash is recomputable by anyone holding the release, and
lineage membership is checked at publish time (§16), while a version is testimony — assigned
by a publishing tool, checkable only against the lineage it projects. Versions serve humans;
hashes decide.

### 12.6 Tags

A release MAY carry **tags**: user-facing, immutable names for the release, in the sense of a
version-control tag. The derived version (§12.5) is a projection of the lineage and says
nothing a human recognizes; a tag carries the name the world already uses. The motivating case
is host contracts (hosts.md §3), whose vendors number by marketing convention: the `jdk`
contract release whose derived version is `8.2.0` carries `tag jdk-19`, and "compatible with
JDK 19" resolves through the tag to a snapshot without anyone memorizing lineage arithmetic.

Each `tag` field names one tag (§14; the `tag-name` scalar). Tags are:

- **signed**: a tag is a manifest field, covered by the manifest signature (§15.2) like every
  other. There is no separate tag object to distribute or verify.
- **unique and immutable**: within one module, a tag names exactly one release, forever. A
  publishing tool MUST refuse to publish a release carrying a tag that any already-published
  release of the module carries (**L142**), and re-signing a release's manifest (as assignment
  does, §12.5) MAY add tags but MUST NOT remove or alter one a published manifest for the same
  release carries (also **L142**).
- **without algebraic authority**: nothing in §12 or §13 reads a tag. A consumer or authoring
  tool MAY resolve a tag to the tagged release's snapshot — for writing a `dependency` or
  `requires` record by name — but what the record carries, and what satisfaction is decided
  on, is the snapshot, exactly as with the `version` hint. Unlike the hint, a tag's uniqueness
  and immutability make the resolution stable: the same tag resolves to the same snapshot on
  every registry that holds the release.

Tagging after publication is the assignment pattern of §12.5 applied again: the payload is
untouched, the manifest gains the tag, and the result is re-signed. The signed manifest is the
tag record. Together the two patterns support the bless workflow: a development release
circulates and is tested by its hashes alone, assignment stamps the version the algebra
dictates, and a later re-signing adds the name the world will use — the payload untouched
throughout, so what was tested is bit-for-bit what was blessed.

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
- **`serves`**, naming the universe in which the dependency itself offers its content, when that
  differs from the universes the depending sections consume it in — the **join** case (§4.1). A
  Scala module intending its `sjsir` build to invoke a TypeScript module declares that dependency
  with `universe sjsir` and `serves js`: the TypeScript release carries a `js` section, not an
  `sjsir` one, and the two universes meet at a bundler join. A dependency without `serves` is
  satisfied in the same universe it applies to. Whether two universes can in fact meet in one
  application is a property of the deliverable's pipeline, declared by ecosystems rather
  than by this specification (informatively,
  [`universes.md`](../design/universes.md) §4); the rules here need only know which universe the
  dependency's section is selected from (§13.3 rule 4, §13.5).
- **`integration`** entries, scoping the dependency to the named integrations (§9.5): the
  dependency vectors that distinguish integrations are expressed here, and the host targets
  that distinguish them by the sections' `requires` records (§9.4, hosts.md §6). A dependency
  without `integration` entries applies to every integration, which is how dependencies common to
  all of them are declared once. The two scopes are independent and conjunctive: a dependency
  applies to a (universe, integration) pair iff it applies to that universe and to that
  integration.
- **`build`**, a development-time pin to an exact implementation identity (§6): the candidate
  must additionally have exactly that `payload.hash`. Build pins express "this exact unpublished
  build" during development; a manifest carrying one is itself unpublishable (**L118**, §12.5).

A `dependency` record naming a module whose releases are not library releases — host
contracts, deployable releases, or environments (§9.4) — is invalid (**L147**): nothing of a
provider composes onto a buildpath, the correct edge for a provider is `requires`, and no
edge at all names an environment.

The `build` pin's prohibition is scoped to this axis, and the boundary is worth stating:
**L118** governs _dependencies_, where a pin is development coupling and satisfaction must
remain lineage-decidable for future composition. A `deploy` or `binding` record's pin
(§13.7, [`environments.md`](environments.md)) is a different kind of claim — desired-state
exactness about a closed artifact that composes into nothing, on the `artifact`-pin
precedent (§9.4) — and is publishable, including when it names a development release
(§12.5), which is the ordinary currency of continuous deployment.

Dependency requirements are per-release facts: a module's dependency graph — which modules it
names, at which snapshots, in which universes — may change freely between releases at any
grade. Grades (§12.3) constrain only the module's own atom set; consumer safety under a changed
graph comes from re-validating the buildpath (§13.3), never from the grade.

### 13.3 Validity

A buildpath is valid **for a target under an assignment**. A **target** is a universe — the one
an egress will close over — together with any universes that join it (§4.1), and optionally a
set of host contracts for rule 7, at most one per module; in the common single-universe case,
every rule below reads exactly as it did when this section was defined over one universe. An
**assignment** is a map from each
release to one of its integrations (§9.5). Each release **serves** one universe of the target:
the target's primary universe, unless the dependency records that name the release carry
`serves` (§13.2), in which case it is the universe they name. Validity holds iff all of the
following hold, and each is decidable from manifests alone. Closure and compatibility quantify
over the dependency records _applicable to the universe a release serves and to its assigned
integration_ (§13.2); uniqueness, namespace disjointness and resource disjointness are global. A
buildpath is **valid for a target** iff some assignment makes it so (**L132**).

1. **Uniqueness**: at most one release per module name (**L111**).
2. **Namespace disjointness**: the `owns` claims (namespaces such as packages, per-ecosystem
   interpretation) of distinct modules are pairwise disjoint — a namespace and any dotted
   extension of it clash (**L112**).
3. **Resource disjointness**: the `export` and `track` resource paths (§11.4) of distinct
   modules are pairwise disjoint (**L126**), so a classpath-style resource reference resolves
   to exactly one module. `scan` directories are exempt — cross-module aggregation under a
   shared directory is their purpose.
4. **Closure**: every module named by an applicable `dependency` record is present (**L113**).
   A dependency carrying `serves` additionally requires the named universe to be in the target
   and the candidate to carry a section in it: a join edge to a universe the target does not
   include, or to content the candidate does not offer, fails closure exactly as an absent
   module does.
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
7. **Host requirements**: when the target names host contracts, every `requires` record of
   every selected section — the section chosen by each release's served universe and assigned
   integration (§13.5) — that names a host-contract module is satisfied per
   [`hosts.md`](hosts.md) §7 (**L136**): the required snapshot appears in the lineage of the
   target's contract for that module, or the requirement's used-set is contained in a target
   contract's atom set (spanning, §13.4, including across modules per hosts.md §7);
   requirements on one module from several releases are jointly judged per hosts.md §10.
   `requires` records sharing an `alternative` identifier within one section form a
   **group**, an ungrouped record being a group of one (§14): a group is satisfied iff at
   least one member is, resolution taking the first satisfied member in declaration order,
   and only that member enters the aggregate. A group none of whose members is satisfied
   fails this rule — unless a member is marked `optional`, in which case the group states a
   preference, not a need: it is excluded from this rule and from aggregation, and resolves
   to nothing, which provisioning and probing report rather than fail. A
   `requires` record naming a module whose releases are neither host contracts nor deployable
   releases (§9.4) is invalid (**L137**) — an environment release, carrying neither a `host`
   nor an `app` section, is neither kind, so no requirement ever names an environment. A requirement naming a _deployable_ module is not a
   buildpath fact at all: it is left **pending** here and judged at environment validity
   (§13.7, [`services.md`](services.md) §6), since which release of a service is present is a
   property of an environment, not of a composition of libraries. A buildpath validated
   without host contracts likewise leaves this rule pending, and a tool MUST report which mode
   it validated in and which records remain pending: a buildpath can be coherent as a library
   composition and still unsatisfiable on the host, or in the environment, a consumer intends.

Where no release declares an integration, every release has one and the assignment is unique:
the rules read exactly as they did before this mechanism, and validity is decided by one pass.

Note what the quantifier does _not_ add. No rule above mentions integrations, and none needs to:
the rules that decide between them are the ones already there. An assignment whose integration
requires a snapshot the present release of that module does not carry in its lineage fails rule
5; one that would need a second release of a module already on the path fails rule 1; and one
requiring a module absent altogether fails rule 4. So the version-alternative case resolves out
of rules that predate integrations entirely, and all the quantifier adds is the choice of an
assignment that survives them.

Rule 4 is also how a consumer expresses a backend choice without pinning: an integration naming a
module the buildpath does not carry fails closure, so putting exactly one backend on the
buildpath selects the integration that uses it. Pinning (below) is for the case where the
buildpath carries both and the choice is genuinely free.

**The canonical assignment.** More than one assignment may be valid — the case where a release
offers alternative backends and the buildpath carries both. Resolution must still be
reproducible, so among the valid assignments the **canonical** one is the lexicographically least
sequence, taken over releases in ascending module-name order, of each assigned integration's
`rank` then `id` (§14). Tools MUST select the canonical assignment unless the consumer pins
otherwise, and a consumer MAY pin any release to a named integration, the remaining releases
still taking their canonical choices. Pinning is how a consumer states a preference the manifests
cannot imply; `rank` is how a publisher states a default so that the unpinned case is
deterministic rather than arbitrary.

**The cost, and why there is almost none.** The existential quantifier reads like a search, and
it is worth saying plainly that under these rules it is not one.

A buildpath is a **fixed** set of releases, so which release provides a module is settled before
any integration is chosen. Every rule an assignment can affect — closure (4), compatibility
(5) and host requirements (7) — then turns on one release together with its own choice, and on
nothing else: no rule relates one release's integration to another's. The choices are therefore independent, and the
canonical assignment is obtained by taking, for each release in isolation, the first of its
integrations in (`rank`, `id`) order whose own dependencies hold. That is linear in the total
number of integrations declared across the buildpath, requires no backtracking, and yields the
canonical assignment by construction rather than by minimizing over candidates.

A buildpath admits no valid assignment (**L132**) exactly when some single release has no viable
integration — never through some irreducible interaction between releases. A specific diagnosis
is therefore always available, and tools SHOULD name that release and, for each of its
integrations, the rule that rejected it. "No valid assignment" as a bare verdict is never the
best a tool can do.

The general problem this resembles — where choices genuinely interact, and search is
intractable — arises only for a resolver that also decides _which releases to include_, since an
integration can then pull a module onto the buildpath and change what other releases resolve
against. That is dependency resolution proper. This specification does not do it: §13.3 audits a
buildpath it is handed, and §13.2 requires exact snapshots satisfied by lineage membership, which
is what keeps the two problems apart. A tool that constructs buildpaths inherits the harder
problem, and inherits it from its own design rather than from this section.

Even so, §13.4's spanning often removes the need for an integration altogether, which remains the
cheapest answer to a version difference (§9.5).

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

From a valid buildpath, a target and the assignment that validated it (§13.3), a consumer
derives one conventional artifact set (e.g. a classpath) **per universe of the target** by, per
release serving that universe: selecting the section for that universe and that release's
assigned integration (a release lacking one is a validation-time error, not a link-time
surprise), materializing it per §9.3 into a cache keyed by implementation identity, and
appending whatever ecosystem-supplied runtime the universe requires. The per-universe artifact
sets are what the target's egress and joins consume; invoking those tools is the build's
business, not this specification's. Provider sections are never materialized onto any artifact
path: a `host` section's content describes the environment and joins nothing, and an `app`
section's content is past composition and joins nothing either (§9.4). Reconstruction of a
standalone per-platform archive is the canonical derivative artifact of §13.6.

### 13.6 Canonical Derivative Artifacts

Each section deterministically derives one **canonical derivative artifact**: the section's
materialized tree serialized to the ecosystem's container format under the canonical profile of
Appendix C (for the motivating ecosystem, a JAR). Derivation is byte-deterministic — a pure
function of the materialized tree — so the artifact's identity,

```text
derivative = hash("lira/1:derivative", artifact bytes)
```

is a stable fact about the section, which the section's OPTIONAL `derivative` field declares
(§14) and verifiers recompute (§16, step 3); a declared derivative hash that does not recompute
from the materialized tree is invalid (**L138**).

The declared derivative hashes make releases **findable from conventional artifacts alone**: a
tool holding only a classpath of ordinary JARs hashes each under the derivative domain and
looks the result up — against a buildpath's manifests, or a distribution index — recovering
the release, its API identity, and its whole compatibility context. Since a derivative belongs to
one section, and a section to one (realm, integration) pair, the lookup also recovers _which
integration_ the artifact is, which no coordinate-mangling convention can tell it.
Materialization caches
(§13.5) SHOULD store sections in exactly this form, so the cache entry _is_ the canonical
artifact.

### 13.7 The Environment

The buildpath decides, from manifests alone, whether a set of releases composes at build time.
The same question arises again after every egress has run: whether a set of _running_ artifacts
composes at deploy time — whether this service can be deployed into that cluster without
breaking a consumer nobody remembered. The **environment** is the buildpath's runtime
counterpart, and it is deliberately not a second algebra. A deployed service publishes the
surface it serves as its own atoms (§9.4); the edges between services are `requires` records on
their `app` sections — requirements rather than dependencies, because at runtime every
other service _is_ environment: nothing of the provider composes into the consumer's artifact,
the provider's contract is read for atoms and never materialized, and whether the provider is
actually present is decided by probing at the third verification moment (hosts.md §9).
Satisfaction is lineage membership and spanning, unchanged.

An environment is stated by an **environment release** (§9.4, **L148**): an operator-signed
release of the `env` realm whose manifest carries `grant` records — the platform contracts
the environment supplies — `deploy` records — the releases intended to run, each pinned by
implementation identity and naming the `app` section deployed (its realm and integration,
whose `requires` records are the applicable ones; the environment needs no assignment
machinery beyond this choice) — and `binding` records, each associating an **address** with a
provider module and a release **selection**: a snapshot, satisfied through the provider's
lineage, or an exact implementation identity. A deploy record is thus _precise_ where a
release is _various_: the release declares every integration it offers, the deploy activates
exactly one, and an environment running two integrations of one module is simply two deploy
records — the concurrency of a rolling deployment, quantified over like any other (below).
Binding addresses MUST be pairwise disjoint —
neither equal to nor a path-prefix of one another, compared as authored strings on the `owns`
precedent (**L149**): the address disambiguates at run time what uniqueness (rule 1, L111)
disambiguates at build time, which is why the environment has no uniqueness rule of its own —
two releases of one module serving concurrently is the normal state of a rolling deployment,
not an error.

Environment validity (**L145**) transposes §13.3. Closure requires every module named by any
deployed release's applicable `requires` records to be **provided** — by a granted platform
contract, by a deployed release of that module, or, where the requirement carries a used-set,
by any provider whose atoms cover it (cross-module spanning, hosts.md §7). Satisfaction must
hold against _every_ concurrently-serving release of a provider — refined per binding
(**L150**): where a requirement resolves to a binding, the quantifier ranges over the
concurrently-serving releases _within that binding's selection_, releases behind other
bindings being other providers; a provider deployed but unbound keeps the unrefined
quantifier. Requirements aggregate across the environment by the rule of hosts.md §10.
Closure and satisfaction quantify over **groups** (§14): `requires` records sharing an
`alternative` identifier in one section are satisfied together iff at least one member is,
an ungrouped record being a group of one, and only the member resolution selects (below)
enters the quantifier and the aggregate. A group none of whose members is satisfied fails —
unless a member is marked `optional`, in which case the group is a preference: it neither
fails closure nor joins the quantifier or the aggregate, and resolves to nothing.

Resolution is deterministic on the canonical-assignment pattern. A requirement's **candidate**
bindings are those whose provider module and selection satisfy it ([`services.md`](services.md)
§5, cross-module spanning included); tools MUST resolve each group to its first member, in
declaration order, that is satisfied, and that member to its first candidate in ascending
(`rank`, `address`) order, unless a `route` pin on the consumer's deploy record names a
candidate of some member, which member and candidate are then chosen — a `route` naming an
address that is not a candidate for any member of its group is invalid (also **L148**), else
a pin could silently defeat satisfaction. **Provisioning** — the §13.5 analog — evaluates a valid environment into
a table from each requirement to its resolved binding's address; a requirement whose provider
carries no binding is _unaddressed_, an advisory fact rather than a failure, since not every
provider answers at an address. A group none of whose members is satisfied — valid only
where a member is `optional` — is likewise **unprovided**: recorded in the table as absent,
so that the artifact's own fallback governs and probing (hosts.md §9) reports an absence
rather than a failure; a provider that is present but would satisfy no member is not a
candidate, and so counts as absent: an environment offers an optional capability only in a
form that satisfies. Reconciling the running world to the judged one is the
orchestrator's business, exactly as invoking egress tools is the build's (§13.5).

A **deploy** is a transition of an environment — any change to its release's `grant`,
`deploy` or `binding` records, a rebinding included — and a release is **deployable** into an
environment iff the state after the transition — and, for a rolling deploy, the intermediate
state in which old and new releases serve together — is valid (**L146**). Deployability is
thus the same judgement as buildpath validity, made at the second moment, from the same
manifests: closure and satisfaction over a set, with the guarantee levels (§11.5) saying
which consumers may keep running and which must move (§12.4). Environment validity and
deployment are elaborated in [`services.md`](services.md); the environment release, its
records, its discipline and provisioning in [`environments.md`](environments.md).

Two readings of this section are worth fixing. The environment every rule above judges is a
statement of **desired** state: each rule reads manifests, and none inspects a process. The
**actual** state of a running environment is knowable only by probing — the third
verification moment (hosts.md §9) — and divergence between the two, **drift**, is a probe
result on probing's usual advisory terms: it enters no validity judgment, and what it
triggers is re-judgment, and reconciliation that is the orchestrator's business exactly as
egress invocation is the build's (§13.5). And execution is not a third composition but the
second one sustained: nothing ever closes over an environment, so its coherence is checked at
every transition and probed for as long as anything runs — which is what the environment
release exists to make possible: the desired state, signed and versioned, consultable by the
runtime for the whole of that lifetime ([`environments.md`](environments.md)).

## 14. Manifest Schema

The `lira` TEL schema, and the four companion schemas for metadata blobs, shown in TEL text;
stored instances are BinTEL (§5.1, §8.3). A fifth companion schema, `lira-increment`, is
defined by [`increment.md`](increment.md) §4 for the header of an increment file, and is held
by readers on the same terms. The scalar
validators are normative: `base-256-hash` is exactly 32 BASE-256 characters; `base-256` is
one or more BASE-256 characters; `module-name` is
kebab-case segments joined by `/` or `.`; `namespace` is dotted package-style segments
(letters, digits, `_`; no leading digit); `semver` is exactly `major.minor.patch`, each a
decimal natural with no superfluous leading zero; `natural` is such a natural; `discipline-id`
and `profile-id` are `<kebab-name>/<positive integer>`; `guarantee` is `linkage` or
`recompilation`; `address` is a DNS name optionally followed by a `/`-separated path prefix,
compared as authored (environments.md §4); `tree-path` is a relative `/`-separated path with no
empty, `.` or `..` segments; `atom-class` is `rigid` or `replaceable`; `tag-name` is a
letter followed by letters, digits, `-` and `.` (`jdk-19`, `scala-3.9`).

The names `base-256-hash` and `base-256` are additionally bound as codecs (TEL §21.7),
declared by the `Hash` and `SignatureValue` scalars respectively: `encode` is the
strict-mode BASE-256 decoding (base256.md §9) of the text to its raw bytes — exactly 32 for
`base-256-hash`, any positive count for `base-256` — and `decode` is the BASE-256 encoding
of those bytes back to text. Strictness makes each pair image-exact, so one value has
exactly one stored form (§7, §17) — and a several-kilobyte ML-DSA signature is stored as
its bytes, never as the UTF-8 of its textual form.

```text
tel 1.0

name lira

scalar Hash
  description  A 256-bit BLAKE3 hash: 32 BASE-256 characters as text, the raw 32 bytes in BinTEL.
  validate     base-256-hash
  encoding     base-256-hash

scalar SignatureValue
  description  A signature's bytes: BASE-256 text in renderings, raw bytes in BinTEL (§15).
  validate     base-256
  encoding     base-256

scalar ModuleName
  validate module-name

scalar Namespace
  validate namespace

scalar Semver
  validate semver

scalar TagName
  description  A user-facing, immutable release name (§12.6), e.g. jdk-19.
  validate     tag-name

scalar Natural
  validate natural

scalar DisciplineId
  description  A discipline identifier, e.g. tasty/1.
  validate     discipline-id

scalar ProfileId
  description  An ecosystem profile identifier, e.g. jvm/1.
  validate     profile-id

scalar Guarantee
  description  A guarantee level (§11.5): linkage or recompilation. Behavior is never certifiable, so never breakable by record.
  validate     guarantee

scalar TreePath
  description  A relative tree path (§9.2).
  validate     tree-path

record Tool
  description  One tool that produced content in this release.

  field name Identifier
  field version String
  field flag Identifier optional repeatable

record Source
  description  The sources a toolchain consumed to produce this release (§17); authorial.

  field scheme Identifier               # e.g. git-commit, blake3-tree
  field digest String                   # the scheme's own identifier, verbatim
  field origin String optional          # advisory retrieval hint; no authority

record Api
  description  One discipline's atomization of this release's public interface.

  field discipline DisciplineId
  field atoms Hash                      # Atoms metadata blob

record Profile
  description  An ecosystem profile whose predicates this release claims to satisfy.

  field id ProfileId
  field breaks Guarantee optional repeatable  # levels not preserved vs the predecessor (§12.4)

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
  field serves Identifier optional      # universe the dependency offers, when joining (§13.2)
  field integration Identifier optional repeatable  # integrations it applies to (§9.5, §13.2)
  field uses Hash optional              # Uses metadata blob
  field spans Hash optional repeatable  # snapshots provably spanned (§13.4)

record Integration
  description  One alternative build context this release was built in: a dependency vector, a host target, or both (§9.5).

  field id Identifier
  field rank Natural optional  # canonical-assignment preference, lower first (§13.3)
  field label String optional  # human-readable note; no authority

record Requires
  description  One requirement of this section, on either kind of provider (hosts.md, services.md); grouped by alternative, a preference where a member is marked optional (§13.7).

  field module ModuleName               # the provider's module name (host contract or deployable, L137)
  field api Hash                        # required contract snapshot (satisfied by lineage membership)
  field version Semver optional         # human-readable hint; no authority
  field uses Hash optional              # Uses metadata blob against the contract (hosts.md §7)
  field alternative Identifier optional # group: records sharing an id need one member satisfied (§13.7)
  field optional Flag optional          # the group is a preference: it may resolve to nothing (§13.7)

scalar Address
  description  An environment address (§4.1): a DNS name or URL prefix, compared as authored — the owns precedent, no canonicalization (environments.md §4).
  validate     address

record Grant
  description  One platform contract this environment supplies (L148, environments.md §4).

  field module ModuleName               # a host-contract module
  field api Hash                        # its snapshot
  field version Semver optional         # human-readable hint; no authority

record Route
  description  A per-requirement routing pin on one deploy (§13.7, environments.md §6).

  field module ModuleName               # the required provider module
  field address Address                 # the candidate binding this requirement resolves to

record Deploy
  description  One release this environment intends to run (L148, environments.md §4).

  field module ModuleName
  field build Hash                      # implementation identity: desired state names artifacts
  field api Hash optional               # snapshot hint; no authority
  field integration Identifier optional # the app section deployed (§13.7)
  field route Route optional repeatable # routing pins (§13.7)

record Binding
  description  One address bound to a provider (L148, L149, environments.md §4).

  field address Address
  field module ModuleName               # provider module: either L137 kind
  field api Hash optional               # selection by lineage constraint (the default form)
  field build Hash optional             # selection by exact implementation identity
  field rank Natural optional           # canonical-resolution preference, lower first (§13.7)

record Artifact
  description  An external pin to this section's closed artifact (§9.4, services.md §4).

  field format Identifier               # e.g. oci-image
  field digest String                   # the foreign store's own content address, verbatim
  field locator String optional         # advisory retrieval hint; no authority

record LibrarySection
  select Universe  # the universe realm this section realizes (§9.4)
  field integration Identifier optional  # the integration realized (§9.5)
  field tree Hash  # Tree metadata blob
  field delete TreePath optional repeatable  # root paths removed in this overlay
  field derivative Hash optional  # canonical derivative artifact (§13.6)
  field requires Requires optional repeatable  # requirements on providers (§9.4)

record HostSection
  field tree Hash  # Tree metadata blob; no requires field — a contract states, it never asks (L135)

record AppSection
  field integration Identifier optional  # the integration realized (§9.4, §9.5)
  field tree Hash  # Tree metadata blob
  field delete TreePath optional repeatable  # root paths removed in this overlay
  field artifact Artifact optional  # closed-artifact pin (§9.4)
  field requires Requires optional repeatable  # requirements on providers (§9.4)

record EnvSection
  field tree Hash  # Tree metadata blob; ancillary content only, MAY be empty (§9.4)

record Library
  description  A library release: composable content in one or more universes (§9.4).

  field owns Namespace optional repeatable
  field resource Resource optional repeatable  # resource/1 claims (§11.4)
  field integration Integration optional repeatable  # alternative build contexts (§9.5)
  field dependency Dependency optional repeatable
  field section LibrarySection repeatable  # first = root (§9.1); keyed (universe, integration)

record HostContract
  description  A host contract: a runtime environment's capability interface (§9.4, hosts.md).

  field section HostSection  # exactly one: L135 by construction

record Deployable
  description  A deployable release: a closed artifact, what it serves, what it requires (§9.4, services.md).

  field integration Integration optional repeatable  # alternative closed builds (§9.4)
  field section AppSection repeatable  # first = root (§9.1); one per integration (§9.4)

record Environment
  description  An environment release: one environment's desired state (§9.4, §13.7, environments.md).

  field grant Grant optional repeatable      # platform contracts supplied (L148, §13.7)
  field deploy Deploy optional repeatable    # releases intended to run (L148, §13.7)
  field binding Binding optional repeatable  # addresses bound (L148, L149, §13.7)
  field section EnvSection  # exactly one: ancillary content, MAY be empty (L148)

record Payload
  field  compression  Identifier          # brotli
  field  length       Natural             # decompressed byte length (enforced)
  field  hash         Hash                # implementation identity (§8.4)

record Signature
  field  signer     String
  field  algorithm  Identifier          # e.g. ml-dsa-65
  field  key        Hash                # public-key fingerprint (§15.3)
  field  value      SignatureValue      # raw bytes in BinTEL; BASE-256 text in renderings

select Universe
  variant  jvm    Flag
  variant  sjsir  Flag
  variant  nir    Flag                  # further universes arrive as schema layers (§9.4)

select Kind
  variant  library  Library             # a library release (§9.4)
  variant  host     HostContract        # a host contract (§9.4, hosts.md)
  variant  app      Deployable          # a deployable release (§9.4, services.md)
  variant  env      Environment         # an environment release (§9.4, environments.md)

select ResourceMode
  variant  export  Flag
  variant  track   Flag
  variant  scan    Flag

document
  field module ModuleName
  field version Semver optional         # absent on development releases (§12.5)
  field tag TagName optional repeatable # immutable user-facing names (§12.6, L142)
  field lineage Hash repeatable         # distinct snapshots, oldest first; last = this release
  field toolchain Tool repeatable
  field source Source optional repeatable  # source identity claims (§17)
  field api Api repeatable
  field profile Profile optional repeatable
  field delta Hash optional             # Delta metadata blob for this lineage step
  select Kind                           # the release's kind: library, host, app or env (§9.4)
  field payload Payload
  field signature Signature optional repeatable  # last member: §15.2 depends on it
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
schema layers; the manifest's schema signature — carried in its BinTEL header (§5.1), and
re-emitted as a pragma line in any TEL rendering — encodes exactly which extensions a file
uses.
Those layers are themselves **shipped through LIRA**: an extension layer is published as a
release of a `tels/1` module ([`tels.md`](tels.md)), referenced as
`‹domain›/‹name›:‹version›` (distribution design §2), its version derived from TEL's own
compatibility relation — so LIRA's extensibility seam is delivered by LIRA itself, with the
same naming, lineage, and verification as any other release. The seam is well-founded
without further rule, at both levels where a cycle could be feared. Composition cannot
cycle: schema references are content hashes (the signature, BinTEL §8), and no hash can
include itself. Acquisition cannot cycle either, though for a different reason — two
releases could each package the layer the other's manifest needs, no hash containing
itself — but a reader decodes only under schemas already in hand (§5.2) and a registry
accepts only releases it can read (§16, **L140**), so every schema a registry holds traces
back, through releases accepted earlier, to the built-in base. A release whose manifest
used the very layer it publishes is no paradox but a brick: unreadable to every consumer,
therefore unpublishable to every registry. The argument leans on one premise worth naming:
that a registry holds only the base and the schemas that arrived through releases it
accepted. A registry configured to admit schemas out-of-band has chosen its own trust root
— its prerogative, and outside this specification's jurisdiction, exactly as publisher key
anchoring is (§15.3).

## 15. Signatures

### 15.1 Algorithms

The default and RECOMMENDED algorithm is **ML-DSA-65** (FIPS 204), identified as `ml-dsa-65`.
The `algorithm` field provides agility; verifiers MUST reject signatures whose algorithm they do
not implement rather than ignore them silently, and MAY be configured to require particular
algorithms. A release MAY carry multiple signatures (co-signing, algorithm diversity).

### 15.2 Signing Domain

The signed message is:

```text
hash("lira/1:manifest", schema-signature ++ BinTEL-root(manifest with all signature fields removed))
```

where `schema-signature` is the manifest's length-prefixed schema signature exactly as stored
(BinTEL §6.1) and `BinTEL-root(…)` is the document-root encoding (BinTEL §7.1) of the
manifest's semantic model under its composed schema. Signing the root under its named schema —
never the text of any rendering — makes signatures immune to re-rendering, and binds the
schema itself: keyword indices mean nothing except under the composed schema, so the
signature covers the schema signature, and through it the schema — the signature is a
function of the composed schema's content (BinTEL §8), which is in any case a published
constant (§5.2). Removing `signature` fields first means signing and
counter-signing never perturb the signed bytes; and since `signature` is the document's last
member, the signing input is derivable from the stored bytes alone — the manifest minus its
BinTEL magic, with the root's trailing signature subtrees dropped and the root child count
adjusted. The message is mode-independent (BinTEL §6), so no future change of stored mode
could perturb a signature. The payload is covered transitively through
`payload.hash`; every metadata blob and section is covered through the hash tower; the magic
numbers, fixed by **L115**, need no coverage.

### 15.3 Keys

`key` is `hash("lira/1:key", public-key-bytes)` over the algorithm's standard interchange
encoding of the public key — for ML-DSA, the X.509 `SubjectPublicKeyInfo` (DER) form. Public-key distribution is out of band (a registry of authorized signers, analogous to
SSH `allowed_signers`, is RECOMMENDED); a future schema layer MAY permit embedding public keys
for trust-on-first-use deployments.

## 16. Verification

Verification is re-execution of the construction, bottom-up. A full verifier, given a `.lira`
file (and, where noted, additional artifacts):

0. checks that the file begins with the fixed eight-byte header (§5.1, **L115**), resolves
   the manifest's schema signature against the schemas it holds (§5.2 — an unresolvable
   signature ends verification: the file is unreadable, not judged), decodes the manifest as
   one embedded BinTEL document conforming to the `lira` schema (**L101**), and checks that
   payload bytes follow (§5.1, **L116**);
1. decompresses the payload within `payload.length` and checks `payload.hash` (§8.4) — a
   receiver applying an increment reaches this step's result by reconstruction instead
   ([`increment.md`](increment.md) §7, **L152**, **L155**), having verified the embedded
   manifest (steps 0 and 8) first, and continues identically;
2. recomputes every blob hash while scanning the stream and checks sortedness and uniqueness
   (§8.2), and resolves every referenced blob (§8.3);
3. checks every tree's path rules and every overlay's minimality (§9.2–§9.3), and recomputes
   every declared derivative hash from the materialized section (§13.6, **L138**);
4. re-atomizes content under each declared discipline and compares against the Atoms blobs —
   a listing that does not recompute is invalid (**L141**) —
   checks the cross-section invariant over each discipline's domain for every section of the
   (realm × integration) matrix (§9.6), that integration declarations are well-formed
   (**L131**) and each is realized (**L133**), that no declared discipline is inapplicable
   (**L127**), that content claiming follows the claiming order (**L134**, **L144**, §11.2),
   that resource declarations are well-formed and effective (**L124**, **L125**, §11.4), and,
   for an `env` release, binding disjointness and route validity (**L148**, **L149**, §9.4,
   §13.7) — the rest of the kind shapes of §9.4 (**L135**, **L143**, L148's record
   exclusions) have held since step 0, discharged by the schema (§14)
   — requires an implementation of each discipline (though `opaque/1`, `resource/1`,
   `capability/1` and `environment/1` are language-blind and implementable by every verifier;
   the last two atomize manifest records rather than tree content, §11.3);
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
cannot implement a declared discipline or profile — or that does not hold a release's
composed schema (§5.2) — MUST reject the release (**L140**) rather than
accept it unchecked — an unverifiable claim is worse than an absent one, because consumers cannot tell the
two apart from the manifest. Every claim in a manifest is thus either recomputable locally or
attested by signature over recomputable claims; nothing is trusted testimony — with two
deliberate, labelled exceptions. A section's `requires` records (§13.3 rule 7,
[`hosts.md`](hosts.md)) are **authorial**: no step above can verify that code needs what it
declares, because a requirement is an assertion about the code's runtime behavior, not a fact
recomputable from its content. What is verifiable is the two ends of the edge — a host
contract's atoms are recomputed from its payload like any release's, and requirement
_satisfaction_ is decided from manifests at resolution time — and the environment itself is
checked at a **third verification moment**: probing at install or launch time (hosts.md §9),
after publish-time recomputation and resolution-time manifest checking. (These three
verification moments are orthogonal to the abstract's two _composition_ moments, build and
deploy: each composition moment draws on all three.) A release's `source` records (§17) are
the second exception, authorial on the same terms — no verification over outputs can decide
which sources produced them — with independent rebuild (§17), rather than probing, as their
check. A deployable release's
served surface, by contrast, is _not_ authorial: its atoms are recomputed from the description
its tree carries (§9.4), like any release's — though what that recomputation proves is the
declaration, and behavior remains behavior (§18).

## 17. Determinism

Producing a release twice from identical inputs **with the same producer toolchain** MUST yield
byte-identical unsigned `.lira` files. To that end: all orderings in this specification are
total (blobs by hash; tree entries by path; atoms by value hash; lineage by history); the
eight-byte header is a fixed byte string (§5.1); no timestamps exist anywhere in the format;
atomization is required to be run-independent (§11.2); producers are required to be
self-deterministic in their compression (§8.1); and the manifest needs no formatting rule at
all: BinTEL is canonical by construction, and the encoding mode is pinned — external-schema,
for the manifest and every metadata blob (§5.1, §8.3) — so one semantic model
has exactly one stored byte sequence.

Two qualifications bound the claim precisely. _Across_ producer toolchains, what is reproducible
is the manifest's stored bytes — BinTEL's canonicality is not per-toolchain — and the
decompressed blob stream — every identity of §6 and
§12 — while compressed payload bytes may differ (§8.1); implementation identity is defined over
the decompressed stream for exactly this reason. And signing is excluded: the default ML-DSA
signing mode is hedged (randomized), so re-signing yields different signature values over the
same signed message; determinism claims apply to the file with its `signature` fields removed,
which is also precisely the signing domain (§15.2). Increment files are excluded altogether:
they are transport, self-deterministic per toolchain but reproducible across none, and carry
no identity ([`increment.md`](increment.md) §8, **L156**).

Reproduction needs the inputs named. A release's `source` records (§14) state, per producer
claim, which sources its toolchain consumed: a scheme (`git-commit`, a tree-hash scheme), the
scheme's own identifier verbatim — on the foreign-identity terms of the `artifact` pin (§9.4,
§18): LIRA does not re-hash other systems' content — and an advisory origin. The record is
**authorial** (§16): no verifier can recompute from outputs which sources produced them, so
the claim is the signer's, strengthened only by independent rebuild under this section's
guarantee, or by attestation, which remains out of scope. What it buys a build tool is the
third cache key (informatively, [`builds.md`](../design/builds.md) §3.1): API identity for
consumers, implementation identity for exactness, source identity for substituting a prebuilt
release for the build of the sources in hand.

Determinism is what makes the implementation identity meaningful and allows independent parties
to reproduce and attest a release.

## 18. Security Considerations

- **Decompression bombs**: bounded by mandatory enforcement of `payload.length` (§8.1); for
  an increment, by bounds derived from the same signed value before any body is decoded
  ([`increment.md`](increment.md) §7, **L152**).
- **Increment tampering**: an increment's header is unsigned, but its manifest is verbatim
  and signed and its result is verified as a payload, so no alteration can yield a release
  other than the one signed; a tampered increment fails (**L155**) rather than misleads.
- **Path traversal**: excluded by tree path rules (§9.2).
- **Hash agility**: deliberately absent within an epoch; BLAKE3-256 is the only hash, and any
  future change is an epoch change (§7.1). Signature agility is present but explicit (§15.1).
- **Substitution attacks**: dependency requirements are by snapshot hash and satisfaction is by
  lineage membership; a registry that verifies lineage steps at publish time (§16) prevents an
  attacker from grafting a hostile "compatible" release onto another module's lineage without
  the signing keys of that module's publishers.
- **No execution surface**: a `.lira` file is binary data beginning with a non-ASCII magic
  number (§5.1); it carries no interpreter directive, is never executable, and producers MUST
  NOT set the executable permission bit. (An earlier draft made files self-executing; retiring
  that removed the format's only execution-adjacent surface.) The `lira` tool MUST still
  treat every file as untrusted data (enforcing §8.1, §9.2 et al.).
- **Guarantee scope**: a grade is a claim at the levels the release's disciplines and declared
  profiles certify (§11.5), and at no others. Tools presenting a grade to a user SHOULD present
  the level with it; a "minor" reported without its level invites a consumer relying on linkage
  to act on a claim that was only ever about recompilation.
- **Behavioral compatibility**: no hash scheme certifies that unchanged signatures have
  unchanged behavior. Patch and minor grades bound _interface_ and _copied-content_ change;
  behavior remains the publisher's promise, mitigated by signatures and (out of scope here)
  attestation of test evidence.
- **Served-surface claims**: a self-described deployable's atoms are recomputed from the
  description it ships (§9.4), which proves what it _declares_ to serve, never that the
  running artifact honors it — behavior, as always (§11.5). The third verification moment
  probes the running instance ([`services.md`](services.md) §8), and tools MUST NOT present a
  described surface as a verified property of the code.
- **External artifact pins**: an `artifact` digest (§14) is the foreign store's own content
  address, trusted on that store's terms, not recomputed under any LIRA domain. Tools resolving
  a pin MUST verify it by the pinned ecosystem's own mechanism (for OCI, digest verification on
  pull) and MUST treat a locator as advisory.
- **Source claims**: a `source` record (§17) is authorial: it binds the signer, not the bytes.
  A consumer substituting a prebuilt release for a from-source build on its strength is
  trusting the signature, and SHOULD prefer independent rebuild (§17) where the stakes warrant
  it; attestation of rebuild evidence is anticipated but out of scope.

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

This is sound _because_ of what the discipline certifies (§11.3): recompilation and TASTy-level
linkage. A consumer's TASTy reference to `c.foo()`, where `C` inherits `foo` from `T`, names the
symbol `T.foo` — the declaring owner — so the atom the consumer's used-set records is the one
that changes if `foo` changes, wherever `foo` was declared. Cross-module hierarchy consistency
then follows from A.2 rather than from redundant keys.

Membership keying would be required for a discipline certifying _classfile_ linkage, where a
call site names the receiver and a type's linkage surface therefore includes members it does not
declare. That is exactly the surface `tasty/1` scopes out to the JVM ecosystem profile
(Appendix D), so the keying choice and the guarantee claim stay consistent: the discipline keys
the way the representation it atomizes actually references things, and the profile covers the
representation that references them differently.

## Appendix B (Informative): Worked Example

The manifest below is shown in its canonical TEL rendering (§5.3, TEL §22.3) — what the
`lira` tool prints; the stored form is the LIRA header followed by the equivalent
external-schema BinTEL document, the Brotli-compressed payload beginning at the byte after
its last.

```text
tel 1.0  <lira schema signature>

module gossamer-core
version 1.2.0
lineage Kx3f…
lineage Lm81…
lineage Pq44…

toolchain
  name scala
  version 3.9.0-RC4-p6

api
  discipline tasty/1
  atoms Vw12…

api
  discipline resource/1
  atoms Wz34…

profile
  id jvm/1
  breaks linkage

delta Xy56…

library
  owns gossamer

  # mode     # path
  resource export     gossamer/text-tables.conf
  resource scan       gossamer/templates

  integration
    id rudiments2
    rank 0
  integration
    id rudiments1
    rank 1
    label  built against the rudiments 1.x line

  # module              # api     # version
  dependency anticipation-core      Ab12…     1.4.0
  dependency rudiments-core         Cd34…     2.0.1
    integration rudiments2
  dependency rudiments-core         Ef90…     1.9.4
    integration rudiments1

  section jvm
    integration rudiments2
    tree Ef56…
    derivative Tu78…
    requires
      module posix
      api Wx56…
      uses Yz78…
  section sjsir
    integration rudiments2
    tree Gh78…
  section nir
    integration rudiments2
    tree Ij90…
    delete gossamer/JvmOnly.class
  section jvm
    integration rudiments1
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
  value  <BASE-256 signature>
```

Reading this manifest alone, a tool can determine: the module's API history (three snapshots,
two minor steps); that it satisfies any dependent requiring `Kx3f…`, `Lm81…` or `Pq44…`; which
universes it supports; that it offers a `jvm` build against the `rudiments` 1.x line as well as
the preferred one, so a buildpath pinned to `Ef90…` resolves without a second artifact, while
`sjsir` and `nir` are offered only under the preferred integration; that the `nir` view omits one
root file; that the resource `gossamer/text-tables.conf` is contractually present on every
universe's classpath; the hash of the classpath JAR each `jvm` section derives, which identifies
which integration a bare JAR is; that only its `jvm` implementation shells out, needing a
`posix` host providing exactly the commands `Yz78…` names — satisfiable by any contract
covering them (hosts.md §7), probed at launch (hosts.md §9); that the last step, though a
minor by the atom
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
Appendix A it is informative, and [`jvm.md`](jvm.md) states it normatively. It is
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
