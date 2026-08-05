# The TypeScript Declaration Discipline `dts/1` — Specification Draft

## Abstract

`dts/1` is the LIRA discipline for TypeScript declaration files. It atomizes the declaration
surface a `.d.ts` file publishes — interfaces, classes, type aliases, enums, functions and
variables, with their members — applying the folding principle of the LIRA specification (§10.3)
so that TypeScript's compatibility rules are encoded in the decomposition itself.

It is named for the carrier and not for a language (LIRA §11.1). A `.d.ts` file is what a
TypeScript consumer compiles against, whatever produced the JavaScript beneath it: hand-written
JavaScript with declarations added, TypeScript sources, or a compiler targeting neither. Without
it, such content falls to `opaque/1`, where the whole file is a single rigid atom over its bytes
and every rebuild is a major event.

The reference implementation is the `xenophile` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline (`dts/2`),
never a revision of this one (LIRA §11.1).

## 2. Scope and Guarantee

The discipline certifies **recompilation**: within a lineage, a consumer's sources still compile
against every later release.

It does **not** certify linkage, and MUST NOT be read as doing so. TypeScript declarations are
erased before anything runs; there is no late linking for them to protect, so certifying linkage
would be a claim about a mechanism that does not exist (LIRA §11.2 requirement 7). Whether the
JavaScript beneath the declarations still exports what it claims to is a separate question, and
one this discipline is silent on.

## 3. Domain

The domain is **every universe**.

This is a consequence of the universe vocabulary, not a claim about JavaScript. LIRA §9.4 defines
`jvm`, `sjsir` and `nir` in the base schema and reserves `js` for a schema layer that does not
yet exist, while LIRA §11.3 already admits foreign JavaScript content in any section under
`opaque/1`. There is consequently nothing narrower to scope to that would not exclude the
discipline from every release that can be written today.

Universality has a second effect, and it is the desirable one: it brings `.d.ts` content under
the cross-section API invariant (LIRA §9.6), so a release offering several universes must publish
the *same* declared TypeScript surface in each. A surface that differed per universe would be a
defect rather than a feature.

A future `js` universe SHOULD narrow this domain, in a `dts/2`.

## 4. Content Claiming

The discipline claims `**/*.d.ts`, atomized. It claims nothing else — in particular it does not
claim `.js`, `.mjs` or `.ts`, which fall to `resource/1` where declared and to `opaque/1`
otherwise.

## 5. Extraction

Atomization is performed over the **parsed declarations** of the file, never over its text. The
following never enter the model: comments, whitespace, declaration order at the top level, the
quoting style of string literals, and tuple element labels (which TypeScript treats as
documentation and which never distinguish two types).

**A construct outside the parser's vocabulary is a hard atomization error.** This is the same
rule `tasty.md` §7 applies to type constructors, and for the same reason: a declaration file is a
contract, and a parser that silently skips what it does not recognise reports a *smaller*
contract than the file declares, which would make every claim computed from it unsound.
Conditional types, mapped types, template literal types, `infer` binders, assertion signatures,
`unique symbol` and decorators are outside the vocabulary of `dts/1` and MUST be rejected.

## 6. Visibility

A declaration is API iff a consumer of the module can name it:

- in a **module** — a file containing any top-level `import` or `export` — only exported
  declarations;
- in a **global script** — a file containing neither — every top-level declaration;
- within an **ambient namespace**, every declaration, since an ambient namespace has no notion of
  a private member. Its contents are as reachable as the namespace itself and no more: an
  unexported namespace exports nothing, however its members are written.

`private` class members produce no atoms. `protected` members do: a subclass may name them.

Index, call and construct signatures are members like any other and produce atoms; they are
distinguished from named members by their selectors (§7).

## 7. Keys

Keying is by **declaration** (LIRA §11.2 requirement 4). A TypeScript reference names the
declaring interface, class or alias, and structural assignability is checked against that
declaration rather than resolved through a receiver at a call site. Since this discipline
certifies recompilation only, declaration keying is sound.

Keys are:

- a declaration: its namespace-qualified name, e.g. `a.b.Client`. Two declarations of the same
  name in different namespaces are different contracts;
- a member: `<declaration>#<selector>`, where the selector is the member's name for a property or
  method, `get <name>` or `set <name>` for an accessor, `()` for a call signature, `new()` for a
  construct signature, and `[]` for an index signature.

