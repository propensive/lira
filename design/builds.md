# Builds: Source, Artifact, and the Continuum to Deployment

A build tool is planned that will work with LIRA bundles: it reads a definition of projects
and their modules; a module's dependencies are fulfilled either by prebuilt `.lira` files or
by building from source against another build definition; one build may define several
variants, packaged into one multi-variant `.lira` file or pinned individually downstream; and
the same definition language should extend to describing a deployment of services. This
document is not that tool's design. It audits the vision against LIRA as specified, derives
the one demand the vision places on the format that it cannot yet meet, and develops the
build-to-deployment continuum on the foundations of [`services.md`](../spec/services.md) and
[`execution.md`](execution.md).

## 1. The premise: a `.lira` file is a verifiable cache of a build step

The vision's core move — *create an artifact from a module definition and its inputs, or
simply use a prebuilt LIRA artifact containing the same information* — is not something LIRA
must be bent to accommodate. It is the reading the format was built for, and it rests on
three facts of the spec:

- **No rule ever asks how a release came to exist.** Dependency satisfaction is lineage
  membership over snapshots (spec §13.2); requirement satisfaction is the same relation
  (hosts.md §7). A release built from source five seconds ago and a release fetched from a
  registry are indistinguishable to every judgment in the algebra, provided their manifests
  say the same things — which is precisely the substitutability the build tool wants.
- **The cache has two keys, and both are in the manifest** (spec §6): API identity (the
  snapshot) answers "is this compatible where I need it?", and implementation identity (the
  payload hash) answers "are these the exact bits?" — the compatibility decision and the
  lockfile entry, respectively.
- **The cache is checkable, not trusted.** Determinism (spec §17) makes the same sources
  under the same toolchain yield the same unsigned bytes, so a build tool can always fall
  back to rebuild-and-compare; and every claim in a manifest is recomputable or signed
  (spec §16), so *using* a prebuilt file means verifying it, not believing it.

A `.lira` file, on this reading, is a **memoization of a build step whose cache key is
carried inside it and whose correctness is recomputable**. "Use prebuilt or build from
source" is then not a mode switch but a cache decision, made per module, per build.

## 2. The audit

Each element of the vision, against the mechanism that already carries it:

| Build-tool concept                          | LIRA mechanism                                        |
| ------------------------------------------- | ----------------------------------------------------- |
| module definition                           | module; one release per built state (spec §4.2)       |
| dependency on a prebuilt bundle             | `dependency` record, resolved by snapshot via the index ([`distribution.md`](distribution.md)) |
| dependency built from source                | development release (spec §12.5) + `build` pin (§13.2, L118) |
| promotion of a from-source build            | version assignment (§12.5): payload untouched, manifest signed |
| variant: alternative dependency versions    | integrations (§9.5)                                   |
| variant: platforms                          | realms — universe sections (§9.4)                     |
| multi-variant `.lira` file                  | the section matrix, (realm × integration), L131; one API everywhere, L108 |
| downstream pins one variant                 | integration pinning; canonical assignment (§13.3)     |
| build outputs `.lira` files                 | library releases; conventional artifacts recoverable per §13.6 |
| build outputs other targets                 | egresses to deliverables (§4.1); deployable releases (§9.4) |
| "snapshot of the build state for a module"  | the manifest: toolchain records, dependency snapshots, atoms, payload identity |
| build feeds deployment                      | aggregated requirements seed the deployable's `requires` (hosts.md §10, services.md §4.1) |
| running modules are "services"              | services.md, by that name                             |
| coherent deployment over services           | environment validity (spec §13.7, L145)               |

Two rows deserve their footnotes. The from-source row is *gated correctly by publication
rules the spec already has*: a module depending on an unpublished from-source build carries
a `build` pin and is itself unpublishable (L118), and publishing demands every required
snapshot appear in a published lineage (L119) — so the build tool's inner development loop
and its publication step are separated by exactly the fence the format already erected. And
the "snapshot of build state" row should be read precisely: the manifest is the complete
record of what a build *produced* and *consumed at the interface level* — it is not, and
should not become, a cache of intermediate build state, whose home is the tool.

## 3. Where the fit is imperfect

### 3.1 Source identity — the one genuine gap

"Use a prebuilt artifact containing *the same information*" — the same as what? The build
tool holds sources and a definition; the candidate `.lira` file holds outputs. Today the
format offers two ways to connect them, and each has a cost: **rebuild and compare** (§17 —
sound, but spends the very build the cache was meant to save) or **trust a lockfile** of
implementation identities (sound against tampering, but it records that *someone once
decided* this artifact matches, not that it matches these sources). What is missing is the
manifest saying, checkably-by-signature, *which sources it is the build of*: a **source
identity** — a hash of the source tree, or a VCS commit, per toolchain record.

This is exactly the provenance attestation the spec has twice deliberately deferred (§9.4's
"a question of provenance attestation, deliberately out of scope"; services.md §4.1). The
build tool is its first concrete customer, and the deferral should now be read as *queued*
rather than parked. The shape it wants is small: a `source` record beside `toolchain` —
source-tree hash, optional VCS coordinates — entering no atom (provenance is not interface),
authorial in the same honest sense as `requires` (no verifier can recompute it from the
payload; a signer vouches for it; an attestation layer can later strengthen it). With that
record, "is this prebuilt file the build of these sources?" becomes one hash comparison, and
the memoization of §1 acquires its missing cache-key component: **API identity for
consumers, implementation identity for exactness, source identity for substitution.**

> **Status.** Adopted since written: the `source` record stands in spec §14 and §17, on
> exactly these terms — foreign identity verbatim, atom-free, authorial per spec §16, with
> independent rebuild as its check (spec §18).

> **Addendum: the input identity, closing the remainder.** Source identity
> under-determines the output: the same sources under a different output-affecting
> setting, a different edge component, or a different dependency *implementation* (the
> manifest records dependencies by snapshot, never payload) yield different bytes. The
> full memoization key of §1 is the **input closure**, and it needs no new record kind —
> the `source` record's scheme vocabulary is open, so the addition is one scheme and one
> law. **Scheme `inputs/1`**: the digest of a canonical BinTEL document (`lira-inputs`,
> a sibling of `lira-uses`) listing, sorted and per cell: source digests; tool
> identities with output-affecting settings and components; dependency
> module→payload-hash pairs; the guarantees document hash; the case coordinates. It is
> computable *without building* — a function of inputs, not outputs — making it the
> cache key §1 always wanted: compute, look up, skip the build. Verification is
> three-tier: authorial (§16), consistency-checkable from the same inputs without
> compiling, bindingly verified by §17 rebuild. **The hashability law**: every
> output-affecting input MUST have a canonical byte encoding, and a tool may receive
> output-affecting data only through declared, hashed channels — the descriptor's
> `affects output` classification implies hashability, and ambient inputs are excluded
> by construction: hashability and hermeticity are the same requirement. A release may
> thus carry arbitrarily many relevant hashes — payload, per-section derivatives,
> per-scheme sources, `inputs/1` — each answering a different question with the same
> mechanism.

### 3.2 Variant axes beyond the matrix

"Different version dependencies, different platforms, *etc*" — the *etc* matters. The
section matrix has exactly two axes, and on principle: a section is keyed by *where its
content composes* (realm) and *what it was built against* (integration). A variant that
changes neither — a debug build, an instrumented build, an optimization level — has no cell
in the matrix, and should not be given one: it presents the same API built against the same
dependencies, so by spec §6 and §12.3 it is a **separate release in patch relation** to its
sibling — same atoms, same snapshot, different payload — distinguished and selected by
implementation identity: a `build` pin during development, an `artifact` pin or a binding
selection (execution.md §5) at deployment. One `.lira` file therefore holds one such
variant, and a "multi-variant file" spans the matrix only. This is an answer, not a
limitation: the matrix axes are the ones consumers must *choose between to compose at all*;
implementation-only variants are the ones consumers must be *unable to observe*, which is
what patch relation means.

### 3.3 Cycles, and where they are legal

Nothing in §13.3 forbids two library modules from depending on each other — but
*construction* effectively does: mutual `dependency` records name each other's snapshots,
which only co-publication of both releases can arrange, and grading each against its
predecessor becomes entangled. The build tool SHOULD keep its module graph acyclic and the
format will never object. Deployments are the opposite, by design: requirement edges between
services may form cycles, and environment validity minds not at all, being a predicate over
a set rather than a resolution order (services.md §5). The asymmetry has a one-line cause:
builds must *terminate* in an artifact; environments must merely *cohere*.

## 4. The continuum to deployment

