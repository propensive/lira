# Junctures: the Edge, the Instant, and the Two Species of Composition

This document re-derives the genus that [`execution.md`](execution.md) §2 named
**composition** — "a set of releases together with granted contracts, under a validity
judgment decidable from manifests alone" — from a smaller primitive: one **edge**, between a
requirer and a provider, exercised at one **instant**. The buildpath (spec §13.3) and the
environment (spec §13.7) are then two species of one thing, and the rules each carries are
derived rather than transposed. It is a derivation in the style of [`universes.md`](universes.md)
§5 and `execution.md`: the motivation, the framing that falls short, the primitive, what it
explains, what it deliberately does not unify, and the spec changes it entails.

> **Status.** Adopted, in this round: the genus and its terms are normative in spec §4.2,
> with the level rule **L151** and the recorded-edge rule **L152** in §11.5 and §13.2; the
> four defects of §11 are corrected in the base spec and its companions; and the TEL schema
> discipline is rebuilt as `tels/2` ([`../spec/tels.md`](../spec/tels.md)). §12 records what
> changed where.

## 1. Motivation: the acceptance, and an observation

TEL's BinTEL specification has gained an **acceptance** (bintel.md §8.4): a small document by
which a reader, about to receive a document, tells the writer which composed schemas it can
consume, in decreasing order of preference, and which further components it would like
included if the writer holds them. The writer serves the first alternative it can. The
relation both sides compute is TEL's subtype check on composed schemas (tel.md §8.2, §24.3),
and what the exchange establishes is the existence of one composition acceptable to both —
found at the moment the two peers meet, from their own libraries.

That is recognizably the shape of LIRA's own question. A `requires` record names what a
consumer was built against; an alternative group lists what it would accept, in declaration
order; a provider's lineage lists what it can serve; resolution takes the first satisfied
member. TEL and LIRA share most of their compatibility algebra, and the observation that
motivated this document is what the sharing reveals about *time*:

> In a running environment, whenever two components must communicate, they must be able to
> establish a compatible edge *at that instant*. There are many independent instants, each
> over a small subgraph — often two nodes and one edge. A build looks like a different problem
> only because there is a single instant at which every edge of the whole graph must be
> compatible. Dependency resolution is the same problem with one instant and one subgraph
> that happens to be the entire graph.

The spec already knows that build and deploy are one judgment: the abstract says so, §13.7
transposes §13.3, services.md is headed "the second composition", execution.md names the
genus, and builds.md §10.1 reduces the difference to "which document supplies the providers".
But the genus is stated *set-first* — a set of releases under a judgment — and *build-first*:
the environment's rules are obtained by transposing the buildpath's, and the two places where
the transposition had to differ (uniqueness dropped; the quantifier over every concurrently
serving release) are asserted, with reasons, rather than derived. The observation above says
the primitive is smaller than the set. This document takes it seriously, and finds it right in
substance and wrong in three particulars.

## 2. What the current framing asserts

Six things the spec states that a smaller primitive ought to derive:

1. **The concurrent quantifier.** L145 rule 2 requires satisfaction "against every
   concurrently-serving release", and L146 requires the rolling overlap state to be valid.
   Both are given as what operations wants, which is true, but not why they are *forced*.
2. **The three guarantee levels.** §11.5 lists linkage, recompilation and behavior as
   independent, and services.md §7 transposes them to wire, regeneration and behavior. Why
   three, and why these, is left to the examples.
3. **Uniqueness versus address.** L111 requires one release per module on a buildpath; the
   environment "deliberately drops" it and replaces it with the binding (L149, execution.md
   §5). The replacement is motivated by rolling deploys, not derived from anything.
4. **Acyclicity.** builds.md §3.3 observes that build graphs are acyclic in practice and
   requirement graphs are not, with the cause "builds must terminate in an artifact;
   environments must merely cohere" — a slogan, not a mechanism.
5. **Staleness.** §13.4's staleness rule — recompile the consumers whose used-sets intersect
   the replaced atoms — is the one place the spec reasons about content copied at one time and
   used at another, without saying so.
6. **The data plane.** §10.5 and services.md §11 exclude topics and databases because they
   "carry both polarities at once", which is correct and leaves unsaid what makes them do so.

## 3. The primitive: an edge

