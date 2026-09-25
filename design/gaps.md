# Gaps

The open items of the build tool design, tiered by how designed they already are. A
living checklist: items move up as they are worked, and off as they land. Section
references are to [`builds.md`](builds.md) unless qualified. Last revised alongside the
junctures round ([`junctures.md`](junctures.md)), after the Fury/Fever design round
([`fury.md`](fury.md) §13).

## 1. Designed — awaiting spec-side application

Worked proposals whose target is `spec/lira.md` (or distribution):

- **L151 and L152 in reliquary** — the level rule (an edge whose required level its
  provider does not certify is reported uncertified, never satisfied) and the
  recorded-edge rule (dependency records for every module the used-set closure reaches)
  are normative (spec §11.5, §13.2) and unimplemented: buildpath validation needs the
  juncture of each edge as an input, and publication needs the closure check.
- **`tels/2` implementation** — the discipline is specified over the composed schema
  (spec tels.md); no atomizer exists, and the root `.tel` schema files' pragma pin
  (`specification.tel/tels:1.0.0` today) should move to `2.0.0` once the pinned `tel`
  tool accepts it.

- **Section-scoped `Tool` records**, with the `Setting` record and LIRA tool identity
  (coordinate + implementation identity; the toolchain analogue of L118). Motivated
  from four directions: the `scala` integration axis, option-case settings, per-edge
  components, and now the `inputs/1` hash, which needs per-cell tool identity as an
  input. The blocking item for the manifest hand-derivation (§5 below).
- **The `inputs/1` source scheme** and its `lira-inputs` document schema (§3.1
  addendum): registration in the spec's scheme vocabulary; the schema file itself.
- **Bare-name provider identity** (§6 item 4): `dependency`/`requires`/`grant`/
  `binding` carry bare names; the domain exists only at resolution.
- **Stewarded namespaces** (§6 item 5): the transparent index record for unclaimed
  vendor namespaces, with DNS-proof supersession.
- **Discipline-obligation promotions** ([`discipline-obligations.md`](discipline-obligations.md)
  B.2): encoding invariance into lira.md §11.2. (B.1, the used-set closure rule, is
  subsumed by **L152**, spec §13.2.)
- **Registry entries**: `oci-image` (jvm-reachable deliverable), `lira-tool`.

## 2. Discipline specs to write

Statements exist; the `dts.md`-pattern documents do not:

- **The tool discipline** (atomization declared in `tool.schema.tel`'s header: one
  rigid atom per edge, one per classified setting). Small — the schema did the design.
- **`envvar/1`** and **`file/1`** (presumption disciplines; builds §12, obligations
  B.3–B.6 apply, temporal classes lifetime/startup respectively).
- **The dataset discipline** (the `dataset` kind's target; version predicates grade
  like contracts).

## 3. Named and half-designed

- **Host provisioning** — hosts.md §3's closing observation as a tool capability:
  driving `jlink` (and its analogues) with exactly the platform modules an
  application's computed per-module requirements name, yielding an image whose
  contract satisfies the aggregated requirement set by construction. No command
  surface, no worked example; pairs with artifact selection below.
- **Windows and other non-POSIX host contracts** — a `windows` entry for catalog.md and a
  capability vocabulary for process conventions (signal model, filesystem semantics),
  including a portable OS surface — capabilities every OS contract publishes under identical
  names, so cross-contract spanning has something to cover; hosts.md §6 and §11 state the
  terms, deferred from the multi-platform round.
- **The `run` settings model** — §10.3's design (side-effect class on the ephemeral
  environment) has no syntax; debugger attach is still inexpressible. Gates the rest
  of the deployment round.
- ~~**Artifact selection**~~ — decided: every `artifact` line is named and selection is
  by name only, with no host default ([`fury.md`](fury.md) §12); `build.schema.tel`
  updated; `build.tel`'s specimen artifacts to be named.
- ~~**The precedence order**~~ — written as one list in [`fury.md`](fury.md) §11, with
  the layer-legality rule and the one-role-per-file allocation; awaiting application to
  builds.md §9.1.
