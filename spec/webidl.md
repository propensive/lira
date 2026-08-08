# The Web IDL Discipline `webidl/1` — Specification Draft

## Abstract

`webidl/1` is the LIRA discipline for Web IDL, the formal grammar in which the web platform
specifies its APIs. It atomizes a **host contract's** capability surface — the interfaces,
dictionary members, enumeration values and namespace members a browser exposes — applying the
folding principle of the LIRA specification (§10.3) so that the platform's own compatibility
behavior is encoded in the decomposition.

It is a host-contract discipline, not a library discipline. A browser is a host (LIRA §4.1):
nothing composes *in* it, and a `js`-universe library's carrier is `.d.ts`
([`dts.md`](dts.md)), which is itself generated from Web IDL. Placed beside `dts/1` this
discipline would atomize, in a representation nobody compiles against, a surface `dts/1`
already covers; placed on the host axis it describes something nothing else does
([`universes.md`](../design/universes.md) §5.4).

The reference implementation is the `xenophile` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline (`webidl/2`),
never a revision of this one (LIRA §11.1).

## 2. Scope and Guarantee

The discipline certifies **recompilation**, for the consumers who type-check against
declarations generated from the IDL — and nothing else. There is no linkage in a browser to
protect, and whether a present API *behaves* is out of scope as always (LIRA §18). Presence of
a capability is certified on the same terms as `capability/1` ([`hosts.md`](hosts.md) §5); the
runtime check is the third verification moment (hosts.md §9), for which feature detection is
the native idiom.

## 3. Domain and Content Claiming

The domain is the single realm `{host}`. A release carrying `webidl/1` is a host contract
(LIRA L135), and the cross-section invariant (LIRA §9.6) is vacuous.

The discipline claims `**/*.idl`, atomized. It claims nothing else.

## 4. Extraction

Atomization is performed over the parsed definitions, never over the text: comments,
whitespace and definition order at the top level never enter the model.

**Partials and mixins are resolved before atomization.** `partial interface` and `partial
dictionary` definitions merge into their targets; `interface … includes Mixin` folds the
mixin's members into the including interface, exactly as the platform presents them. This is
the syntax that exists *because* the platform adds members to existing interfaces continuously,
and it is what declares the usage direction TypeScript cannot see (`dts.md` §11): nothing
outside the browser implements `Element`, so member addition is additive here where `dts/1`
must grade it major.

**A construct outside the parser's vocabulary is a hard atomization error** (the rule of
`tasty.md` §7 and `dts.md` §5, for the same reason: a partial reading understates a contract).

## 5. Keys

Keying is by **declaration**. Keys are:

- an interface, dictionary, enumeration, namespace, typedef or callback: its name;
- an interface or namespace member: `<container>#<selector>`, where the selector is the
  member's name for an attribute, operation or constant, `<name>(<argument types>)` for an
  overloaded operation, and `new()` for a constructor;
- a dictionary member: `<dictionary>#<name>`;
- an enumeration value: `<enumeration>#<value>`.

Where `[Exposed=…]` restricts a construct to particular global scopes, the exposure set is part
of the key (sorted, comma-joined): `Window` and `WorkerGlobalScope` genuinely offer different
surfaces, and a member exposed in one is a different capability from the same member exposed in
both.

## 6. Atoms and Folding

All atoms are **rigid**; there are no replaceable atoms and no reference lists. The folding
decisions are the platform's own compatibility rules:

- **Interface members are standalone atoms** — adding one is a minor, which is the actual
  compatibility behavior of every browser release. The member's value folds its kind
  (attribute, operation, constant, constructor), its `readonly` and `static` flags, and its
  canonically-encoded type or signature.
- **The interface's own atom** folds its name, its inheritance parent (or an absence marker)
  and its flags. Member lists do **not** fold into it: that is the deliberate inversion of
  `dts.md` §10–§11, licensed by the declared usage direction.
- **Dictionary members fold when `required` and stand alone when optional.** A required member
  folds into the dictionary's own atom — adding one breaks every caller constructing the
  dictionary — while an optional member is a standalone atom whose addition breaks nobody.
  Making an optional member required, like any fold change, registers as removal-plus-addition:
  major.
- **Enumeration values are standalone atoms**: an enumeration is a parameter type, so a new
  value widens what the platform accepts.
- **Namespace members and typedefs** follow interface members and dictionaries respectively; a
  typedef's atom folds its target type, and a callback's its signature.

Removing or renaming anything, narrowing an argument type, or changing any folded property is a
removal, hence major — by the algebra, with no rule engine (LIRA §10.3).

## 7. Canonical Encoding

Atom values are hashes over a deterministic tag-length-value encoding on the same principles as
`tasty.md` §7: unsigned LEB128 lengths, length-prefixed UTF-8 strings, single-character
constructor tags. Primitive types canonicalize to width-explicit names before encoding
(`unsigned long` → `u32`, `long long` → `s64`, `octet` → `u8`, `double` → `f64`, and so on),
the string types (`DOMString`, `USVString`, `ByteString`, `CSSOMString`) to `string`, and `T?`
to the union of `T` with `null` — so a spelling difference that no consumer can observe never
distinguishes two hashes. Union member types sort by their encoded bytes (`(A or B)` is
`(B or A)`);
argument lists keep declaration order (they are positional); extended attributes that do not
affect consumers' view of the surface (`[SameObject]`, `[Replaceable]` and kin) are not folded,
while those that do (`[Exposed]`, in the key; nullability; optionality and defaults'
*existence*) are.

## 8. Determinism

Two parses of identical sources MUST yield identical atom sets (LIRA §17): no positions, no
interning, and no order above what §7 names as semantic. Partial/mixin resolution is a set
union keyed by member selector, independent of definition order across files.

## 9. Prior Art (Informative)

The `@webref/idl` curated extracts, MDN's browser-compat-data and the "Baseline"
interoperability definitions are, in effect, published host contracts already; this discipline
gives them an identity and an algebra rather than inventing them. A `browser-baseline` contract
atomized by `webidl/1` is the anticipated first instance (hosts.md §11).
