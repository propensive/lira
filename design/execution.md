# Execution: Environments, Bindings, and the Runtime

This document extends LIRA's story from the two composition moments the spec already decides
— build (spec §13.3) and deploy (spec §13.7, [`services.md`](../spec/services.md)) — to
**execution in long-running environments**: `.lira` manifests consumed by runtimes, not only
by compilers and deployers; addresses at which deployed services are callable; and the
environment's provided surface maintained as a live aggregation of what is deployed, against
which the deployability of the next artifact is a safety judgment.

It is a derivation in the style of [`universes.md`](universes.md) §5, which worked out host
contracts before [`hosts.md`](../spec/hosts.md) specified them: motivation, the encodings
that fail, the inversion that works, the mechanism, and what would change in the spec if
adopted. Nothing here is normative yet.

## 1. Motivation: composition sustained over time

A `.lira` file today has two kinds of consumer. A **compiler** consumes a buildpath: the
composition is judged (spec §13.3), materialized (§13.5), closed by an egress, and the
composition moment is over. A **deployer** consumes an environment: the composition is
judged again (§13.7, services.md §6), a transition is checked (L148), and the artifact
starts running.

But the second composition does not end the way the first one does. An egress closes over a
buildpath and produces an artifact; nothing ever closes over an environment. The environment
*persists* — services keep calling each other, deploys keep arriving, and the compatibility
question that a buildpath answers once is asked continuously, for as long as anything runs.
Execution is not a third composition; it is the second composition **sustained as a steady
state**, with deploys as its transitions (services.md §7) and the third verification moment
(hosts.md §9, services.md §8) iterated from a readiness check into ongoing liveness.

That makes a **runtime** the third consumer of a `.lira` manifest. Everything a deployer
reads — requirements, served surfaces, lineages, used-sets — a runtime can keep reading: to
resolve a requirement to the place it is served, to detect that a provider it depends on has
moved or broken, to report a failure with a named module and snapshot instead of a stack
trace. The information in the manifests is exactly as useful at hour one thousand as at
deploy time; what is missing is the machinery that keeps it consultable: a durable statement
of what the environment is supposed to contain, and a vocabulary for *where* in the
environment each provider is reachable.

## 2. The genus

The spec already knows that buildpath and environment are two species of one thing —
services.md §2 is headed "the Second Composition", and §13.7 opens by transposing §13.3. The
genus deserves a name: a **composition** is a set of releases together with given contracts,
under a validity judgment decidable from manifests alone. (The name must be fenced at once:
this is *not* composition in a universe. Services.md §2.1 paid for that distinction —
universes compose artifacts, environments compose processes — and the genus term names the
shared *judgment*, never a claim that anything links.)

The transposition table, with the two cells the environment currently leaves empty:

| Judgment            | Buildpath (§13.3)                  | Environment (§13.7, services.md §6)   |
| ------------------- | ---------------------------------- | ------------------------------------- |
| Closure             | rule 4 (L113)                      | rule 1 (L147)                         |
| Satisfaction        | rule 5 (L114); rule 7 (L136)       | rule 2, per hosts.md §7               |
| Aggregation         | hosts.md §10                       | rule 3                                |
| Profile coherence   | rule 6                             | rule 4                                |
| Canonical choice    | canonical assignment               | routing pin (a tool-level MAY)        |
| Uniqueness          | rule 1 (L111)                      | **deliberately dropped**              |
| Disjointness        | rules 2–3 (L112, L126)             | **empty**                             |
| Publication         | every member is a signed release   | **"a set a tool is handed"**          |
| Hand-off            | materialization (§13.5)            | **unstated**                          |

The filled rows are the reason services.md could be short: transposition, not invention. The
empty rows are this document's subject. They are not independent gaps — each one is a face
of the same absence, and §5 fills them with one mechanism.

There is also a symmetry to complete on the hand-off row. At build time, a valid judgment is
converted into concrete access by canonical assignment plus materialization: sections become
a classpath, and "invoking those tools is the build's business" (§13.5). At run time the
analogous act — call it **provisioning** — converts a valid judgment into a table from each
requirement to the place its provider answers. Nothing is copied and nothing is linked;
satisfaction is evaluated into addresses. And the closing division of labor transposes
verbatim: *reconciling the running world to the judged one is the orchestrator's business.*

