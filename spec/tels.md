# The TEL Schema Discipline `tels/2` — Specification Draft

## Abstract

`tels/2` is the LIRA discipline for TEL schema payloads: releases whose content is a TEL
schema document, published and versioned through LIRA so that a TEL pragma may reference a
schema as `‹domain›/‹name›:‹version›` (tel repository, `design/lira-schema-references.md`).
It atomizes the schema's **atomic expansion** — the schema atoms of its base and declared
layers, in canonical order (tel.md §20.3) — with each atom's position folded into its value,
so that LIRA's set inclusion is exactly the *prefix* relation on expansions: the relation TEL
proves subtype-producing (tel.md §24.4). Schema versions are therefore *derived*, never chosen
(LIRA §12.5), and every minor step is a TEL subtype step, certified by TEL's own theorem.

The discipline additionally enforces, at publish time, the name bindings the pragma grammar
relies on: a schema's declared `name` binds its module name, and its declared layer names are
the names addressable by `+` layer selections.

It is named for the carrier: `tels` is the meta-schema to which every TEL schema document
conforms, itself published through this discipline at the pinned coordinate
`specification.tel/tels:2.0.0`. The discipline's version and the meta-schema's are unrelated
numbers that happen to agree.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline (`tels/3`),
never a revision of this one (LIRA §11.1), migrated by the dual-declaration bridge of LIRA
§11.1's non-normative note: a bridging release declares both, graded minor; dropping the old
one is the deliberate major.

`tels/1` encoded the signature-subsequence relation of an earlier TEL revision — one atom per
component, one per ordered pair — and TEL has since withdrawn that relation as unsound
(tel.md §8.2; the remark closing §24.4). `tels/1` is retired with it, not bridged: no lineage
was published under it, so there is nothing to carry across. §8 records why.

## 2. Scope and Guarantee

The discipline certifies **recompilation**, in its natural transposition for schemas:
**readability** — within a lineage, every document produced under a later release's schema
is readable by a consumer holding any earlier release's, because the later composed schema is
a subtype of the earlier (`S_doc <: S_cons`, tel.md §8.2), and §9 makes every minor step
preserve exactly that relation. The direction is the algebra's own (LIRA §10.5): the schema
under which documents are produced is the provider's surface, and it grows; a consumer
reading under an earlier release projects (tel.md §24.5). The converse — that a document
valid under an earlier release validates under a later one — is not certified, and is false
in general: a layer may add a required field.

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

Atomization is performed over the schema's **canonical BinTEL form**, never over its text
(whitespace, comments, and soft-space alignment never enter the model — the same rule every
carrier discipline applies).

From the canonical form, extraction computes the **component sequence** — the base document,
then each declared layer in declaration order, exactly as bintel.md §8 orders them for the
palimpsest signature — and its **atomic expansion** `A(S)` (tel.md §20.3, *Atoms and
Canonical Decomposition*): each component replaced by its schema atoms in canonical order,
an atom occurring twice retained once. Every standalone `description` atom — a `record`'s,
`scalar`'s or `select`'s `description` line — is then dropped: documentation enters no atom.
The result is the **retained expansion** `R(S)`, a sequence of pairwise-distinct atoms, each
carrying the 256-bit BLAKE3 value hash TEL assigns it (tel.md §20.3; bintel.md §8.1), which
depends on the atom and its path alone. A `description` child of an overlay or document
`field` is part of that field's atom in TEL's decomposition, and remains so here.

## 6. Keys

Keying is by **declaration**: `‹position› ‹path› ‹line›` — the atom's zero-based position in
`R(S)`, its path (`head`, `record ‹N›`, `scalar ‹N›`, `select ‹N›`, `overlay` or `document`),
and the atom's first line as TEL text. Keys are metadata for reporting (LIRA §10.4); identity
is the value hash, so a layer renamed without a change to its canonical bytes changes no atom
— precisely mirroring TEL, whose compatibility relation reads composed schemas and never
names.

## 7. Rigid Atoms

One rigid atom per element of `R(S)`. The value hashes the byte `0x01`, the atom's zero-based
position in `R(S)` as a 32-bit big-endian unsigned integer, then the atom's TEL value hash,
verbatim. A retained expansion of `n` atoms yields `n` rigid atoms; nothing is quadratic.

## 8. Why Position