An **edge** joins a requirer to a provider. On the requirer's side stands a **group**: the
ordered alternatives of hosts.md §6 and spec §14 — `requires` records sharing an `alternative`
identifier, an ungrouped record being a group of one, a member marked `optional` making the
group a preference rather than a need. Each member names a module, a required snapshot, and
optionally a used-set. On the provider's side stands an **offer**: a release's lineage and its
atom set. The edge is compatible under exactly the relation the spec already has and nothing
else: the required snapshot appears in the lineage (§13.2, hosts.md §7), or the used-set is
contained in the atom set (§13.4, spanning, across majors and across modules).

Two kinds of edge exist, and the difference between them is the difference hosts.md §8
already draws, now read as a difference in how the provider end is *found*:

- a **dependency** edge names a module, and its provider is the release of that name — found
  by name, unique on a buildpath (L111), its content materialized into the requirer;
- a **requirement** edge names a module too, but its provider is any release that
  *satisfies* it — found by satisfaction, possibly a different module altogether (cross-module
  spanning, hosts.md §7), nothing materialized.

Polarity is fixed per edge: the provider ascends, the requirer descends (§10.5). One
qualification, from `openapi/1`: an offer may carry flows in both directions — a callback or a
webhook reverses who supplies the request (openapi.md §5) — and the discipline folds each flow
by its own direction. The *edge's* polarity, who requires of whom, is fixed; the flows inside
the offer are the discipline's business.

The group, not the bare record, is the primitive. It carries the preference order that
resolution reads, and `optional` is a property of the group's emptiness, not of a member.
This is the first thing the acceptance makes visible: a reader's list of alternatives *is* a
group, authored by the same side.

## 4. Junctures: when an edge is exercised

An edge is a standing relation; it is *exercised* at instants. Call one exercise a
**juncture**. Three kinds recur throughout the spec, unnamed:

- **Construction.** The requirer is compiled, generated or otherwise built against the
  provider: a Scala module compiled against a dependency's TASTy; a client generated from a
  service's OpenAPI description; a presumption's config contract emitted by the compiler
  (builds.md §12). The required snapshot in a `dependency` or `requires` record is the
  requirer's memory of this juncture — where the record has one. Not every edge does: a
  `capability/1` requirement (`git`, `kubernetes`) was constructed against nothing; the
  record is authorial (hosts.md §9), and it certifies presence.
- **Linking.** Requirer and provider are bound together without reconstructing the requirer:
  classloading onto one classpath, native symbol resolution, an egress closing over a
  buildpath, a connection to a service that is already running. The provider present now may
  differ from the provider present at construction, and satisfaction is precisely the question
  whether it is compatible with the one then present — which is what lineage membership
  decides, and why a lineage is a *history*.
- **Operation.** Each call, each message, each read of a file. What happens here is behavior,
  which no hash scheme certifies (§18); the third verification moment (hosts.md §9) *probes*
  around it, checking that the provider present is the one that was judged.

The juncture fixes the guarantee level a judgment **requires**. A construction juncture
requires the recompilation level; a linking juncture requires the linkage level — wire
compatibility, for a service (§11.5); an edge that exists only to assert that something is
there requires presence; operation requires behavior, which nobody certifies. The provider's
disciplines and profiles fix the level **certified** (§11.2 item 7, §11.6). These are two
different quantities, and the spec's soundness rests on the first not exceeding the second: a
judgment is a claim only at certified levels, and where an edge's required level is not
certified the honest report is *uncertified at that level*, not *satisfied*. §11 records the
place where the current text lets the two slip past each other, and §12 the rule (**L151**)
that closes it. `breaks <level>` (§12.4) is the same distinction seen from the publisher's side:
a lineage step that satisfies the atoms while failing a level a profile certifies records the
shortfall, and the consumers that must move are exactly those whose linking junctures the
step's delta touches.

Presence is the fourth level, and naming it is not an addition but a correction: `capability/1`
and `environment/1` already "certify presence" (§11.3), and hosts.md §5 had to explain
presence as "the recompilation level for content addressed by name", which is a definition
by analogy. A capability's existence is what a presence-only edge asks, and no other level
describes it.

Three consequences follow immediately, and they are three of the six assertions of §2:

- **Why three certified levels, and a fourth.** Linkage and recompilation are the levels of
  the two junctures at which a compatibility claim can be *checked against content*; presence
  is the level of an edge with no content to check; behavior is the level of the juncture at
  which nothing is checked at all. The levels are independent (§11.5) because the junctures
  are: a change can preserve what a linking juncture needs while breaking what a construction
  juncture needs, and vice versa.