The vision's second half — the build definition generalizing to a deployment definition — is
the genus of execution.md §2 seen from the tool's side: one definition language, two species
of composition. What the tool's language can unify, and what it must keep distinct beneath
the surface:

- **Edges compile to two kinds.** A module's "depends on" clause targeting a library becomes
  a `dependency` record: the buildpath supplies it, it materializes, it composes into the
  artifact. The same clause targeting a service or a platform capability becomes a
  `requires` record: the environment supplies it, nothing materializes, probing verifies it.
  The surface syntax may be one word; the compiled records must not be, because the three
  criteria that separate them (services.md §2.1) are what the whole deployment algebra
  stands on. A build tool that flattened the distinction would rediscover it as bugs.
- **A module becomes a service by crossing an egress.** The continuum is concrete: a module
  definition (sources, dependencies, variants) gains an egress target, a served-surface
  description, and requirements — and its build now emits a deployable release (services.md
  §4) instead of, or beside, a library. "Running modules are services" is thus already the
  spec's own vocabulary, and the build file's service stanza is the operator-facing syntax
  for what services.md §4 specifies.
- **The deployment definition is the environment manifest.** A deployment stanza —
  services, their bindings, the platform contracts of the target environment — is precisely
  the desired-state document of execution.md §5–§6, and the build tool that emits it is
  acting in the operator role (execution.md's third signature). The feed-forward is
  mechanical: the buildpath's aggregated requirement set seeds each deployable's `requires`
  (hosts.md §10), and the deploy records plus bindings are authored from the deployment
  stanza.
- **"Closing over a set of services" needs its precise reading.** In LIRA's vocabulary,
  *closing over* is what an egress does to a buildpath — and nothing ever does it to an
  environment (execution.md §1). The coherence the deployment definition needs is
  **closure** in the §13.3/§13.7 sense: every requirement provided, every provider
  satisfying, checked over the whole set (L145) and re-checked at every transition (L146).
  The build tool gets its guarantee — no service deployed into a set that cannot support
  it — from validity, not from closure-into-an-artifact, and the two words should not be
  allowed to blur, since one produces a thing and the other sustains a state.
- **The pin symmetry completes the continuum.** Downstream module pins an integration
  (§13.3); a deployment pins a binding selection (execution.md §5). Both are the consumer's
  side of a choice the manifests cannot imply, expressed in the same place the choice is
  judged.

## 5. Open questions

1. **The `source` record's shape** (§3.1): tree hash vs VCS coordinates vs both; per-release
   or per-toolchain; and its relation to the eventual attestation layer (signed rebuild
   evidence). The recommendation here is the minimal authorial record now, attestation
   later — the same staging `requires` itself went through.
2. **Projects.** The build tool groups modules into projects; LIRA has no such object, and
   probably needs none — a project is a build-time affordance, and module coordinates are
   already namespaced by domain (distribution.md §2). Confirm nothing in the multi-module
   build (shared versioning? shared signing?) leaks a project concept into the format.
3. **The lockfile.** Is it anything more than implementation identities plus index inclusion
   proofs (distribution.md §8)? If not, the build tool gets its lockfile from the
   distribution design for free, and should not invent a second format.
4. **Co-publication.** Whether mutually-dependent library releases (§3.3) ever deserve
   real support (atomic multi-release publication) or remain formally-possible-and-
   practically-discouraged.
5. **Whether the deployment stanza emits the environment manifest directly or a delta** —
   given environment overlays (execution.md §8), a build might naturally emit "prod plus
   these three changes," which is the overlay calculus applied at authoring time.

## 6. Spec impact (if adopted)

Almost none immediately — which is the audit's finding: the vision is what the format was
shaped for, and §2's table is citation, not construction. The motivated changes:

1. **Promote provenance from "deferred" to "queued"**: a `source` record (schema layer)
   beside `toolchain`, authorial, atom-free (§3.1) — the one addition the build tool cannot
   work well without. *(Adopted: spec §14, §16–§18.)*
2. **A terminology note** in the spec's taxonomy when the genus lands (execution.md §10):
   the two compiled edge kinds under one surface syntax, and the closure/closing-over
   distinction of §4 — one sentence each, to keep tool documentation from bending the
   format's words. *(Landed with the genus: spec §4.2's **Composition** entry carries both.)*
3. **Section-scoped toolchain records** (§7.3): the manifest's `toolchain` field is
   release-global, but per-compiler integration cells are *defined by* different tool
   versions, and profile predicates over mutual readability (spec §13.3 rule 6) need to
   discriminate cells, not releases. Either `Tool` records gain section scoping or the
   `Section` record gains its producing tools. The same change is where output-affecting
   key-value settings (`encoding UTF-8`, `-release 19`) find their missing home — `Tool`
   today carries only name, version and bare flags, yet option cases (§7) are *defined
   by* setting differences the manifest cannot currently record.
4. **Bare-name provider identity** (distribution): `dependency`, `requires`, `grant` and
   `binding` records all carry bare module names; the domain of the coordinate exists only
   at index resolution. Two domains publishing one module name are indistinguishable in a
   manifest.
5. **Stewarded namespaces** (distribution): vendors like Adoptium will not publish LIRA
   releases or DNS namespace proofs. A stewardship record in the index log — the index
   transparently designates a publisher key as steward of an unclaimed namespace, with
   resolutions carrying stewarded status and a genuine DNS proof superseding it — follows
   the same staging precedent as the `source` record.
6. Everything else in this document is build-tool business, and stays here.

## 7. The configuration space

The build-file exercise (`build.tel`) forced a general account of build variation, and it
reduces to one discriminator: **what a consumer can observe**. Every setting or selection
axis a build file can express falls into exactly one of four classes, and each class has
one LIRA compilation:

0. **Changes nothing in the artifact** (side effects only: `verbose`). Not a variation
   axis at all: invocation configuration on the tool definition or command, and never
   manifest content — recording it would make byte-identical builds look differently
   configured under §17 comparisons. Which of a tool's settings are output-affecting is
   per-tool knowledge, carried by the tool definitions in the pipeline registry
   (universes.md §6).
1. **Changes what a consumer must reason about to compose** — the dependency vector, or
   an interface-format-affecting tool version (§7.3). An **integration** (§9.5), selected
   by dependency resolution: canonical assignment (§13.3), with closure rule 4 selecting
   automatically when only one candidate backend is on the buildpath.
2. **Changes implementation only** (output-affecting tool settings, API-preserving source
   deltas). A separate release in **patch relation** (§3.2 of this document): same atoms,
   same snapshot, different payload, selected by implementation identity — never a
   section. In the build file, an `option`.
3. **Changes the API.** Forbidden within a release (L108): a different lineage or module,
   rejected when atoms differ post-build.

The surface consequence is two axis kinds with one shape (`integration`/`option`, each
grouping `case` blocks), plus universes; peer axes form a product, flattened at compile
time to composite integration ids ranked lexicographically by declaration order — a tool
convention, requiring nothing of the format, since integration ids are flat. Nesting is
governed by **declaration versus refinement**: a declaration nested inside another axis's
case exists only within that selection (matrix holes elsewhere — legal, §9.5); a nested
block naming an already-declared axis refines the intersection with deltas. Conjunctive
scoping makes either nesting order meaningful, with two lints: one intersection refined
from one direction only, and delta conflicts an error, never a merge.

### 7.1 Source-only combinations cost the format nothing

Axes multiply combinations; distribution need not carry them all. An undistributed
combination is a cache miss under §1's reading — rebuildable, and checkable against the
`source` record (§17). Patch-sibling combinations (class 2) were separate releases anyway;
for integrations, a published release declares only its shipped vectors (L133), and a
from-source build of an unshipped vector is itself a patch-relation sibling release
carrying that integration, since integrations enter no atom. The build file's surface is
an `ephemeral` flag: everything declared is distributed unless marked, the marker sits on
the declaration itself (or on a single cell via refinement nesting), so no combination is
ever named twice. It is pure distribution policy, with no format mechanism behind it —
the strongest single confirmation of §1 the exercise produced. The trade-off of the
distributed-by-default polarity is that adding an axis silently multiplies the payload,
so the tool reports the shipped-combination count as a lint.

### 7.2 Multi-target axes are the escalation of last resort

For targets with graded contract lineages (JDKs; Scala compiler lines), three mechanisms
apply in strictly escalating order, and the cheap ones usually suffice:

1. **Build against the oldest target**: newer targets satisfy by lineage membership
   (hosts.md §7). One cell, no axis.
2. **Span a lineage break**: where a target's major removed API the module never used,
   `used ⊆ atoms` licenses the same single build across the break (§13.4).
3. **An integration axis**: only when the built-against state genuinely differs per
   target — a backport dependency present on one JDK, a different compiler line — and
   with L108 binding throughout: per-target API differences are a different lineage, not
   an integration.

