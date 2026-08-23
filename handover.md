# Handover: `build.tel` — a build-file format as a live test of LIRA

> **Round update (2026-08-23b).** builds.md §3.1 gains its closing addendum: the
> **input identity** — scheme `inputs/1` on the existing open-vocabulary source record
> (no new record kind), digest of a canonical `lira-inputs` BinTEL doc (per-cell:
> source digests, tool identities + output-affecting settings + components, dependency
> module→payload pairs, guarantees doc hash, case ids). Computable WITHOUT building —
> the §1 memoization's real cache key (compute → look up → skip build); three-tier
> verification (authorial / consistency-check from inputs / §17 rebuild). The
> **hashability law**: output-affecting inputs must have canonical byte encodings and
> reach tools only through declared hashed channels — descriptor `affects output`
> implies hashable; hashability = hermeticity. Glossary + catalog updated
> (lira-inputs planned). This closes §3.1's remainder; the lira-inputs schema file and
> the spec-side scheme registration are follow-ups, and input-hash lookup joins the
> online-service query list.
>
> **The open items now live in design/gaps.md** — a tiered living checklist
> (1: designed, awaiting spec application — Tool records first, blocking everything;
> 2: discipline specs to write — tool, envvar/1, file/1, dataset; 3: named and
> half-designed — run settings, artifact selection, precedence order, local.tel
> completion, environment deltas, command args, multi-parent assemble, parameterized
> edges in practice; 4: untouched — build.lock, the publishing workflow,
> universes.tel, diagnostics, codegen, remote store, CLI relationship, the real
> trait, warm-equals-cold mechanics; 5: verification debt — the module core manifest
> hand-derivation FIRST, the two leak-checks, the local.tel gitignore question).
> Update gaps.md as items land rather than growing this handover further.

