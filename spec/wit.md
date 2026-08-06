# The WIT Discipline `wit/1` — Specification Draft

## Abstract

`wit/1` is the LIRA discipline for WIT (WebAssembly Interface Types), the interface carrier of
the WebAssembly Component Model. It atomizes the worlds, interfaces, functions, resources and
types a WIT package declares, applying the folding principle of the LIRA specification (§10.3).

WIT serves two roles, and the discipline serves both. A **WASI world** is a host contract —
"a WASM component assumes a WASI world" is the canonical host assumption of
[`hosts.md`](hosts.md) — and a contract carried as WIT is atomized by this discipline exactly
as a Node contract carried as `.d.ts` is atomized by `dts/1`. And in the `component` universe,
where library components compose by WIT interfaces, the same atomization is the natural
compatibility carrier for libraries; that universe is reserved but not yet defined in the base
schema (LIRA §9.4), so the library role becomes exercisable only when its schema layer lands.

The reference implementation is the `xenophile` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline (`wit/2`),
never a revision of this one (LIRA §11.1).

## 2. Scope and Guarantee

The discipline certifies **recompilation**: consumers' bindings still generate and their
sources still compile against every later release of the contract or library. Component-level
linkage (whether a composed component instantiates against an import) follows the same shape —
the component model links by name and type — but this discipline does not certify it in
version 1: the canonical encoding covers the declared interface, not the canonical ABI's
lowering of it.

Presence of an interface or function in a host contract is certified on the terms of
`capability/1` (hosts.md §5); the runtime check is the third verification moment (hosts.md §9).

## 3. Domain and Content Claiming

The domain is `{host, component}`: the `host` world for WASI-world contracts, and the reserved
`component` universe for library components when its schema layer arrives. Until then every
release carrying this discipline is in practice a host contract, and the cross-section
invariant (LIRA §9.6) is vacuous.

The discipline claims `**/*.wit`, atomized. It claims nothing else.

## 4. Extraction

Atomization is performed over the parsed model of the claimed `.wit` files — packages,
interfaces, worlds and their contents — never over the text: comments, whitespace and
declaration order at the top level never enter the model.

**A construct outside the parser's vocabulary is a hard atomization error** (the rule of
`tasty.md` §7, for the same reason). Two gates are treated distinctly: `@since` is consumed and
ignored — it documents when a stable item arrived, which the lineage already records — while
`@unstable` is a hard error, since an unstable item published in a contract would be a stable
claim about an unstable surface.

## 5. Keys

Keying is by **declaration**. Names are qualified by their package and container, in WIT's own
spelling:

- an interface or world: `<namespace>:<package>/<name>`, with the package version where the
  package declares one, e.g. `wasi:clocks/monotonic-clock@0.2.0`;
- a function: `<interface>#<name>`; a resource method `<interface>#<resource>.<name>`, with
  constructors and static functions spelled as WIT spells them;
- a type (record, variant, enum, flags, resource, type alias): `<interface>#<name>`;
- a world's import or export: `<world>#import <name>` or `<world>#export <name>`.

The package version is part of the key deliberately: WASI versions its worlds by exact package
version, and `monotonic-clock@0.2.0` and `@0.3.0` are different contracts by upstream
convention. Lineage relates them (a contract release carrying both is a superset; one dropping
`@0.2.0` is a major); the keys never alias them.

## 6. Atoms and Folding

All atoms are **rigid**; there are no replaceable atoms and no reference lists. Folding follows
what the component model's own evolution rules make safe:

- **Functions are standalone atoms.** The value folds the canonically-encoded parameter list
  (names and types — WIT calls are by named parameter) and result type. Any signature change is
  removal-plus-addition: major.
- **An interface's own atom** folds its name and the sorted key list of its **type**
  declarations — not its functions: adding a function to an interface is additive for callers,
  and nothing implements a host interface from outside. Its use-clauses are transparent:
  references are encoded fully qualified, so re-exports and renames carry no semantic content.
- **A world's imports are standalone atoms**, keyed `<world>#import <name>`: an import is a
  capability the host supplies to components targeting the world, so a world gaining one is a
  minor — the polarity of hosts.md §2, where a host gaining a capability is the additive
  direction. **Its exports fold**, as a sorted key list, into the world's own atom: an export
  is an obligation on every component targeting the world, so adding one is breaking for them —
  the required-dictionary-member rule of `webidl.md` §6, transposed.
- **Record fields fold** into the record's atom, in declaration order (the canonical ABI is
  positional). **Variant cases and enum cases fold** in declaration order (case indices are
  ABI). **Flags fold** likewise. Adding a field or case is therefore major — the conservative
  side, which is correct for version 1 because the canonical ABI is positional and gated
  evolution (`@since`) is not yet modelled. A future `wit/2` SHOULD read `@since`/`@unstable`
  gates and unfold what they license, exactly as Rust's `#[non_exhaustive]` licenses unfolding
  (LIRA §10.3).
- **A resource's atom** folds its name; its methods are standalone atoms like functions.

## 7. Canonical Encoding

Atom values are hashes over a deterministic tag-length-value encoding on the same principles as
`tasty.md` §7: unsigned LEB128 lengths, length-prefixed UTF-8 strings, single-character
constructor tags. Every named type reference is encoded fully qualified (package, interface,
name, version); the primitive vocabulary is WIT's own (`bool`, the fixed-width integers and
floats, `char`, `string`) plus the constructors `list`, `option`, `result`, `tuple`, `own`,
`borrow`, and named references. Lists keep declaration order except where §6 names a sort.

## 8. Determinism

Two parses of identical sources MUST yield identical atom sets (LIRA §17). File order cannot
matter: the atom set is a union with duplicate keys forbidden, and package membership is
declared, not positional.

## 9. Prior Art (Informative)

WASI's own world versioning — exact package versions, additive evolution behind `@since` gates
— is the rule table this discipline transcribes. The anticipated `wasi` host contract
(hosts.md §11) carries the `wasi:*` packages of one WASI release, atomized by this discipline;
a library's `requires` on it, with a Uses blob naming only the interfaces it imports, makes
"runs on any WASI 0.2 host" a set-inclusion fact rather than a README sentence.
