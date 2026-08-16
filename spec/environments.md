# Environment Releases, Bindings, and Provisioning — Specification Draft

## Abstract

The buildpath is judged and then closed; an environment is judged and then *lived in*.
Services keep calling each other, deploys keep arriving, and the compatibility question a
buildpath answers once is asked continuously, for as long as anything runs. This document
specifies the object that keeps the question answerable: the **environment release** — an
ordinary `.lira` release of the `env` realm (LIRA §9.4, **L150**) whose manifest states one
environment's **desired state**: the platform contracts it supplies (`given`), the releases
intended to run (`deploy`), and the **addresses** at which providers answer (`binding`). It
also specifies **`environment/1`**, the discipline that atomizes an environment's topology,
and **provisioning**, the runtime analog of materialization.

The document introduces the format's third signing role. A publisher signs what a release
*is*; the index signs what was *published*
([`distribution.md`](../design/distribution.md)); the **operator** signs what is *intended
to run*. Everything else is machinery the format already has: environments are versioned by
lineage, graded by the atom algebra, tagged, verified and distributed as any release is —
which is the design's whole content, derived in [`execution.md`](../design/execution.md).

## 1. Status

This document is a working draft. It is normative for the `env` realm (LIRA §9.4), the
`given`, `deploy` and `binding` records (LIRA §14), the `environment/1` discipline, and
resolution and provisioning (LIRA §13.7); the labels **L150**, **L151** and **L152** are
defined in the base specification and elaborated here. Environment *validity* (L147) and
deployment (L148) remain the subject of [`services.md`](services.md), unchanged in
substance by this document.

## 2. Motivation: the Judgments Need a Home

Environment validity (LIRA §13.7, services.md §6) was specified before any object existed to
hold what it judges: the environment was "a set a tool is handed." Three pressures broke
that arrangement, each derived at length in [`execution.md`](../design/execution.md) §§4–5:

1. **Intent that lives nowhere is not intent.** The desired/actual distinction (LIRA §13.7)
   makes every validity rule a judgment about desired state — but an unpublished set cannot
   be signed, versioned, diffed, or consulted by a runtime at hour one thousand. The gap was
   confessed early: hosts.md §10 calls the aggregated requirement set a "host contract in
   all but publication." The provision side had the same status, and this document ends it.
2. **The dropped uniqueness rule left a question unanswered.** A buildpath answers "which
   release provides module M?" by rule 1 (L111); the environment deliberately dropped
   uniqueness for rolling deploys, and with it the answer. The **binding** answers it by
   address: two majors of one service coexist at two addresses, and "which provider does
   this consumer get?" is decided per address (L151, L152) instead of per path.
3. **Concurrent majors were inexpressible.** Under L147 rule 2's unrefined quantifier, two
   concurrently-serving releases with disjoint lineages fail every lineage-only consumer;
   the routing pin that fixes this was tool lore "the manifests cannot imply." The binding's
   selection makes it a manifest fact (L152).

