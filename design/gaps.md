# Gaps

The open items of the build tool design, tiered by how designed they already are. A
living checklist: items move up as they are worked, and off as they land. Section
references are to [`builds.md`](builds.md) unless qualified. Last revised alongside the
input-identity round (§3.1 addendum).

## 1. Designed — awaiting spec-side application

Worked proposals whose target is `spec/lira.md` (or distribution):

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
  B.1, B.2): the used-set closure rule and encoding invariance into lira.md §11.2.
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
- **The `run` settings model** — §10.3's design (side-effect class on the ephemeral
  environment) has no syntax; debugger attach is still inexpressible. Gates the rest
  of the deployment round.
- **Artifact selection** — artifacts are anonymous; no host-triple default; no
  per-invocation naming. `extract` sharpened the need without solving invocation-time
  selection.
- **The precedence order** — six configuration layers (tool built-ins → user-global
  local → build.tel → project local.tel → command → CLI), never written as one list;
  plus the cross-file refinement rule (local.tel may refine `environment local` with
  system/occasion content only).
- **`local.tel` completion** — the `pin` record (described §9.1, absent from file and
  schema); machine-configuration shape (tool paths, cache location, parallelism); its
  own TEL schema (currently validates as bare syntax only).
- **Environment deltas** — the schema's `Environment` record holds only an address
  override; deploy deltas, given deltas, and the staging-mock worked example remain
  (original open items 5–7/10), with `serve` → `api` extraction details and the
  `service database` given semantics.
- **Command arguments** — passthrough ("run just this one test") so occasions never
  require edits.
- **Multi-parent `assemble`** — repeatable-with-paths, deferred until a real example
  lands (§15.1); the static layer-disjointness lint comes with it.
- **Parameterized edges in practice** — the descriptor supports the parameter; no
  worked instance exercises binding beyond the oci-index sketch (`clang` is the
  motivating case).

## 4. Genuinely untouched

- **`build.lock`** — its content is known (Release records + inclusion proofs; §9.2)
  but there is no schema file, no `update` command design, no alternate-lock
  (`--lock canary.lock`) surface.
- **The publishing workflow** — the largest undesigned area: the publish command's
  UX, signing, version-assignment flow, promotion of development releases,
  multi-module publication ordering (co-publication, §5 question 4).
- **`universes.tel`** — the machine-readable registry ([`catalog.md`](catalog.md) is
  its prose precursor): format, and how built-in tool definitions, extension→form
  mappings and kind→discipline tables ship.
- **Diagnostics as a design surface** — validity failures are rich judgments (which
  rule, which cell, which missing guarantee); the reporting model is unspecified.
- **Codegen** — source→source edges (annotation processors, protocol compilers):
  admitted by the DAG in principle, exercised by nothing; interacts with the
  feedback-loop lint (generated sources live in trees, never the project root).
- **Remote sharing of the store** — team caches and build farms; presumably the
  online service, undesigned. Input-hash lookup (§3.1 addendum) joins the service's
  query list beside the commit reverse-lookup.
- **The build tool ↔ `lira` CLI relationship** — `tool.md`'s "one program, composable
  roles" suggests one binary; undecided.
- **The `lira.tool` trait in earnest** — the real Scala surface in the project's own
  stack; descriptor extraction; the uses return path's file mechanics.
- **Incremental correctness mechanics** — how often the warm-equals-cold law (§14.5)
  is spot-checked, and by whom.

## 5. Verification debt

- **Hand-derive `module core`'s manifest against spec §14** — the design's
  outstanding falsification test: three integration axes (flattened, minus the
  `ephemeral` nir column), an option axis, presumptions compiling to a generated
  contract, per-case tool components, `inputs/1`. Cannot complete until the
  Tool-record proposal is applied — which is itself informative. **Do this first.**
- **The two leak-checks** — does anything tempt `project` or `topology` into the
  format? (§5 question 2; §10.4.)
- **The `.gitignore` question** — `local.tel` is committed as a worked example but
  designed to be per-user; decide its status before it misleads.
