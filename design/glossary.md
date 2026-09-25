# Glossary

Terms used across the LIRA specification and the build tool design, defined briefly and
cross-referenced. "Spec" is [`lira.md`](../spec/lira.md); "builds" is
[`builds.md`](builds.md).

**assemble** — Build-file keyword naming the buildpath an egress closes over on an
application module. Compiles to no dependency record (L143): it marks egress input, not
composition.

**atom** — The hash of one indivisible fragment of public API, produced by a
discipline. *Rigid* atoms are monotonic within a lineage; *replaceable* atoms (inline
bodies, macros) may be replaced. Compatibility is set arithmetic over atoms — the whole
algebra rests on this.

**binding** — An environment record tying an address to a provider module, with a
selection by `api` (lineage constraint) or `build` (exact). One of the two record kinds
that enter an environment's atoms.

**buildpath** — The set of releases a module is built against, validated by seven rules
(uniqueness, disjointness, closure, compatibility, coherence, provider requirements) —
all decidable from manifests alone (spec §13.3).

**carrier** — The interface-bearing artifact kind a discipline canonicalizes: TASTy,
`.d.ts`, WIT, a classfile signature. Disciplines are named for carriers. A form may
contain several carriers.

**case** — One value of a selection axis (`integration` or `option`) in the build file;
also the keyword introducing it. Cases group under an axis; peer axes form a product.

**cell** — One entry of a release's section matrix: the section for one
(universe × integration) pair. Each cell has its own tree and requires.