What this document deliberately does **not** do: give addresses lineages (an address has
nothing to atomize — its API is the bound release's, borrowed; execution.md §4), let
requirements name addresses (a release naming an address is welded to one environment), or
make environments requirable or dependable (LIRA §9.4: an environment is neither provider
kind under L137, and no dependency names one under L149 — environments are judged and
consulted, never composed against).

## 3. The Environment Release

An environment release is an ordinary `.lira` release: a module with a name, a lineage,
`api` records, a payload, and signatures, to which everything in the base specification
applies unchanged. **L150** (LIRA §9.4) fixes the shape: exactly one `env` section; no
integrations, no dependencies, no `requires` records; the `given`, `deploy` and `binding`
records that are its substance — and that no other kind of release may carry; and the
`environment/1` discipline declared over them. The `env` section's tree holds only ancillary
content (probe metadata, operator notes; atomless by default, **L146**) and MAY be empty:
unlike a host contract, whose carrier is content, an environment's substance is manifest
records, because they are exactly what the composition judgments read, and every judgment
input in this specification is manifest-level (LIRA §13.1).

**Signing.** The operator signs the manifest by the ordinary machinery (LIRA §15), which
covers the records directly and the section transitively. Who may operate an environment is
a governance question outside the format, exactly as publisher key anchoring is (LIRA
§15.3); what the format guarantees is that a desired state is exactly as attributable,
tamper-evident and non-repudiable as any release.

**Versioning.** Development releases (LIRA §12.5) are the natural currency of continuous
operations: each transition yields a new hash-identified state, and version assignment is a
promotion the operator performs when a state deserves a name. Tags (LIRA §12.6) carry the
names operations already uses — `2026-q3-freeze`, `dr-test` — resolved to exact states on
any registry that holds the release.

**History.** Successive environment releases of one module are the environment's history:
implementation identity distinguishes every state; API identity (§5) distinguishes every
*topology*. The lineage is the topology's history, and the grade algebra reads it (§5).

## 4. Givens, Deploys, and Bindings

Three record families (LIRA §14), one desired state:

**`given`** — a platform contract this environment supplies: module and snapshot. Polarity
matters: a given is *provision*, not requirement — it is read by environment closure
(services.md §6 rule 1) from the provision side, which is why L150 forbids `requires` on the
`env` section rather than spelling givens as requirements. The satisfied side is the
deployed releases' `requires` records, per hosts.md §7 unchanged.

**`deploy`** — a release intended to run: module; **`build`**, the implementation identity
of the exact artifact (desired state names artifacts, not hopes); optionally an `api` hint;
the `integration` naming which `app` section is deployed (LIRA §13.7); and `route` pins
(§6). The `build` pin is publishable, and deliberately so — the boundary with **L118**
(LIRA §13.2) is that L118 governs the *dependency* axis, where a pin is development coupling
and satisfaction must remain lineage-decidable for future composition; a deploy pin is
desired-state exactness about a closed artifact that composes into nothing, on the
`artifact`-pin precedent (services.md §4.2), and remains publishable even when it names a
development release — which, in continuous deployment, it usually does.

**`binding`** — an address, a provider module, a selection, and an optional `rank`:

- The **address** is the environment's own kind of name: a DNS name or URL prefix, compared
  as authored — the `owns` precedent, no canonicalization; operators who want normalization
  apply it before authoring. Within one manifest, addresses MUST be pairwise disjoint:
  neither equal to, nor a path-prefix of, one another (**L151**) — L112's prefix-clash rule
  transposed, so `orders.internal` clashes with `orders.internal/v2` exactly as a namespace
  clashes with its dotted extension.
- The **provider module** is either L137 kind: a deployable service, or a host contract — the
  latter being how a third-party endpoint (a vendor API nobody here deploys) enters an
  environment: as a given bound to the address it answers at.
- The **selection** admits releases to the binding: `api`, a snapshot satisfied through the
  provider's lineage (the default form — a rolling deploy is two releases transiently inside
  one selection), or `build`, an exact implementation identity (the L118 boundary above
  applies verbatim). A binding selects; it never satisfies — satisfaction remains the
  consumer's requirement against the selected releases, per services.md §5.

One address, one provider, at a time: rebinding an address is a **transition** (§7), never a
property of the address, which has no lineage and no grade of its own.

## 5. The `environment/1` Discipline

`environment/1` is the discipline of environment topology. Its domain is the single realm
`{env}`; its keying is by declaration; it emits only rigid atoms and no reference lists; it
certifies **presence**, on `capability/1`'s terms (hosts.md §5). Like `resource/1` (LIRA
§11.4), its input reaches beyond the tree: it atomizes the manifest's `binding` and `given`
records, which is unproblematic for the same reason — atomization runs only where the
manifest is in hand.

**Atomization.** One rigid atom per `binding` row: the key is `binding <address>`; the
canonical encoding is the byte `0x01`, the address's UTF-8 bytes, `0x00`, then the provider
module's UTF-8 bytes. One rigid atom per `given` row: the key is `given <module>`; the
canonical encoding is the byte `0x02` followed by the module's UTF-8 bytes. Nothing else
enters any atom — not selections, not deploys, not routes, not the given's snapshot — on
exactly the precedent of `capability/1`'s `probe` field: they participate in implementation
identity (they are manifest content under the signature) but never in API identity, so all
of them change at patch grade. The exclusions are load-bearing: a selection in the atom
would make every routine roll of a binding a major, and the environment's lineage a proxy
for its occupants' histories — the address-level lineage this design exists to refuse
(execution.md §4); a snapshot in the given's atom would make every platform upgrade an
environment major.

**The grade reading.** With those exclusions, the ordinary algebra (LIRA §12.3) grades an
environment's evolution exactly as operations would wish:

| Change                                              | Grade |
| --------------------------------------------------- | ----- |
| selection rolled; deploys changed; routes edited    | patch |
| probe metadata, notes, given's snapshot upgraded    | patch |
| new address bound; new given granted                | minor |
| address removed or retargeted to a different module | major |
| given withdrawn                                     | major |

The major row is a **safety interlock**, obtained for free from **L110**: a publishing tool
refuses to extend the lineage with a topology-destroying successor unless the operator
explicitly requests a major. "Can I turn this off?" (services.md §7) thus acquires a
publish-time gate to go with its validity-time answer. Grades *record and gate*; they never
schedule: deployability is judged by L148 alone, and rebinding an address's occupant within
its selection is the gradeless transition-validity case, exactly as
[`execution.md`](../design/execution.md) §9 recommended.