- **`local.tel` completion** — designed in [`fury.md`](fury.md) §11: `pin`, `store`,
  `budget`, the user-global file's role, `local.schema.tel` drafted at the repo root;
  raw local tool paths deferred with local binaries (fury.md §6). Awaiting the worked
  file and the schema's registration.
- **Environment deltas** — the schema's `Environment` record holds only an address
  override; deploy deltas, given deltas, and the staging-mock worked example remain
  (original open items 5–7/10), with `serve` → `api` extraction details and the
  `service database` given semantics.
- ~~**Command arguments**~~ — decided: declared named arguments only, no passthrough
  tail, option arguments by explicit declaration ([`fury.md`](fury.md) §12); schema
  updated.
- **Multi-parent `assemble`** — repeatable-with-paths, deferred until a real example
  lands (§15.1); the static layer-disjointness lint comes with it.
- **Parameterized edges in practice** — the descriptor supports the parameter; no
  worked instance exercises binding beyond the oci-index sketch (`clang` is the
  motivating case).

## 4. Genuinely untouched

- **The data plane** — [`junctures.md`](junctures.md) §10 names its shape (an edge whose
  ends are exercised at different junctures over retained data; the rolling-overlap
  quantifier over a retention interval, with node persistence beyond deploy records) and
  stops there. Excluded from the format as before (services.md §11).

- **`build.lock`** — designed in [`fury.md`](fury.md) §12: content (dependency, tool,
  component and host resolutions with proofs and STH), the stale-lock rule, `--update`,
  `--lock`; `lock.schema.tel` drafted. Awaiting the worked file.
- **The publishing workflow** — the largest undesigned area: the publish command's
  UX, signing, version-assignment flow, promotion of development releases,
  multi-module publication ordering (co-publication, §5 question 4).
- **`registry.tel`** (formerly `universes.tel`) — designed in [`fury.md`](fury.md) §7:
  one document, sibling `.tool.tel` descriptors, published layers for extension;
  `registry.schema.tel` drafted. Awaiting the content transcribed from
  [`catalog.md`](catalog.md).
- **Diagnostics as a design surface** — validity failures are rich judgments (which
  rule, which cell, which missing guarantee); the reporting model is unspecified.
- **Codegen** — decided for form-changing edges: declared with `generate <tool>`,
  verified against form-based resolution, outputs as store trees ([`fury.md`](fury.md)
  §12); same-form edges (annotation processors) excluded for now and still open.
- **Remote sharing of the store** — the swarm ([`fury.md`](fury.md) §8) covers a
  user's own machines: memo hits on any member are hits. Team caches and build farms
  beyond a swarm remain undesigned; input-hash lookup (§3.1 addendum) joins the online
  service's query list beside the commit reverse-lookup.
- ~~**The build tool ↔ `lira` CLI relationship**~~ — decided: three binaries (`lira`,
  `fury`, `fever`) sharing library code, developed together in this repository
  ([`fury.md`](fury.md), [`fever.md`](fever.md)).
- **The `lira.tool` trait in earnest** — its home (the `lira-tool` module), base
  (anthology's types) and carrier (BinTEL) are decided ([`fury.md`](fury.md) §6,
  [`fever.md`](fever.md) §3); the Scala surface, descriptor extraction and the uses
  return path's file mechanics land at ladder step 5.
- ~~**Incremental correctness mechanics**~~ — moot: compiles are cold and tools hold
  no incremental state ([`fury.md`](fury.md) §4); reopens only if incremental
  compilation is ever adopted.

## 5. Verification debt

- **Hand-derive `module core`'s manifest against spec §14** — the design's
  outstanding falsification test: three integration axes (flattened, minus the
  `ephemeral` nir column), an option axis, presumptions compiling to a generated
  contract, per-case tool components, `inputs/1`. Cannot complete until the
  Tool-record proposal is applied — which is itself informative. **Do this first.**
- **The two leak-checks** — does anything tempt `project` or `topology` into the
  format? (§5 question 2; §10.4.)
- ~~**The `.gitignore` question**~~ — decided: at the self-hosting step the specimen
  moves under `design/` and the root `local.tel` is gitignored ([`fury.md`](fury.md)
  §11).