Accessors carry distinct selectors from a property of the same name because they are distinct
contracts: a type with a getter and no setter is not assignable where a mutable property is
required.

## 8. Canonical Encoding

Atom values are hashes over a deterministic tag-length-value encoding, on the same principles as
`tasty.md` §7: unsigned LEB128 lengths, length-prefixed UTF-8 strings, single-character
constructor tags.

Three rules govern the ordering of folded lists:

- **Type parameters encode as de Bruijn indices** — a (depth, position) pair against the
  enclosing binder scopes — so a binder's *name* never enters a hash. Renaming `T` to `U`
  throughout a declaration changes nothing a consumer can observe and must therefore change no
  atom.
- **Unions and intersections sort**, by their members' encoded bytes. `A | B` and `B | A` are the
  same type. Sorting by encoded bytes rather than by rendered text keeps the order a property of
  the structure.
- **Everything else keeps declaration order**, because that order is semantic: tuple elements are
  positional; heritage clauses decide how conflicting inherited members resolve; overload
  signatures decide which signature a given call selects.

The type-constructor vocabulary covers: named types with arguments; string, numeric and boolean
literal types; unions; intersections; tuples; arrays; inline object types; `keyof`; `typeof`;
indexed access; type predicates; and function and constructor types with their own type
parameters. A construct outside it is an error (§5).

Within a function or constructor type, parameter **names** are not folded — TypeScript call sites
are positional — while optionality and rest-ness are, since both decide which calls are legal. A
parameter's default *value* is behaviour rather than contract and is not folded; the optionality
it confers is.

Within a type predicate `x is T`, the narrowed type `T` is folded and the parameter name is not:
which parameter is narrowed is positional.

## 9. Rigid Atoms: Members

One rigid atom per visible member of an interface, class or inline object type. The value folds,
in order: the member's selector; its kind; its visibility; its `static`, `readonly`, `optional`
and `abstract` flags; and its signature list, in declaration order.

## 10. Rigid Atoms: Declarations

One rigid atom per exported declaration. The value folds a kind tag and the declaration's key,
then per kind:

- **interface**: its type parameters (bounds and defaults), then its `extends` list in
  declaration order;
- **class**: its type parameters, its `abstract` flag, its superclass or an absence marker, then
  its `implements` list in declaration order;
- **type alias**: its type parameters, then its target type;
- **enum**: its `const` flag, then its members in declaration order with their explicit values
  where given. The `const` flag folds because a `const enum` is inlined into consumers at *their*
  compile time, which is a materially stronger commitment than an ordinary enum's, and a change
  of kind must never be invisible;
- **function**: its signature list, in declaration order;
- **variable**: its `const` flag, then its type or an absence marker.

Every declaration's atom then folds, finally, **the sorted selector list of its members**.

## 11. Why Members Fold Twice

§9 makes each member an atom of its own and §10 folds the member key list into the enclosing
declaration's atom. The redundancy is deliberate, and it is the central rule of this discipline.

Adding a member to an interface is two events at once:

- for a consumer who **calls** the interface it is pure extension, and cannot break anything;
- for a consumer who **implements** it, it is a break — and TypeScript's structural typing means
  anyone may implement an interface without declaring an intention to, so implementors cannot be
  enumerated or opted out of.

The member's own atom records the first: it is a new rigid atom, which is a minor event. The fold
in §10 records the second: the enclosing declaration's atom value changes, which is a major
event. The grade reports the major, which is correct, because the discipline must not certify a
compatibility it cannot deliver to every consumer.

This is the shape of rule 5 of `tasty.md` §8, where an open template folds its abstract members
for exactly this reason. The difference is that in TypeScript *every* interface is open in that
sense, so the fold is unconditional.

## 12. Replaceable Atoms

None. `dts/1` emits only rigid atoms.

Replaceability exists for content copied into consumers at their compile time (LIRA §10.2), and
the natural candidate here is the `const enum`, whose values are inlined. It is nonetheless rigid
in `dts/1`: a `const enum`'s member *set* and its values are indistinguishable at the point where
a consumer depends on them, and treating a value change as replaceable churn would permit a minor
release to change a constant a consumer had already inlined into a comparison. A future
discipline MAY separate the two.

## 13. Reference Lists

Empty, since §12 emits no replaceable atoms.

## 14. Determinism

Two parses of identical sources MUST yield identical atom sets (LIRA §17). The encoding depends
on no property of the parse that the source does not determine: no positions, no interning, no
declaration order above the level §8 names as semantic.