## 3. Desired and actual

Two ways of knowing an environment suggest themselves: **inspection** — query the cluster
and see what is running — and **journal** — replay the record of every deploy and undeploy.
Neither survives contact with operations alone: inspection reports a state with no authority
(who *meant* it to be this way?), and a journal is falsified by the first ad-hoc change or
partial failure that bypasses it.

The distinction that resolves this is the one the two failures point at: **desired state**
versus **actual state**.

- The **desired state** is a statement of intent: what is supposed to be deployed, and where
  each provider is supposed to answer. It is manifest-shaped, and every validity judgment in
  the algebra is a judgment about it. This is already true today without being said: nothing
  in L147 or L148 inspects a process — they read manifests — so the deploy-time rules *are*
  desired-state rules, and no new label family is needed to make them so.
- The **actual state** is knowable only by looking, and looking is the third verification
  moment (hosts.md §9). Extended over an artifact's whole life, readiness becomes liveness,
  and a new judgment becomes expressible: **drift**, the divergence of actual from desired —
  a provider gone, an endpoint answering with a surface that is not the one its release
  ships, an ad-hoc change nobody recorded.

Drift detection inherits probing's epistemic standing unchanged, and this must be said
plainly because the temptation is to promote it: probes are advisory, untrusted and
unprivileged (hosts.md §9), so a drift report carries no authority over any validity
judgment. What it does is trigger *re-judgment* — of the desired state, which may need
amending, or of the reconciliation the orchestrator owes — and give the resulting action a
named, versioned cause. The desired-state document is the source of truth; inspection tests
the world against it; the journal, where one exists, is how tooling maintains the document,
not a competing authority. Migration from actual toward desired — restarts, rescheduling,
rollbacks — is Kubernetes-shaped work, and it stays out of scope on the same sentence that
keeps egress invocation out: the format supplies the predicate; the controller supplies the
convergence.

## 4. Three encodings that fail

As with hosts (universes.md §5.1), the fastest way to the right design is through the wrong
ones.

**(a) Give addresses lineages.** If the API at `api.example.com/orders` has a history, why
not version the address — atoms at the address, a lineage per endpoint, compatibility "of
the URL"? Because an address has *nothing to atomize*: it is a name, not a carrier, and
every byte of interface anyone could hash belongs to the release that happens to answer
there. This is the identical failure to the naive `shell` discipline of universes.md §5.1 —
no content, wrong polarity of ownership — and the conclusion is identical: the address
borrows the bound release's atoms and never has its own. What *changing* an address's
occupant means is settled in §6: a transition, judged by the consumers resolved to it, with
no address-level grade at all.

**(b) Let requirements name addresses.** If a consumer needs the orders API at
`orders.internal:443`, why not write that in its `requires` record? Because it welds the
release to one environment: the same artifact must require the production address in
production and the test address in test, so either the release is rebuilt per environment —
destroying the one-artifact-many-environments property that makes variants cheap (§8) — or
the address is a lie in all environments but one. Requirements name *modules* (L137's two
provider kinds), and where the provider is a third party nobody deploys — a vendor API — the
spec already has its category: capability *given* rather than deployed (services.md §6), a
platform contract (hosts.md §3), which §5 will simply allow to be bound to an address like
any other provider.

**(c) Keep the environment an unpublished set.** Services.md §6 currently says the
environment is "a set a tool is handed, not an object this specification publishes." That
was the right first cut, and it cannot survive this document's ambitions: an unpublished set
cannot be signed, cannot carry the addresses of §5, cannot have the history or the overlays
of §8, and cannot be the desired state of §3 — intent that lives nowhere is not intent. The
spec has already confessed the gap once: hosts.md §10 calls the aggregated requirement set
"the application's host contract in all but publication," and services.md §2.1 answered by
publishing the *requirement* side. The provision side — what the environment offers, and
where — is still in-all-but-publication. This document publishes it.

