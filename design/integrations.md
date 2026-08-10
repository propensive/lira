# Integrations: One Release, Several Dependency Vectors

A `.lira` release carries one dependency vector. It can hold several *universes* — `jvm`,
`sjsir`, `nir` views of one compilation — but only one answer to "what was this built against".
This document works through lifting that restriction, and the surprisingly small set of changes
it turns out to need.

> **Status.** Adopted: spec §9.5, §13.2–§13.3 (L131–L133) implement this design. Terminology
> has moved on since it was written: the matrix axis is now the **realm** (spec §4.1), "valid
> for a universe" reads "valid for a target" (L132), the `against` stub discussed in §2 was
> removed rather than extended (dependency records carry `integration` scopes instead), and
> deployable releases (`app` realm, [`spec/services.md`](../spec/services.md)) participate in
> the matrix with one `app` section per integration.

## 1. The two cases

**Version alternatives.** `foo:2.0.0` is built against `bar:8.0.0`, but a slightly different
build works against `bar:7.0.0`, for consumers who cannot move yet. Today these are two
artifacts, distinguished by mangling the coordinate (`foo_bar7`) — a compatibility fact encoded
in a string, invisible to every rule in the format.

**Backend alternatives.** `foo:2.0.0` depends on `bar`, or on `baz` instead, according to which
backend the consumer wants. This is not a version question at all; it is a choice the consumer
makes, and nothing in the buildpath determines it.

The two look similar and behave differently, and the difference matters for §4: the first is
**constraint-driven** — what else is on the buildpath decides it — and the second is
**preference-driven**, decided by the consumer or by a declared default.

## 2. What the format already has, and why it is not enough

Spec §9.5 defines `against` on a section: "the API snapshot hashes of dependency releases it was
compiled against, when these differ from the release's declared dependency list." The intent is
right and the mechanism is a stub. It has no behaviour anywhere in `reliquary` — `Section`
decodes and renders it, `LiraAssembler` never sets it, and nothing reads it — and it is too weak
to build on: a bare list of snapshot hashes carries no module names, so a consumer would have to
reverse-look-up each hash through every candidate's lineage to learn what it constrains.

More fundamentally, a section is keyed by universe alone. `select Universe`'s alternatives are
bare flags and the decoder requires exactly one atom in a section header, so there is no room in
the key for a second axis. Two `jvm` sections are not illegal today; they are simply
first-wins — `Materializer` does `find(_(0) == universe)` and the derivative cache path
`<payload-hash>/<universe>.jar` gives them one slot between them.

## 3. The shape: a second scoping axis

A release declares **integrations**, each a named alternative dependency vector. Sections are
keyed by (universe × integration); dependency records are scoped to integrations exactly as
they are already scoped to universes.

```text
integration
  id    bar8
  rank  0
integration
  id    bar7
  rank  1
  label built against bar 7.x for consumers pinned to it

dependency bar  Kx3f…  8.0.0   integration bar8
dependency bar  Lm81…  7.4.2   integration bar7
dependency rudiments-core  Cd34…  0.64.1        # no scope: applies to every integration

section jvm    integration bar8   tree Ef56…
section jvm    integration bar7   tree Pq44…
section sjsir  integration bar8   tree Gh78…
section sjsir  integration bar7   tree Rs90…
```

Two things make this cheap. First, scoping rather than nesting: dependencies stay a top-level
list, tagged, exactly as main already tagged them with `universe` for the case where a platform's
implementation needs a dependency the others do not. The single place universe scoping is
interpreted — the `applies` test in `Buildpath.validate` — becomes the single place integration
scoping is interpreted, as a conjunction of two axes. Second, a release declaring no
integrations is unchanged in every byte: one implicit integration, no migration, no new required
fields.

## 4. The load-bearing rule: one API across the whole matrix

Spec §9.6 (**L108**) requires every universe to atomize to an identical atom set. Generalize the
quantifier to the whole (universe × integration) matrix and everything downstream survives
untouched: one release still has exactly one API identity, so the snapshot, the lineage, §13.2
satisfaction and diamond resolution need no changes at all. Integrations become invisible to
consumers' compatibility reasoning — which is precisely what makes them safe.

It also tells the truth about the backend case. `foo` can offer `bar` and `baz` integrations iff
its API does not leak `bar`/`baz` types — which is exactly the condition under which swapping
backends is a meaningful thing to offer. If the API does leak them, the invariant fires and the
answer is two modules: the same answer §9.6 already gives for a library whose API differs by
platform.

The implementation cost is nil. `LiraAssembler` already compares sections on
`(discipline, key, atomClass, valueHash)` and aborts on divergence. It needs to range over more
sections, not to change.

**The limitation, stated plainly.** Replaceable atoms — public `inline` and macro bodies —
encode the content they splice into consumers. A public inline method that splices
integration-differing code will differ in value hash between integrations and fail the
invariant, so such a module cannot use integrations and must publish separately. The obvious
relaxation is identity over rigid atoms with same-keys-only over replaceable ones, letting
§13.4's staleness machinery carry the difference; but the snapshot covers replaceable atoms, so
relaxing it changes what API identity means. That belongs in a later schema layer, decided on
its own merits, not smuggled in here.

## 5. Resolution: validity under an assignment

§13.3 gains one quantifier. A buildpath is valid for a universe iff there **exists** an
assignment mapping each release to one of its integrations such that rules 1–6 hold, with
dependency applicability evaluated under (universe, assigned integration).