## 6. Resolution and Provisioning

A deployed consumer's requirement on module `M` has **candidate** bindings: those whose
provider module and selection satisfy it — by lineage membership or spanning, services.md §5
verbatim, cross-module spanning included, so a mock's binding is a candidate for exactly the
consumers whose used-sets it covers. Resolution is deterministic on the canonical-assignment
pattern (LIRA §13.3): tools MUST resolve each requirement to its first candidate in
ascending (`rank`, `address`) order, unless a `route` pin on the consumer's deploy record
names a candidate binding, which is then chosen. A `route` naming an address that is not a
candidate for its requirement is invalid (**L150**): a pin states a preference among
satisfying options, never an escape from satisfaction. Routes live on deploy records — in
the environment release, authored by the operator — and never in any release manifest, which
would weld the release to one environment (execution.md §4); the route is the buildpath's
integration pin made a signed fact, per deployment.

**Provisioning** is the §13.5 analog: from a valid environment, a tool derives the table
from each deployed release's requirements to the addresses of their resolved bindings — the
consumer-facing answer to "where is my provider?", computed from manifests, handed to the
runtime. A requirement whose provider carries no binding is **unaddressed**: an advisory
fact, not a validity failure, since not every provider answers at an address (`kubernetes`
is a given nobody dials). What happens next divides on the sentence that has divided every
such question in this specification: provisioning produces the table, and *reconciling the
running world to the judged one is the orchestrator's business*, exactly as invoking egress
tools is the build's (LIRA §13.5).

## 7. Transitions

Every change to an environment release's records is a transition, judged by **L148**
(services.md §7): the posterior state — and, for rolling changes, the overlap state — must
be valid. The record-level reading of the three shapes:

- **Deploying** adds or replaces `deploy` rows (and, mid-rollout, two releases stand
  transiently inside one binding's selection — L152's quantifier covers exactly them).
- **Rebinding** retargets a `binding` row. Its validity condition falls out of L147 with no
  new rule: every consumer whose requirement resolves to that address must be satisfied by
  the new occupant's selected releases. Same-module retargeting is patch-grade; retargeting
  to a different module is additionally a topology major (§5), so the interlock and the
  validity judgment fire together.
- **Retiring** removes rows. Removing a binding or given is valid only if no requirement
  resolves to it — closure names the objecting consumers — and is a topology major behind
  L110's gate: the algebra requires the operator to *say* they mean it.

## 8. Drift and Liveness (Informative)

Nothing in §3–§7 inspects a process. The actual state is the third verification moment's
business (hosts.md §9; services.md §8): probe before serving, keep probing while serving,
and report **drift** — divergence of the actual environment from the desired state — on
probing's usual advisory terms. What this document adds is the report's vocabulary: a drift
report SHOULD name the module, the snapshot, the address, and the cause, all of which the
environment release supplies — so "something is wrong in prod" becomes "the release serving
`orders.internal/v2` does not satisfy the binding's selection," a sentence a tool can both
generate and act on. Probe cadence, escalation, and reconciliation strategy are tooling and
orchestration concerns, outside the format.

## 9. Variants (Informative)

Production and test are two environment releases, usually of two modules. The same deployed
releases are judged against different givens and bindings; a mock stands in by cross-module
spanning (services.md §5, §9) for exactly the consumers that published used-sets; and no
release-side mechanism exists or is needed — a variant is the operator's side of the
judgment, as a target (LIRA §13.3) is the consumer's. Expressing one environment as a
minimal delta over another — test = prod with three substitutions, on the overlay calculus
of LIRA §9.3 — is anticipated but not specified: environment releases are small, and the
overlay's payoff (a computed, signed difference) should wait for evidence that operators
want it more than they want two whole manifests they can diff.

## 10. Boundaries

Three exclusions, restated from services.md §11 with the address dimension answered:

- **The data plane.** A message broker is a *given*; its topics are not bindings, and their
  two-sided evolution over retained data still needs machinery a single lineage does not
  have (LIRA §10.5). The exclusion stands; a topic is not an address.
- **Orchestration.** Surge policies, rollback triggers, traffic shifting: the controller's
  business. This document supplies the desired state and the predicate; nothing here moves a
  process.
- **Aggregate contract publication.** An environment's full provided surface — the union of
  its bindings' surfaces and its givens — is derivable from the named releases' manifests at
  any moment, and tools SHOULD report it (hosts.md §10). Publishing that union as a host
  contract of its own is possible today by ordinary means and needs no mechanism here.