> **Round update (2026-08-21c, tool anatomy).** builds.md §14: DAG nodes are **forms**
> (genus over source forms / universes / application types; carrier stays the
> discipline-side word; "format" retired from the node role — universes.md §4 updated).
> Edges are **hyperedges** with three roles — input (consumed; required/optional),
> context (dependency cells read, with disciplines the tool *employs* and carriers it
> *emits* — where disciplines finally surface in the build's world, licensing §12's
> compiler-emitted used-sets), output. Edge kinds (compiler/egress/join/packaging) are now
> derivable, not declared. The tool descriptor's **information model is fixed, its carrier
> deliberately open** (WIT / .d.ts / TASTy beside the ABI, atomized under that carrier's
> discipline) — Jon's call; don't fix a single TEL descriptor schema. Source forms:
> inferred from extension via the registry; declared via a `form` child field on `source`
> (never positional — TEL fields shouldn't vary type by position), L107 lint on redundant
> declarations. Resolution: covering-edge rule (strictly-containing input set wins:
> scala+java → scalac; incomparable overlaps error, pinned per-module). build.tel:
> toolchain comment rewritten, `source … / form scala-source` example; build.schema.tel:
> Form scalar + Source record (glob, form optional), re-registered, all files validate.
> The dual-declaration bridge is recorded non-normatively in lira.md §11.1 (+ tels.md §1
> pointer): grade of a dual-declared release = max of per-discipline grades (a §12.3
> theorem); stricter canonicalization governs during overlap. Issues #13/#14 closed.
> Next in this area: the invoke ABI's context payload shape, and the descriptor's
> concrete WIT/TASTy sketch. Tool granularity settled (Jon revised from the earlier
> separate-tools rule): **tool = implementation/distribution unit; edge = invocation
> kind; component = separately-versioned constituent of an edge.** scalac is ONE tool
> with jvm/sjsir/nir edges, backend plugins as per-edge `component` entries
> (selector-versioned, locked); each cell's manifest records tool + edge components via
> the section-scoped Tool records of spec gap 1. Edge ids default to the output form
> (scalac/sjsir); shadowing is per-edge with no addressing syntax (the fork's jvm edge
> shadows scalac/jvm; sjsir/nir inherited). sjs-linker stays a separate tool (its own
> implementation). clang/native/<triple> still wants a **parameterized edge** — open.
> Input/context per edge carries the closing-over semantics (compiler deps = context;
> egress buildpath = input). Source-form names lose the -source suffix (`scala`, `java`,
> `typescript`…): forms are one namespace, source forms avoid universe names (the js
> universe IS JavaScript's form). build.tel: scalac gains edge blocks (components +
> per-edge `set module esmodule`), version now a selector (`3.9` — gap 1 fixed for
> tools); schema gains Edge/Component records; all files validate. The plugin carrier is
> decided (builds.md §14.3 rewritten): **ABI = Scala trait** (`lira.tool`, jvm section,
> tasty/1-graded; plugins implement it, so abstract additions are majors — grow by
> defaulted methods/side-traits; plugin loadability by lineage or spanning via
> compiled-reference used-sets), **descriptor = extracted data** (`descriptor` run at
> publish → `tool.tel` in the tree, schema published via tels/1, atomized by a small
> tool discipline — because edges are invisible to TASTy, the services.md §4.3
> extraction pattern applies). A WIT twin = second contract module lira.tool-wit;
> equivalence criterion = identical extracted descriptors; trade-off recorded
> (in-process = fast, unsandboxed; WIT = isolation). The trait sketch is committed in
> builds.md §14.3; the invoke payload is the accumulated context (inputs by form,
> context cells, presumptions, cell coordinates, merged settings). builds.md §15: the
> container chain — a Docker image is a frozen environment-of-one, making packaging
> edges the THIRD site of the one validity algebra; the chain core presumes → app
> requires → image satisfies-or-propagates → environment guarantees; `guarantee` on a
> packaging module declares internal satisfaction, probe-verified in the built image
> (the third verification moment applied early); the requirement-transformation formula
> R(image) = (R(app) − G(verified)) ∪ C(app-type), the app type swapping the contract
> half by construction; secrets deliberately propagate. §15.1: multi-stage builder
> stages are subsumed by the LIRA build; legitimate multi-parent = runtime composition
> (union closure over parents' provisions; L126-style layer disjointness; `assemble`
> would become repeatable — deferred). §15.2: oci-image/<platform> is a parameterized
> family, oci-index the composite; platforms declared once at the outermost artifact and
> the parameter FLOWS BACKWARD through hyperpath resolution to bind earlier egresses
> (platform↔triple = registry data); nir cells are arch-agnostic (one cell, N links),
> native/<triple> buildpaths are per-triple. Open: base images as steward-published
> host contracts. build.tel: module image reshaped (assemble app, dockerfile form,
> docker/** context, guarantees, artifact oci-index + platforms); schema: Module gains
> `guarantee`, Product gains `platform`. Validator feedback for Jon: an unrecognized
> child compound (E306) is attributed to line 1, which cost a bisection to localize.
> New reference docs: design/scenarios.md (14 user-POV scenarios with enabling-feature
> bullets — the 6 probed in session plus JDK/Scala targeting, downstream testing,
> npm-link, reproducibility, topology, totality, TASTy re-lowering, self-shipping
> extensions), design/glossary.md (~60 terms, LIRA + build-tool vocabulary), and
> design/catalog.md (instances by category: source forms, universes, application
> types, carriers, disciplines, tools with their edges, host contracts, presumption
> kinds, schemas — the prose precursor of universes.tel; ends with the
> overloaded-words disambiguation: WASM is four things, JAR two, WASIp2 a host
> contract, JavaScript a universe). builds.md §12.2 + guarantees.schema.tel (new,
> registered as lira-guarantees): the guarantee interchange format — canonical BinTEL,
> entries (kind, name, predicate?, temporal class, contract module, atom hash) sorted
> by atom hash; the load-bearing decision is that consulting and recording are one act
> (each entry carries its atom, so macros compiling total reads simultaneously collect
> the uses blobs — the compiler never contains an atomizer); temporal class in-band so
> macro libraries never hardcode discipline semantics; predicates license
> specialization soundly (folded into the atom, demanded by the emitted used-set);
> in-process plugins get the value via the lira.tool trait, external compilers the
> file via an output-affecting setting; return path = accumulated atoms merged/sorted
> into lira-uses. TEL note: a select cannot be a field's type (E217) — Section-style
> `select` members only; used a validated scalar (TemporalClass) instead. Paths
> (builds.md §15 "No assumed paths"): absolute locations are the tool's (workspace,
> side-effect class, SHOULD be varied/normalized to smoke out embedded-path bugs);
> relative layout is the user's and always authored — `assemble <module> / path
> bin/example` (Assembly record in the schema; consumer names what it consumes, full
> path incl. filename; platform-stable under oci-index), source files keep authored
> glob paths, the Dockerfile is identified by form and passed with -f. Declared paths
> make §15.1 layer disjointness statically checkable. TEL tooling update landed: bare
> schema names in pragmas are now E121 — schema files' headers are now
> `tel 1.0 specification.tel/tels:1.0.0` (the pinned meta-schema coordinate).
> builds.md §14.5 (new; old 14.5 renumbered 14.6): the three execution spaces —
> project root (authored only, read-only to tools, no build dir, worktree never
> dirtied), store (the tool.md content-addressed store; all outputs incl.
> intermediates as trees), workspace (per-invocation ephemeral view; stack frames not
> a heap; inter-tool data flows tree→store→workspace, never a shared directory).
> Consequences: every edge invocation memoized under a computable identity (§1
> extended inward — incrementality is cache hits); tools can only communicate through
> declared edges (hidden coupling unrepresentable); warm-equals-cold law for
> tool-private incremental state. Debugging is an operation: failed workspaces
> retained + named in diagnostics, `lira workspace` re-materializes any, store
> inspection via CLI — all occasion-class. Glossary gains store + workspace.
> `extract` (command step + CLI op) is the one way content leaves the store:
> `extract <module> <entry> <path>` with entries named by build-file vocabulary
> (artifact app-type, or container format for a §13.6 derivative) and fully-authored
> destinations; SUPERSEDES `emit` (which assumed an output location — removed from
> build.tel, schema and glossary); feedback-loop lint: extraction destination matched
> by a source glob is an error; command extract = shared intent, CLI = occasion.
> build.tel gains `command jar`; schema gains Entry scalar + Extract record on
> Command, drops Module.emit. The tool descriptor schema is written and registered:
> tool.schema.tel (`name tool`) — edges positional on output form, name defaulting to
> it; parameter field closes the parameterized-edge gap (placeholder in form names,
> bound by §15.2 backward flow); Input(form, optional), context Forms,
> employs/emits DisciplineIds, Component(name only — versions configured in build.tel
> and locked), Setting = SPECIFICATION (key + Effect: output|nothing), tool-level +
> per-edge sets. Worked instance scalac.tool.tel validates (three edges; per-edge
> `release`/`module` settings; components on sjsir/nir). Catalog + builds.md §14.3
> updated. Atomization (tool discipline: rigid atom per edge, per classified
> setting) stated in the schema header; the discipline spec itself is still to write.
> builds.md §16: how disciplines relate — leaf canonicalizers, the algebra the only
> composer (inter-discipline delegation would kill decidability-from-manifests).
> §16.1 hierarchy case: folding routes extends-lists into type atoms + the USED-SET
> CLOSURE RULE (used-sets must include atoms of nominal types referenced by used
> members — an obligation on every signature discipline). §16.2 content case:
> predicates graduate to the graph ("a predicate that needs an algebra is a module
> reference in disguise") — `presume file X / schema domain/config 1.2` = file/1
> presence atom + requires on the tels/1 schema module, lineage-satisfied; newer
> extended schemas satisfy older presumptions. §16.3 inventory: coexistence /
> shared encodings (economy only; comparability needs the SAME discipline) /
> composition through the graph. Schema: Presumption gains `schema Include optional`;
> build.tel has the worked example. design/discipline-obligations.md (new): the
> checklist for foo/1 authors — Part A pointers to lira.md §11.2's seven normative
> requirements + §11.1 identification; Part B the eight design-round obligations
> (used-set closure; encoding invariance across producing tools; temporal class;
> configuration-vs-liveness; presence-never-values; probes never atomized;
> native-relation coincidence à la tels/1; no inter-discipline delegation); Part C
> shared conventions to elevate (hard errors on out-of-vocabulary constructs;
> lockstep versioning; state what is NOT certified). Ends with which parts apply to
> which discipline kinds.

> **Round update (2026-08-21b, TEL/LIRA integration).** Issues #13/#14 implemented (design
> in propensive/tel `design/lira-schema-references.md`): new discipline spec `spec/tels.md`
> — atomizes a TEL schema's component sequence (one rigid atom per component + one per
> ordered pair) so LIRA grades coincide with TEL's signature-subsequence relation; versions
> derived; publish-time name binding (schema `name` = module name; layer names = `+`
> selections); composed signature exposed; claims fixed path `schema.tel`; domain = every
> universe + host. distribution.md: normative reference syntax `‹domain›/‹name›[:‹selector›]`
> (§2; version/tag disjoint by first character; selector form matches published releases
> only; bare = local-only development reference), new ops RESOLVE-VERSION / RESOLVE-TAG /
> RESOLVE-EXTENDS (§6), and the resolver rule "manifest signatures are the authority, store
> indexes are caches" (§8). lira.md: `tels/1` added to the §11 discipline registry, and the
> §14 extensibility sentence now closes the seam — extension layers ship as tels/1 modules,
> LIRA delivered by LIRA. Cross-refs added in universes.md (§2 note, §6 item 1) and
> builds.md §13. Earlier same-day: TEL soft/hard space fixes in lira.md §14 code blocks +
> build.schema.tel; schema registered (`tel schema add`), build.tel validates against it.

> **Round update (2026-08-20).** A full design round has happened since this handover was
> written; `build.tel`, `local.tel` (new) and `builds.md` §§6–8 (new) are now the current
> record. In brief: open items 1–4 and 8–9 below are resolved in the file. Vocabulary
> settled with Jon: `apply` (toolchain), `assemble` (egress input), `artifact` (egress
> product, registry application-type names only), `emit` (canonical derivatives to disk),
> `source` (singular), paired add/remove keywords (`include`/`exclude`, `require`/`obviate`,
> `source`/`omit`) replacing nested delta blocks, and an `ephemeral` flag on declarations
> (distributed-by-default; source-only is free — builds.md §7.1; supersedes the earlier
> `retain` block, which duplicated declaration names). Two axis kinds, `integration` and `option`, each grouping
> `case` blocks, classified by consumer observability (builds.md §7); declaration-vs-
> refinement nesting in any order. External dependencies use domain-scoped coordinates with
> version-prefix/tag selectors; `build.lock` is distribution.md §5/§8 material. `repository`
> = transport + scope only (never identity/version authority); `local.tel` holds per-user
> overrides and downstream test projects. Scala compiler lines: integration axis + rule-6
> profile predicate (builds.md §7.3); TASTy re-lowering design in builds.md §8. Escalations
> recorded in builds.md §6: section-scoped toolchain records (now load-bearing), bare-name
> provider identity, stewarded namespaces. The governing principle is builds.md §9: four
> sources of variation (project / world / system / occasion) in bijection with four homes
> (build.tel / build.lock / local.tel / commands+CLI); hacky modification = a variation in
> the wrong home; the lockfile is a verifiable memoization with authority only over when
> the world was sampled (multiple locks legitimate only along that dimension). §9.1 lists
> six known surface gaps (tool-version selectors, run settings, artifact selection, local
> pins, precedence order, machine config + command args). The deployment round has now
> begun: builds.md §10 records the running/deployment unification — one validity algebra
> with two world documents (lockfile ↔ environment manifest); each axis consumed by one
> stage (universe→egress, option→deploy pin, integration→environment assignment, making
> deployability verifiable from manifests); a local run as an ephemeral environment of
> one; and environments as delta-variations of a shared `topology` block ("the topology
> is the environment's API, deployment is its implementation" — env atoms are exactly
> bindings+givens). build.tel's environment stubs are replaced by `topology main` with
> production/staging/local nested as its children (delta blocks; `address`, `serve`;
> coordinates main/production etc.); local.tel gains a machine `given`. Still open from items 5–7/10: service self-description details
> (serve → api records), database-given closure semantics, staging used-set publication,
> and the §10.4 unpulled thread (cross-environment topology identity). builds.md §11:
> tools are services whose host is the build tool — plugin API as host contract
> (lira.tool), `lira-tool` application type, edges as served surface, loadability =
> deployability, bootstrap = §1's cache decision (stage0 from prebuilt), recursively
> verifiable toolchains. Toolchains nest as delta children (one tool per edge;
> `tools/forked`; per-module `apply`); the Tool record gains its third sharpening (LIRA
> identity + toolchain-L118) and the three tool-trust tiers are named. build.tel has
> `toolchain forked`, `module scalac-fork` (artifact lira-tool) and `module fork-test`.
> builds.md §12: presumption disciplines (capability/1 existing and keeping its general
> name — the kind→discipline mapping is many-to-one; envvar/1, file/1 as siblings —
> presence/format only, never values or mutable content), guarantees as a
> generated per-topology config contract given to environments, and the totality loop:
> `presume` (module) / `guarantee` (topology, and local.tel for the machine) with the
> compiler consuming the guaranteed set via the lira-tool context, treating reads as
> total, and emitting the used-set itself; sound via deployability-verifiability; each
> discipline must state its temporal guarantee class (envvars strong, files weaker).
> builds.md §12.1 widens the inventory under two laws — guarantees erase configuration
> partiality, never liveness partiality; datasets-at-version are a fourth kind — with
> configuration-class kinds (locale, dataset, isa, device, tty…) vs admission-gate
> kinds (memory, disk, clock, privilege), schema staying excluded (data plane), and
> exclusivity guarantees flagged as the frontier (a judgment shape, not an atom shape).
> The §12 inference asymmetry is named: demand inferred, supply never (guarantee lines
> are load-bearing). Spec gap 1 now has a worked proposal (scope Tool records like
> Dependency records — universe/integration fields, Setting record, LIRA identity
> fields, two L118/L119-analogue L-numbers; awaiting Jon applying it spec-side).
> `build.schema.tel` (new, repo root) is the draft build-file schema, written as a
> consistency audit — findings in builds.md §13 (Universe≡Case; TEL lacks record
> composition; the two `require` operand scalars; thin environment deltas). build.tel
> gained examples: tag selector, sed:gnu + dataset presumes, boolean option with
> ephemeral case, omit/exclude in nir, jdk19×rudiments0 cell refinement, worker
> service, dataset guarantees (also in local.tel).

## The exercise

Jon Pretty (the maintainer of LIRA) is designing a build tool that consumes and produces
LIRA bundles. As a test of **LIRA's applicability**, he is drafting the build file format in
TEL, in `build.tel` at the repo root, with comments embedding open questions. The working
loop: he edits, hands it over for review, gets feedback keyed to specific spec sections, and
iterates. Several rounds have happened already.

Evidence runs **both ways**. Tensions surfaced in the build file are evidence about the
*spec*, not only about the build file — one round already caused a spec change (the `source`
record, §14/§17, adopted because "use a prebuilt artifact or build from source" had no
checkable notion of source identity). If something in the build file feels natural but has no
LIRA expression, that is a finding worth escalating, not automatically a build-file error.

Repo: `/Users/propensive/work/lira`, `main` @ `dd6bfe0` ("WIP build.tel"). The spec is
mature and internally consistent; it has been through a full four-agent consistency review.

## What to read, in order

1. **`design/builds.md`** — audits *this exact build tool* against the format. §1 (a `.lira`
   file as a verifiable cache of a build step), §2 (the concept-mapping table), §3 (where the
   fit is imperfect), §4 (the build→deployment continuum). **Start here.**
2. **`spec/lira.md`** — the core. §4 taxonomy, §9 sections/realms/integrations, §10–§12 the
   atom algebra, §13 buildpath and environment, §14 schema.
3. **`spec/services.md`** (deployable releases, `app` realm, environment validity),
   **`spec/environments.md`** (environment releases, `env` realm, bindings, provisioning),
   **`spec/hosts.md`** (host contracts, `requires`, spanning).
4. **`design/universes.md`** — §1 taxonomy (format / universe / host / application type /
   egress / join), §2 universe registry, §4 the pipeline DAG. Directly relevant to the
   build file's `toolchain` block.

## LIRA in one page, for build-file purposes

- A `.lira` file is **one release of one module**: a human-readable TEL manifest plus a
  Brotli payload of content-addressed blobs. Nothing in the format ever asks *how* a release
  came to exist — which is what makes "use the prebuilt bundle or build from source" a cache
  decision rather than a mode switch (`builds.md` §1).
- **Module**: a named library, host contract, deployable service, or environment. One API
  lineage, many releases.
- **Section**: one compiled view, keyed by **(realm, integration)**. The root section is the
  first; every other section is an **overlay** on it — deletes plus replace/add, minimal by
  construction (§9.3, **L107**).
- **Realm**: the section-key axis. Universes (`jvm`, `sjsir`, `nir`; `js`, `klib`,
  `component`, `native/<triple>` reserved) plus the three non-universe realms `host`, `app`,
  `env`.
- **Universe**: a realm where *independently-published libraries compose*. That litmus test
  is the whole definition.
- **Integration**: one alternative dependency vector a release was built against (§9.5).
  Sections form a (realm × integration) matrix; every cell presents the same API (**L108**).
- **Atom**: the hash of one indivisible fragment of public API. *Rigid* (monotonic within a
  lineage) or *replaceable* (inline/macro bodies). The **folding principle**: additions that
  are safe become standalone atoms; additions that break fold into the parent's value, so
  compatibility checking is set arithmetic and never a rule engine.
- **Snapshot** = hash of the sorted atom set = **API identity**. **Lineage** = the ordered
  snapshots of one major series. **`payload.hash`** = **implementation identity**.
- **Grades**: patch (same atoms), minor (rigid ⊆, replaceables replaced), major (anything
  else — a new lineage). **L110** forbids extending a lineage with a non-conforming
  successor unless a major is explicitly requested.
- **Versions** are *derived*, optional, strictly `x.y.z` (§12.5). No version = a
  **development release**, identified purely by hashes — the natural currency of an inner
  build loop. Consumers decide everything on hashes; a version is a human-readable hint with
  **no authority**.
- **`dependency`** = a buildpath edge (content composes in). **`requires`** = an edge to a
  *provider* — a host contract or a deployed service (environment supplies it, nothing
  materializes). Same satisfaction rule for both: the required snapshot appears in the
  candidate's lineage.
- **Used-sets and spanning**: a consumer may record which of a provider's atoms it actually
  uses; `used ⊆ atoms(B)` then licenses substitution across majors and across modules. This
  is what makes mocks, rewrites and alternative implementations expressible.
- **Buildpath validity** (§13.3): uniqueness, namespace disjointness, resource disjointness,
  closure, compatibility, profile coherence, provider requirements — every rule decidable
  **from manifests alone, without reading any payload**. That principle is load-bearing
  across the whole spec.
- **Egress**: a linking edge from a universe to an **application type** (a pair of closed
  artifact format and host contract). One universe has many egresses — `sjsir` egresses to
  js-app, wasm-browser, and wasi-component. *A library never chooses its egress; an
  application does.*
- **Environment**: an `env`-realm release stating desired state — `given` (platform
  contracts supplied), `deploy` (releases, pinned by implementation identity, with `route`
  pins), `binding` (address + provider module + release selection).

## Current `build.tel` state

Structure: `command` blocks (build/dev/test) → `toolchain tools` (scalac, javac, docker) →
`project example` with modules `api`, `base`, `js-shims`, `core`, `image`, `test` → two
`environment` blocks (`production`, `staging`).

**Settled and correct:**
- `realm jvm` / `sjsir` / `nir` now use the right names (was `js`/`wasm`/`native`).
- The empty `realm jvm` block reads correctly as pure opt-in.
- Delta blocks (`add`/`remove` inside `sources`, `require`, `include`) mirror §9.3 overlays.
- `service main2 example/image` — the positional form is the right answer for deployment.
- `produce xeq` on `test` matches a real application type (the xeq-bundle egress).

## Concept mapping (condensed from `builds.md` §2)

| Build file | LIRA |
| --- | --- |
| module definition | module; one release per built state |
| dependency on a prebuilt bundle | `dependency` record, resolved by snapshot via the index |
| dependency built from source | development release + `build` pin (§13.2, **L118**) |
| promotion of a from-source build | version assignment (§12.5) — payload untouched, re-signed |
| variant: alternative dependency versions | **integrations** (§9.5) |
| variant: platforms | **universes** — realm sections (§9.4) |
| multi-variant `.lira` | the (realm × integration) section matrix (**L131**, **L108**) |
| downstream pins one variant | integration pinning; canonical assignment (§13.3) |
| build outputs `.lira` | library releases |
| build outputs other targets | egresses to application types; deployable releases (`app`) |
| deployment stanza | an environment release (`env` realm) — the build tool acts as *operator* |
| "connect to service X" | `requires` naming a deployable module |
| coherent deployment | environment validity (§13.7, **L147**) |

**The guardrail** (`builds.md` §4): one surface syntax may present both edge kinds, but they
must **compile to distinct records** — `dependency` (buildpath supplies, materializes,
composes) versus `requires` (environment supplies, probes verify). Also: *closing over* is
what an egress does; an environment merely **coheres**. Don't let the two words blur.

## Decisions already made — please don't relitigate

1. **`realm` → `universe` in the build file** (recommended last round, not yet applied). A
   library module's blocks can only ever name universes; `realm` is the genus that also spans
   `host`/`app`/`env`, which never appear in a module body. The **spec keeps `realm`** as the
   genus: every alternative fails — `platform` is explicitly rejected by §4.1, `target` is
   taken by §13.3, `world` collides with WIT, `domain` is taken by disciplines (§11.2),
   `profile` is taken twice, `facet` breaks on host contracts.
2. **A universe block opts in *and* configures.** Which universes are reachable is the
   toolchain's knowledge; which sections a release *carries* is a per-release commitment,
   because opting in binds you to **L108** (one API in every section).
3. **Module body = root section; universe blocks = overlays on it.** This is §9.3's own
   calculus surfacing at definition level. Minimality (a delta restating an unchanged
   setting) should be a lint, per **L107**.
4. **js / wasm are not universes for Scala.** `sjsir` is the universe; js-app,
   wasm-browser and wasi-component are *egress products* from that one section. Likewise
   `nir` is the universe and elf/pe/macho per architecture are application types.
5. **The `toolchain` block is `universes.tel`** — the machine-readable pipeline registry
   still listed as proposed in `universes.md` §6. Jon's DAG comment (tools as edges, formats
   as nodes, path resolution between any format pair) is almost word-for-word `universes.md`
   §4. It should eventually distinguish edge *kinds*: compiler edges into universes, egress
   edges out to application types, join edges between. (DAG search here is fine — §13.3's
   intractability warning is about choosing *which releases* to include, a different problem.)

## Open items

1. **Versions and external dependencies — the biggest gap, and the natural next iteration.**
   Nothing in the file names a third-party dependency or any version. One external `include`
   with a coordinate and a resolution selector would exercise: domain-scoped coordinates
   (`distribution.md` §2), index resolution to a snapshot, the version-as-hint rule (§12.5),
   the lockfile (implementation identities — obtainable free from `distribution.md` §8), and,
   if targeted at two majors of one dependency, the file's first real **integration**.
2. **Products on library modules.** `produce jar` sits on `core`, which is a library. Note
   the nuance worth resolving explicitly: a JAR derived from a `jvm` section is the
   **canonical derivative artifact** (§13.6 — deterministic, hash-declarable, recoverable
   from the section) whereas an *executable* JAR is an **egress product** (jvm-app). Two
   different things share the word "jar", and the build file should distinguish them.
   `produce js/dom` similarly conflates an application type with a host contract.
3. **`module image`** uses `include core`, but a deployable declares no dependencies
   (**L143**). It wants a distinct word — `from core` was suggested — meaning *egress input*.
4. **`include` is overloaded three ways**: compose (dependency), close over (egress input),
   and run (deploy). Only the first is a `dependency`.
5. **Addresses.** Nothing says where `main` answers. Suggestion: `service main example/image
   at api.example.com`, compiling to a `binding` row; address prefix-disjointness (**L151**)
   then becomes a build-file lint.
6. **Served surfaces.** For `require database` (or anything requiring `main`) to be
   satisfiable, the provider needs a self-description — e.g. `serve openapi.json` compiling to
   an `api` record. An empty atom set can satisfy no requirement (`services.md` §4.3).
7. **`service database`** is a bare name. It must resolve either to a deployed service with
   an artifact, or — more likely for a managed database — to a **`given`**, which has
   different closure semantics (`environments.md` §4). Note the deliberate boundary: a
   database's *protocol* is requirable; its *schema* is the data plane, explicitly excluded
   (`environments.md` §10) because two-sided evolution over retained data needs machinery a
   single lineage doesn't have.
8. **Delta syntax consistency.** Nested `add`/`remove` reads best (and `delete` would match
   §9.3's own word). Host-contract names are still inconsistent: `java.base` (dot) at lines
   56–57 versus `java/base` (slash) at lines 79–80. Dots, per the host-contract registry.
9. **Toolchain versions.** Absent, but needed: reproducibility (§17) and the manifest's
   `toolchain` records are per-version. Also unresolved: bare flags (`experimental`) map to
   `Tool.flag`, but key-values (`encoding UTF-8`) have no schema home yet.
10. **`environment staging`** is a stub. Fleshing it out would exercise variants: the same
    releases judged against different givens and bindings, with a mock standing in by
    cross-module spanning — conditional on consumers publishing used-sets
    (`environments.md` §9, `services.md` §5).

## Working notes

- Jon writes the TEL by hand and embeds questions as `#` comments — read the comments as the
  agenda for each round.
- Feedback is most useful when keyed to specific spec sections and L-numbers, and when it
  distinguishes *"the build file is wrong"* from *"the spec has a gap"*.
- The spec's own worked examples (`lira.md` Appendix B, `services.md` §12,
  `environments.md`) are good models for what a manifest produced from this build file should
  look like — a useful cross-check is to hand-write the `.lira` manifest that `module core`
  ought to produce, and see whether the build file supplies everything it needs.