### 7.3 Scala compiler versions: the axis is an integration, the semantics are a profile

A Scala compiler version is consumer-observable — a 3.9 consumer cannot read 3.10-emitted
TASTy though the API is identical — so it cannot be *just* an integration: integrations
are interface-invisible (§9.6) and canonical assignment never consults the consumer. The
spec's own answer is rule 6 of §13.3, whose text names this very case: a profile predicate
over `toolchain` records — *mutual readability of metadata format versions*. The
readability relation (patch-stable within a minor; forward-only across minors; LTS
carve-outs; `experimental` flags) lives in the profile definition, decidable from
`Tool.version` and `Tool.flag`, and because assignment search respects all seven rules,
it filters the cells a consumer's toolchain can read; canonical assignment lands on the
first readable case. The axis multiplexes; the profile decides.

Two format pressures follow, recorded in §6: **section-scoped toolchain records** (the
manifest must be able to say which cell is 3.9-TASTy), and a discipline requirement that
**`tasty/1` atomize semantically**, normalizing compiler-encoding differences so that
cross-built cells share one API identity, or L108 fails spuriously. And one classification
refinement: the compiler version is the one tool setting that belongs on an integration
case rather than an option case, because it changes the emitted interface encoding — the
lint is "interface-invisible settings in an integration case", not "any setting".

## 8. Re-lowering from TASTy

A `jvm`/`sjsir`/`nir` section already stores `.tasty` beside its lowered artifacts,
content-addressed in the payload. If the compiler can regenerate output from TASTy alone,
the bundle's TASTy is a **portable intermediate source**: a cache-refresh path that stays
inside the bundle — no repository, no source fetch, no trust question, cheaper than
recompiling `.scala` because typing is done, and deterministic under a pinned compiler.

The algebra blesses the operation specifically. Re-lowering does not touch the TASTy, so
the atom set — and the snapshot — is unchanged: the result is a **patch-relation sibling**
of the published release, the category consumers are defined to be unable to observe.
Substituting a re-lowered dependency is safe by construction. In pipeline terms it is an
intra-universe edge in the toolchain DAG (`tasty → classfiles`, jvm to jvm), a registry
entry on the same footing as any other tool edge (universes.md §4, §6); the derivation is
a deterministic function of (section tree, compiler version), so the materialization
cache (§13.5) keys it naturally, and the online service could serve pre-lowered, attested
variants. The re-lowered artifact's implementation identity differs from the published
release's, and lockfiles pinned on `payload.hash` distinguish them — correctly: it is
locally-derived state, recorded as what it is.

**What it buys.** Cross-compiler linkage seams come from mixed classfile *encodings* of
Scala features (mixin forwarders, lazy val schemes, lambda encodings). Re-lowering the
whole Scala buildpath with the consumer's compiler gives one backend, one encoding, no
seams — and helps the two worst offenders directly: inline methods are re-expanded from
TASTy anyway, and macro implementations are regenerated to run under the compiling
compiler. Caveats: the benefit requires re-lowering *all* Scala dependencies (a mixed
buildpath has seams, though supported ones); `javac` classfiles are untouched and stable;
the re-lowered world links against the newer `scala3-library`, covered by minor-lineage
forward compatibility.

**Not by default, and the compiler will not detect the need.** In normal operation scalac
reads dependencies' TASTy for typing and inlining but links against their existing
classfiles; regeneration is a separate explicit mode (`-from-tasty`), per invocation, and
historically an under-exercised corner of the compiler. Three responsibilities fall to
the build tool, none to scalac:

1. **Detect necessity** — a manifest judgment LIRA already makes: a rule-6 readability
   failure, or a `Tool` version mismatch across the buildpath, is the trigger, decidable
   before anything compiles.
2. **Orchestrate** — invoke the re-lowering edge per dependency in topological order,
   into the cache keyed by (tree hash, compiler version).
3. **Police scope** — all-or-nothing per buildpath, or the seam-elimination claim fails.

LIRA supplies the trigger (manifests), the safety argument (patch relation), the
execution model (a registry edge), and the cache. The one external dependency is that
`-from-tasty` be robust enough to lean on — which makes hardening TASTy-only
recompilation the single most valuable upstream-Scala investment for this build tool: an
ecosystem dependency, not a spec or build-file item.

## 9. Sources of variation, and their homes

The build definition must accommodate variation without editing — the worst outcome being
a file that needs hacky modification per scenario. Attempting to enumerate the kinds of
variation produces not a list of axes but a taxonomy of **sources**, and the design
principle turns out to be a bijection:

| Source of variation | Its one home |
| --- | --- |
| the **project** — intent, including the declared configuration space (§7) | `build.tel` |
| the **world** — external state, sampled at a moment | `build.lock` |
| the **system** — machine and person | `local.tel` |
| the **occasion** — a point selected in the declared space | command blocks / CLI |

Two clarifications make the taxonomy exact. First, "what is produced" (JVM, JS, native
from one definition) is not a variation context at all: the declared *space* is project
intent, living in `build.tel`; an occasion merely selects a *point* in it. Second, the
project's own evolution is the one variation that legitimately edits the build file — it
is not accommodated by the file; it *is* the file.

**Hacky modification** now has a precise definition: **a variation landing in the wrong
home.** World drift forcing a `build.tel` edit; a machine fact leaking into the
checked-in file; an occasion requiring a persistent change anywhere. The requirements are
one rule applied four times:

- **R1 (world):** `build.tel` changes only when intent changes; the world's movement
  lands in the lockfile. The format already deletes the largest class of ritual edits —
  the project's own version is derived (§12.5), so bump commits do not exist.
- **R2 (system):** identity facts never outside `build.tel`; machine facts never inside
  it. The normative invariant: *same commit + same lockfile ⇒ same release identity, on
  any machine, for any developer, under any local file.* Divergence is not forbidden but
  quarantined: local overrides that change outcomes produce development releases with
  build pins, and L118 blocks publication until reconciled. CI is just another machine
  with a generated local file.
- **R3 (occasion):** every selection reachable from the command line without editing any
  file, under a total, stated precedence order; nothing persists unless written.
- **R4 (project):** one definition, many outputs; adding a target is adding a
  declaration, never cloning one; the invocation selects the subset.

### 9.1 Gaps in the current draft, each a misrouting

1. **Tool versions are exact-pinned while dependencies get selectors** — world drift
   routed into `build.tel` (a scalac patch release forces an edit). Fix: the same
   selector grammar for tools (`version 3.9`, prefix), exact resolution in `build.lock`,
   moved by the same explicit update command. Integration cases wanting exact versions
   still write them.
2. **`run` has no settings model** — occasion variation (debugger attach, JVM args,
   environment, working directory) currently inexpressible. All side-effect class, never
   manifest content: home is `run` under command blocks, overridable in `local.tel` and
   at the CLI. Also the seed of the deferred deployment round — a local run of a service
   is an environment of one.
3. **Artifact selection is undefined** — `module app` declares four `native-exe`
   triples; what an invocation builds needs a default (the host triple, which the tool
   knows) and a selection syntax, which exposes that artifacts are anonymous: optional
   names, or selection by deliverable pattern.
4. **Local pins** — `local.tel` can redirect a repository's transport but cannot resolve
   a coordinate to a specific commit, binary or path regardless of selector (the
   npm-link workflow). A `pin` record in `local.tel` compiles to a build pin; the L118
   fence applies with no new rule.
5. **Precedence is implied, nowhere defined.** The layers: tool built-ins → user-global
   local file (`~/.config/lira/local.tel`, currently missing as a concept) → `build.tel`
   → project `local.tel` → command block → CLI. One ordered list, plus the
   layer-legality rule §7's classification already supplies (identity-affecting facts
   only in `build.tel`; side-effect settings anywhere; selections everywhere, layered).
6. **Small but real:** machine-configuration shape in `local.tel` (tool install paths,
   cache location, parallelism); and command argument passthrough, so "run just this
   test" is an occasion rather than an edit.

None requires format machinery: every gap is surface syntax plus one stated rule, with
enforcement (L118, the lockfile, the §7 classification) already in place.

### 9.2 The lockfile is a memoization, not a cache

A cache is deletable without observable consequence; deleting the lockfile and
re-resolving can yield a different world. The lockfile is the same genus as a `.lira`
file under §1: a **verifiable memoization** — derived (from the selectors plus the index
state at a moment), trustless (its entries are `Release` records with inclusion proofs,
distribution.md §5/§8 — nobody *believes* a lockfile), and carrying authority over
exactly one thing: **when the world was sampled**. Selectors are intent; the lock is the
fact intent resolved to, held stable until deliberately re-sampled. Pins (local `pin`
records, `build` pins) take precedence over selector resolution, overlaying the lock
without touching it.