Nothing else in §13.3 changes, and that is the best argument for this design. The rules that
decide between integrations are the ones already there. Walk the version case: a buildpath
carries `foo` and `bar:7`, and `foo` offers `bar8` and `bar7` integrations. Under the `bar8`
assignment, `foo` requires a snapshot of `bar` that the present `bar:7` does not carry in its
lineage — rule 5 rejects it. Under the `bar7` assignment every rule holds, so that is the one
selected.
Uniqueness (rule 1) does the same work from the other direction, when an assignment would need a
second release of a module already fixed, and closure (rule 4) when an integration names a module
the buildpath does not carry at all.

That last one is worth drawing out, because it answers the backend case without any new
machinery: an integration naming `baz` fails closure on a buildpath that carries only `bar`. A
consumer therefore selects a backend simply by putting it on the buildpath. Explicit pinning is
needed only where the buildpath carries both and the choice is genuinely free.

**Determinism.** Two conforming resolvers must choose the same assignment or reproducibility is
lost. Among valid assignments the canonical one is lexicographically least on the sequence, over
releases in ascending module-name order, of each assigned integration's (`rank`, `id`). A
consumer MAY pin integrations explicitly, the remaining releases still taking their canonical
choices.
Pinning is how the preference-driven backend choice is expressed; `rank` is what makes the
unpinned case reproducible.

**The cost, stated plainly — and it is smaller than it looks.** The obvious reading is that this
turns §13.3 from a linear audit into an NP-hard search, and that was the assumption when the
mechanism was first drafted. Implementing it showed otherwise, and the reason is worth keeping.

A buildpath is a *fixed* set of releases: which release provides a module is settled before any
integration is chosen. Every rule an assignment can affect — closure and compatibility — then
turns on one release together with its own choice, and no rule relates one release's integration
to another's. The choices are independent. So the canonical assignment is a per-release first
fit: for each release in isolation, take the first of its integrations in (`rank`, `id`) order
whose own dependencies hold. Linear in the total number of integrations, no backtracking, and
canonical by construction rather than by minimizing over candidates.

That also sharpens the diagnosis. A buildpath admits no valid assignment exactly when some single
release has no viable integration, so a tool can always name the release and, for each of its
integrations, the rule that rejected it. "No valid assignment" as a bare verdict is never the
best a tool can do.

The intractable problem this resembles arises only for a resolver that also decides *which
releases to include*, since an integration can then pull a module onto the buildpath and change
what everything else resolves against. That is dependency resolution proper, and §13.3 does not
do it — it audits a buildpath it is handed, and §13.2's exact snapshots keep the two apart. A
build tool that constructs buildpaths inherits the harder problem from its own design, not from
this mechanism.

None of which displaces the cheaper answer: **spanning often removes the need entirely**. §13.4
lets a publisher prove that one compilation is valid against several majors of a dependency when
its used-set is contained in all of them, and §9.5 instructs producers to prefer that proof over
emitting alternatives. That instruction should be read as the governing principle, not an aside —
an integration is the fallback for genuine incompatibility, and a publisher who reaches for one
first has usually skipped a cheaper answer.

## 6. Storage: the overlay base stays flat

Sections form a matrix, and every non-root section overlays the single root, leaving §9.3's one
equation alone. The matrix duplicates *tree rows* — the (sjsir, bar7) section repeats the sjsir
divergence that (sjsir, bar8) already carries — but not blobs, which are content-addressed and
stored exactly once. The cost is therefore metadata, not payload, and it is proportional to
divergence rather than to the size of the library.

Two-level overlaying — a universe overlay, then an integration overlay on top — would remove the
row duplication, at the cost of turning §9.3 from an equation into a chain, with a base that
must be defined, validated and made acyclic. Not worth it now; a schema layer can add it if real
manifests turn out to bloat.

## 7. Rejected alternatives

- **Per-integration snapshots.** Let integrations present different APIs, and give each its own
  snapshot. This is the honest model if you want `foo` to expose `bar:8`-only methods when built
  against `bar:8` — but API identity, lineage membership and §13.2 satisfaction all become
  per-integration, dependents must record which integration they compiled against, and the
  diamond argument ("jointly satisfiable iff some lineage contains both") stops holding. The
  whole compatibility algebra pays for a case that §9.6 already answers with "then it is two
  modules".
- **Dependencies nested inside sections.** Structurally tidier, but it moves `dependency` out of
  the document level, rewrites the common case for the benefit of the rare one, and duplicates
  every shared dependency across every section.
- **Reusing `against`.** Retaining it as a per-section shorthand keeps two mechanisms for one
  idea, and the field cannot name a module. Since nothing implements it, deleting it costs
  nothing.
- **Global capability tags** (`backend:bar` as a cross-module concept, so one preference selects
  a backend everywhere). Genuinely useful and genuinely speculative; release-local ids answer
  the stated need. A later layer can add coordination if the need appears.

## 8. Spec impact

Applied to the spec: §9.5 becomes "Integrations", §9.6's L108 ranges over the matrix, §13.2
gains integration scoping beside universe scoping, §13.3 gains the assignment quantifier and the
canonical-assignment rule, §13.5 selects by universe *and* integration, §14 gains the
`Integration` record and the `integration` fields, §16 verifies every cell, and `against` is
removed. New labels: L131 (well-formed declarations), L132 (an assignment exists), L133 (a
declared integration has at least one section).

One incidental strengthening: §13.6's derivative hash now identifies a (universe, integration)
pair, so a tool holding an ordinary JAR recovers not just the release but which integration it
is looking at.
