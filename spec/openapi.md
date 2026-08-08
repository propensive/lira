# The OpenAPI Discipline `openapi/1` — Specification Draft

## Abstract

`openapi/1` is the LIRA discipline for OpenAPI documents, the formal carrier in which HTTP
services describe their interfaces. It atomizes the surface a description publishes —
operations, parameters, request and response bodies, named schemas — applying the folding
principle of the LIRA specification (§10.3) so that the compatibility rules of an HTTP API are
encoded in the decomposition itself.

Its distinguishing feature among the registered disciplines is that atomization is
**directional**. An OpenAPI document, like Web IDL and unlike `.d.ts`, declares the usage
direction of every position — what a caller supplies, what a caller receives — so the folding
decisions are computed as variance (LIRA §10.5) rather than resolved conservatively: the same
construct folds in one polarity and stands alone in the other, and a schema reachable in both
polarities is atomized once per direction.

The discipline serves the two kinds of provider ([`services.md`](services.md)): it atomizes a
**standard contract**'s carrier in its `host` section, and a **deployable release**'s served
surface in its `app` section, under one identifier — so a requirement is satisfiable
interchangeably by a standard or by a service publishing its own surface, the hashes comparing
like with like (services.md §5).

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline
(`openapi/2`), never a revision of this one (LIRA §11.1). A reference implementation is
anticipated in Soundness alongside those of `dts/1` and `webidl/1`.

## 2. Scope and Guarantee

The discipline certifies **recompilation** — which, in the runtime transposition of
services.md §7, is **regeneration**: within a lineage, clients generated from any earlier
release's description still generate, compile and type-check against every later release's.

It does **not** certify linkage — wire compatibility for already-running consumers — and MUST
NOT be read as doing so, for a reason worth recording. The wire-level claim depends on a
convention the carrier cannot enforce: that consumers ignore unknown response fields
(must-ignore), the JSON norm that OpenAPI presumes but no description guarantees of any
particular client. A claim resting on an ecosystem convention belongs to an ecosystem profile
(LIRA §11.6, §11.2 requirement 7): an anticipated `http-json/1` profile imposes must-ignore
tolerance and status-class fallback as predicates and certifies linkage over `openapi/1`'s
atoms, on exactly the division of labor between `tasty/1` and `jvm/1` — the discipline
atomizes the level it can prove; the profile certifies the level the ecosystem's conventions
deliver; and a lineage step that satisfies the atoms while breaking the wire records
`breaks linkage` (LIRA §12.4), which services.md §7 reads as a coordinated deploy.

Whether a described endpoint *behaves* is out of scope as always (LIRA §18); presence of the
running service is the third verification moment (hosts.md §9), for which the served
description and health endpoint are the native probes.

## 3. Domain and Content Claiming

The domain is the pair `{host, app}`: the contract carrier and the deployable
self-description, and nothing else. In a `host` section the release is a host contract (LIRA
L135); in an `app` section the discipline is the exception to the atomless default (LIRA L146,
§9.4) that the default exists to permit — the artifact's bytes stay atomless, its description
does not. The cross-section invariant (LIRA §9.6) binds across `app` integrations: a release
offering several integrations MUST serve one described surface.

The discipline claims any tree item whose final path segment is `openapi.json` or
`openapi.yaml`, or ends in `.openapi.json` or `.openapi.yaml`, atomized. It claims nothing
else. Where several claimed items exist in one section, each atomizes independently and the
section's atom set is their union; publishers SHOULD keep one document per served API.

## 4. Extraction

Atomization is performed over the **parsed document**, never over its text: JSON and YAML
serializations of one description yield identical atoms, and comments, key order, whitespace
and anchors never enter the model. Documents MUST declare OpenAPI 3.0 or 3.1; the two versions'
constructs are canonicalized to one model (§8), so re-serializing a 3.0 document as 3.1
changes no atom.

