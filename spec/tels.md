# The TEL Schema Discipline `tels/2` — Specification Draft

## Abstract

`tels/2` is the LIRA discipline for TEL schema payloads: releases whose content is a TEL
schema document, published and versioned through LIRA so that a TEL pragma may reference a
schema as `‹domain›/‹name›:‹version›` (tel repository, `design/lira-schema-references.md`).
It atomizes the schema's **composed form** — the `Schema` value obtained by composing the
document's base and layers (tel.md §20.3) — into rigid atoms chosen so that rigid growth of
the atom set implies TEL's own compatibility relation between the two composed schemas:
`compose(successor) <: compose(predecessor)` under the subtype relation of tel.md §24.3, which
tel.md §8.2 makes normative for document compatibility. A LIRA minor is therefore a
TEL-compatible step by construction, and schema versions are *derived*, never chosen
(LIRA §12.5) — conservatively, since the converse does not hold in every case (§9).

The discipline additionally enforces, at publish time, the name bindings the pragma grammar
relies on: a schema's declared `name` binds its module name, and its declared layer names are
the names addressable by `+` layer selections.

It is named for the carrier: `tels` is the meta-schema to which every TEL schema document
conforms, itself published at the pinned coordinate `specification.tel/tels:2.0.0`.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline (`tels/3`),
never a revision of this one (LIRA §11.1). Migration between two disciplines is the
dual-declaration bridge of LIRA §11.1's non-normative note: a bridging release declares both,
graded minor; dropping the older is the deliberate major.

