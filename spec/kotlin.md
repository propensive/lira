# The Kotlin Metadata Discipline `kotlin-metadata/1` — Specification Draft

## Abstract

`kotlin-metadata/1` is the LIRA discipline for the Kotlin declaration surface carried by the
`@Metadata` annotation on JVM classfiles. It atomizes what `classfile/1` cannot see: the
Kotlin-level contract — nullability, properties as properties, default-parameter existence,
suspend coloring — that Kotlin consumers, and foreign-function layers reading Kotlin metadata,
actually depend on.

It exists because the metadata is real API that no other discipline covers. A dependency
flipping a return type from `T` to `T?` changes no descriptor and no TASTy — `classfile/1` and
`tasty/1` are both blind to it — yet every consumer reading the metadata sees a different
contract. Registering this discipline makes that change a major, which it is.

It is named for the carrier, not the language (LIRA §11.1): the `@Metadata` annotation is the
interface convention, and one this discipline reads via the same published `kotlin-metadata`
library the Kotlin toolchain uses. The klib carrier of Kotlin multiplatform is a different
carrier and would be a sibling discipline.

The reference implementation is the `xenophile` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline
(`kotlin-metadata/2`), never a revision of this one (LIRA §11.1).

## 2. Scope and Guarantee

The discipline certifies **recompilation** for consumers compiling against the metadata. It
does not certify JVM linkage — that is `classfile/1`'s or the `jvm/1` profile's, over the same
classfiles — and the two are deliberately separable: a Kotlin library SHOULD declare this
discipline for its metadata surface and the `jvm/1` profile for its bytecode surface, the exact
split of LIRA §11.6.

## 3. Domain and Content Claiming

The domain is `{jvm, host}`: the metadata rides in JVM classfiles, and the `host` inclusion
admits contracts carried as API-stub classfiles — the anticipated Android surface
(hosts.md §3) — where the stubs carry Kotlin metadata.

The discipline claims `**/*.class` files **whose class carries a `@Metadata` annotation**,
atomized; a classfile without one is not claimed and falls through — to `classfile/1` where
registered, else to `opaque/1`. Facade and multi-file-part classes are claimed and atomized
under the facade the metadata declares. **Claiming order is load-bearing** (LIRA §11.2,
`classfile.md` §4): a registry listing `classfile/1` before this discipline leaves it nothing
to claim, so a release registering both MUST list `kotlin-metadata/1` first.

## 4. Extraction

Atomization reads the metadata's declaration model — via the published metadata library, whose
version travels in `toolchain` — and never the bytecode: `Code`, synthetic bridges and
`$default` machinery are invisible here (they are `classfile/1`'s subject). `private`,
`internal` and local declarations never enter the model: neither is nameable by a consumer
outside the module.

**Metadata the library cannot read is a hard atomization error**: an unreadable contract must
fail at the producer, not understate at the consumer.

## 5. Keys

Keying is by **membership**, as `classfile.md` §6: a Kotlin call site resolves members through
the receiver after inheritance, so the contract surface of a type includes what it presents.
The presented set is walked through the **Kotlin** supertype closure — the supertypes that
themselves carry metadata. A supertype carrying none contributes nothing here: a Java
superclass's surface belongs to a classfile-level discipline, and the virtual builtins
(`kotlin.Any` and the mapped types) have no metadata carrier at all.

Keys are:

- a class, interface, object or companion: its Kotlin fully-qualified name;
- a function: `<presenting type>#<name>(<parameter types>)`, parameter types in Kotlin
  spelling with nullability marks;
- a property: `<presenting type>.<name>`;
- a type alias: `<container>.<name>`;
- top-level functions and properties: keyed under their JVM facade class.

## 6. Atoms and Folding

- **Functions and properties are standalone rigid atoms.** A function's value folds its
  presenting type (the membership-keying obligation of `classfile.md` §10 item 2), modifiers
  (`suspend`, `operator`, `infix`, `inline`'s *existence*, visibility), full Kotlin signature —
  parameter names (named-argument surface), parameter types with nullability, default-value
  *existence* per parameter, receiver type, return type with nullability — and variance where
  generic. A property's folds mutability (`val`/`var`), its type with nullability, and receiver.
- **`suspend` folds**: adding or removing it is a signature change, hence major.
- **A class's own atom** folds its kind, modality (`open`/`final`/`abstract`/`sealed`),
  variance and bounds of type parameters, supertypes in declaration order, and — iff the class
  is open or abstract — the sorted key list of its abstract members (the rule of `tasty.md` §8
  rule 5); iff sealed, its sorted subclass list (exhaustive `when`); iff an enum class, its
  entry list in declaration order (ordinals are surface).
- **Data classes**: constructor parameters fold into the class's atom — `copy`'s signature
  changes when any is added, so the addition is correctly major (the
  binary-compatibility-validator rule).
- **Replaceable atoms: none in version 1.** The natural candidate is the `inline` function's
  body, which is compiled into consumers — but the `@Metadata` carrier holds declarations, not
  bodies, and a replaceable atom whose value cannot be computed from the claimed content would
  violate LIRA §11.2's purity requirement. The inline flag's *existence* folds into the rigid
  signature atom, so gaining or losing inline-ness registers; body churn is invisible at this
  level, exactly as classfile-level bridge churn is invisible to `tasty/1`, and a
  `kotlin-metadata/2` MAY add body tracking over a carrier that has one.

## 7. Canonical Encoding

Atom values are hashes over a deterministic tag-length-value encoding on the same principles as
`tasty.md` §7: unsigned LEB128 lengths, length-prefixed UTF-8 strings, single-character tags;
type references fully qualified in Kotlin spelling with nullability and platform-type marks;
type parameters as de Bruijn indices; folded member-key lists sorted, declaration-order lists
(supertypes, enum entries, constructor parameters) kept.

## 8. Determinism

Two compilations of identical sources by identical toolchains MUST yield identical atom sets
(LIRA §17). The metadata's own stability is the carrier's promise; where the metadata library's
reading of one blob could differ between its versions, the `toolchain` record arbitrates and an
ecosystem profile may impose coherence (LIRA §13.3).

## 9. Prior Art (Informative)

JetBrains' binary-compatibility-validator is the rule table: its `.api` dump files are an atom
listing in textual form, and its change classification is this discipline's folding,
transcribed. The motivating consumer inside Soundness is the Kotlin foreign-function layer,
whose facades read exactly the surface this discipline atomizes — nullability driving
`Optional` results, default-parameter existence driving call synthesis — so a Kotlin dependency
governed by this discipline can no longer change that surface invisibly.
