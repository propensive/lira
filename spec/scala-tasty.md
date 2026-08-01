# The Scala Discipline `scala-tasty/1` — Specification Draft

## Abstract

`scala-tasty/1` is the LIRA discipline for Scala libraries. It atomizes a module's public API
from TASTy — the interface carrier shared by the `jvm`, `sjsir` and `nir` universes — applying
the folding principle of the LIRA specification (§10.3) so that Scala's compatibility rules are
encoded in the decomposition itself: adding anything consumers can safely gain is a standalone
atom; adding anything that can break consumers changes an enclosing atom's value. Everything
above the atom level remains the language-blind algebra of the base specification.

The reference implementation is the `degustation` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline
(`scala-tasty/2`), never a revision of this one (LIRA §11.1).

## 2. Scope and Guarantee

The discipline certifies **recompilation and TASTy-level linkage**: within a lineage, consumers
compiled against release *A* recompile and link against every later release, and content copied
into consumers at their compile time (inline expansion) is tracked by replaceable atoms so that
staleness is computable. Classfile-level invariants (bridges, forwarders and other
binary-compatibility concerns of the JVM's late linking) are the province of a JVM ecosystem
profile with bytecode-level checks, and are out of scope here.

## 3. Content Claiming

The discipline claims:

- `**/*.tasty` — **atomized**: the API surface is read from these files;
- `**/*.class`, `**/*.sjsir`, `**/*.nir` — **atomless** (LIRA §11.2): these are derived
  representations whose interface is exactly the TASTy's. They contribute no atoms, so
  implementation churn in derived binaries never registers as API change — and they never fall
  through to `opaque/1`, where every rebuild would be a major event.

All other content falls to whatever discipline claims it — declared resources to `resource/1`
(LIRA §11.4) — or to `opaque/1`.

## 4. Extraction

Atomization is performed over the **semantic model** of TASTy as unpickled by the Scala
compiler itself (via the `tasty-inspector` interface), never over raw TASTy bytes. Extraction
therefore requires the module's dependency classpath — which the buildpath supplies at assembly
and publish time, the only times atomization runs (LIRA §16, step 4). The compiler used is
recorded in the manifest's `toolchain` (LIRA §14); the TASTy version window it accepts is the
compiler's own.

The following never enter the model: positions, comments, source-file attributes, tool version
strings, pickled UUIDs, and the compiler's tree- and type-sharing structure.

## 5. Visibility

Excluded from the API (producing no atoms and folding into nothing):

- `private` and `private[this]` members;
- local definitions (anything inside a term body — atomized only as part of a replaceable
  body's encoding, §12);
- compiler-internal artifacts (local dummies and artifact-flagged symbols).

Included:

- `protected` members (consumers extend open classes);
- qualified-private (`private[scope]`) members, conservatively. (A refinement is anticipated:
  where `scope` lies within the module's own `owns` namespaces, namespace disjointness — LIRA
  L112 — guarantees no consumer can ever be inside the scope, so such members could be
  excluded. `scala-tasty/1` includes them; a future discipline version may tighten this.)
- synthetic members that are real API: case-class and enum companions' generated members
  (`apply`, `copy`, `unapply`, `fromProduct`, ordinals and friends) are atomized as ordinary
  members, which makes constructor-parameter changes ripple into member removals-plus-additions
  — correctly major — with no bespoke modeling; `inline$` accessors and retained-body methods
  likewise (signatures only).

## 6. Keys

Every atom's key is stable text:

- **Template atoms**: the fully-qualified name of the class, trait, object or enum.
- **Term members**: `<fq-owner>.<name>(<paramSigs>)<resultSig>`, where the parenthesized
  disambiguator is the member's **erased signature** as the compiler computes it — the same
  spelling consumers' TASTy references carry in their `SIGNED` names, so overloads have
  distinct, linkage-aligned keys.
- **Type members**: `<fq-owner>.<name>`.
- **Top-level definitions**: keyed by *package* (`<package>.<name><sig>`); the synthetic
  `<file>$package` carrier object is folded into the atom's value (§8), so moving a top-level
  definition between files is a value change — honest, since consumer TASTy references the
  carrier — while keys stay clean.
- **Replaceable atoms**: the corresponding rigid key suffixed `[inline]`.

Key text is what reference lists name (§13) and what Atoms listings display; it participates in
no hash.

## 7. Canonical Encoding

Atom values are hashes over a bespoke, deterministic tag-length-value encoding. Its general
principles: every non-local symbol reference is the symbol's fully-qualified name; binder-local
type parameters encode as de Bruijn (depth, index) pairs, so binder names vanish; all
variable-length data is length-prefixed (unsigned LEB128); strings are UTF-8; folded member
lists sort by ascending UTF-8 key bytes, while parents and sealed/enum child lists keep
declaration order (which is semantic: linearization and ordinals).

The type-constructor vocabulary covers: named type and term references; applied types; and/or
types; by-name types; annotated types (denylisted annotations transparent, §10); constant
types; type lambdas, method types and poly types (parameters de Bruijn-indexed); parameter,
`this`, super and recursive references; refinements; type bounds; match types and cases;
flexible types (transparent). **A construct outside this vocabulary is a hard atomization
error**: toolchain vocabulary drift is detected, never silently absorbed.

Modifier flags fold as a fixed-order bit set: abstract, case, deferred, enum, erased, exported,
final, given, implicit, infix, inline, lazy, macro, module, mutable, opaque, open, protected,
sealed, trait, transparent.

## 8. Rigid Atoms: Templates

One rigid atom per class, trait, object or enum (carrier objects excepted, §6). The value
folds, in order:

1. the modifier bit set;
2. type parameters, positionally: variance and canonical bounds (names alpha-normalized away);
3. parents, in declaration order, as canonical types (constructor arguments excluded);
4. the declared self type, when present;
5. **iff the template is open** (not final, not sealed, not an object): the sorted key list of
   its abstract members — so adding an abstract member to an open template changes the
   template's atom (major), while on sealed or final templates the same addition is pure
   extension (minor), the in-module implementors changing their own atoms;
6. **iff sealed or an enum**: the child list, in declaration order — exhaustivity and ordinals
   are consumer surface;
7. retained annotations (§10), sorted by annotation class name.

Abstract members are *also* standalone rigid atoms (they are callable surface); the fold in (5)
is what makes their *addition* major exactly when it must be.

## 9. Rigid Atoms: Members

One rigid atom per concrete or abstract term member — each overload standalone — and per type
member. A term member's value folds: member kind; the modifier bits (including the *existence*
of inline/macro nature — the body is a separate replaceable atom); parameter names per clause
(named-argument surface); the full declared type, canonically encoded; and retained
annotations. Default-argument *existence* travels through the default-getter members, which are
ordinary atomized members whose bodies are excluded (defaults resolve in the callee). The
`exported` flag is stripped: an `export` forwarder atomizes identically to the equivalent
hand-written forwarder, so converting between them is a non-event.

A type member's value folds its modifier bits and its full info — bounds for abstract types,
the right-hand side for aliases, and for opaque types the underlying alias as the compiler
presents it: opacity does not hide the underlying from erasure or from same-scope inline
bodies, so changing it is correctly a major event.

## 10. Annotations

Annotations fold into their carrier's value by **denylist**: since macros and derivation can
read any retained annotation from dependency TASTy, an allowlist would under-protect, while the
denylist direction only ever produces spurious majors. Denylisted (never folded):

- diagnostic and lint annotations: `deprecated`, `deprecatedInheritance`,
  `deprecatedOverriding`, `deprecatedName`, `implicitNotFound`, `implicitAmbiguous`,
  `migration`, `nowarn`, `unused`;
- compiler-internal annotation namespaces (`scala.annotation.internal.*`,
  `scala.annotation.unchecked.*`);
- cross-universe interop namespaces (`scala.scalajs.js.annotation.*`,
  `scala.scalanative.unsafe.*`) — these are API to *foreign* callers, not to Scala consumers,
  and folding them would spuriously break the cross-universe invariant (§14).

## 11. Replaceable Atoms

Each `inline` (including `transparent inline`) and macro definition yields, in addition to its
rigid signature atom, one **replaceable** atom keyed `<rigid key>[inline]`, whose value is the
canonical encoding of the definition's body — the content consumers copy at their compile time.
Replacing it (same key, new value) within a lineage is a minor event that leaves compiled
consumers behaviorally stale but never broken: the compiler guarantees everything an inline
body reaches is public or accessor-wrapped, all of which is rigid-atomized (LIRA §11.2).

The body encoding is a total fold over the term-tree vocabulary — applications, selections,
blocks, conditionals, matches (including inline matches and `summonFrom`), patterns, closures,
try/finally, assignments, local definitions, named and repeated arguments, quoted and spliced
trees (as ordinary applications of the quotation runtime) — with local symbols alpha-normalized
to traversal-order indices and every outward reference spelled as its fully-qualified name and
erased signature. As with types, an unknown tree form is a hard error.

## 12. Reference Lists

While encoding a replaceable body, the discipline collects every reference to a non-local term
or type: the input to used-set closure (LIRA §13.4). References are **symbolic keys**, not
hashes — a cross-module value hash is not computable from one module's content — and are
deduplicated and sorted. A reference to a member that is itself inline also includes the
member's `[inline]` key, closing the used-set over nested inlining with no further analysis.

After atomization, references whose keys belong to this module's own atom set are classified
**own**; the rest are **foreign**, resolved at assembly time against the dependencies' Atoms
listings by exact key match — exact, because both sides spell keys with the same
erased-signature disambiguators.

## 13. Cross-Universe Policy

The atomization of each universe's materialized `.tasty` set (with that universe's classpath)
MUST be identical as (key, class, value hash) — LIRA's L108. Universes MAY differ in:

- implementation, including byte-divergent `.tasty` files (a fresh compiler run pickles fresh
  UUIDs and tool strings, none of which enter the model);
- platform-split source files, provided the API they present is identical;
- denylisted interop annotations (§10).

In nothing else. A library whose API genuinely differs by platform is two modules.

## 14. Determinism

Atomization is a pure function of the semantic model: folded lists are sorted; locals and
binders are index-normalized; file order cannot matter (the atom set is a union with duplicate
keys forbidden); no timestamp, position, tool string, UUID or sharing choice is read. The
strict vocabularies of §7 and §11 turn compiler-evolution surprises into errors at the
producer, where they are curable, rather than silent hash drift at consumers.

Across compiler releases, the discipline's promise is that any two toolchains implementing
`scala-tasty/1` produce identical atoms for the same semantic model. A compiler change that
would alter canonical output for any input requires a new discipline version; a change that is
canonically invisible does not. The manifest's `toolchain` records which compiler produced a
release; ecosystem profiles may impose coherence over it (LIRA §13.3).

## 15. Worked Example

For the source

```scala
package fixture

sealed trait Choice
case class Alpha(x: Int) extends Choice

class Overloads:
  def f(x: Int): Int = x
  def f(x: String): String = x

inline def double(n: Int): Int = n * 2
```

the discipline yields (keys abridged): a rigid template atom `fixture.Choice` folding the
sealed child list `[fixture.Alpha]`; rigid atoms for `Alpha`, its constructor, `apply`,
`copy`, `unapply` and accessors; two rigid atoms `fixture.Overloads.f(scala.Int)scala.Int` and
`fixture.Overloads.f(java.lang.String)java.lang.String`; a rigid atom
`fixture.double(scala.Int)scala.Int`; and a replaceable atom
`fixture.double(scala.Int)scala.Int[inline]` whose references include the foreign key
`scala.Int.*(scala.Int)scala.Int`. Adding a `case class Beta` changes `fixture.Choice`'s value
(major); adding a third overload adds one atom (minor); changing `double`'s body to `n + n`
changes only the `[inline]` atom's value (minor, with staleness computable).