Multiplicity follows from the bijection: multiple lockfiles are legitimate along exactly
the **world** dimension — different sampling policies (a stable `build.lock` beside a
nightly-resolved `canary.lock`; an archived lock reproducing a past build; an LTS-frozen
lock on a support branch) — and illegitimate along every other. Not per selection: one
lock spans the whole declared space, its entries keyed by (coordinate, selector), since
different cases resolve different selectors of even the same module. Not per machine or
developer: the R2 invariant is *anchored* on the shared lock. The lock is therefore
singular and checked in by default; an alternate lock is a named, deliberate artifact
selected per invocation (`--lock canary.lock`), the selection itself occasion-class —
CLI, never `build.tel`.

## 10. Running is deployment: one algebra, one topology

### 10.1 One validity algebra, two world documents

Buildpath validity (§13.3) and environment validity (§13.7, L145) are the same judgment —
closure, satisfaction, aggregation, coherence, quantified over an assignment of one
integration per release — differing only in **which document supplies the providers**. At
build time the providers are the target's host contracts, and requirements naming
deployables are left explicitly pending (rule 7); at run time the providers are the
environment's grants and bindings, and the pending judgments close. The environment
release is the run's world document exactly as the lockfile is the build's (§9.2): the
same genus of verifiable memoization, with one honest difference in polarity — the
lockfile is *sampled* state, the environment is *desired* state, and L146's re-check at
every transition holds the two together. Extending §9's bijection to the running half:
the environment manifest is its `build.lock`.

### 10.2 Each axis is consumed by exactly one stage

Of §7's three axis kinds, follow each through the egress into an environment:

- the **universe** axis is consumed by the **egress** — an application chooses, closing
  over one universe; the source universe is thereafter consumer-invisible;
- the **option** axis is consumed by the **deploy record** — a `deploy` names one
  `build`, which is precisely how patch-sibling selection was defined to work;
- the **integration** axis **survives** — `app` sections are keyed by integration, each
  with its own `requires`, and environment validity is *defined* as a search for an
  assignment of one integration per deployed release.

The observability classification predicts this: integrations are the consumer-observable
axis, and the environment is the final consumer. Deployability into a particular
environment is therefore **verifiable from manifests alone**: "can this deploy into E?" =
"does some integration's requirement set get satisfied by E's grants and bindings?" — a
service shipped with `pg15` and `pg16` cases is deployable into either estate, the
environment's assignment selecting exactly as the buildpath's canonical assignment
selects a backend.

### 10.3 A local run is an environment of one

If running is judged by environment validity, `run` should construct an **ephemeral
environment release** and judge it before launching. Every ingredient has a home in §9's
bijection, and the correspondences run word-for-word:

| Build side | Run side | Shared mechanism |
| --- | --- | --- |
| `host` declaration (project) | `grant` records | contracts provided; hosts.md §7 |
| lockfile entry | `binding` selection by `api` | intent resolved to a lineage fact |
| local `pin` | `route` pin on a deploy | consumer choosing a candidate |
| `local.tel` repository override | machine-supplied grants | the system home supplying the world |
| downstream test list | staging with a dev build substituted | spanning and mocks (environments.md §9) |

The machine's local file declares (or the tool detects) what it provides — a JDK, a local
postgres — and those are the ephemeral environment's grants: the *system* home doing its
§9 job. The command's run steps become deploy records, build-pinned to fresh development
builds — which the spec blesses: deploy pins are publishable even naming development
releases. Addresses default to localhost bindings. Validity is checked before launch;
probes verify after. Run-time settings (debugger attach, JVM arguments, environment
variables) are side-effect class, attached to the ephemeral environment and never
entering a manifest — closing gap 2 of §9.1. The promotion story completes the
continuum: an ephemeral local environment is a development release of an environment
module; dev → staging → production is one module's machinery — development release, then
promotion — applied to environments, because environments are releases.

### 10.4 Environments are variations of a topology

Real estates want staging and production as similar as possible, local development
similar too, modulo declared changes — so environments should be **variations of a
common architecture**, not independent documents. This is the third appearance of the
§7 pattern (root + case deltas), and the build file expresses it structurally, as the
pattern's other appearances do: a `topology` block — the common shape: services, their
requirements, served surfaces, binding addresses — with each `environment` nested as
its **child**, a delta over the topology body under the same paired keywords,
declaration-versus-refinement rules and minimality lints as module overlays. Nesting
(rather than peer blocks naming their topology) makes the association unforgeable — an
environment belongs to exactly one topology, which is what an environment *is* — and
yields module coordinates for free: the environments of `topology main` are
`main/production`, `main/staging`, `main/local`, as project modules are
`example/core`.

The spec agrees at the atom level. The `environment/1` discipline atomizes **only
bindings and grants** — nothing else: not deploys, not selections, not routes. Rolling a
new build into production is patch-grade; binding a new address is minor; retargeting or
withdrawing is major. That is: **the topology is the environment's API, and deployment
is its implementation.** Routine promotion never changes an environment's API identity;
topology evolution does, and regrades every instantiation at once.

Operationally, difference between environments has exactly one place to be written, so
drift is impossible by construction, and a review of the delta block reviews the entire
gap — the staging mock (environments.md §9) collapses to a one-line deploy delta,
satisfying by cross-module spanning. The local environment joins as a third
instantiation, not a third mechanism: the same topology with grants from the system home
and localhost bindings, at the least formal end of the promotion continuum.

Compilation is honest about what is shared: each environment still emits a **complete,
standalone env release** — production and staging are separate coexisting modules
(environments.md §9) whose manifests share nothing. The topology is authoring-time
structure only, like `project`, and warrants the same leak-check as §5's question 2.
This also resolves §5's question 5: the overlay calculus for environments is applied at
**authoring time by the tool**, not in the format — noting the honest asymmetry that
for sections the overlay calculus *is* spec-level (§9.3). One thread deliberately left
unpulled: because addresses compare as authored, environments binding different
hostnames have genuinely different atom sets — a shared topology gives them the same
*shape*, not the same atoms, so "same topology" is a build-file relation no manifest
reader can verify across two releases. If cross-environment verifiability ever matters,
that is the thread (tags, or a topology-identity claim); until then it stays unpulled.

## 11. Tools are services whose host is the build tool

LIRA should be a distribution format for tools themselves — including a compiler built
by the very build that then uses it. The machinery is §10's, applied reflexively:

- The **plugin API is a host contract**: a module (`lira.tool`) publishing the
  invocation ABI, atomized like any contract, with the build tool *providing* its
  snapshot exactly as a JDK provides `java.base`. Compatibility across build-tool
  versions is contract lineage.
- **(jar, `lira.tool`) is a deliverable** — by the definition's own pair — one
  registry entry, `lira-tool`. A tool is an ordinary application module targeting it;
  its `requires` are seeded by aggregation (hosts.md §10).
- **The edges a tool implements are its served surface**: a descriptor in its tree
  (which pipeline arrows: `scala → jvm`), atomized under a tool discipline, precisely
  parallel to a service's OpenAPI description (services.md §4.3). Dropping an edge is a
  major; edge claims are part of the tool's API.
- **Loadability is deployability**: "can this build tool run this tool?" is decidable
  from manifests before invocation — §10.2's verifiability, with the build tool as the
  environment.

**Bootstrap is §1.** A tool built in the same build adds tool-provider edges to the
module DAG; acyclicity holds until self-hosting, and self-hosting resolves by the
format's own reading: **stage0 is the prebuilt release** — the "use prebuilt or build
from source" cache decision *is* the bootstrap. Consequence worth headlining: a
LIRA-distributed tool carries a `source` record, so the toolchain is **recursively
verifiable** — independent rebuild (§18) applies to the compiler itself, and two
independent stage0s give diverse double-compilation against trusting-trust.

**Surface.** A toolchain entry takes a positional module reference, parallel to
`service api example/image`: `tool myscalac example/scalac-fork` (project-local) or
`tool fmt soundness.dev/scalafmt-next 4.2` (index coordinate + selector, locked as any
dependency). A toolchain must map **each edge to at most one tool**, so alternatives
live in *nested child toolchains* — the §7 pattern's fourth appearance: a child
(`tools/forked`) shadows edges and inherits the rest, path-named like environments in a
topology, selected per module with `apply`. Lint: a toolchain may not be applied to the
build of a module it references, the self-hosting case resolving via stage0.

