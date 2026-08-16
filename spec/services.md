# Services, Deployable Releases, and Environments — Specification Draft

## Abstract

The buildpath answers, from manifests alone, whether a set of libraries composes at build time
(LIRA §13). This document extends the same algebra to the second moment the question arises:
whether a set of *running* artifacts composes at deploy time. It specifies **deployable
releases** — `.lira` files whose content is a closed artifact in the `app` realm (LIRA §9.4)
and whose atoms are the network surface they serve — **requirements on services**, by which
one deployed module needs another, the **environment** as the runtime counterpart of the
buildpath, and **deployment** as a validity-preserving transition of an environment.

No new compatibility machinery is introduced, and no new record either. A service is **its own
contract**: its release's atoms are recomputed from the interface description it ships,
exactly as a library's are recomputed from its TASTy; consumers `requires` the service module
directly; satisfaction is lineage membership and spanning, verbatim; and grades and guarantee
levels carry over with their meanings transposed from compilation to operation. The design's
whole content is that the machinery built for build-time composition, read at runtime, is
already the deployment checker every platform team assembles by hand from schema linters,
contract tests and runbooks — here derived from one algebra, and decidable from manifests.

## 1. Status

This document is a working draft. It is normative for the `app` realm (LIRA §9.4), for
requirements naming deployable modules (LIRA §13.3, **L137**), and for environment validity
and deployment (LIRA §13.7); the labels **L143** and **L146** through **L148** are defined in
the base specification and elaborated here. The environment *release* — the `env` realm, its
records, the `environment/1` discipline, and provisioning — is the companion
[`environments.md`](environments.md).

## 2. Motivation: the Second Composition

A build composes *artifacts*: libraries meet on a buildpath, an egress closes over them, and
the composition produces a thing — a JAR, a bundle, an image. A deployment composes
*processes*: services meet in a cluster, calls resolve between them at runtime, and the
composition never produces an artifact at all. It produces a **state**, continuously linked and
continuously re-linkable, in which the compatibility question is asked not once but at every
deploy.

