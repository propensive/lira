# Scenarios: the build format under scrutiny

Each scenario below was used to probe the build file format (`build.tel`) and the design
recorded in [`builds.md`](builds.md). For each: what the user is trying to do, and the
features of the LIRA build system that make it work. Section references are to
`builds.md` unless qualified.

## 1. One library, three platforms

*I maintain a Scala library and want to publish it for the JVM, JavaScript and native,
without three build definitions — and my JS build needs extra shim sources and different
host requirements.*

- The module body is the root section; `universe` blocks are overlays carrying only
  deltas (`source`, `include`/`exclude`, `require`/`obviate`) — §7, spec §9.3.
- One release carries a section per universe under the cross-section invariant: one API
  everywhere (L108).
- `scalac` is one tool with `jvm`, `sjsir` and `nir` edges; the target universe selects
  the edge, so nothing is configured per platform beyond the genuine differences —
  §14.2.
- `ephemeral` marks universes as source-only: distributed payloads carry what is
  retained; everything else rebuilds on demand, checkable against the `source` record —
  §7.1.

## 2. A complex multi-edge compiler

*The Scala compiler targets three backends, each a separate compilation run, with the JS
and native backends versioned independently of the compiler itself.*

- A tool is the implementation unit; an **edge** is an invocation kind; a **component**
  is a separately-versioned constituent of an edge (`scala-js`, `scala-native`) — §14.2.
- Edges are hyperedges with roles: `input` (consumed), `context` (dependency cells
  read, with the disciplines the tool employs), `output` — §14.2.
- Per-edge settings and components are selector-versioned and locked like dependencies;
  each cell's manifest records the tool *and* its edge's components (section-scoped
  Tool records, §6 item 3).
- Shadowing is per-edge: a fork declaring only a `jvm` edge replaces exactly
  `scalac/jvm` in a child toolchain, inheriting the rest — §11, §14.2.

## 3. Building a tool, then building with it

*I want to fork the Scala compiler, build my fork in this very build, and compile some
of my modules with it — eventually publishing the fork for others.*

- A tool is a service whose host is the build tool: the plugin API is a host contract
  (`lira.tool`, a Scala trait graded by `tasty/1`), and (jar, `lira.tool`) is the
  deliverable `lira-tool` — §11, §14.3.
- Loadability is deployability: whether the build tool can run the tool is decided from
  manifests, by lineage or by spanning over the plugin's compiled-reference used-set.
- The tool's edges are its served surface: a descriptor extracted at publish
  (`tool.tel`), atomized so edge changes are graded — §14.3.
- Bootstrap is the §1 cache decision: a self-hosted compiler's stage0 is its own
  prebuilt release. Anything compiled by a development-release tool is build-pinned and
  unpublishable until the tool publishes (the toolchain analogue of L118).
- Consequence: toolchains are recursively verifiable — independent rebuild applies to
  the compiler itself, enabling diverse double-compilation against trusting-trust.

## 4. Packaging an application in a Docker container

*My application needs `ACCESS_KEY` set, `git` on the PATH and a CA bundle on disk. I
package it with a Dockerfile into an OCI image; the image installs git and the certs but
must not bake in the secret. I want the machine to know all of this.*

- Presumptions (`presume envvar ACCESS_KEY`, `presume command git`) aggregate through
  the egress onto the deployable's requires automatically — §12, hosts.md §10.
- The image is a frozen environment-of-one: `guarantee` on the packaging module declares
  what it satisfies internally; declared guarantees are probe-verified *inside the built
  image*, failing the build on a miss — §15.
- What is not guaranteed propagates: `R(image) = (R(app) − G(verified)) ∪ C(deliverable)`,
  the deliverable swapping libc-and-kernel for container-runtime by construction.
- The secret deliberately propagates, and the deployment topology's own `guarantee`
  answers it — the chain *presumes → requires → satisfies-or-propagates → guarantees*
  is checkable from manifests at every joint.

## 5. Multi-architecture images

*I ship the same service for amd64 and arm64 as one multi-arch image, which means the
native executable inside must be built per architecture, from one definition.*

- `oci-image/<platform>` is a parameterized deliverable family; `oci-index` is the
  composite over its members — §15.2.
- Platforms are declared once, at the outermost artifact; the parameter flows
  *backward* through hyperpath resolution, binding each earlier egress's triple
  (platform↔triple mapping is registry data).
- TASTy-family sections are architecture-agnostic — one `nir` cell, N links — while
  `native/<triple>` dependencies resolve per-triple cells; the split falls out of the
  universe definitions, not configuration.

## 6. Multi-parent images

*My runtime image merges a distroless base, a tooling image and a static-assets image.*

- Builder stages are subsumed: hermetic compilation is what the LIRA build *is*, so
  multi-stage Dockerfiles reduce to their legitimate half, runtime composition — §15.1.
- Each parent is a grant-provider; closure runs against the union of their declared
  provisions; overlapping content between parents is the resource-disjointness rule
  transposed to layers (an error, never a merge).

