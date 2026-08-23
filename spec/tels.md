# The TEL Schema Discipline `tels/1` — Specification Draft

## Abstract

`tels/1` is the LIRA discipline for TEL schema payloads: releases whose content is a TEL
schema document, published and versioned through LIRA so that a TEL pragma may reference a
schema as `‹domain›/‹name›:‹version›` (tel repository, `design/lira-schema-references.md`).
It atomizes the schema's **composed component sequence** — the base document and its declared
layers, as hashed for the BinTEL palimpsest signature (bintel.md §8) — such that LIRA's grade
computation coincides, by construction, with TEL's own signature-subsequence compatibility
relation (tel.md §8.2, §24.4, §24.5). Schema versions are therefore *derived*, never chosen
(LIRA §12.5), and the derivation is certified by TEL's subtype relation itself.

The discipline additionally enforces, at publish time, the name bindings the pragma grammar
relies on: a schema's declared `name` binds its module name, and its declared layer names are
the names addressable by `+` layer selections.

It is named for the carrier: `tels` is the meta-schema to which every TEL schema document
conforms, itself published at the pinned coordinate `specification.tel/tels:1.0.0`.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline (`tels/2`),
never a revision of this one (LIRA §11.1). Migration between the two, when it comes, is the
dual-declaration bridge of LIRA §11.1's non-normative note: a bridging release declares
both, graded minor; dropping `tels/1` is the deliberate major.

## 2. Scope and Guarantee

The discipline certifies **recompilation**, in its natural transposition for schemas:
**revalidation** — within a lineage, every document valid against a release's schema remains
valid against every later release's, because TEL's subtype relation `S_doc <: S_cons` holds
whenever the consumer's component sequence is a subsequence of the document's (tel.md §8.2),
and §9 makes every minor step preserve exactly that relation.

It does not certify linkage; nothing links against a schema.

## 3. Domain

The domain is **every universe, and the `host` realm** — the `dts/1` precedent, for the same
reason: a schema is universe-agnostic content with no universe of its own to name, and
universality brings it under the cross-section API invariant (LIRA §9.6), so a release
offering several universes must publish the *same* schema surface in each. The `host`
inclusion is a use, not a workaround: LIRA's own extension layers — new universes, new
manifest fields (LIRA §14) — are infrastructure contracts published as `tels/1` modules, and
a platform MAY likewise carry configuration schemas on its host contract.

The `app` and `env` realms are excluded: a schema inside a closed artifact is data no
consumer resolves a pragma against, and an environment's substance is manifest records.

## 4. Content Claiming

The discipline claims the tree item at the fixed path `schema.tel`, which MUST exist, MUST be
a TEL schema document conforming to the `tels` meta-schema, and MUST be the release's only
claimed content. A release declaring `tels/1` whose `schema.tel` is absent or fails to
conform is invalid. Ancillary content (documentation, examples) falls to `resource/1` where
declared and `opaque/1` otherwise, and enters no atom of this discipline.

One release publishes one schema. A project with several schemas publishes several modules.

## 5. Extraction

Atomization is performed over the schema's **canonical BinTEL form**, never over its text
(whitespace, comments, and soft-space alignment never enter the model — the same rule every
carrier discipline applies).

From the canonical form, extraction computes the **component sequence**: the 256-bit BLAKE3
hash of the base document, followed by the hash of each declared layer, in declaration order,
exactly as bintel.md §8 computes them for the palimpsest signature. The composed signature is
the palimpsest of this sequence, and §11 requires tooling to expose it.

Component hashes within one schema MUST be pairwise distinct; a schema with two identical
components is degenerate (its layers are indistinguishable to the signature) and MUST be
rejected at atomization. Distinctness is what licenses the set-encoding of §9.

## 6. Keys

Keying is by **declaration**. Keys are:

- the base component: `base`;
- a layer component: `layer ‹name›`, the layer's declared name;
- an order pair (§8): `order ‹key-a› ‹key-b›`, the keys of the two components in sequence
  order.