## 5. The inversion: the environment publishes, and bindings replace uniqueness

The environment becomes a document: the **environment manifest**, authored and signed by the
*operator* — the party the spec has never yet given a pen. It states the desired state of
one environment: the platform contracts that are given, the releases that are deployed, and
— the new vocabulary — where each provider answers.

A **binding** is the unit of that last part: a record of

- an **address** — a DNS name or URL prefix, the environment's own kind of name;
- a **provider module** — either L137 kind: a deployable service, or a host contract for a
  given/third-party surface;
- a **selection** — which releases of that module the binding admits: a snapshot, a lineage
  constraint, or an exact implementation identity.

Now the three empty cells of §2's table fill at one stroke, and the derivation is the same
shape as hosts.md's polarity inversion:

**Bindings are the transposed uniqueness rule.** L111 exists because a buildpath must answer
"which release provides module M?" with one release. L147 deliberately dropped it, because
two releases of one module serving concurrently is the normal state of a rolling deploy —
and dropping it left the question unanswered rather than answered differently. The address
answers it: *the binding disambiguates at run time what uniqueness disambiguated at build
time.* Two majors of one service coexist by holding two addresses; a rolling deploy is two
releases transiently inside one binding's selection; and "which provider does this consumer
get?" is decided per address instead of per path.

**Binding disjointness is the transposed L112.** One address, one binding: two bindings
whose addresses are equal — or where one is a prefix of the other, path-extension clashing
exactly as "a namespace and any dotted extension of it clash" — are invalid in one
environment manifest. Addresses are compared as authored strings on the `owns` precedent: no
canonicalization, no atomization, no hash. (An address that differs only in case or trailing
slash is two addresses; operators who want normalization apply it before authoring, exactly
as they would for a package namespace.)

**The routing pin becomes a manifest fact.** Services.md §6 lets a routing-capable tool
relax rule 2's quantifier for a consumer pinned to one release — "a consumer preference the
manifests cannot imply." With bindings, the manifests *can* imply it, and moreover must: two
concurrent majors of one module have disjoint lineages, so under rule 2's current letter
every lineage-only consumer of that module is unsatisfied the moment both serve — concurrent
majors are today expressible only through spanning or tool lore. Re-scoped by bindings, rule
2 quantifies over the releases *within the binding a consumer resolves to*: satisfaction
must hold against every concurrent release inside that selection (the rolling-deploy
overlap), and releases behind other addresses are simply other providers. Multi-version
service targeting stops being an exception and becomes address arithmetic.

One asymmetry with `owns` should be recorded rather than smoothed over: `owns` claims are
authored by the *publisher* in the release manifest, while bindings are authored by the
*operator* in the environment manifest — necessarily, since the same release must deploy at
different addresses in different environments. The judgment transposes; the authorship does
not. That is not a defect but the appearance of a genuinely new role: LIRA has publisher
signatures and (per distribution.md) index signatures; the environment manifest introduces
the operator's.

**Resolution, made deterministic.** Provisioning (§2) evaluates each consumer requirement to
a binding: the bindings whose provider module and selection satisfy it — by lineage
membership or by spanning, hosts.md §7 unchanged — are its candidates. Where several
satisfy (v2 by lineage, v3 by spanning), determinism is restored exactly as §13.3 restores
it for integrations: a canonical order over bindings (rank, then address) chooses, and a
consumer MAY pin, per-requirement, to a named address — the environment-side analog of
pinning a release to an integration. A requirement with no candidate binding fails closure;
one with candidates fails nothing and resolves to exactly one.

## 6. Mechanism sketch

What the environment manifest would be, in the format's own terms — each choice stated with
its precedent, and the unsettled ones flagged for §9:

- **A `.lira`-style document.** A TEL manifest with the same discipline the release manifest
  has: human-readable, canonically encodable, signed (spec §15). Whether it is literally a
  release — a module in a new realm, following the one-section shape pattern of L135 and
  L143 — or a sibling document under its own schema is open (§9); the one-section-realm
  pattern has twice proven the cheap way to give new content ordinary verification.