**References are resolved before atomization.** A `$ref` to a component within the document,
or to a document within the same materialized tree, resolves; a reference that escapes the
tree, is cyclic through non-schema objects, or does not resolve is a hard atomization error.
Schema-to-schema cycles are permitted and broken by name: a reference to a *named* schema
encodes as that schema's key rather than inlining (§8), which is also what gives named schemas
their standing as declarations.

**A construct outside the vocabulary of this document is a hard atomization error** — the rule
of `tasty.md` §7, `dts.md` §5 and `webidl.md` §4, for the same reason: a partial reading
understates a contract, and every claim computed from an understated contract is unsound. In
particular, `openapi/1` excludes: dynamic JSON Schema keywords (`$dynamicRef`,
`$dynamicAnchor`), `unevaluatedProperties` and `unevaluatedItems`, `not`, and non-string enum
member types. A `dts`-style widening of the vocabulary is a future discipline's business.

**Ignored by construction**, entering no atom: `info`, `servers`, `tags`, `summary`,
`description`, `example`/`examples`, `externalDocs`, `deprecated` (advisory, like a probe;
deprecation changes no compatibility fact), and every `x-` extension field.

## 5. Direction

Every atomized position carries a **direction**, the discipline's central computation:

- **request** — content the caller supplies: parameters, request bodies, and everything
  reachable within them;
- **response** — content the caller receives: response bodies, response headers, and
  everything reachable within them.

Direction is a property of *position*, not of a schema: a named schema is atomized in each
direction from which it is reachable, its keys qualified by direction (§6), so a schema used
in both emits both atom families and each direction's folding rules apply to its own family. A
change safe in one direction and breaking in the other then grades correctly with no special
case: the safe side gains an atom, the breaking side loses one, and the loss decides (LIRA
§12.3).

**Direction flips under `callbacks` and `webhooks`.** Within a callback or webhook, the roles
reverse — the service calls the consumer — so the request position is what the *service*
supplies and the consumer must accept, and every rule of §7 applies with the polarities
exchanged. This is the variance flip of LIRA §10.5 appearing in the carrier, exactly as a
function type flips the variance of its arguments; a discipline that atomized callbacks
without flipping would certify additions that break every webhook receiver.

## 6. Keys

Keying is by **declaration**. Path templates canonicalize before keying: template variable
names are not observable on the wire, so `/users/{id}` and `/users/{userId}` are one route,
and each template variable is replaced by `{}` with its parameter bound positionally — the
de Bruijn rule of `dts.md` §8, applied to routes. Keys are:

- an operation: `<METHOD> <canonical path template>`, e.g. `GET /users/{}`;
- a parameter: `<operation>#<location>:<name>` with location `query`, `header`, `path`
  (positional index, per the canonicalization) or `cookie`;
- a request body: `<operation>#req/<media type>`;
- a response: `<operation>#resp/<status>` where `<status>` is a code, a class pattern
  (`4XX`), or `default`;
- a response header: `<response key>#<header name>`;
- a named schema, per direction: `<name>@req` or `<name>@resp`;
- a named schema's property: `<schema key>#<property name>`;
- an enumeration value, where standalone (§7): `<containing key>#=<value>`.

Media types participate in request-body and response keys, so adding one is the addition of
atoms and removing one is a removal — standalone behavior obtained through keying. The
tolerance this presumes in the response direction (content negotiation) is part of the
`http-json/1` profile's business (§2), not this discipline's claim.

## 7. Atoms and Folding

All atoms are **rigid**; there are no replaceable atoms and no reference lists (a description
copies nothing into consumers at their generation time that its atoms do not already cover).
The folding decisions, by direction:

**Operations.** Each operation is a standalone atom — adding an endpoint is a minor, the
actual compatibility behavior of every operated API. The operation's value folds: its
canonical route and method; the *existence and requiredness* of its request body; its
**security requirements** (what a caller must supply is contravariant: adding a scheme or a
scope breaks callers, so the sorted requirement list folds — and loosening one is equally a
fold change, hence major, on the conservative terms of `capability/1`'s version predicates);
and the sorted key list of its **required** request parameters.

**Request position** (contravariant — what callers supply):

- a **required** parameter or required body property folds into its operation's or schema's
  atom: adding one breaks every existing caller, and registers as removal-plus-addition —
  major — with no rule engine consulted;
- an **optional** parameter or optional property is a standalone atom: adding one breaks
  nobody. Making it required, like any fold change, is major;
- an **enumeration value is standalone**: a new accepted value widens what the service
  accepts;
- narrowing any type, tightening any constraint keyword (`maxLength` down, `pattern` added),
  or removing anything is a removal, hence major.

**Response position** (covariant — what callers receive):

- every **property is a standalone atom**, and its `required` status folds into the property's
  own value rather than the parent's: a required response property is a presence guarantee,
  and weakening it to optional changes the property's atom — major — while *adding* a property,
  required or not, is a minor under the must-ignore presumption that §2 assigns to the
  profile;
- an **enumeration value folds** into its containing schema's atom: a new value in a response
  breaks every consumer that matched exhaustively. The inversion against request-position
  enums is deliberate and is this discipline's clearest exhibit of directional folding — one
  syntax, opposite variance, exactly as `webidl/1`'s dictionaries stand against `dts/1`'s
  interfaces;
- a **response entry** (per status, per media type) is standalone: a new documented status or
  media type adds atoms; removing one is major. Response **headers** follow properties;
- widening a type or loosening a constraint in response position is a fold change — what a
  consumer was promised has changed shape — hence major.

**Named schemas.** Each direction's schema atom folds the schema's name, its canonicalized
type structure (§8) *minus* whatever stands alone in that direction, and — in request
direction — its sorted required-property list; properties, per direction, follow the rules
above. `additionalProperties: false` folds in both directions: in request position it narrows
what callers may send; in response position it is a promise of shape that must not silently
weaken.

**Composition keywords.** `allOf` resolves by merge before atomization where its members are
object schemas (the merged object is the contract); `oneOf` and `anyOf` member lists fold in
response position (a new alternative breaks exhaustive consumers) and stand alone in request
position (a new alternative widens what is accepted) — the enum rule, lifted to schemas. A
discriminator's property name and mapping fold wherever they appear.

## 8. Canonical Encoding

Atom values are hashes over a deterministic tag-length-value encoding on the principles of
`tasty.md` §7: unsigned LEB128 lengths, length-prefixed UTF-8 strings, single-character
constructor tags. Version-dependent spellings canonicalize to one model before encoding:
`nullable: true` (3.0) and the `null` member of a type array (3.1) encode as the same
nullability marker; `exclusiveMinimum` in its boolean (3.0) and numeric (3.1) forms encode
numerically; `example` never encodes (§4). Types encode structurally: primitive types by name
with their folded constraint keywords sorted by keyword; arrays by their item encoding; object
schemas by their sorted property selectors; references to named schemas by the target's key
(§4), never by inlining. Enumeration member lists, where folded (§7), sort by encoded value;
parameter lists in keys are positional and keep order. Two descriptions no generated consumer
can distinguish MUST NOT encode differently.

## 9. Replaceable Atoms

None. `openapi/1` emits only rigid atoms, and its reference lists are empty (§7). Content a
consumer bakes in at generation time — the description itself — is exactly what the atoms
cover, so replaceable churn has nothing to carry that a rigid change does not already grade.

## 10. Determinism

Two parses of identical documents MUST yield identical atom sets (LIRA §17): no positions, no
key order, no serialization format, no reference-resolution order above what §4 names as
semantic. Direction assignment (§5) is a reachability computation over the resolved document
and depends on no traversal order.

## 11. Prior Art (Informative)

The API-diff linters (oasdiff, openapi-diff, Optic) are the grade computation of LIRA §12.3
maintained as hand-written rule tables; consumer-driven contract testing (Pact) is used-sets
and spanning established by recorded interaction rather than set inclusion (services.md §9);
and `buf breaking` is the anticipated `proto/1`'s rule table in the same sense. This
discipline's contribution is not new judgements but their derivation from one principle — the
folding principle, computed directionally — so that the rule table is a consequence, the
grades feed lineages, and the lineages feed an algebra shared with every other carrier in this
specification.