- **Why the required snapshot is what it is.** A requirement record is the requirer's memory
  of one juncture, carried forward to be judged at the next. Satisfaction by lineage
  membership is compatibility *across junctures*: the provider present at linking must stand
  in the lineage of the provider present at construction.
- **Staleness.** An inline body (§10.2, a replaceable atom) is content copied into the
  requirer at its construction juncture. A later linking juncture against a release that
  replaced the atom carries the stale copy — linkage holds, since replaceability soundness
  (§11.2 item 5) guarantees it, and the requirer *should* be reconstructed. §13.4's staleness
  rule is the construction/linking distinction stated for one atom class.

One more thing the junctures explain is the pending judgment of §13.3 rule 7. A buildpath
judged without host contracts, or a requirement naming a deployable, is not judged twice: the
edge will be exercised at a linking juncture, its judgment can be made at any earlier moment
against whatever providers are then hypothesized, and "pending" means only that none were.
Judgment precedes exercise; that is the whole design.

## 5. Media: how an endpoint is found

An edge names a module; something must turn the name into a release. Call it the **medium**:
the space in which offers are placed and in which an edge's provider end is resolved. Two
media exist today, and the spec's non-edge-local rules are exactly their rules, in two layers
each:

| Layer | Universe (a buildpath's medium) | Environment (addresses) |
| --- | --- | --- |
| Provider identity | one release per module — **L111** | one binding per address — **L149** |
| Linkage names | namespaces and resource paths pairwise disjoint — **L112**, **L126** | none: an address is the name |
| Existence | a join edge's served universe must be in the target — rule 4's `serves` clause | a provider with no binding is *unaddressed* (environments.md §6) |

Two readings keep the table honest. A node participates in a medium's rules only where it
places an offer in it: a host contract on a buildpath contributes nothing to rules 2–3
(hosts.md §8), and a deployed-but-unbound provider has no address, so the quantifier of L150
falls back to every serving release of its module. And the first layer is not about linkage
at all — L111 is uniqueness of *module name* in the composition, so that a dependency edge
resolves to one release; only the second layer is about the universe's linkage mechanism.
Execution.md §5 had seen the correspondence ("the binding disambiguates at run time what
uniqueness disambiguated at build time"); the medium is its name.

A **join** edge (spec §13.2, `serves`) resolves in the *served* universe's medium — a Scala.js
module's dependency on a TypeScript module was constructed against `.d.ts` and links at the
bundler — and the target is the set of media a buildpath makes available. That the spec
declares uniqueness and disjointness global across a multi-universe target (§13.3, "global")
is over-strong across distinct linkage namespaces and harmless; the medium reading says which
rule belongs to which universe, should it ever matter.

The universe taxonomy of universes.md §1 was built on three characteristics — an interface
convention, a linkage mechanism, a capability model. In the present vocabulary a universe is
a medium whose second layer is a linkage mechanism, and the environment is a medium whose
second layer is empty because an address *is* its linkage mechanism: the name resolves to
exactly the release bound there, and nothing else in the environment shares its namespace.

## 6. Compositions, states, transitions

A **composition** is a finite set of nodes — releases and granted contracts — with the groups
they carry, judged over a **state**: the set of edges that could be exercised while that state
holds. **Validity** of a state is:

1. every group resolves to a member that is compatible with its resolved provider's offer
   (§13.3 rules 4, 5, 7; L145 rules 1–2);
2. the medium's rules hold (§13.3 rules 1–3; L149);
3. every profile predicate holds (§13.3 rule 6; L145 rule 4).

Nothing else. In particular **aggregation** — hosts.md §10's rule that requirements on one
provider from several releases are jointly satisfiable iff one release satisfies each, and
services.md §6 rule 3's transposition — is not a fourth kind of rule. Where the medium
resolves every edge naming a module to the *same* provider, the joint judgment is the
conjunction of the per-edge judgments against that provider: at build time L111 and the
target's one-contract-per-module make it so, and the diamond rule is what the conjunction
looks like. Where the medium resolves edges naming one module to *different* providers — two
majors of a service at two addresses; a mock standing in, by cross-module spanning, for one
consumer and not another — the edges are judged separately, each against every release
concurrently serving inside the binding it resolved to (L150). The aggregated requirement set
remains a useful *report* — "this application needs a host providing these capabilities",
the host contract in all but publication — and the seed of a deployable's `requires`; it is
not a rule.

A **transition** replaces one state by another. A transition is valid iff every state it
passes through is valid. L146 names the two states a rolling replacement passes through —
the posterior state and the overlap in which predecessor and successor serve together — and
the general statement covers any intermediate state a multi-release transition produces. Here
the first assertion of §2 is derived rather than asserted: within a state, the requirer may
meet any release concurrently serving in its resolved binding at any juncture, so each must be
compatible; the universal quantifier over instants, made finite, is the universal quantifier
over concurrently-serving releases.

Judgment precedes exercise, and may be made against a provider set that is **hypothesized** —
a target (§13.3): host contracts named but not present — or **authored** — an environment
release, whose grants and bindings are signed intent. Probing (hosts.md §9, services.md §8)
later verifies that the actual providers are the judged ones: it checks the medium's
actuality, not behavior. Drift is a probe result, and enters no judgment (§13.7).

## 7. What the framing explains

Read back against §2:

1. **The concurrent quantifier** is forced (§6): any release serving in the resolved binding
   may be the one met at the next juncture.
2. **The guarantee levels** are the junctures (§4), plus presence for the edges that have no
   construction.
3. **Uniqueness versus address** is the medium's first layer (§5): the buildpath resolves by
   name, the environment by address, and each medium's identity rule is the one that makes
   resolution single-valued.
4. **Acyclicity** has a mechanism. A dependency edge is constructed against a provider
   *product* — the built interface carrier, TASTy or `.d.ts` — which must therefore exist
   before the requirer's construction juncture; two modules each constructed against the
   other's product can only be published together, which builds.md §3.3 records as the
   practical bar. A requirement edge is constructed against a *description* — an OpenAPI
   document, a capability listing — or against nothing, and the description is authored
   independently of the provider's own build; two services generated against each other's
   descriptions are ordinary. The one requirement edge that *does* order a build is the
   tool-provider edge (builds.md §11): a tool built in the same build is a provider whose
   product the build must produce before the step that requires it, and the step DAG
   (fury.md §5) orders by production of inputs, context *and tools*, not by edge kind. The
   step DAG and the dependency graph coincide on dependency edges and diverge there.
5. **Staleness** is a construction juncture's copy met at a linking juncture (§4).
6. **The data plane** is an edge whose two ends are exercised at different junctures over
   retained data (§10).

Two more fall out. **Loadability is deployability** (builds.md §11) because the build tool is
an environment and `lira.tool` an ordinary requirement edge — by that route and no other; it
is not evidence for a general law about requirement edges. And **`optional`** is what a group
resolving to nothing means at the linking juncture: provisioning records the absence and the
artifact's own fallback governs, which is the use-time adaptation hosts.md §6 describes.

## 8. What the framing does not unify: two species

The observation of §1 proposed that build is the special case of runtime. It is not, and the
derivation shows why, in both directions. Each species has degeneracies the other lacks:

| | Buildpath | Environment |
| --- | --- | --- |
| Edge set | under-determined: an assignment of integrations is *searched* (L132) | *authored*: each deploy record names the integration deployed (§13.7) |
| Providers | hypothesized: a target names contracts it does not contain | authored: grants and bindings, operator-signed |
| Edge kinds | dependency and requirement | requirement only (L143) |
| Constructed against | a product (hence ordered; acyclic in practice) | a description, or nothing (hence cycles are ordinary) |
| Materialization | sections onto artifact paths (§13.5) | none; provisioning evaluates into addresses |
| Closure | an egress closes over the buildpath and produces a *different* object | nothing closes; the state persists |
| Transitions | none of its own — the buildpath is judged once and consumed | many; each judged (L146) |
| Its own identity | none: a buildpath is a set a tool holds | a release, with a lineage graded by `environment/1` |

The buildpath is not "one transition and then frozen": it has zero transitions, and the
egress is not a state change of the buildpath but the production of a deliverable that then
enters *another* composition as a node. The environment is not "the general case": it lacks
dependency edges, materialization and the searched assignment entirely, and has a lineage of
its own that no buildpath has. What the two share is everything in §3–§6 — edges, junctures,
media, states, the validity shape — and that is the genus. The spec should say so once, name
the species' degeneracies, and stop presenting either as the other transposed.

Assignment deserves one more sentence, because the spec contradicts itself about it (§11).
"Can this release deploy into E?" is a real question a tool answers by trying each of the
release's integrations against E's grants and bindings (builds.md §10.2), and that search is
exactly the canonical-assignment search of §13.3 — but it *precedes* the judgment and authors
its input, the deploy record. L145 judges the authored state; it does not search.

## 9. TEL acceptances beside LIRA resolution

The two negotiations line up per document flow, and the alignment is worth stating exactly,
including where it stops.

| | TEL acceptance (bintel.md §8.4) | LIRA resolution (§13.3, §13.7) |
| --- | --- | --- |
| Who lists alternatives | the reader, in preference order | the requirer's group, in declaration order |
| The requirement | an ordered composed schema `S_cons` | a required snapshot, plus a used-set |
| The offer | the writer's composition and the base's published lineage | the provider's lineage and atom set |
| The relation | `compose(S_doc) <: compose(S_cons)` (tel.md §24.3) | snapshot ∈ lineage, or used ⊆ atoms |
| Resolution | the writer serves the first alternative it can | the first satisfied member, the first candidate by rank |
| When | at the operation juncture, by the peers, from their own libraries | before any juncture, from manifests |
| Result | one document, under one composition | a provisioning table; a classpath |

Three differences keep them complementary rather than equivalent:

- **The relation.** TEL's subtype check is decided on composed schemas and is order-dependent
  — a component's effect depends on the components before it, so a subsequence of components
  does not imply a subtype (tel.md §24.4, remark) — and it is semantic in one place (pattern
  containment). LIRA's is set inclusion, made monotone by folding (§10.3). A discipline over
  TEL schemas can be *sound* with respect to the subtype relation — growth of the atom set
  implies a subtype — and the rebuilt `tels/2` is; it cannot be complete.
- **The writer's choice.** Having found an alternative it can serve, a TEL writer chooses the
  *richest* composition the reader can take, adding components one at a time and re-checking
  the subtype relation after each. A LIRA offer is a fixed atom set; the provider makes no
  choice.
- **The guarantee.** LIRA judges, from manifests, that a satisfying offer exists at the level
  the provider's discipline certifies. It does not — cannot — guarantee that a particular
  handshake at the operation juncture succeeds: that is the acceptance's own check, run on the
  actual documents. The two verify different things, and both are wanted.

Where they touch is precise. An alternative's `any-published` flag lets the writer use "any
published component of the lineage" — and *published lineage* is LIRA's lineage of the schema
module, so TEL already delegates that word. And a request/response call is two document flows
with the reader alternating: the server reads the request, the client reads the response. The
per-flow polarity of the acceptance is exactly what `openapi/1`'s directional atomization
encodes (openapi.md §5): request positions fold because the server, as reader, must keep
accepting what the client sends; response positions stand alone because the client, as reader,
accepts a richer document by projection. One LIRA edge with one requirer carries both flows;
each flow is one acceptance.

## 10. The data plane (informative lead)

Services.md §11 excludes topics and databases because readers and writers both evolve against
retained data. The juncture reading names the shape: a topic is an edge whose *writer* end was
exercised at one juncture and whose *reader* end is exercised at a later one, against data that
persists between them. The reader at the later juncture faces every writer release that ever
wrote retained data — including releases no longer deployed, so the nodes of that composition
outlive their deploy records — and the interval is per-datum retention, not the interval
between two transitions. Both polarities appear because the retained document is an offer the
reader requires *and* the reader's acceptance is an offer the writer must have satisfied.

This is a research direction, not a payoff: "the rolling overlap quantifier over a retention
interval" is the right first sentence of a design, and it needs the writer-side forward
compatibility edge and a notion of node persistence that nothing here supplies. The exclusion
stands.

## 11. Four defects the framing exposes

Making the edge explicit found four places where the current text is inconsistent with itself
or silently unsound. Each is corrected in this round.

1. **Aggregation versus per-requirement resolution.** hosts.md §10 and services.md §6 rule 3
   require that requirements on one module be jointly satisfiable by one provider — "the
   union of the used-sets must be covered by each" — while environments.md §6 resolves each
   requirement to its own candidate binding, possibly a different module by cross-module
   spanning. Under §6 above aggregation is derived, not primitive: edges are grouped by
   *resolved provider*, and requirements on one module that resolve to different bindings are
   judged separately. The aggregated set stays as a report and as the seed of `requires`.
2. **Assignment at run time.** spec §13.7 says the environment "needs no assignment machinery
   beyond" the deploy record's choice; services.md §6 and builds.md §10.2 say validity holds
   "for an assignment of one integration per deployed release" found by search. §8 above:
   the search is a tool's, precedes the judgment, and authors the deploy record; L145 judges
   the authored state.
3. **Unrecorded construction edges.** Used-sets close transitively over the reference lists of
   dependencies' replaceable atoms (§13.4), and reference lists are cross-module (§11.2 item
   6): a module `C` depending on `D1`, whose inline body splices content of `D2`, has `D2`'s
   content materialized into it at construction — an edge — with no `dependency` record to
   carry a Uses blob, so staleness under a `D2` replacement, and spanning across a `D2` major,
   are undefined by the letter. The invariant is the one hosts.md §6 states for branches —
   every edge an artifact has is declared — and the fix is a publish-time rule (**L152**): a
   release's `dependency` records name every module whose atoms enter its used-set closure,
   with a Uses blob, whether or not its sources name that module. Closure edges are then
   recorded edges, and rules 4 and 5 judge them like any other. No schema change; no flag.
4. **Required level above certified level.** services.md §7's table reads the rows for
   *running* consumers — linking junctures — off atoms that `openapi/1` certifies only at the
   recompilation level, and openapi.md §2 concedes that the wire claim is a profile's. The
   rule of §4 (**L151**) makes the concession normative: the juncture fixes the required
   level, the disciplines and profiles fix the certified level, and an edge whose required
   level is not certified is reported as uncertified at that level. The table gains its
   certified-level column, and "a cautious operator treats every minor as potentially
   coordinated" stops being advice.

## 12. Spec impact (adopted)

1. **spec §4.2**: **composition** restated as the genus of §6; **edge**, **juncture**,
   **medium**, **state** and **transition** defined; **guarantee level** counts four.
2. **spec §11.5**: presence as the fourth level, with the juncture reading of each level and
   **L151**; hosts.md §5 and §11.3's `resource/1` and `capability/1` entries no longer define
   presence by analogy to recompilation.
3. **spec §13.2 / §13.4**: **L152**, the recorded-edge rule; closure edges decidable.
4. **spec §13.1, §13.3, §13.7**: the buildpath and the environment introduced as species; the
   seven rules regrouped (edge-local 4, 5, 7; medium 1–3; global 6) with rule 7's "pending"
   read as deferred judgment; aggregation per resolved provider; the deploy record's
   integration as authored input and the tool's search as its author.
5. **services.md §5–§7, hosts.md §10, environments.md §6–§7**: the same four corrections at
   their normative homes; services.md §7's table with a certified-level column; transitions in
   the general form.
6. **spec §10.5, §16, introduction.md §10, §12, §13**: polarity per edge; the verification
   moments tied to junctures; "one question, two moments" retold as one question at every
   juncture, decided in advance by two compositions.
7. **`tels/2`** ([`../spec/tels.md`](../spec/tels.md)): the TEL schema discipline rebuilt on
   TEL's subtype relation — atoms over the composed schema, sound in the direction that a LIRA
   minor implies a TEL subtype, conservative where TEL's relation is semantic or narrowing —
   replacing a `tels/1` that faithfully encoded a relation TEL has since withdrawn as unsound.
   The catalog gains `specification.tel/tels:2.0.0` and `specification.tel/acceptance:1.0.0`.
8. **Design docs**: execution.md §2 carries a status note; builds.md §3.3, §4, §10 read the
   genus; the glossary gains the terms; gaps.md records the reliquary-side application of L151
   and L152 and the data-plane lead of §10.

## 13. Open questions

1. **Multi-release transitions.** L146 enumerates two states; a controller that replaces
   several releases at once passes through a lattice of intermediates. Whether the spec should
   name a canonical order (per binding, in `rank` order) or leave the enumeration to the
   controller is undecided; §6's general statement is compatible with either.
2. **Presence certified by content.** `resource/1` certifies presence of named content it can
   actually check (an export path exists in the tree). Whether that is the same level as a
   `capability/1` presence claim, which only a probe can check, or whether "presence" wants
   splitting by verification moment, deserves a worked case before it is answered.
3. **Implied edges in tooling.** L152 asks publishing tools to emit records for modules the
   sources never name. A tooling-only marker distinguishing authored from implied records was
   considered and not added; if diagnostics want it, it is a schema-layer question, not a
   semantic one.
4. **The data plane** (§10), as ever.