**Two pressures.** First, the `Tool` record's third sharpening: beyond section scoping
and settings (§6 item 3), a LIRA-distributed tool needs a **LIRA identity** in the
record — coordinate plus implementation identity, not name-plus-version — and a
development-release tool's pin should make the consuming release unpublishable until
reconciled: the toolchain analogue of L118. §17 reproducibility then means "same tool
*bytes*, recursively." Second, **three tool-trust tiers**, made explicit: built-in
(registry-defined), LIRA-distributed (fully verifiable), and raw local binaries — which
are machine facts, belonging in `local.tel` by §9's bijection, digest-pinned at best,
and flagged by a lint as the one unverifiable link in an otherwise closed chain.

## 12. Presumption disciplines, and guarantees that flow into the type system

Code presumes things about its runtime beyond APIs: environment variables set, shell
commands available, data files present at paths. These are governed by **presumption
disciplines**, and the genus already exists: `capability/1` (hosts.md §5) is exactly the
shape — contract rows of *name* (atomized: the guarantee's identity), optional
*predicate* (folded into the atom: strengthening minor, weakening major), and *probe*
(never atomized: how a runtime checks) — and its worked examples are shell commands
(`sed:gnu`, `awk:bsd`). Environment variables (`envvar/1`) and filesystem paths
(`file/1`) are sibling disciplines of the same three-part shape. The algebra
needs nothing: atoms, `requires` + used-sets, grants and spanning apply unchanged.

Two boundaries, drawn at once. **Presence and format are atomizable; mutable content is
data plane** — "a file exists at this path in this format" is a platform fact, its
evolving contents are environments.md §10's exclusion again. And **presence, never
value**: `ACCESS_KEY`'s atom encodes name and predicate; nothing in any manifest or
probe may carry a secret.

**Provision and constraint: zero new machinery.** Guarantees enter environments as a
`grant` naming a contract release carrying the atoms — naturally a **generated
per-topology config contract** (`main/config`), produced from `guarantee` declarations
by the same generated-release convention as the ephemeral local environment, and
promoted with it. "Deployment constrained to environments where `ACCESS_KEY` is set"
is then environment validity rule 1, verbatim: the requirement aggregates to the app
section (hosts.md §10) and an environment lacking the grant is not valid for it. Local
development inherits the constraint helpfully: `environment local` must guarantee it
too (from `local.tel`, or machine detection), so a missing variable fails **before
launch, from manifests**.

The asymmetry of inference is deliberate: **demand is inferred, supply never is.**
Presumptions aggregate through the egress automatically — no topology restates a
requirement — but a `guarantee` cannot be inferred from the requirement it answers,
or validity becomes vacuous and the compiler's totality rests on a tautology; it is
the operator's commitment, the fact the startup probe verifies against reality. The
tool's affordances around the asymmetry: report each topology's aggregate presumption
set (the advisory pattern of environments.md §6); name the missing guarantee in the
validity error; and lint the reverse direction — a guarantee no deployed module
presumes is superfluous, so supply cannot silently outgrow demand.

**The totality loop.** The compiler story closes a loop whose every link exists:

1. the module declares the presumption (`presume ACCESS_KEY`); the build tool passes
   the guaranteed set to the compiler through the `lira-tool` context (§11);
2. the compiler treats the read as **total** — in Scala, a synthesized capability in
   scope; no partiality handling;
3. the compiler **emits the used-set** — services.md §5's "computed by tooling, not
   authored", with the computing tool being the compiler itself: the strongest
   provenance a used-set can have;
4. requires + uses aggregate to the app section; environment validity enforces them;
   probes verify at startup; L146 re-checks at every transition.

Soundness is §10.2's verifiability doing new work: the compiler may assume totality
*because the egress cannot land where the guarantee fails*. This is the first place
the format feeds static typing rather than describing its output. One honest caveat:
guarantee strength is temporal — environment variables are immutable per-process, so
startup-probe implies lifetime totality; files can vanish after the probe, so
`file/1`'s class is weaker ("present at startup"), and **each presumption
discipline's definition must state its temporal class** so compilers know what
totality they may claim. Modules that do not presume keep partial reads: the choice is
per-module, as it should be.

**Surface**: the dual pair `presume` (module side: compiles to requires+uses against
the config contract) and `guarantee` (topology side: an atom in the generated
contract; in `local.tel`, the machine's counterpart), sharing one grammar:
`presume|guarantee <kind> <name> [<predicate>]`. The kind word is a **discipline
reference, deliberately indirect**: it resolves through the registry to whichever
discipline id currently governs it (`envvar` → `envvar/1`; `command` → `capability/1`;
`file` → `file/1`) — the build file names kinds, never versioned discipline ids,
exactly as it never writes `tasty/1`. Discipline evolution never touches build files,
and the kind vocabulary is extensible by registry entry, not grammar change. The
mapping is **many-to-one by design**: `capability/1` is the general
no-formal-carrier discipline (hosts.md §5 — commands, Web APIs, Android API levels),
so `command` maps to it today and a future `webapi` kind would map to the same
discipline. The kind word carries the surface semantics; the discipline carries the
atomization — which is why `capability/1` keeps its general name. The
optional predicate follows the discipline's own convention (colon-variants like
`sed:gnu`; version tokens where defined), folding into the atom under the
strengthening-minor/weakening-major rule. No default kind on either keyword.
Findings for the record: one new discipline family (two new disciplines, one
existing), one generated-contract convention, one temporal-class requirement on
discipline definitions, and the kind→discipline registry mapping.

### 12.1 The wider inventory, and the two laws that govern admission

Surveying what else could be guaranteed this way yields two general laws before any
inventory:

**The soundness law: guarantees may erase *configuration* partiality, never *liveness*
partiality.** A compiler may treat as total anything that fails only when the
environment is misconfigured — missing file, unset variable, unknown timezone, denied
permission — because environment validity plus startup probes ensure configuration
before code runs. It may never erase failures the world produces after the probe —
remote host down, disk full mid-write, allocation failure. This generalizes the
temporal-class caveat into the admission criterion for any proposed kind, and splits
kinds that mix both: *authorization* to open an outbound connection is guaranteeable;
the peer answering is not.

**The fourth kind: datasets-at-version.** Several of the strongest candidates are
versioned data artifacts — tzdata, Unicode/ICU tables, CA trust roots, the MIME
database. Their identity is the dataset, not a path; their predicate is a version;
they grade like contracts (newer tzdata = minor). They deserve their own kind
(`presume dataset tzdata 2026a`) because the compiler can specialize on the version —
`ZoneId.of` total, normalization pinned — a stronger claim than "a file exists."

**Configuration-class kinds (compiler-erasable):**

| Guarantee | Static win |
| --- | --- |
| `locale` / charset | encoding lookups and conversions total |
| `dataset` (tzdata, unicode, CA roots) | zone/normalization/collation total, version-consistent |
| `isa` — instruction sets, microarchitecture (avx2, neon, CUDA level) | the egress specializes unconditionally: `-march=native` made deployment-safe, closing the gap that `native-exe/<triple>` underspecifies microarchitecture |
| `device` (/dev/urandom), loopback, IPv6 | non-blocking secure randomness; dead-path elimination |
| writable tmpdir | temp-file creation total (weak temporal class: capacity is liveness) |
| `tty` — terminal capabilities | total terminal control for CLI applications; notable for extending "environment" to *invocation context* (xeq tools) |

**Admission-gate kinds (deploy-constrain only; totality unsound):** resource floors
(`memory`, `disk` at path — the mounted-volume-with-capacity case), `clock` (NTP skew
within a bound — the presumption every lease and lock algorithm silently makes,
probe-able, valuable purely as a gate), `privilege` (uid, non-root, bind-below-1024,
umask), and `net` outbound authorization. These make deployments validly refusable
from manifests, but their failure modes are liveness-class or continuous, so they
never reach the compiler.

**Maps to existing machinery, not presumptions:** sidecars and mesh proxies are
services — grants bound to localhost addresses; vendor endpoints likewise. **Stays
excluded:** database schema — the data plane (environments.md §10), the case where a
guarantee would feel like configuration but behave like shared mutable state.
**Known frontier, not admitted:** exclusivity guarantees ("no other writer for this
directory", "sole consumer of this queue") — they would license single-writer
reasoning, but they are global properties, guaranteeable only by *constraint over all
of an environment's deployments*: a new judgment shape, not a new atom shape.

The projected kind vocabulary, in sum: `envvar`, `command`, `file`, `dataset`,
`locale`, `isa`, `device`, `net`, `tty`, plus the admission-gate kinds (`memory`,
`disk`, `clock`, `privilege`) that deploy-constrain but never reach the compiler —
every admission decided by the configuration-versus-liveness law, every kind a
registry entry mapping to a discipline, never a grammar change.

### 12.2 The guarantee interchange format

How the guaranteed set reaches a compiler, concretely — for Scala first, but abstract
over kinds by construction. The build tool serializes the compiled cell's
configuration-class guarantees to **canonical BinTEL** under the `lira-guarantees`
schema (`guarantees.schema.tel`, registered; publishable as a `tels/1` module). The
file is the serialized form of the `lira.tool` Invocation's presumption set: in-process
plugins receive the value directly through the trait; external compilers receive the
file through an output-affecting setting. Macros running in the compiler parse it and
decide, per reference, how to compile.

The structure's one load-bearing decision: **consulting and recording are the same
act.** Each entry carries `kind`, `name`, optional `predicate`, temporal `class`, and —
decisively — the guarantee's **atom value hash and contract module**. A macro that
compiles `env["ACCESS_KEY"]` as a total read simultaneously collects the atom it relied
on; the accumulated atoms, grouped by module and sorted, *are* the release's uses blobs
(`lira-uses`). The compiler emits the used-set (§12's step 3) without ever containing a
presumption discipline's atomizer — the build tool atomized at generation time, and the
file carries the results. The return path mirrors the input: the macro library
accumulates consulted atoms during compilation and writes them out beside the outputs;
the build tool merges, sorts and deduplicates them into the manifest's uses blobs.

Three further consequences of the shape:

- **The temporal class travels in-band**, so macros never hardcode discipline
  semantics: `lifetime` (envvars, datasets, isa — total for the process) versus
  `startup` (files — verified at start, may lapse), per §12.1. A macro library offers
  totality only where the class permits, and new kinds need no macro-library release.
- **Determinism**: the file is an input to compilation, so its bytes affect output
  bytes — canonical BinTEL, entries sorted by ascending atom hash, no duplicates (the
  `lira-atoms` precedent). It is a pure function of the buildpath and topology, so
  rebuilds regenerate it byte-identically, and §17 is undisturbed.
- **Predicates license specialization**: a macro reading `dataset tzdata 2026a` may
  fold the version into what it generates (zone tables, ISA-specialized code paths) —
  sound because the predicate folded into the atom, so the emitted used-set demands
  exactly the guarantee that was specialized against, and no environment lacking it can
  satisfy the release.

Abstractness falls out of the registry indirection: entries are (kind, name, predicate,
class, atom, module) whatever the kind; per-domain macro libraries (an envvar reader, a
dataset accessor) consult one structure; a new presumption kind is new entries in the
same file, no format change.

## 13. The build schema

`build.schema.tel` (repo root) is the draft TEL schema for `build.tel`, written as a
**consistency audit**: pinning the informal grammar — positional forms (field order,
adopting lira.md §14's convention), repeatability, optionality, operand scalars —
makes every awkward corner of the design visible as an awkward corner of the schema.
What the exercise surfaced:

1. **`Universe` and `Case` are structurally identical records** — name, `ephemeral`,
   the overlay fields, nested axis refinements. The schema is quietly saying that
   universe blocks *are* cases of an axis (the realm axis), which §7 knew
   semantically; whether the surface syntax should ever unify is left as an
   observation, not a proposal.
2. **A TEL finding — evidence now reaches TEL itself**: the shared overlay grammar
   must be repeated across `Universe` and `Case` because TEL schemas have no record
   composition or mixin mechanism. The surface language expresses the sharing
   uniformly; the schema layer cannot. Worth weighing as a TEL feature.
3. **The §4 guardrail became visible in types**: module-side `require` takes a
   contract module reference; service-side `require` takes a service/grant name. Two
   operand scalars for one keyword — the two compiled edge kinds, now distinguishable
   by the schema rather than only by context.
4. **The environment delta grammar is thin** (an address override and additive
   service/guarantee rows) — an honest measure of how much of the deployment round
   remains; the record will grow deploy deltas, grant deltas and mocks.
5. Semantic rules that stay lints, deliberately outside the schema: subtractive
   keywords in root bodies, L107 minimality, one-tool-per-edge, option cases touching
   `include`, one-cell-refined-once, toolchain self-application.

The schema also positions `build.tel` to carry a schema signature in its header
exactly as manifests do — at which point the build file's own grammar is versioned by
the same mechanism as everything else in the system. With TEL's pragma now taking LIRA
references (`‹domain›/‹name›:‹version›`, tel repository
`design/lira-schema-references.md`) and schemas publishing as `tels/1` modules
(spec/tels.md), that is literal: the build schema publishes like any release, its
versions derived from TEL's own compatibility relation, and a build file references it
by coordinate.

## 14. The anatomy of a tool

§11 gave tools an identity (services whose host is the build tool) but not an anatomy:
what a tool *is*, what the DAG's nodes are, and what happens implicitly when a path is
resolved. This section makes the implicit explicit.

### 14.1 Forms

The DAG's nodes are **forms**: the kinds content is *in* between tool invocations. The
existing taxonomy becomes species of this genus — a **source form** (`scala`, `java`,
`dockerfile`) is what humans write; a **universe** is a form whose content
composes (the litmus test unchanged); a **deliverable** is a closed form paired with
a host contract; and a **carrier** keeps its discipline-side meaning — the
interface-bearing artifact kind *within* a form (`jvm` holds `.class`, `.tasty` and Kotlin
metadata, which is why a node cannot *be* a carrier). "Format" is retired from the node
role — universes.md's "formats as nodes" reads "forms as nodes" — leaving `Artifact.format`
unambiguous for closed-artifact encodings.

Forms are one namespace, so source forms are named plainly for their languages —
`scala`, `java`, `kotlin`, `typescript`, `rust`, `c`, `dockerfile` — under the one
constraint that they avoid universe names. The litmus case shows why this works: there is
no `javascript` source form to name, because the `js` universe already *is* the form
JavaScript sources compose in; TypeScript is its own source form, compiled into `js`.
Universes and deliverables keep their established names.

### 14.2 Edges are hyperedges, with three input roles

A tool edge is not `A → B`. Scalac is: *scala-source, optionally java-source, plus the
buildpath's `jvm` cells* → *`jvm` content*. The descriptor names the roles:

- **input** — a source-side form consumed, required or optional;
- **context** — composition read but not consumed: dependency cells, by universe. This is
  where disciplines finally surface in the build's world: an edge declares the disciplines
  it **employs** over its context (scalac reads `tasty/1` to type against) and the carriers
  it **emits** into its output (`tasty/1` + `classfile/1`) — which is what the build tool
  needs to atomize results, and what licenses the compiler-emitted used-sets of §12;
- **output** — the form(s) produced.

The four edge kinds of universes.md §4 (compiler, egress, join, packaging) stop being
declared: they are derivable from the node kinds at an edge's ends. The input/context
distinction carries the closing-over semantics per edge: a compiler's dependencies are
`context` (read, never consumed); an egress's composed buildpath is `input` (consumed
into the artifact).

**Tool granularity** follows one rule: **a tool is the implementation/distribution unit;
an edge is an invocation kind; a component is a separately-versioned constituent of an
edge.** So `scalac` is one tool with `jvm`, `sjsir` and `nir` edges — each a separate
compilation run — the backend plugins appearing as per-edge `component` entries
(`scala-js`, `scala-native`) with their own version streams, selector-versioned and
locked like anything else. The manifest side is exactly what spec gap 1's section-scoped
`Tool` records provide: the sjsir cell records `scalac 3.9.6` **and** `scala-js 1.16.0`.
Separate tools are for separate *implementations*: a fork is a different tool
(`myscalac`); `sjs-linker` is a different tool (its own implementation, three egress
edges); a backend plugin driven by the same front end is an edge with a component.

Two pieces of machinery this needs come free. **Edge ids default to the output form**
(`scalac/jvm`, `scalac/sjsir`), explicit only if a tool had two edges to one output,
which nothing does. And **shadowing needs no addressing syntax**, because it was always
per-edge (one-tool-per-*edge*): a child toolchain's `myscalac` declaring only a `jvm`
edge shadows exactly `scalac/jvm`, and the parent's `sjsir`/`nir` edges survive.
Settings classify at tool level (applying to every edge: `encoding`) or per edge
(`module` kind, meaningful only to sjsir). `clang` over the `native/<triple>` family
still wants a **parameterized edge** (the triple bound at invocation) — the one
descriptor feature left open.

### 14.3 The descriptor and the ABI: two halves, two disciplines

A tool describes itself with: its edges (roles, forms, employed and emitted disciplines
per §14.2, per-edge components and settings) and its settings, each classified
(`affects output` / side-effect-only) — resolving §9's classification at its source.
This **information model** is fixed and carrier-neutral. Its realization splits:

- **The ABI is a Scala trait** (`lira.tool`), the decision following from the build tool
  being written in Scala: the contract module carries the trait in a `jvm` section,
  graded by `tasty/1`; a plugin is a `lira-tool` application (jar, `lira.tool`) whose
  requirement is satisfied by lineage — or by **spanning**, since a plugin's used-set
  over the contract is computed from its compiled references, so even a major trait
  revision keeps untouched plugins loadable. The §12 machinery, applied reflexively to
  the build tool's own plugin interface. Trait shape: `descriptor` (run at publish) and
  `invoke`, whose payload is the accumulated context — input trees by form, context
  cells, the guaranteed presumption set, the (universe, integration case, option case)
  coordinates, merged tool+edge settings — returning outputs by form, compiler-computed
  used-sets, and diagnostics. One evolution rule from day one: plugins *implement* the
  trait, so under `tasty/1`'s open-template fold an abstract addition is a **major**
  (the `dts/1` §11 logic); the trait grows by defaulted concrete methods or optional
  side-traits, never abstract members.

  The sketch (shapes indicative — the field inventory is the load-bearing part):

  ```scala
  package lira.tool

  trait Tool:
    def descriptor: Descriptor                    // run at publish; extracted to tool.tel
    def invoke(invocation: Invocation): Outcome

  case class Descriptor(name: Name, edges: List[Edge], settings: List[SettingSpec])

  case class Edge                                 // one invocation kind, named by output
      (output:     Form,
       inputs:     List[Input],                   // consumed; required or optional
       contexts:   List[Form],                    // dependency cells read, by universe
       employs:    List[DisciplineId],            // what it reads from context carriers
       emits:      List[DisciplineId],            // carriers it produces
       components: List[ComponentSpec],           // separately-versioned constituents
       settings:   List[SettingSpec])             // per-edge settings

  case class SettingSpec(key: Name, affects: Effect)   // Effect.Output | Effect.Nothing

  case class Invocation
      (edge:         Name,                        // which edge (e.g. sjsir)
       inputs:       Map[Form, Tree],             // source trees by form
       context:      Map[Form, List[Cell]],       // materialized dependency cells
       settings:     Map[Name, Argument],         // tool + edge, merged
       presumptions: Set[Guarantee],              // §12: the tool may assume these
       cell:         (Universe, CaseId, CaseId))  // target universe, integration, option

  case class Outcome
      (outputs:     Map[Form, Tree],
       uses:        Map[ModuleRef, UsedSet],      // compiler-computed used-sets (§12)
       diagnostics: List[Diagnostic])
  ```
- **The descriptor is extracted data, not Scala API.** A tool's edges are invisible to
  its TASTy — edges can change with no signature change — so "edges are the tool's API"
  (§11) cannot ride on `tasty/1`. Instead, the services.md §4.3 extraction pattern: at
  publish, the build tool runs `descriptor` and writes `tool.tel` into the tree
  (conforming to a descriptor schema published as a `tels/1` module), atomized by a
  small tool discipline — one rigid atom per edge, one per classified setting — so edge
  changes are graded as promised. The schema exists: `tool.schema.tel` (registered),
  with `scalac.tool.tel` as its worked instance; it carries the §14.2 model verbatim —
  edges positional on their output form, input/context roles, employs/emits
  disciplines, per-edge components and setting specifications (key + effect) — and
  closes the parameterized-edge gap: an edge declares a `parameter` (triple, platform)
  referenced as a placeholder in form names, bound by backward flow per §15.2.

The split also defines what a **WIT twin** would mean, precisely: a second contract
module (`lira.tool-wit`; carrier WIT, graded `wit/1`; deliverable
(wasi-component, `lira.tool-wit`)), with equivalence judged by the descriptor — a Scala
tool and a WASM tool are the same kind of thing exactly when both extract identical
`tool.tel` documents. The trade-off is honest: in-process JVM plugins are direct and
fast but unsandboxed; the WIT twin is where third-party tools would gain isolation.
Built-in tools carry the same descriptor information as registry entries.

### 14.4 Source forms: inferred, declaratively overridable

Sources acquire forms. The registry (tool definitions) owns the extension→form mapping,
so inference covers the common case; where inference fails or misleads (generated files,
unusual extensions), the source declares its form as a child field — not a positional
parameter, since TEL fields should not vary type by position:

```tel
source src/core/*.scala          # form inferred: scala
source src/generated/*.scala.txt
  form scala                       # declared where inference fails
```

A declaration that merely restates a correct inference is an L107-style minimality lint.

### 14.5 Spaces: the project root, the store, and the workspace

Execution involves exactly three spaces, and their disciplines differ:

- **The project root** — durable, user-owned, authored content only (sources, the build
  and local files, the lockfile), and **read-only to tools**, enforced. No build
  directory ever appears in it: a build never dirties the worktree.
- **The store** — durable, machine-wide, content-addressed: the single store of
  `tool.md` and §13.5, holding fetched sections and every tool output — intermediates
  included — as trees of blobs keyed by hash. Its location is a machine fact
  (`local.tel`); retention is LRU.
- **The workspace** — per-invocation, ephemeral, a *view*: the driver materializes the
  invocation's declared inputs at their authored relative paths (links from the store),
  the tool runs, outputs are ingested back, the workspace is destroyed. Inter-tool data
  never travels through a shared directory — it goes output tree → store → next
  workspace. Workspaces are stack frames, not a heap.

Two consequences a monolithic "build space" would not give. **Every intermediate is
memoized for free**: an invocation's identity is computable — hash of (edge, input tree
hashes, context cell identities, output-affecting settings, tool and component
versions, the guarantees file) — and its outputs cache under that key: §1's
verifiable-cache reading extended inward to every edge of the hyperpath, so incremental
builds are cache hits rather than a mechanism. (Intra-module incrementality à la Zinc
stays the tool's private business, in a tool-private cache area under one law: *warm
output must equal cold output*, spot-checked by discarding the warm state.) And **tools
can only communicate through declared edges**: with no shared directory, the descriptor
roles are the only plumbing that exists — hidden coupling between build steps is
unrepresentable, and parallelism needs no locking.

**Debugging is an operation, not a place.** On failure the workspace is retained
automatically and the diagnostic names its path; `lira workspace <module>/<edge>`
re-materializes any invocation's workspace on demand (the store holds everything needed
by construction), with `--keep` to retain all; store inspection answers "what did that
tool produce?" without a workspace at all. All occasion-class, CLI-only — no location
here ever appears in `build.tel`, preserving §15's rule that the only paths users see
are the authored relative ones inside trees.

**Extraction is the one way content leaves the store.** A command step (or ad hoc CLI
operation) `extract <module> <entry> <path>` saves a **named entry** — an artifact's
deliverable name, or a container-format name for a §13.6 canonical derivative
(`jar`, which exists for every jvm section with no declaration) — into the project tree
at a fully-authored destination. This supersedes the earlier `emit` keyword, which
assumed an output location. The store stays hidden; entries are addressed by build-file
vocabulary, never internal paths. The project-root read-only rule stays intact for
tools: extraction is the driver acting on explicit instruction, the one sanctioned
writer. And one lint keeps builds functional: **a destination matched by any source
glob is an error** — an extracted file feeding the next build's inputs is a feedback
loop, statically detectable from the build file. A checked-in `extract` in a command is
shared intent; the CLI form is occasion — the bijection sorts the two homes.

### 14.6 Resolution, restated — *(see also §15, where packaging edges join the algebra)*

Per (module × universe × integration case × option case): required outputs are the
universe's stored forms, or the artifact's deliverable; available inputs are the
module's source forms plus dependency context; resolve a hyperpath through the applied
toolchain's edges. The one-tool-per-edge rule generalizes to hyperedges as the
**covering-edge rule**: a given source form maps to at most one edge, except that an edge
whose input set strictly contains another's takes the whole set (a mixed `.scala`+`.java`
module goes wholly to scalac; a pure-java module to javac). Incomparable overlaps are
errors, resolved by a per-module `tool` pin — the same shape as integration pinning.

