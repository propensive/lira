# The C Header Discipline `cheader/1` — Specification Draft

## Abstract

`cheader/1` is the LIRA discipline for C header files used as **host-contract carriers**: the
declared surface of a shared library the environment supplies — `libcrypto`, a system libc, any
`dlopen`ed dependency — published as a host contract whose atoms are the declarations a
consumer's FFI resolves against.

It exists because FFI is a host assumption with no other home. A library that calls
`EVP_DigestInit_ex` through Panama or `dlsym` assumes an environment providing it, exactly as a
shell-out assumes `git`; `capability/1` could name the shared library as one coarse capability,
but where a header exists the contract has a formal carrier, and atomizing it makes "needs
`libcrypto` ≥ 3" a per-symbol, set-inclusion fact (hosts.md §7) rather than a coarse pin.

The reference implementation is the `xenophile` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline
(`cheader/2`), never a revision of this one (LIRA §11.1).

## 2. Scope and Guarantee

The discipline certifies **recompilation** — a consumer's FFI bindings still compile and
resolve by name against every later release of the contract — and symbol **presence** on the
terms of `capability/1` (hosts.md §5). It does not certify ABI compatibility beyond what the
declared prototypes express: calling conventions, struct layout padding and target-triple
concerns are outside the carrier. The runtime check is the third verification moment
(hosts.md §9): `dlopen`/`dlsym` is the probe.

## 3. Domain and Content Claiming

The domain is the single world `{host}`. A release carrying `cheader/1` is a host contract
(LIRA L135), and the cross-section invariant (LIRA §9.6) is vacuous.

The discipline claims `**/*.h`, atomized. It claims nothing else.

## 4. Extraction

Atomization is performed over the parsed declarations of the claimed headers, never over their
text: comments, whitespace, declaration order and preprocessor structure never enter the model.
The parser reads declarations, not code: function prototypes, typedefs, and the type vocabulary
they mention.

**A construct outside the parser's vocabulary is a hard atomization error** (the rule of
`tasty.md` §7): a partially-read header would understate the contract.

## 5. Keys

Keying is by **declaration**. C has one flat namespace, so keys are bare names:

- a function: its name;
- a typedef, struct, union or enum: its name (tagged types under their tag).

## 6. Atoms and Folding

All atoms are **rigid**; there are no replaceable atoms and no reference lists.

- **Functions are standalone atoms.** The value folds the canonically-encoded parameter types,
  variadicity, and return type. Parameter *names* are not folded — C calls are positional and
  header parameter names are documentation.
- **A typedef's atom** folds its target type. **Struct and union atoms** fold their field
  lists in declaration order (layout is positional), fields as (name, type) pairs; a struct
  declared opaquely (tag without definition) folds an opacity marker instead, and completing it
  later is a value change. **Enum atoms** fold their enumerator names and values in declaration
  order.
- Object-like macros and enumerators consumed as constants are not atomized in version 1: the
  preprocessor is not part of the parsed vocabulary, and a contract whose surface depends on
  macros should carry them as typedefs or enums, or fall back to `capability/1` rows.

Adding a declaration is a minor; removing or changing one is major — by the algebra.

## 7. Canonical Encoding

Atom values are hashes over a deterministic tag-length-value encoding on the same principles as
`tasty.md` §7: unsigned LEB128 lengths, length-prefixed UTF-8 strings, single-character
constructor tags. The type vocabulary covers the builtin arithmetic types (canonicalized to
their exact spelling after sign/length normalization), pointers, arrays, function pointers, and
named references to typedefs and tagged types; qualifiers that do not affect a caller (`const`
on a by-value parameter) are not folded, while pointee constness is.

## 8. Determinism

Two parses of identical sources MUST yield identical atom sets (LIRA §17). Header inclusion
order cannot matter: the atom set is a union with duplicate keys forbidden, and a redeclaration
identical under canonicalization is idempotent.

## 9. Prior Art (Informative)

Every distribution's `.so` symbol-versioning policy and every `pkg-config` version constraint
is an informal instance of what this discipline formalizes. The motivating consumer is
Soundness's OpenSSL binding, whose curated `openssl.h` header is exactly a host contract in
waiting: its atoms are the symbols the binding names, its lineage is OpenSSL's additive
history, and a consumer's Uses blob against it names the handful of EVP functions it actually
calls.