Set inclusion sees membership, not order, and order is load-bearing: TEL's merge applies
atoms left to right, and a component's effect depends on the components before it. `tels/1`
recovered order with one atom per ordered pair, which encodes the *subsequence* relation —
and the subsequence relation is unsound, by the counter-example of tel.md §24.4: with base
`field id String`, layer `loose` declaring `field note String optional` and layer `strict`
declaring `field note String`, the composition `[base, strict]` has `note` required while
`[base, loose, strict]` has it optional, so the longer composition is *not* a subtype of the
shorter, though the shorter's sequence is a subsequence of the longer's.

What TEL proves is the **prefix** theorem: applying further components to a composition
yields a subtype of that composition (tel.md §24.4). Folding position into the value makes
the atom set of a sequence determine the sequence, and makes `atoms(A) ⊆ atoms(B)` hold iff
`R(A)` is a prefix of `R(B)`: an insertion shifts every later atom, which registers as their
removal. The counter-example grades major, as it must — `strict` stands at position 2 in one
expansion and 3 in the other.

## 9. Coincidence: Prefix Extension as LIRA Grades

For successive releases of a schema module:

| TEL relation between successive releases              | Atom relation             | LIRA grade (§12.3) |
| ----------------------------------------------------- | ------------------------- | ------------------ |
| equal retained expansions                             | identical atom set        | patch              |
| predecessor's retained expansion a proper prefix      | proper rigid subset       | minor              |
| anything else (removal, reorder, insertion, edit)     | atoms removed or replaced | major (L110)       |

**Soundness.** A minor step is a prefix extension, so the successor's composition is the
predecessor's with further atoms applied — a subtype, by tel.md §24.4. The dropped
`description` atoms are no-ops under the merge and do not disturb this; a description inside
a field atom is not dropped. Every document produced under the successor is therefore
readable under the predecessor: §2's guarantee, certified by TEL's theorem rather than by
this discipline's argument.

**Completeness for layering.** Every operation a layer may perform (tel.md §20.3, *Permitted
Operations*) — adding a field, a select reference or a definition; refining a field, a
record, a select or a scalar in place; excluding a variant; appending validators; checked
pattern replacement; adding an encoding; marking a field as key — is, in the atomic
expansion, the appending of atoms: an in-place refinement is a new atom merged over the old,
never a change to the old. Appending a layer to a published schema is therefore always a
minor, and the ordinary case of schema evolution needs no thought.

**Incompleteness, stated plainly.** TEL decides compatibility by computing `<:` on composed
schemas (tel.md §8.2), and `<:` is wider than prefix extension: a successor whose base was
rewritten into a composition of the same meaning but a different expansion, or one whose
atoms were reordered where the subtype relation does not care about order (two independent
definitions swapped), is a TEL subtype that this discipline grades major. LIRA computes `⊆`
and never `<:` (LIRA §10.3); the discipline is sound, exact for the layer discipline, and
conservative beyond it — the posture of every carrier discipline. A publisher who wants the
minor expresses the change as a layer.

LIRA's version derivation (§12.5) then yields the schema's published version from the grade
with no further mechanism: **schema versions are computed, never chosen.** A consumer asking
for the newest release of a module whose retained expansion extends a given one
(distribution design, `RESOLVE-EXTENDS`) receives an answer TEL's own theorem certifies.

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

The composed schema signature — the BASE-256 palimpsest of the component sequence
(bintel.md §8) — is recomputable from the payload by every verifier, and tooling MUST report
it for any `tels/2` release. It is the identity under which TEL caches and matches the schema
(tel.md §8.2), and the query key for signature-form resolution (distribution design §6): a
signature's retained expansion is computed from the components it names — groups or atoms
alike (tel.md §8.1) — and the index answers "the newest release of this module whose retained
expansion has the signature's as a prefix", which by §9 is precisely "the newest release
whose schema is provably a subtype of the one the signature names": a release holding every
component the signature needs.

The composed signature is not the release's snapshot — the snapshot is the hash of the atom
set (LIRA §12.1), which additionally fixes every position — but each is derivable from the
payload, and the two agree on every judgment by §9.

## 12. Replaceable Atoms

None. `tels/2` emits only rigid atoms. A schema is consumed whole at parse time; there is no
fragment copied into consumers whose replacement could be certified independently of the
composition it belongs to.

## 13. Reference Lists

Empty, since §12 emits no replaceable atoms.

## 14. Determinism

Two atomizations of identical canonical BinTEL bytes MUST yield identical atom sets (LIRA
§17). The retained expansion is a pure function of the canonical form; the atom values are
pure functions of position and the TEL value hashes; no property of the parse that the
canonical form does not determine enters the model.