## 7. Targeting two JDKs, or two Scala versions

*My library should work on JDK 19 and JDK 26, with a backport dependency only on the
older one — and be consumable by both Scala 3.9 (LTS) and 3.10 users.*

- Three escalating mechanisms, cheapest first: require the oldest target (newer JDKs
  satisfy by lineage membership); span a lineage break with a computed used-set; only
  when dependency vectors genuinely differ, an `integration` axis — §7.2.
- Compiler lines: the axis is an integration, but the compatibility semantics are a
  profile predicate over toolchain records (TASTy readability, §13.3 rule 6), filtering
  the cells a consumer's compiler can read — §7.3.
- Peer axes form a product, flattened to composite integration ids; cells are refined
  individually by nesting — §7.

## 8. Testing my change against everyone who depends on me

*Before merging a change to my library, I want to know which downstream projects it
breaks — ideally without compiling them all.*

- `local.tel` lists downstream projects as test cases; impact is two-stage: first
  **manifest arithmetic** (grade the fresh development release; check each downstream's
  requirements by lineage membership or used-set spanning — no compilation), then build
  and test only the survivors, with the dev release substituted by build pin — §9's
  local-file design.
- The index knows the full reverse-dependency graph from published manifests, so
  "who else should I test?" is a service query; the local list selects who to run.

## 9. The npm-link workflow

*I'm fixing a bug in a dependency and want my application built against my local
checkout of it until the fix publishes.*

- `repository` declarations are transport and scope only; `local.tel` redirects one to
  a local checkout without touching identity — §9.
- A dirty checkout resolves to a development release under a tree-hash source scheme;
  everything built against it carries build pins, so publication is blocked until
  reconciled (L118) — divergence is quarantined, not forbidden.
- Same commit + same lockfile ⇒ same release identity on any machine: the local file
  cannot change what a release *is*, only where bytes come from.

## 10. Reproducing and trusting a build

*A colleague's machine, CI, and my laptop must produce byte-identical releases — and a
prebuilt artifact from the registry must be checkable, not trusted.*

- The four-source bijection: project intent in `build.tel`, the world sampled in
  `build.lock`, machine facts in `local.tel`, occasion at the CLI — a variation landing
  in the wrong home is the definition of a hacky build — §9.
- The lockfile is a verifiable memoization, not a cache: its entries are signed release
  records with inclusion proofs; deleting it changes *which world* you build against,
  never correctness — §9.2.
- Every prebuilt `.lira` file is a verifiable cache of a build step: rebuild-and-compare
  is always available, and the `source` record ties outputs to exact sources — §1.

## 11. Dev, staging, production — one architecture

*Staging must be production-with-three-differences, my laptop a third instance of the
same shape, and drift must be impossible.*

- A `topology` declares the common shape (services, requirements, served surfaces,
  addresses); environments nest as its children, each a delta — difference has exactly
  one place to be written — §10.4.
- The topology is the environment's API: env atoms are exactly bindings and grants, so
  routine deploys are patch-grade and architectural change regrades every instantiation.
- A local `run` constructs an ephemeral environment-of-one — grants from the machine,
  localhost bindings, dev builds pinned into deploy records — judged by environment
  validity *before launch* — §10.3.

## 12. Total reads of the environment *(added)*

*I never want to write partiality handling for an environment variable my deployment
pipeline guarantees is set.*

- `presume` compiles to requires+uses against a generated config contract; deployment
  is constrained to environments guaranteeing it; probes verify at startup — §12.
- The compiler receives the guaranteed set through the `lira.tool` context, treats the
  read as total, and *emits the used-set itself* — the strongest provenance a used-set
  can have.
- The soundness law bounds the trick: guarantees erase *configuration* partiality,
  never *liveness* partiality — §12.1.

## 13. Upgrading a compiler without relinking the world *(added)*

*A dependency was published with Scala 3.9; I compile with 3.11 and want one backend's
encodings throughout, without waiting for every library to republish.*

- TASTy in the bundle is a portable intermediate source: re-lowering regenerates
  classfiles under the consumer's compiler, deterministically, inside the bundle — §8.
- Re-lowering changes no atoms: the result is a patch-relation sibling, safe to
  substitute by construction.
- The trigger is a manifest judgment (a readability-predicate failure); the action is a
  registry edge; the cache keys it by (tree hash, compiler version).

## 14. Shipping the build system's own evolution *(added)*

*A new universe (say `js`) needs to reach every consumer's tooling as more than a spec
footnote.*

- LIRA extensions are TEL schema layers, published as `tels/2` modules and referenced
  as `‹domain›/‹name›:‹version›` — LIRA delivered by LIRA (spec tels.md; lira.md §14).
- Schema versions are derived from TEL's own subtype relation, so a layer append is
  provably a minor.
- The build schema itself (`build.schema.tel`) publishes the same way, so `build.tel`'s
  grammar is versioned by the same mechanism as everything it builds.
