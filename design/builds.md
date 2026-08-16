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
| build outputs other targets                 | egresses to application types (§4.1); deployable releases (§9.4) |
| "snapshot of the build state for a module"  | the manifest: toolchain records, dependency snapshots, atoms, payload identity |
| build feeds deployment                      | aggregated requirements seed the deployable's `requires` (hosts.md §10, services.md §4.1) |
| running modules are "services"              | services.md, by that name                             |
| coherent deployment over services           | environment validity (spec §13.7, L147)               |

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
  satisfying, checked over the whole set (L147) and re-checked at every transition (L148).
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
3. Everything else in this document is build-tool business, and stays here.