- **Contents**: the environment's name; the platform contracts that are given (module +
  snapshot, satisfied from their published releases); the deploy records (module, release
  identity, `app` section — realm and integration, per §13.7); the bindings of §5; and the
  operator's signature over the whole.
- **Pinning, against L118.** Desired state wants exactness — "this build, not whatever the
  lineage says next" — but a `build` pin makes a manifest unpublishable (L118), and for good
  reason on the dependency axis. The precedent that fits is the other pin the spec already
  has: the `artifact` pin (services.md §4.2, spec §14), which pins exact closed content in a
  publishable manifest. A binding's selection pinning an implementation identity is a claim
  of the same kind — about a deployed artifact, not about a composable dependency — and
  L118's rationale (unpublishable development coupling) does not reach it. The document
  recommends selection-by-snapshot as the default and selection-by-implementation-identity
  as the exact form, with L118 untouched.
- **Validity**: §13.7's rules, with the two additions of §5 — binding disjointness, and rule
  2's quantifier re-scoped per binding — each of which would take a fresh label in the
  §13.3 style (one rule, one label) rather than silently widening L147. Deployability
  (L148) is unchanged in form: a transition of the *document* — including a rebinding, which
  is judged as the transition it is: valid iff every consumer resolved to the address
  remains satisfied by the new occupant. No address-level grade exists (§4a).
- **The aggregate, at last published.** The environment manifest's provision side — the
  union of its bindings' surfaces plus its platform contracts — is the dynamic aggregation
  sought at the outset: recomputable from the named releases' manifests at any moment,
  updated by nothing but deploys (transitions of the document), and queryable by any tool
  holding the document and the releases it names. "What does this environment provide?"
  becomes a derived fact with a signature chain, not a cluster query.
- **The runtime's read.** A launcher or sidecar holding the environment manifest resolves
  its release's requirements to addresses (§5), probes the aggregated requirement set before
  serving (services.md §8), keeps probing after (liveness — §3), and reports drift against
  the desired state with named causes. `lira run` on a deployable, given an environment
  manifest, has everything it needs; the behavior stays a tool's business (spec §5.1), but
  the inputs are now all format objects.

## 7. Worked example

The cast of services.md §12, one release later. The orders team runs two majors
concurrently; the environment manifest for `prod` binds them at two addresses:

```text
environment prod

given
  module postgres
  api Ll22…
given
  module kubernetes
  api Mm33…

deploy
  module checkout/orders
  build Qq55…                # implementation identity of the v2-line release
deploy
  module checkout/orders
  build Rr66…                # implementation identity of the v3-line release
deploy
  module checkout/payments
  build Nn44…

binding
  address orders.internal/v2
  module checkout/orders
  api Ff66…                  # v2-line snapshot; selection by lineage position
binding
  address orders.internal/v3
  module checkout/orders
  api Ss77…                  # v3-line snapshot (a fresh lineage)
binding
  address payments.internal
  module checkout/payments
  api Cc33…
```

The `checkout/payments` release (services.md §12) requires `checkout/orders` at `Ff66…`
with used-set `Gg77…`. Provisioning resolves it: the `/v2` binding satisfies by lineage; the
`/v3` binding satisfies only if `Gg77… ⊆` the v3 release's atoms — say it does, since the
six operations it calls survived the major. Two candidates; canonical order (or a pin)
chooses `/v2`. A lineage-only consumer — no Uses blob — resolves to `/v2` alone and is
untouched by v3's existence: the concurrent-major case that rule 2's current letter rejects
is here just two rows. When the orders team rolls `/v2` from one v2-line release to the
next, both releases sit transiently inside that binding's selection and rule 2's quantifier
applies within it — the rolling-deploy check of services.md §7, now scoped to an address.
And when `/v2` is finally retired, deleting its binding is a transition: valid only once no
deployed release's requirement resolves to it, which is "can I turn this off?" answered
with names (services.md §7), now including *where*.

## 8. Variants

Production and test are two environment manifests. That sentence is most of the design:

- **Same releases, different providers.** The test manifest binds `payments.internal` to a
  mock — its own module, serving a description under the same discipline — and every
  consumer whose used-set the mock covers resolves to it by cross-module spanning, exactly
  as services.md §12 already stages it. No release changes; the substitution trade and its
  Uses-blob condition are stated at services.md §5 and inherited here unchanged: variants
  are free precisely for the consumers that publish used-sets.
- **A variant is the operator's side of the judgment**, as a target (§13.3) is the
  consumer's side at build time: the same releases are judged against a different
  environment, and neither judgment touches the artifacts.
- **Overlays.** With environments as documents, "test is prod with three substitutions"
  can be literal: the overlay calculus of §9.3 — delete, replace-or-add, minimality on pain
  of invalidity (L107) — transposes from section trees to environment manifests, so the
  difference between test and prod is a computed, minimal, signed delta rather than a
  README. This is the payoff of §4(c)'s reification that nothing else provides: you cannot
  diff a set a tool is handed.

## 9. Open questions

1. **The environment manifest's identity.** Is it a release of a module in a new realm
   (one-section shape, L135/L143 pattern) — in which case: what are its atoms (plausibly
   none, on the leaf-application precedent of services.md §4.3), and is its lineage the
   environment's history, making environment evolution gradeable? Or a sibling document
   under its own schema, outside the release machinery? The lineage-as-history idea is
   attractive enough to deserve the examination, and suspicious enough to withhold.
2. **Rebinding grades.** This document recommends none — transition validity only (§6) —
   but the recommendation should survive a worked adversarial example before it is settled.
3. **Probe cadence and drift reporting.** How often is liveness probed, what does a drift
   report contain, and does any of it belong in the format rather than in tooling? (Current
   lean: the report's *vocabulary* — module, snapshot, address, cause — is format; the
   cadence is not.)
4. **Third-party contract governance.** A binding to a vendor's API needs a contract module
   somebody publishes; hosts.md §11 and universes.md §5.6 already hold this question and
   this document adds the address dimension to it, not an answer.
5. **The data plane, again.** Services.md §11 excludes topics and databases; long-running
   execution makes them more pressing, not less (a binding to a broker? an address for a
   topic?). The exclusion is re-affirmed here — two-sided evolution over retained data
   still needs machinery lineage does not have — but the boundary should be redrawn
   consciously in any adoption, not inherited silently.
6. **Operator identity.** The operator's signature is a new trust role beside publisher and
   index (distribution.md); whether it wants its own anchoring story (keys, endorsement,
   logs) or reuses the publisher machinery verbatim is unexplored.

## 10. Spec impact (if adopted)

> **Status.** Partially adopted: the genus term (spec §4.2, **Composition**) and the
> desired/actual reading, with drift's advisory standing (spec §13.7; services.md §6, §8),
> are in the spec. Bindings, addresses, provisioning, and the environment-manifest schema
> remain design-side, pending §9's open questions.

In the format of universes.md §6 — what this design would change, none of it executed here:

1. **lira.md §4**: taxonomy entries for the genus term (**composition**: buildpath and
   environment as its species), **binding**, **address**, **desired/actual state**, and
   **provisioning**; the consumer triple (compiler, deployer, runtime) named in the
   abstract's story.
2. **lira.md §13.7 + services.md §6**: re-read "a set a tool is handed, not an object this
   specification publishes" as the desired-state document — the largest single change, on
   hosts.md §10's "in all but publication" precedent; add binding disjointness and the
   re-scoped rule-2 quantifier as fresh labels; state canonical binding resolution beside
   the canonical assignment.
3. **Schema**: the environment-manifest schema (or realm), with `given`, `deploy` and
   `binding` records; selection semantics defended against L118 via the `artifact`-pin
   precedent.
4. **services.md §8**: liveness as the third moment sustained; drift's advisory standing;
   provisioning as the §13.5 analog.
5. **Tooling** ([`tool.md`](tool.md)): `lira` subcommands for environment validation,
   provisioning tables, and drift reports — registry and orchestrator business, outside the
   spec proper.