**component** — (1) Of an edge: a separately-versioned constituent (the `scala-js`
plugin of scalac's `sjsir` edge), recorded per cell (builds §14.2). (2) Of a TEL
schema: one hashed element of its composed sequence (base or layer); `tels/2` atomizes
the composed schema those components produce, not the components themselves.

**composition** — The genus of the buildpath and the environment (spec §4.2): a finite
set of nodes with their groups, judged over a **state** — the edges exercisable while it
holds. The buildpath searches its edge set, hypothesizes its providers, materializes and
is closed over; the environment's edge set is authored, nothing materializes or closes,
and it is itself a release. Neither is a special case of the other.

**context** — (1) Edge role: dependency cells a tool reads but does not consume
(builds §14.2). (2) The payload the build tool passes a tool at invocation: inputs,
context cells, presumptions, cell coordinates, settings.

**coordinate** — `<domain>/<name>`: a module's globally-resolvable name, the domain
DNS-proven. Manifests carry bare module names; the domain lives at resolution.

**deliverable** — A pair of a closed artifact format and a host contract:
`jvm-app`, `js-app`, `xeq-bundle`, `oci-image`, and the triple-parameterized families
`native-exe/<triple>`, `native-image/<triple>`, `oci-image/<platform>`. What an egress
produces. A registry object, never a manifest object. Distinct from **deployable
release**, the `app`-realm release that carries one.

**dependency** — A buildpath edge: content that materializes and composes into the
consumer. Satisfied when the required snapshot appears in the candidate's lineage.
Contrast **requires**.

**deployable release** — An `app`-realm release: a closed artifact (or artifact pin)
plus its requires and served surface. Declares no dependencies (L143) — its composition
already happened at the egress.

**development release** — A release without a version, identified purely by hashes: the
currency of the inner build loop. Depending on one exactly (a build pin) makes the
dependent unpublishable until the pin lifts (L118).

**discipline** — A canonicalization of one carrier into atoms, identified as
`<name>/<version>` (`tasty/1`, `openapi/1`, `tels/2`). Any change to the
canonicalization is a new discipline; two versions of one discipline stand in no formal
relationship (see **dual-declaration bridge**).

**dual-declaration bridge** — The migration path between discipline versions: a release
declares both, graded minor on the way in, major when the old one drops; the grade of
the union is the maximum of the per-discipline grades (spec §11.1, non-normative).

**edge** — One invocation kind of a tool: a hyperedge of the pipeline DAG with roles
*input*, *context* and *output*, named by its output form (`scalac/sjsir`). Edge kinds
(compiler, egress, join, packaging) are derivable from the node kinds at its ends.

**egress** — A linking edge from a universe to a deliverable: the step that
*closes over* a buildpath and produces an artifact. A library never chooses its egress;
an application does.

**emit** — Of an edge: the carriers it produces (`emits tasty/1`). (Its former
build-file sense — writing a derivative to disk — is superseded by **extract**.)

**extract** — Command step (and CLI operation): save a named entry — an artifact's
deliverable, or a container format naming a §13.6 canonical derivative — from the
hidden store into the project tree at a fully-authored path. The one way content leaves
the store; a destination matched by any source glob is an error (feedback loop).

**environment** — An `env`-realm release stating desired runtime state: grants, deploys,
bindings. In the build file, a delta-variation nested in a **topology**. Its atoms are
exactly its bindings and grants — the topology is its API, deployment its
implementation.

**ephemeral** — Build-file flag marking a declaration (universe, case, cell) as
source-only: excluded from the published payload, rebuilt on demand, checkable against
the source record.

**folding principle** — The discipline-design rule that additions which are safe become
standalone atoms while additions which can break fold into their parent's value — so
grading is set arithmetic, never a rule engine.

**form** — A node of the pipeline DAG: the kind content is *in* between tool
invocations. Source forms (`scala`, `java`, `dockerfile`), universes, and deliverables
are its species. One namespace; source forms avoid universe names.

**grade** — The computed relation between successive releases: *patch* (same atoms),
*minor* (rigid growth), *major* (anything else; a new lineage, behind L110's explicit
gate). Versions are derived from grades, never chosen.

**grant** — An environment record: a platform contract the environment supplies
(a JDK, a database's protocol, a config contract). Provision, not requirement — the
satisfied side of deployed releases' requires.

**guarantee** — (1) Build-file keyword, the dual of `presume`: a provision declared by
a topology, a machine (`local.tel`), or a packaging module — probe-verified, never
inferred from demand. (2) In the spec's sense: the certification level of a discipline —
presence, linkage, recompilation, or behavior (spec §11.5) — each the level one kind of
**juncture** requires; a judgment is a claim only at certified levels (L151).

**edge** — The standing relation between a requirer and a provider (spec §4.2): a
**group** of ordered alternatives on one side, an offer (lineage, atom set) on the other,
compatible by snapshot ∈ lineage or used ⊆ atoms. A *dependency* edge resolves by module
name and materializes; a *requirement* edge resolves by satisfaction and does not. Not to
be confused with a tool edge (below), a hyperedge of the pipeline DAG.

**juncture** — One exercise of an edge at an instant (spec §4.2): *construction* (the
requirer built against the provider; the required snapshot is its memory), *linking*
(bound together without reconstruction), *operation* (each call). The juncture fixes the
guarantee level a judgment requires. Derived in [`junctures.md`](junctures.md).

**medium** — The space in which offers are placed and an edge's provider end is resolved
(spec §4.2): a universe, resolving by module name with disjoint linkage names (spec §13.3
rules 1–3), or an environment's addresses, resolving to a binding (L149). Carries a
composition's only non-edge-local rules.

**host** — Anything code runs *on* that supplies capability without composing: a JDK, a
browser, an OS+libc — and, reflexively, the build tool itself (to its tool plugins) and
a running service (to its consumers).

**host contract** — The published, verifiable statement of a host's capability
interface, as a `host`-realm release with a lineage. Required via **requires**,
supplied via **grant**, coordinated across modules by tags (`jdk-19`).

**hyperedge** — See **edge**: tool edges take multiple inputs in distinct roles, so
path resolution is search over hyperedges, with parameters (a triple, a platform)
unified along the path.

**implementation identity** — `payload.hash`: the identity of a release's exact bytes.
The lockfile's currency, the deploy pin's target, and the discriminator between patch
siblings. Contrast **snapshot**.

**include** — Build-file keyword for composition: a dependency on a project-local
module or an external coordinate with a selector. Compiles to a `dependency` record.
Its subtractive counterpart is `exclude`.

**input identity** — The hash of a release's full input closure (sources, per-cell
tool identities and output-affecting settings, dependency implementation identities,
the guarantees document, case coordinates), carried as a `source` record under scheme
`inputs/1`. Computable without building: the memoization key of the whole build.
Governed by the hashability law — output-affecting inputs must have canonical byte
encodings and reach tools only through declared, hashed channels.

**integration** — One alternative dependency vector a release was built against
(spec §9.5), selected by resolution (canonical assignment), invisible to API identity.
In the build file, an axis of cases whose peer product flattens to composite ids.

**join** — An edge kind where a second universe's content merges into an application
at egress (a JS bundler joining `js` content into a `js-app`).

**lineage** — The ordered, distinct snapshots of one major series of a module: its API
history. Satisfaction of dependencies and requirements is lineage membership; a major
begins a new lineage.

**lockfile** (`build.lock`) — The build's world document: selector resolutions frozen
as signed release records with inclusion proofs. A verifiable memoization with
authority over exactly one thing — when the world was sampled. Singular per project;
plural only along the world dimension (a canary lock).

**manifest** — The BinTEL head of a `.lira` file, rendered by tools as canonical TEL:
identity, lineage, toolchain, sources, atoms, dependencies, requires, sections, payload
identity, signatures. Every buildpath and environment judgment is decidable from manifests alone.

**module** — (1) LIRA: a named library, host contract, deployable, or environment — one
API lineage, many releases. (2) Build file: a buildable unit within a project,
compiling to releases of a LIRA module.

**option** — A build-file axis of implementation-only variation (debug/release,
instrumentation): each combination is a separate release in patch relation, selected by
implementation identity, never a section. Contrast **integration**.

**overlay** — A section expressed as a delta on the root section: deletes plus
replace/add (spec §9.3). The build file's universe blocks and case refinements are its
authoring-time face.

**payload** — The Brotli-compressed, content-addressed blob stream beneath a manifest;
its hash is the release's implementation identity.

**presume / presumption** — Build-file keyword: a guarantee code assumes about its
runtime (`envvar`, `command`, `file`, `dataset` kinds), governed by presumption
disciplines; compiles to requires+uses, aggregates through egresses, and licenses
compiler totality for configuration-class failures only.

**probe** — A discipline-carried runtime check of a declared capability or guarantee
(`command -v git`), never atomized. Run at environment startup and, for packaging,
inside the built image — the third verification moment.

**profile** — An ecosystem-wide predicate set imposed over a whole buildpath
(spec §11.6, §13.3 rule 6), including predicates over toolchain records — e.g. TASTy
mutual readability.

**project** — A build-file grouping of modules; authoring-time only, appearing in no
manifest.

**realm** — The section-key axis: the universes plus `host`, `app` and `env`. The genus
of which **universe** is the composing species.

**reference** — `<domain>/<name>[:<selector>]`: LIRA's general versioned-artifact
syntax. Selector-form references match only published releases; bare references are
local-only development references.

**release** — One immutable, signed published state of a module: manifest plus payload.
The unit of every judgment.

**repository** — Build-file declaration of transport and scope for source dependencies
— never identity or version authority. Locally overridable in `local.tel`.

**requires / requirement** — An edge to a *provider* (host contract or deployable):
the environment supplies it, nothing materializes, probes verify it. Same satisfaction
relation as dependencies; different everything else. The guardrail: one surface may
present both edge kinds, but they compile to distinct records.

**section** — One compiled view of a release, keyed by (realm, integration). The first
is the root; the rest are overlays on it. See **cell**.

**selector** — The release-choosing half of a reference or `include`: a version prefix
(digit-first) or a tag (letter-first), disjoint by first character; resolved once, then
held by the lockfile.

**serve / served surface** — A deployable's self-description (an OpenAPI document, a
tool's descriptor), placed in its tree and atomized — the atoms that satisfy consumers'
requirements. An empty atom set can satisfy no requirement.

**snapshot** — The hash of a release's sorted atom set: its API identity. Lineages are
sequences of snapshots. Contrast **implementation identity**.

**source form** — See **form**: the form of human-written content, named for its
language, inferred from extensions, declarable with a `form` child.

**source record / source identity** — The manifest's authorial statement of which
sources a release is the build of (scheme, digest, origin hint), checked by independent
rebuild. What makes prebuilt-versus-rebuild a cache decision and `ephemeral` sound.

**spanning** — Substitution licensed by used-sets: if a consumer's used atoms are a
subset of a candidate's atom set, the candidate satisfies — across majors and across
modules. What makes mocks, forks and alternative implementations expressible.

**store** — The single durable, machine-wide, content-addressed store (`tool.md`,
spec §13.5): fetched sections and every tool output — intermediates included — as
trees of blobs keyed by hash. Its location is a machine fact (`local.tel`); retention
is LRU. Contrast **workspace**.

**steward / stewarded namespace** — A publisher key the index transparently designates
for a namespace whose owner has not claimed it (Adoptium's JDK contracts), superseded
by a genuine DNS proof. The bootstrapping mechanism for vendor contracts.

**tag** — A signed, immutable, user-facing name on a release (`jdk-19`); resolvable to
a snapshot, carrying no algebraic authority. Coordinates contract families across
modules.

**tool** — The implementation/distribution unit of the pipeline: built-in, or a LIRA
release implementing the `lira.tool` contract (a service whose host is the build tool).
Carries edges; described by an extracted descriptor; configured in a toolchain.

**toolchain** — A build-file mapping from edges to tools, at most one tool per edge;
child toolchains are deltas that shadow edges and inherit the rest; selected per
project or per module with `apply`.

**topology** — The build file's declaration of a service architecture — services,
requirements, served surfaces, addresses, guarantees — which its child environments
instantiate as deltas. Corresponds to the atomized surface of env releases.

**tree** — The content of a section: content-addressed paths to blobs, materialized
into caches and serialized canonically into derivative artifacts.

**universe** — A realm in which independently-published libraries compose (`jvm`,
`sjsir`, `nir`; `js`, `klib`, `wasmc`, `native/<triple>` reserved). That litmus
test is the whole definition. A species of **form**.

**workspace** — A per-invocation, ephemeral view: the invocation's declared inputs
materialized from the store at their authored relative paths; outputs ingested back;
then destroyed (retained automatically on failure, re-materializable on demand).
Inter-tool data flows output tree → store → next workspace, never through a shared
directory. Its absolute location is the tool's, never the user's (builds §14.5).

**used-set / uses** — The atoms of a provider a consumer actually uses, computed by
tooling (at its strongest, by the compiler itself), recorded on the requiring edge.
The license for **spanning**.

**validity** — The one algebra, over a **state** of a composition (spec §4.2): every
group resolved to a compatible member, the medium's rules, profile coherence — with
aggregation the conjunction grouped by resolved provider. Run at three sites: the
buildpath, the environment, and the packaging edge (an image being a frozen
environment-of-one). A **transition** between states is valid iff every state it passes
through is (L146).

**version** — A derived, optional, strictly `x.y.z` label computed from grades; a
human-readable hint with no authority. Consumers decide everything on hashes.