The state of practice answers that question with disconnected point tools: a schema linter
grades API diffs, a contract-testing broker records what consumers actually call, a schema
registry enforces evolution rules per topic, an admission controller enforces platform policy,
and a runbook says which services must redeploy together. (LIRA's three objects, for a reader
arriving from operations rather than from the core specification: an **atom** is the hash of
one indivisible fragment of an interface; a **lineage** is the recorded history of an
interface's compatible states; a **used-set** is the atoms a consumer actually touches.) Each
is a partial reinvention of one corner of LIRA's algebra — the linter is the grade
computation, the contract broker is used-sets and spanning, the registry is lineage, the
runbook is `breaks linkage`. This document
replaces none of their *judgement* and all of their *bookkeeping*: the same three objects that
decide build-time composition — atoms, lineages, used-sets — decide deploy-time composition,
from signed manifests, with no new trust.

### 2.1 Why Inter-Service Edges Are Requirements, Not Dependencies

The tempting encoding is a new universe — a `net` realm in which services are the libraries and
the network is the linkage mechanism. It fails the universe litmus test (LIRA §4.1) in a subtle
way: services do meet and compose, but composition never *closes over artifacts*. No egress
consumes a set of service artifacts and produces one thing; nothing of a provider ever enters a
consumer's build. Universes compose artifacts; environments compose processes.

Apply instead the three criteria by which hosts.md §8 separates requirements from dependencies,
to the edge between a consumer service and the provider it calls:

1. **Who supplies the content?** The environment. A consumer of a payments service does not
   bundle the payments service; it is supplied by whatever is running alongside.
2. **Materialization.** The provider's surface is read for atoms at validation time and
   contributes nothing to the consumer's artifact.
3. **Verification moment.** Whether the provider is actually present, at a satisfying version,
   is decidable only by probing the running environment — and the deployment world already has
   a name for that moment: the readiness check.

All three point the same way. **A running service is a host to its consumers** (LIRA §4.1), and
the edge is a `requires` record — which is why this document adds no edge type. A deployed
service is Janus-faced — a *module* at distribution time, published, versioned and signed like
any release; a *host* at runtime, required, satisfied and probed like any environment — and
this specification makes the two faces one object: the release's atoms are simultaneously its
published API and its capability surface. The base specification's aggregation rule had
already noticed the convergence: hosts.md §10 observes that a buildpath's aggregated
requirement set "is the application's host contract in all but publication." This document
publishes it.

## 3. A Service Is Its Own Contract

A service publishes **one module**. Its releases are deployable releases (§4), and each
release's atom set *is* the network surface that release serves, recomputed at publish time
from the interface description its tree carries — an OpenAPI document atomized by `openapi/1`
([`openapi.md`](openapi.md)), Protobuf descriptors by the anticipated `proto/1`, WIT by
`wit/1` ([`wit.md`](wit.md)) where the boundary is a component world — exactly as a library
release's atoms are recomputed from its TASTy. Everything a module has, a service therefore
has, with the runtime reading:

- its **lineage** is the history of the surface it serves, graded by the ordinary rules (LIRA
  §12): an endpoint added is a minor, an endpoint removed begins a new lineage — the actual
  compatibility behavior of every operated API, expressed by the rule that was already there;
- its **snapshot** is its API identity, and a consumer's `requires` names it (§5);
- its **implementation identity** is the artifact — and a development release (LIRA §12.5),
  versionless and hash-identified, is the natural currency of continuous deployment, with
  version assignment the act of promotion, payload untouched;
- **tags** (LIRA §12.6) carry the names the world uses: `tag v2` on the release whose derived
  version is `3.4.0`, resolved to a snapshot on any registry.

There is deliberately no separate contract object and no bridging record: a service's claim to
serve a surface is not authorial testimony but recomputation from shipped content — the same
verification standing as every other claim in the format (LIRA §16), with the same bound:
recomputation proves the *declaration*, and behavior remains behavior (§8, LIRA §18).

**Standards remain host contracts.** An interface that is not any service's own — a standard
several vendors implement (an S3-style storage API), a mock target, a protocol a gateway
guarantees — is published as an ordinary host contract (hosts.md §3): a module of its own,
under the *same discipline* as the services that serve it. No service declares a relationship
to it; a consumer's requirement on the standard is satisfied by an actual service through
**cross-module spanning** (§5, hosts.md §7), which is set inclusion over identically-hashed
atoms. One discipline across `{host, app}` is what makes that inclusion a comparison of like
with like.

## 4. Deployable Releases

### 4.1 Shape

A **deployable release** is a release carrying `app` sections and only `app` sections
(**L143**, LIRA §9.4) — one per integration where integrations are declared, exactly as the
section matrix requires (LIRA §9.5). It MUST NOT declare `dependency` records (also **L143**):
its composition already happened at the egress that produced it, and the record of *what* it
was built from is provenance — real, valuable, and deliberately deferred to a future
attestation layer rather than half-expressed through a mechanism whose satisfaction semantics
do not apply to closed content.

Its `app` sections carry **`requires`** records freely: everything the artifact asks of its
environment — platform contracts (`kubernetes`, `postgres`, a JDK for a JAR-shaped artifact)
and other service modules, *undifferentiated*, because at runtime every other service is
environment. Tooling that produced the artifact from a buildpath SHOULD seed these records
from the buildpath's aggregated requirement set (hosts.md §10), which was computed for exactly
this purpose.

### 4.2 Content: Embedded or Pinned

An `app` section's tree holds the closed artifact where the artifact is naturally file-shaped —
an executable JAR, a static binary, a bundle — and the canonical-derivative machinery (LIRA
§13.6, Appendix C) then closes the tree into the conventional artifact deterministically, so
"the deployable" has a stable identity computed from content, as always.

Where the artifact lives natively in another content-addressed store, the section instead
carries an **`artifact` pin** (LIRA §14): a format identifier, the foreign store's own content
address verbatim (an OCI digest, for the motivating case), and an advisory locator. LIRA does
not re-hash foreign stores: the pin is to an identity *in that ecosystem*, verified by that
ecosystem's own mechanism (LIRA §18), and the `.lira` file is then what the deployment world
conspicuously lacks — a small, signed, verifiable manifest *about* the image: its served
surface, its requirements, its lineage, its authorship. The distribution posture is the same
as the index's ([`distribution.md`](../design/distribution.md)): identity in the manifest,
bytes hosted where bytes are best hosted.

A pinned section's tree never carries a copy of the artifact itself — only ancillary content:
the interface descriptions self-description needs (§4.3) and probe metadata. Where a
tree-carried artifact is *also* pinned, the pin is a claim about its packaged form, and any
disagreement between them is the publisher's error, not the reader's problem.

### 4.3 Self-Description

Content in an `app` section that no discipline claims is atomless (**L146**, LIRA §9.4): a
closed artifact's bytes are implementation, covered by implementation identity, and making
them `opaque/1` atoms would turn every rebuild into a major. What stands as interface is the
**description**: the release declares `api` records over the interface descriptions its tree
carries, under a discipline whose domain includes `app` (§3), and the atoms those records list
are recomputed by every verifier (LIRA §16, **L141**) — a deployable's served surface is
checked content, not a label.

A deployable declaring no `api` records has an empty atom set: every such release shares the
empty snapshot, its lineage never grows, and every step grades as a patch. That is legitimate
for a **leaf application** — a batch job, an edge deployable nothing requires — whose
interesting identities are its implementation identity and its requirements. It is useless for
a service: an empty atom set can satisfy no requirement, so publishers SHOULD self-describe
any module other modules are to require — not a validity rule but an arithmetic fact: the
algebra simply has nothing to satisfy with. Where the artifact serves an interface but ships
no description, tooling SHOULD extract one at egress time (the served OpenAPI document,
compiled descriptors) and place it in the tree; a surface that cannot be described is a
surface that cannot be promised.

The cross-section invariant (LIRA §9.6) binds across `app` integrations: a release offering
several integrations serves one surface, which is what keeps integrations invisible to
consumers here exactly as on the buildpath.

## 5. Requiring a Service

A `requires` record may name either kind of **provider** (LIRA §9.4, **L137**): a host
contract — capability the environment is *given* — or a deployable module — capability
*deployed into* it. The two are recognizable by their `host` and `app` sections, and the
satisfaction rules are LIRA §13.2's, verbatim:

- **By lineage**: a deployed release `R` of module `M` satisfies a requirement `(M, api)` iff
  `api` appears in `R`'s lineage. There is no indirection: the service's own lineage is the
  contract's lineage, because the service is its own contract (§3).
- **By spanning**: where the requirement carries a Uses blob — the provider atoms the consumer
  actually calls — `R` satisfies it whenever `used ⊆ atoms(R)`, including across majors (LIRA
  §13.4) and **across modules**, on the terms and soundness argument of hosts.md §7: a
  requirement on one service is provably satisfied by a different service, a mock, or a
  standard whose atoms cover the used-set.

At *buildpath* validation, a requirement naming a deployable module is pending, not judged
(LIRA §13.3 rule 7): which release of a service is present is a fact about an environment, and
the rule that reads it is environment validity (§6).

One consequence of the unified design must be stated plainly, because it is the trade this
specification chose. Without a Uses blob, a requirement is satisfiable only by its named
module's lineage — so a consumer that publishes no used-set is coupled to its provider's
*module identity*, and substituting a rewrite, a canary from another codebase, or a compatible
competitor is not expressible for it. The escape is spanning, and it is cheap: used-sets are
computed by tooling from the consumer's generated client or recorded traffic, not authored.
Publishers of consumers SHOULD therefore emit a Uses blob on every `requires` record naming a
service, and ecosystems that want substitution as a norm (mock-based testing pipelines,
multi-vendor standards) should treat a service requirement without one as a lint. A standard
that several services implement belongs in a standalone host-contract module from the start
(§3), which gives consumers a neutral name to require.

Two structural notes. Requirement edges between services may form **cycles** — two services
that call each other are ordinary — and nothing here minds: environment validity (§6) is a
predicate over a set, not a resolution order. And nothing on the provider side is authorial:
where hosts.md §9 must caveat that no verifier can check code against its *requirements*, a
service's *provision* is recomputed from its shipped description — the strongest verification
position in the specification, bounded only by the behavior gap (§8).

## 6. The Environment

An **environment** is a set of deployable releases — the deployed set — together with a set of
host contracts describing its platform: the orchestrator, the operating surface, the managed
services (a database, an object store) that are *given* rather than deployed. It is the
runtime counterpart of the buildpath — a statement of **desired** state, since every rule
below reads manifests and none inspects a process (LIRA §13.7) — and it is published: an
operator-signed **environment release** whose manifest carries the givens, deploys and
bindings this section judges ([`environments.md`](environments.md), **L150**). A cluster's
controller knows what is *running*; this document says what to check it against.

Environment validity (**L147**) holds, for an assignment of one integration per deployed
release (LIRA §13.3, unchanged), iff:

1. **Closure**: every module named by any deployed release's applicable `requires` records is
   **provided** — by one of the environment's platform contracts, or by a deployed release of
   that module. A requirement on a module nobody provides fails closure exactly as an absent
   module fails a buildpath — except where a Uses blob licenses cross-module satisfaction
   (rule 2), which is how a mock or a standard's implementation stands in for a named module.
2. **Satisfaction, against every concurrent release**: each requirement is satisfied, per §5,
   by *every* concurrently-serving release of its provider — and, for cross-module
   satisfaction, by every concurrently-serving release of the standing-in module. Where the
   requirement resolves to a binding, the quantifier ranges within that binding's selection
   (**L152**, LIRA §13.7): releases behind other addresses are other providers.
3. **Aggregation**: requirements on one provider from several releases are jointly judged by
   the rule of hosts.md §10, over the whole environment, under rule 2's quantifier: by
   lineage, jointly satisfiable iff *every* concurrently-serving release of the provider
   carries every required snapshot in its lineage (the diamond rule, universalized over the
   overlap); by spanning, the union of the used-sets must be covered by each.
4. **Platform coherence**: any profiles declared by deployed releases impose their predicates
   over the environment, on the terms of LIRA §13.3 rule 6 — this is where an operator's
   platform policy (every deployable pinned, every artifact signed by a release key, a
   readiness probe on every requirement) becomes a named, versioned, checkable object rather
   than an admission-controller configuration.

There is deliberately **no uniqueness rule**: two releases of one module serving concurrently
is the normal state of a rolling deployment. What replaces it is the binding (LIRA §13.7,
L151): the address disambiguates at run time what uniqueness disambiguated at build time,
and rule 2's quantifier — during the overlap, every consumer must be satisfied by *both* —
ranges per binding (L152). A consumer is pinned to one candidate binding by a `route` row on
its deploy record ([`environments.md`](environments.md) §6): the routing pin is to
environments what the integration pin is to buildpaths — a consumer preference which the
*release* manifests cannot imply, and the *environment* manifest states.

Replication is invisible, and should be: *n* replicas of one release are one provider, because
the algebra reasons about releases, not processes.

## 7. Deployment

A **deploy** is a transition of an environment: any change to its release's `given`,
`deploy` or `binding` records ([`environments.md`](environments.md) §7) — adding a release,
removing one, replacing one with a successor, or rebinding an address. A release is
**deployable** iff the posterior state is valid, and — for a
rolling replacement — the intermediate state, in which predecessor and successor serve
together, is valid too (**L148**). Deployability is therefore not a new judgement but validity
(§6) applied to the states a transition passes through, decidable from manifests before
anything moves. The three transition shapes:

- **Adding** a release: its requirements must close and satisfy against the environment
  (nothing existing can break, since nothing existing gains a requirement).
- **Removing** a release: every requirement it was satisfying must still be provided — closure
  is what makes "can I turn this off?" a computable question, and the answer names the exact
  consumers that object.
- **Replacing** a release: both of the above, plus the overlap state; and here the guarantee
  levels (LIRA §11.5) do the operational work.

**Grades schedule operations.** Consider a provider whose new release extends its surface
lineage, and read the lineage step through §11.5's runtime transposition — linkage is wire
compatibility for already-running consumers, recompilation is regeneration for consumers who
rebuild. Each cell is a claim at the levels the release's disciplines and declared profiles
actually certify (LIRA §11.5, §12.4): for an `openapi/1` surface, the wire column is the
anticipated `http-json/1` profile's claim, not the discipline's
([`openapi.md`](openapi.md) §2).

| Lineage step                | Rolling deploy | Running consumers                  | Rebuilt consumers |
| --------------------------- | -------------- | ---------------------------------- | ----------------- |
| patch                       | safe           | unaffected                         | unaffected        |
| minor                       | safe           | safe — wire compatibility preserved | safe             |
| minor, `breaks linkage`     | coordinated    | must redeploy — and *which* is computed, not guessed | safe |
| major (new lineage)         | new surface    | satisfied only by spanning         | re-audited        |

The `breaks linkage` row (LIRA §12.4) is the coordinated deploy, named in a signed manifest
rather than in a runbook: the step is minor by the atom algebra, so regenerated clients need
nothing, but wire compatibility was not preserved, so running consumers must move — and the set
that must move is exactly the consumers whose used-sets intersect the step's delta (LIRA
§13.4's staleness, transposed from "should recompile" to "must redeploy"). The row is live
only where the provider declares a linkage-certifying profile (LIRA §12.4): for the
`openapi/1` family, the anticipated `http-json/1`; absent one, the wire level is simply
unclaimed, and a cautious operator treats every minor as potentially coordinated. The major row is
where spanning earns its keep: a consumer whose used-set avoids everything the new lineage
dropped keeps running, provably, through a break that would otherwise force a fleet migration
on a date.

## 8. The Third Moment, at Runtime

Nothing in §6 or §7 checked a running process; everything was manifests. The third verification
moment (hosts.md §9) closes the loop, and in the deployment world it has always existed under
another name: **probing is the readiness check**. Before a deployed release serves traffic, a
probing tool checks the aggregated requirement set against the actual environment — platform
capabilities by their advisory probes, service providers by their health endpoints — and a
served surface's natural probe is the description itself, where the protocol exposes one, since
a service that serves its description invites the comparison with the description it shipped.
Probes remain advisory, untrusted, and unprivileged (hosts.md §9), and what they buy remains
precision and timing: a failed requirement surfaces at deploy, with a named module and a named
snapshot, not mid-traffic as a 500 with a stack trace behind it. Provisioning
([`environments.md`](environments.md) §6) supplies the addresses the probes dial. A tool
SHOULD keep probing while the release serves: readiness sustained is liveness, and
divergence of the actual environment from the *published* desired state — **drift** (LIRA
§13.7; report vocabulary, environments.md §8) — is a probe result on probing's usual
advisory terms, triggering re-judgment rather than entering it.

The verification split is worth restating from this document's side, because it is cleaner
here than anywhere else in the specification. What a service *requires* is authorial, exactly
as hosts.md §9 says, and is checked against the world by probing. What a service *serves* is
recomputed from shipped content and is not testimony at all — but recomputation proves the
declaration, never the behavior behind it (LIRA §18), so the probe and the promise meet in the
middle: manifests decide compatibility, probes decide presence, and behavior remains the
publisher's signed word.

The interpreter directive (LIRA §5.1) completes the picture: every `.lira` file is executable
by design, and for a deployable release the natural behavior of `lira` invoked on it — after
its mandatory presentation of the manifest — is to verify, probe the environment, and run the
artifact. That behavior is a tool's business, not this specification's; the point of recording
it is that the format was one step from runnable before this document existed.

## 9. Spanning Is Consumer-Driven Contracting

The requirement mechanism, carried over unchanged, lands on ground the deployment world has
already prepared. A requirement with a Uses blob — the provider atoms a consumer actually
calls — satisfied by `used ⊆ atoms(provider)` is **consumer-driven contract testing** computed
by set inclusion: what a contract-testing broker establishes by recording and replaying
interactions, the manifests establish by arithmetic, per consumer, per provider release,
without a test run. And cross-module spanning (§5, hosts.md §7) is **service virtualization**
made sound: a mock, a simulator, or a compatible reimplementation publishes its own surface
under the same discipline, and provably satisfies exactly the consumers whose used-sets it
covers — the `scalajs-javalib` argument, transposed from standard libraries to staging
environments. Under the unified design this is not an optional elegance but the substitution
mechanism itself (§5), which is why used-sets are the one practice this document asks
ecosystems to make habitual: "this consumer runs against any provider offering these twelve
operations" stops being tribal knowledge and becomes a line a tool prints.

## 10. The First Network Discipline: `openapi/1` (Orientation)

This section is an orientation; the normative specification is [`openapi.md`](openapi.md),
whose folding logic largely inherits from [`webidl.md`](webidl.md). The sketch is here because
the discipline is where the deployment story touches content, and its folding decisions show
the algebra fitting the wire as naturally as it fits linkage.

**Domain** `{host, app}` — standard contracts and served surfaces, one discipline, which is
what makes cross-module spanning (§5) meaningful. **Keying** by declaration: an operation's
key is its method and canonicalized path template; a named schema's key is its name, qualified
by direction.

**Folding, by declared direction.** OpenAPI, like Web IDL and unlike `.d.ts`, declares the
usage direction of every position — request or response — so the folding principle (LIRA
§10.3) resolves by variance (LIRA §10.5) rather than by conservatism: covariant response
positions stand alone (adding an endpoint or a response field is a minor, the actual behavior
of every operated API), contravariant request positions fold (adding a required parameter
breaks every caller, and registers as a major with no rule engine consulted), and enumeration
values invert by position — standalone in requests, folded in responses. The full folding
table, keys and canonical encoding are openapi.md §5–§8.

**Certifies**: recompilation — regeneration, per §11.5's transposition — and nothing else.
The wire-level claim rests on a convention the carrier cannot enforce (consumers ignoring
unknown response fields), so it belongs to the anticipated `http-json/1` ecosystem profile
([`openapi.md`](openapi.md) §2), on exactly the division of labor between `tasty/1` and
`jvm/1`. A future `proto/1` is the family's `classfile/1`: Protobuf's tag numbers and
unknown-field rules are defined by the wire format itself, so its linkage claim is
correspondingly stronger.

## 11. What This Document Does Not Cover

Three boundaries, drawn deliberately.

**The data plane.** Request/response surfaces fit the algebra because one party provides and
the other requires. A message topic or a shared database does not fit so cleanly: writers and
readers *both* evolve, against data that persists — an event written under last year's schema
is read by next year's consumer — so compatibility there is two-sided and extended over time,
which is the distinction schema registries encode as backward/forward/full, and which LIRA
§10.5 identifies as the point where a single lineage stops being the right structure. A topic
or a database schema is very likely a module of its own, whose migrations are lineage events
with grades ("can I run this migration?" wants to be a containment check over deployed
consumers' used-sets), but the two-polarity evolution relation needs design the current
lineage does not carry. It is anticipated as its own companion, and nothing in this document
forecloses it.

**Orchestration.** How a transition executes — surge policies, rollback triggers, traffic
shifting — is the controller's business. This document supplies the predicate the controller
consults, on the same division of labor as LIRA §13.5's "invoking those tools is the build's
business."

**Behavior.** Unchanged from LIRA §18, and more consequential here, where the common production
failure is a field whose *meaning* changed under a stable signature. The honest mitigations
are the honest ones: signatures, probing, and — the natural future layer — attestation of
contract test evidence against a named snapshot.

## 12. Worked Example

The service, one module, self-described — a development release (LIRA §12.5), identified by
its hashes, which is the natural currency of continuous deployment; promotion to a numbered
release is version assignment, later, with the payload untouched:

```text
#!/usr/bin/env lira
tel 1.0 <lira schema signature>

module checkout/payments
tag v1
lineage Aa11…
lineage Bb22…
lineage Cc33…                 # the surface it serves today; two minor steps behind it

api
  discipline openapi/1
  atoms Dd44…                 # atomized from the openapi.json its tree carries

section app
  tree Ee55…                  # holds openapi.json and probe metadata
  artifact
    format oci-image
    digest sha256:1f2e3d…
    locator ghcr.io/checkout/payments
  requires
    module checkout/orders
    api Ff66…
    uses Gg77…                # the six orders operations it actually calls
  requires
    module postgres
    api Hh88…
  requires
    module kubernetes
    api Ii99…

payload
  compression brotli
  length 61302
  hash Jj00…
```

A consumer's manifest simply carries `requires module checkout/payments, api Cc33…, uses
Kk11…` on its own `app` section: the service module is required directly, and `Cc33…` is a
snapshot of *this* module's lineage — no second module, no bridge.

A deploy tool holding this manifest, the environment's platform contracts, and the manifests of
what is already running now computes, without pulling the image or starting a process: that the
served surface `Dd44…` recomputes from the shipped description (LIRA §16 — a checked claim,
not a label); that `checkout/orders` is deployed, and that this release's used-set `Gg77…` is
satisfied by *both* releases of it currently serving mid-rollout (§6 rule 2); that `postgres`
and `kubernetes` satisfy by lineage membership; and that removing the predecessor
`checkout/payments` release afterwards is valid, because every requirement naming the module
is satisfied by the successor's lineage too. If the orders release's last step had carried
`breaks linkage` — under a declared linkage-certifying profile (§7) — the tool would instead
name this release among the consumers that must move with it, because `Gg77…` intersects the
step's delta: computed, not remembered from a meeting. In the staging environment,
a mock of orders — its own module, serving a description under the same discipline — satisfies
this release by cross-module spanning, because `Gg77… ⊆ atoms(mock)` (§5): the substitution is
licensed by the used-set, which is why §5 asks that consumers publish one. The readiness gate
then probes what manifests cannot: that the environment the manifests were judged against is
the environment actually there.