## 15. Packaging is an environment: the container chain

A Docker image is a **frozen environment-of-one**: it supplies grants — commands
installed, files baked in, variables set — to the application deployed inside it. The
packaging edge therefore performs environment validity in miniature, which makes it the
**third site of the one validity algebra** (§10.1's buildpath and environment being the
first two): rule-1 closure of the inner application's requirements against the image's
declared provisions.

The chain, end to end: **core presumes → app requires → image satisfies-or-propagates →
environment guarantees.** The inner application's app section already carries everything
by aggregation (hosts.md §10): presumption requirements and host contracts alike. The
image module declares what it satisfies with the word that already means provision:
`guarantee command git`, `guarantee file /etc/ssl/certs.pem` — and deliberately omits
what must pass through (a secret `ACCESS_KEY` propagates, to be answered by the
deployment environment's own guarantee). The §12 inference asymmetry holds unchanged:
satisfaction is declared, never inferred from the Dockerfile; the reverse-minimality lint
flags guarantees matching nothing the inner application requires.

**No assumed paths.** Absolute locations belong to the tool; relative layout belongs to
the user, and every relative path is authored. The tool driver materializes inputs into
an invocation workspace whose location is side-effect class — and SHOULD vary or
normalize it between builds, so a tool that embeds an absolute path into output breaks
in development rather than silently later. Inside the context: the assembled artifact
lands at the path the *consumer* declares (`assemble app` / `path bin/example` — full
path including filename, never inherited from the producer's artifact naming); source
files keep their authored glob paths; the Dockerfile is identified by its `form`, not
by name-and-location convention, and passed explicitly. Under `oci-index`, every
platform's matched artifact lands at the same declared path, so one Dockerfile serves
all platforms. Declared paths also make §15.1's multi-parent layer-disjointness check
static: overlapping claims are detectable from the build file, before anything runs.

**Post-build checks are the probe machinery, verbatim.** Every presumption discipline
carries a probe; hosts.md §9 names a third verification moment at runtime. Image
verification is that moment applied early: run each declared guarantee's probe *inside
the built image*, and fail the build on any miss. Declared plus probed is the same
authorial-but-verified pattern `requires` itself uses, now at two moments — image build
and service start.

**The requirement-transformation formula.** Repackaging an application with one set of
host requirements into an application with a different set is exact:

```text
R(image) = ( R(app) − G(probe-verified) ) ∪ C(deliverable)
```

— the inner requirements, minus the internally-satisfied guarantees, plus the target
deliverable's own contract half. The last term needs no declaration: a deliverable
is by definition (closed format, host contract), so moving from
`native-exe/<triple>` to `oci-image` swaps libc-and-kernel for container-runtime by
construction. The propagated remainder lands in the image's app-section `requires`,
where environment validity picks it up — checkable from manifests at every joint.

**The base-image thread (open).** The ELF's libc requirement is satisfied by the base
image's OS layer — the same satisfaction relation one level down. Honestly modeled, a
base image *supplies host contracts* (`glibc-x86-64-linux` at a lineage point), which
walks directly into the stewarded-namespaces machinery: no distribution will publish
LIRA contracts, but a steward can, and base-image choice becomes a checkable contract
decision rather than folklore. Noted as the natural extension, not designed here.

### 15.1 Multi-parent composition

Docker's multi-stage builds serve two purposes with different fates here. The **builder
stage** — a hermetic environment to compile in — is *subsumed*: that is what the LIRA
build is, and a Dockerfile that compiles is a Dockerfile doing the build tool's job
without its guarantees. What legitimately survives is **runtime composition**: merging
content from several parents (a distroless base, a tooling image, an assets image) into
one image.

The frozen-environment reading extends without new machinery: each parent is a
grant-provider, the closure judgment runs against the **union** of their declared
provisions, and overlapping content between parents is the resource-disjointness rule
(L126) transposed to layers — two parents supplying the same path is an error, not a
merge. Surface-wise this makes `assemble` repeatable on a packaging module (each
assembled parent contributing content and provisions), with the disjointness lint across
them; the schema currently holds `assemble` singular, and widening it is deliberately
deferred until a real multi-parent example lands in `build.tel`.

### 15.2 Multi-architecture images, and the backward flow of the platform parameter

An OCI multi-arch artifact is an **index** over per-platform images. In form vocabulary:
`oci-image/<platform>` is a parameterized deliverable family (the parameter part of
the type, exactly as `native-exe/<triple>`), and `oci-index` is a composite whose
packaging edge takes several members of that family as inputs — the parameterized-edge
feature (§14.2, still open) now demanded from a second direction.

The build file declares platforms once, at the outermost artifact:

```tel
artifact oci-index
  platform linux/amd64
  platform linux/arm64
```

and resolution **flows the parameter backward**: each declared platform instantiates the
packaging chain — `oci-image/<platform>` needs `native-exe/<matching triple>`, binding
the triple of the *earlier-phase egress* — with the platform↔triple correspondence being
registry data. Parameter unification along a hyperpath is the general mechanism:
parameters bind at the demand end and propagate to every parameterized edge on the path.

The upstream implication splits cleanly by universe. For TASTy-family content (`nir`),
sections are architecture-agnostic: **one cell, N links** — the same `nir` section
serves every platform, and only the egresses multiply. For `native/<triple>` universes
(C, Rust dependencies), the buildpath itself is per-triple, so the platform parameter
reaches into buildpath resolution and selects per-triple cells. The requirement formula
of §15 applies per member — `R(oci-image/arm64) = (R(app@arm64) − G) ∪
C(oci-image/arm64)` — and the index's own requirement set is judged per platform member:
an index is satisfiable in an environment iff the member the runtime would select for
that environment's platform is. Guarantees on the image module apply to every member;
per-platform differences in provision would be a refinement case, none yet motivated.

## 16. How disciplines relate

Two cases motivate the question. A `file/1` presumption asserts a path exists but says
nothing of its contents — which may be a TEL document that `tels/1` could validate. And
a nominal method signature's compatibility depends on the class hierarchy of the types
it mentions, which no signature atom seems to own. Both feel like one discipline
delegating to another. Neither is, and the refusal is principled:

> **Disciplines are leaf canonicalizers; the algebra is the only composer.** A
> discipline turns one carrier into atoms and never consults another discipline. Every
> apparent interaction is either *folding* (within a module) or *an edge* (between
> modules) — both set arithmetic, both decidable from manifests. Inter-discipline
> delegation would be the rule engine returning through the side door the folding
> principle exists to keep shut.

The stake is concrete: the moment `file/1` could invoke `tels/1` at satisfaction time,
validity checking would need discipline *implementations* rather than atom sets, and
decidability-from-manifests — which deployability, the totality loop, and every §10
judgment rest on — would quietly die.

### 16.1 The hierarchy case: folding plus used-set closure

There is no class-hierarchy discipline because the folding principle already routed
hierarchy facts into the type's own atom: a declaration's atom folds its `extends`
list (`dts/1` §10; `tasty/1` equivalently), precisely because a hierarchy change is
consumer-observable and must grade. Cross-module reliance travels the graph: the
consumer's dependency plus its used-set — which MUST close over the atoms of nominal
types *referenced by* used members, not only the members themselves (**the used-set
closure rule**, an obligation on every signature discipline's used-set computation).
A supertype change then alters the type's atom, grades the declaring module, and
either rides the consumer's lineage requirement or fails its spanning — the
cross-discipline-feeling judgment computed by the algebra over two manifests, each
discipline having canonicalized only locally.

### 16.2 The content case: predicates graduate to the graph

Presence and conformance are two claims, and conformance must not be smuggled into the
presence atom's predicate: an opaque `conforms-to-X` token satisfies only by exact
match, so an environment providing a *newer, extended* schema would wrongly fail.

> **A predicate that needs an algebra is a module reference in disguise.**

Predicates remain for closed vocabularies with trivial orderings (colon-variants,
version tokens). Structured conformance graduates: the schema is a published `tels/1`
module, and the presumption compiles to two records in two disciplines joined by the
requirement graph — a `file/1` atom for presence (probed by existence plus
`tel validate` at startup) and a `requires` on the schema module at a snapshot,
satisfied by lineage membership and graded by `tels/1`'s own subsequence-coincident
relation. Surface:

```tel
presume file /etc/example.tel
  schema example.dev/config 1.2
```

An environment guaranteeing the file under schema 1.4 satisfies a 1.2 presumption
because 1.2's snapshot is in the schema module's lineage — content compatibility
reduced to the one satisfaction relation.

### 16.3 The inventory

The complete set of ways disciplines relate:

1. **Coexistence** — multiple `api` records on one release; the snapshot is the atom
   union; claiming order resolves content overlap (`kmeta/1` beside
   `classfile/1`). The dual-declaration bridge (spec §11.1) is this mechanism's
   special case for two versions of one discipline.
2. **Shared canonical encodings** — `jsig/1` over `classfile/1`'s encoding:
   specification economy only. Domain separation keeps their atoms incomparable;
   true comparability (spanning) requires literally the same discipline, which is why
   `jdk` and `scalajs-javalib` sharing `jsig/1` is load-bearing.
3. **Composition through the graph** — facts governed by another discipline are
   reached by edges to the modules that carry them (dependency, requires, used-set
   closure), never by inter-discipline calls.