Keys are metadata for reporting (LIRA §10.4); identity is the value hash, so a layer renamed
without a change to its canonical bytes changes no atom — precisely mirroring TEL, whose
compatibility relation reads component hashes and never names.

## 7. Rigid Atoms: Components

One rigid atom per component of the sequence. The value hashes the byte `0x01` followed by
the component's 256-bit BLAKE3 hash, verbatim.

## 8. Rigid Atoms: Order

One rigid atom per **ordered pair** of components `(i, j)` with `i < j` in the sequence. The
value hashes the byte `0x02`, the earlier component's hash, then the later component's hash.

A sequence of `n` components yields `n` component atoms and `n·(n−1)/2` order atoms. Layer
counts are small in practice; the quadratic term is noted and accepted, because it is what
makes §9 exact rather than approximate.

## 9. Coincidence: TEL Subtyping as LIRA Grades

For sequences of pairwise-distinct elements (§5), sequence `A` is a subsequence of sequence
`B` **iff** `A`'s element set is a subset of `B`'s *and* `A`'s ordered-pair set is a subset
of `B`'s: elements give membership, pairs give relative order, and distinctness makes the
reconstruction unambiguous. The atoms of §7 and §8 encode exactly these two sets, so for
successive releases of a schema module:

| TEL relation between successive releases              | Atom relation             | LIRA grade (§12.3) |
| ----------------------------------------------------- | ------------------------- | ------------------ |
| identical composed signature                          | identical atom set        | patch              |
| predecessor's sequence a proper subsequence           | proper rigid subset       | minor              |
| subsequence relation broken (removal, reorder, edit)  | atoms removed or replaced | major (L110)       |

The common minor case is layers appended, where the predecessor's sequence is a prefix; the
encoding also correctly grades an insertion that preserves relative order, which the
subsequence relation admits. Any edit to a component's canonical bytes changes its hash,
removing its component atom and every pair atom it participates in — a major, as TEL's
relation requires.

LIRA's version derivation (§12.5) then yields the schema's published version from the grade
with no further mechanism: **schema versions are computed, never chosen.** A consumer asking
for the newest release of a module whose schema extends a given composed signature
(distribution design, `RESOLVE-EXTENDS`) receives an answer that TEL's own subtype relation
certifies.

## 10. Name Binding

Two bindings are enforced at publish time, so that the names in a pragma are exactly the
names in the schema source a reader will find:

- the schema's declared `name` MUST equal the release's module name: a schema declaring
  `name foo` is publishable only as `‹domain›/foo`;
- the schema's declared layer names are the names addressable by `+` layer selections in a
  TEL pragma, in declaration order; a publishing tool MUST reject a schema whose layer names
  are not pairwise distinct.

A publishing tool MUST refuse a `tels/1` release violating either binding, on the same
footing as LIRA §12.5's publication rules.

## 11. The Composed Signature

The composed schema signature — the BASE-256 palimpsest of the component sequence
(bintel.md §8) — is recomputable from the payload by every verifier, and tooling MUST report
it for any `tels/1` release. It is the identity under which TEL caches and matches the schema
(tel.md §8.2), and the query key for signature-form resolution (distribution design §6): the
index answers "the newest release of this module whose component sequence contains this
signature's sequence as a subsequence", which by §9 is precisely "the newest release a
document carrying this signature validates against".

The composed signature is not the release's snapshot — the snapshot is the hash of the atom
set (LIRA §12.1), which additionally encodes the pair structure — but each is derivable from
the payload, and the two agree on every judgment by §9.

## 12. Replaceable Atoms

None. `tels/1` emits only rigid atoms. A schema is consumed whole at parse time; there is no
fragment copied into consumers whose replacement could be certified independently of the
composition it belongs to.

## 13. Reference Lists

Empty, since §12 emits no replaceable atoms.

## 14. Determinism

Two atomizations of identical canonical BinTEL bytes MUST yield identical atom sets (LIRA
§17). The component sequence is a pure function of the canonical form; the atom values are
pure functions of the component hashes; no property of the parse that the canonical form does
not determine enters the model.