**`tels/1` is superseded.** The first draft atomized the schema's *component sequence* — one
atom per component hash and one per ordered pair — so that LIRA's grade coincided with TEL's
then-current rule that a document is compatible with a consumer whenever the consumer's
component sequence is a subsequence of the document's. TEL withdrew that rule as unsound
(tel.md §8.2; the remark in §24.4: a component's effect depends on the components before it,
so [base, loose, strict] is not a subtype of [base, strict] though the latter's hashes form a
subsequence of the former's) and replaced it with the subtype check on composed schemas.
`tels/1` reproduced the unsound relation faithfully and is retired with it; no release ever
declared it, so no bridge is needed.

## 2. Scope and Guarantee

The discipline certifies **recompilation**, in its natural transposition for schemas: within
a lineage, **every document valid under a later release's composed schema projects (tel.md
§24.5) to a valid document under every earlier release's**, so a reader holding any earlier
release reads what writers produce under any later one, and no reader need be regenerated
within a lineage. This is the direction TEL's relation runs — `compose(S_doc) <:
compose(S_cons)` licenses a consumer expecting `S_cons` to read `S_doc` — and it is the
direction operations needs: writers advance, readers upgrade when they choose.

The converse — that documents written under an earlier release remain valid under a later one
— is *not* certified and is not generally true: a layer may add a required field (tel.md
§20.3), which every conforming successor is free to do and which invalidates earlier
documents. Readers of this specification arriving from `tels/1`, whose §2 stated the
guarantee in that direction, should note the correction.

It does not certify linkage; nothing links against a schema.

## 3. Domain

The domain is **every universe, and the `host` realm** — the `dts/1` precedent, for the same
reason: a schema is universe-agnostic content with no universe of its own to name, and
universality brings it under the cross-section API invariant (LIRA §9.6), so a release
offering several universes must publish the *same* schema surface in each. The `host`
inclusion is a use, not a workaround: LIRA's own extension layers — new universes, new
manifest fields (LIRA §14) — are infrastructure contracts published as `tels/2` modules, and
a platform MAY likewise carry configuration schemas on its host contract.

The `app` and `env` realms are excluded: a schema inside a closed artifact is data no
consumer resolves a pragma against, and an environment's substance is manifest records.

## 4. Content Claiming

The discipline claims the tree item at the fixed path `schema.tel`, which MUST exist, MUST be
a TEL schema document conforming to the `tels` meta-schema, and MUST be the release's only
claimed content. A release declaring `tels/2` whose `schema.tel` is absent or fails to
conform is invalid. Ancillary content (documentation, examples) falls to `resource/1` where
declared and `opaque/1` otherwise, and enters no atom of this discipline.

One release publishes one schema. A project with several schemas publishes several modules.

## 5. Extraction

Atomization is performed over the schema's **composed form**, never over its text or over
its component sequence. From the canonical BinTEL form of `schema.tel`, extraction:

1. computes the component sequence — the base document and its declared layers, in
   declaration order — and its atomic expansion (tel.md §20.3, *Atoms and Canonical
   Decomposition*);
2. composes the sequence into a `Schema` value by the merge algorithm of tel.md §20.3,
   checking the composed-schema validity constraints of tel.md §20.1; a schema that does not
   compose validly MUST be rejected at atomization;
3. walks the composed value — its document Struct, and every Definition of its composed
   namespace — emitting the atoms of §7 at each **path**.

A **path** is one of: `document`, the document root; `record ‹N›`, `scalar ‹N›` or
`select ‹N›`, a Definition; or `‹path›/‹keyword›`, the inline type of a member — a Field whose
type is an inline Struct or Scalar emits that type's own atoms under the path extended by the
Field's keyword. Paths are compared as strings.

`description` lines enter no atom: two schemas differing only in descriptions have identical
atom sets and stand in patch relation, as tel.md §8.2's matching rule treats them.

## 6. Keys

Keying is by **declaration**: every atom's key names the path and, where applicable, the
member, validator or definition it concerns:

- `head`, the base schema's head;
- `definition ‹path›`, one per Definition;
- `member ‹path› ‹K›`, one per Struct member, `K` being a Field's keyword or, for a Select
  member, `select:‹N›` naming its SelectDefinition;
- `required ‹path› ‹K›`, `single ‹path› ‹K›`, `key ‹path› ‹K›`, the member's constraints;
- `order ‹path› ‹K₁› ‹K₂›`, one per ordered pair of members of a Struct;
- `validate ‹path› ‹name›`, one per validator of a Struct, Scalar or Select;
- `pattern ‹path› ‹hash›`, one per pattern line of a Scalar, `‹hash›` being the BLAKE3 hash of
  the pattern text in BASE-256;
- `encoding ‹path›`, a Scalar's encoding;
- `variants ‹path›`, a SelectDefinition's variant set.

Keys are metadata for reporting (LIRA §10.4); identity is the value hash.

## 7. Rigid Atoms

Every atom is rigid. A composed schema emits:

1. **Head.** One atom for the base schema's `name` and `sigil`, folded: changing either is a
   major. (The name binding of §10 makes a name change a module change in any case.)
2. **Definitions.** One atom per Definition of the composed namespace — record, scalar or
   select — covering its kind and name, so that a definition is never lost from the atom set
   by being empty, and removing one, which breaks every reference to it, is a major.
3. **Members.** For every Struct — the document root, each RecordDefinition, and each inline
   Struct — one atom per member, covering the member's kind (Field or Select), its keyword
   (for a Select member, the SelectDefinition's name), its **type descriptor** — `flag`; a
   Reference by name; or the markers `inline-scalar` or `inline-struct`, whose content is
   atomized under the extended path — and, for a Field, its default, present or absent.
   Defaults fold into the member because tel.md §24.3 admits default-divergent subtypes while
   forbidding the inference that changing one is safe.
4. **Member constraints.** For every member: an atom `required` iff the member's effective
   `required` is true; an atom `single` iff its effective `repeatable` is false; an atom `key`
   iff it is a key field. Tightening adds an atom; loosening removes one — the encoding of
   tel.md §24.3's premises `r₂ ⟹ r₁`, `p₁ ⟹ p₂` and `k₂ ⟹ k₁` as set growth.
5. **Order.** For every Struct with members `m₁ … mₙ` in member order, one atom per ordered
   pair `(mᵢ, mⱼ)`, `i < j`. `n·(n−1)/2` atoms per Struct: member counts are modest, and the
   quadratic term is what encodes [Sub-Struct]'s premise that the matched members keep their
   relative order — the premise the component-sequence encoding of `tels/1` got wrong by
   placing at the level of layers.
6. **Validators.** One atom per validator of a Struct, Scalar or SelectDefinition, covering the
   validator's canonical form; validators are identified by name (tel.md §8.2 compares them as
   name sets).
7. **Patterns.** One atom per `pattern` line of a Scalar, covering the pattern text. TEL treats
   a scalar's pattern lines as a single schema atom, because a replacement is decided by one
   containment check (E223); this discipline atomizes them one by one, because adding a
   pattern intersects and therefore narrows — sound with no semantic check — while any
   replacement is graded as a removal (§9).
8. **Encoding.** One atom per Scalar carrying an encoding, covering the encoding's name.
9. **Variant sets.** One atom per SelectDefinition, covering its variant keywords and each
   variant's type descriptor, in keyword order, **folded**: any change to the variant set is
   a removal. Narrowing by `exclude` is a TEL subtype, and this fold grades it major; §9
   records the choice.

## 8. Canonical Encoding

Every atom value is `hash("lira/1:atom:tels/2", bytes)` over a canonical byte encoding that
begins with a one-byte tag identifying the class of §7 — `0x01` head, `0x02` definition,
`0x03` member, `0x04` required, `0x05` single, `0x06` key, `0x07` order, `0x08` validator,
`0x09` pattern, `0x0A` encoding, `0x0B` variants — followed by the path's UTF-8 bytes,
`0x00`, then the class's fields in the order §7 lists them, each as its UTF-8 bytes
terminated by `0x00`, with absent optional fields encoded as an empty string. Type
descriptors are the literal strings `flag`, `inline-scalar`, `inline-struct`, or `ref:‹N›`.
A member's keyword is its declared keyword, or `select:‹N›` for a Select member.

The TEL value hashes of schema atoms (tel.md §20.3) are deliberately not reused: they are
hashes of *modifications* in a composition, and this discipline hashes *facts* about the
composed result. The two identities meet in §11.

## 9. Coincidence: TEL Subtyping as LIRA Grades

**Theorem (soundness).** For two composed schemas `P` (predecessor) and `S` (successor), if
`atoms(P) ⊆ atoms(S)` then `compose(S) <: compose(P)` under tel.md §24.3.

*Sketch.* Proceed by induction on `P`'s type structure at each path, coinductively across
Definition references. At a Struct path: every member of `P` has its member atom in `S`, so
[Sub-Struct]'s φ is defined on every member of `P` (not merely the required ones), matching
kind, keyword, type descriptor and default; every order atom of `P` is in `S`, so φ is
strictly increasing; `P`'s validator atoms are in `S`, so `V₂ ⊆ V₁`. For each matched member,
[Sub-Field]'s premises hold: keywords agree; `r₂ ⟹ r₁`, `p₁ ⟹ p₂` and `k₂ ⟹ k₁` because
the `required`, `single` and `key` atoms of `P`'s member are present on `S`'s; and the type
premise `T₁ <: T₂` holds by reflexivity for `flag`, by the inductive hypothesis at the
extended path for an inline type, and by the coinductive hypothesis on the named Definition
for a Reference, whose definition atom and whose own atoms `P` contributes are all in `S`.
For a Select member, [Sub-Select] holds because the SelectDefinition's folded variant atom is
preserved, so the variant sets are equal, and its `required`/`single` atoms carry as above.
At a Scalar path: validator atoms give `V₂ ⊆ V₁`; pattern atoms give `P₂ ⊆ P₁` textually,
whence `L(⋂P₁) ⊆ L(⋂P₂)`; the encoding atom, if `P` has one, is preserved, giving `e₁ = e₂`,
and if `P` has none the premise is vacuous. Every premise of §24.3 is thus discharged. ∎

The relation is therefore graded by LIRA §12.3 with no further mechanism:

| Relation between successive releases                         | Atom relation             | LIRA grade (§12.3) |
| ------------------------------------------------------------ | ------------------------- | ------------------ |
| identical composed schema, up to `description`               | identical atom set        | patch              |
| rigid growth — hence `compose(S) <: compose(P)` by the theorem | proper superset           | minor              |
| anything else                                                | atoms removed or replaced | major (L110)       |

**What grades major though TEL would accept it.** The theorem's converse fails, by design,
wherever TEL's relation is semantic or narrowing and the set algebra cannot see it: dropping
an optional member (a subtype, since an element of `S` never populates it); narrowing a
variant set by `exclude`; replacing a Scalar's pattern lines by a textually different set
whose language is contained in the old one; changing a member's type to a *different*
Reference whose definition is a subtype; reordering members. Each of these is a
TEL-compatible step that removes an atom here, and is graded major. The consequence is stated
plainly: **a `tels/2` minor is always TEL-compatible; some TEL-compatible steps are `tels/2`
majors.** Derived versions (LIRA §12.5) are sound with respect to TEL's relation and
occasionally stricter than it, which is the right side to err on for a relation whose
unsound approximation has already been retired once (§1). A publisher who needs one of the
conservative steps within a lineage may express most of them additively — a new member
beside the old, a new scalar beside the old — and retire the predecessor at the next major.

The common minor case is a layer appended, which by tel.md §24.4 always produces a subtype
and here always produces rigid growth: every atom of the prior composition survives, since
layers only add members, tighten constraints, add validators and patterns, add encodings,
and exclude variants — the last being the one layer operation this discipline grades
conservatively.

## 10. Name Binding

Two bindings are enforced at publish time, so that the names in a pragma are exactly the
names in the schema source a reader will find:

- the schema's declared `name` MUST equal the release's module name: a schema declaring
  `name foo` is publishable only as `‹domain›/foo`;
- the schema's declared layer names are the names addressable by `+` layer selections in a
  TEL pragma, in declaration order; a publishing tool MUST reject a schema whose layer names
  are not pairwise distinct.

A publishing tool MUST refuse a `tels/2` release violating either binding, on the same
footing as LIRA §12.5's publication rules.

## 11. The Composed Signature

The composed schema signature — the BASE-256 palimpsest of the component sequence (bintel.md
§8) — is recomputable from the payload by every verifier, and tooling MUST report it for any
`tels/2` release, together with the sequence's atomic expansion (tel.md §20.3). It is the
identity under which TEL caches and matches the schema (tel.md §8.2), and the query key for
signature-form resolution (distribution design §6): asked for the newest release of a module
compatible with a given composed signature, the index answers the newest release in whose
lineage a release with that signature's atomic expansion appears — by §9 every later
release of that lineage is a subtype of it — and, for a signature naming a composition no
release published (a document naming only the atoms it uses, tel.md §8.1), evaluates
`compose(S_release) <: compose(S_query)` directly, on the release's shipped schema.

The composed signature is not the release's snapshot — the snapshot is the hash of the atom
set (LIRA §12.1) — but each is derivable from the payload, and the two agree on every
judgment the theorem of §9 covers.

## 12. Acceptances (Informative)

A TEL **acceptance** (bintel.md §8.4) is the document by which a reader tells a writer which
composed schemas it can consume, in decreasing order of preference, and which further
components it can resolve. In LIRA's terms (LIRA §4.2) an acceptance's `schema` alternatives
are the reader's **group** — ordered alternatives, first satisfied member served — and each
alternative's requirement `S_cons` plays the part of a required snapshot: the writer's
document must be a subtype of it, as a provider's lineage must contain the requirer's
snapshot. The correspondence is per document flow: a request and its response are two flows
with the reader alternating, and each flow is one acceptance.

Where the two mechanisms touch is the `any-published` flag, which permits the writer any
*published* component of the base's lineage: "published" there is this discipline's lineage,
and a reader that sets the flag is delegating to LIRA the question of which compositions
exist. Where they part is the writer's choice: having found an alternative it can serve, a
TEL writer picks the *richest* composition the reader can take, re-checking the subtype
relation after each added component (bintel.md §8.4, *Choosing a composition*); a LIRA offer
is one release's fixed atom set, and no such choice arises. LIRA decides, from manifests and
before any document is written, that a compatible composition exists at the level this
discipline certifies; the acceptance decides, at the moment of exchange and on the actual
documents, which one is served. The two are complementary, and [`junctures.md`](../design/junctures.md)
§9 sets them side by side.

## 13. Replaceable Atoms

None. `tels/2` emits only rigid atoms. A schema is consumed whole at parse time; there is no
fragment copied into consumers whose replacement could be certified independently of the
composition it belongs to.

## 14. Reference Lists

Empty, since §13 emits no replaceable atoms.

## 15. Determinism

Two atomizations of identical canonical BinTEL bytes MUST yield identical atom sets (LIRA
§17). The composed schema is a pure function of the canonical form (tel.md §20.3's merge
algorithm is deterministic); the atoms are pure functions of the composed schema; no property
of the parse that the canonical form does not determine enters the model.
