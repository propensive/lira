# Compatibility Across Languages: Discipline Designs

LIRA's compatibility algebra (spec §10–§12) is language-blind above the atom level: rigid atoms
are monotonic within a lineage, replaceable atoms may be replaced, "compatible extension" is
the subset relation, and buildpath validation is set arithmetic on hashes. Everything
language-specific lives in one place — the **discipline**, which decides what an atom is,
applying the folding principle (§10.3): *safe additions become standalone atoms; breaking
additions fold into their parent's atom.*

This document works through the discipline design for each focus language: Scala, Kotlin,
TypeScript, Rust, and (more briefly) Java and JavaScript. The recurring pattern: every
language ecosystem already has a tool that encodes its compatibility rule table — the
discipline is that rule table, recast as an atomization.

## 1. What "compatible" means — three guarantees, per language

A discipline's rigid atoms certify different things depending on the universe's linkage model.
It is worth naming the three guarantee levels once:

- **Linkage**: already-compiled consumers continue to load/link (JVM classloading, native
  symbol resolution). Meaningful where linking is late and by-name.
- **Recompilation**: consumers' sources still compile (and inline/generic material re-expands)
  against the new release. Meaningful everywhere; the *only* meaningful level for TypeScript
  and for monomorphizing Rust.
- **Behavior**: out of scope for any hash scheme; bounded but not certified (spec §18).

Each discipline below states which guarantee its rigid atoms certify. The algebra is
indifferent — but publishers and consumers must know what a "minor" promises in each world.

These three levels are now normative in the spec (§11.5), as is the mechanism for a guarantee a
discipline cannot itself certify: an **ecosystem profile** (§11.6), a versioned predicate set
checked outside the atom algebra, whose shortfalls a release records as `breaks <level>`
(§12.4). The rule of thumb for which mechanism to use: a claim belongs in atoms if it should
change the release's API identity, and in a profile if it should not. Bytecode-level checks
belong in a profile, because a bridge moving should not change what a recompiling consumer
depends on.

## 2. Scala — `tasty` (spec Appendix A; normative spec: `tasty.md`)

- **Interface carrier**: TASTy (shared across `jvm`, `sjsir`, `nir` universes — hence one
  discipline and the cross-section invariant, spec §9.6).
- **Guarantee**: recompilation and TASTy-level linkage. Classfile-level linkage is the `jvm/1`
  profile's business, not the discipline's (spec §11.3, Appendix D), and the two diverge in both
  directions — a trait gaining a concrete method is a clean minor that still perturbs every
  subclass's mixin forwarders, and an erasure-invisible bound change is a major that no classfile
  would notice.
- **Keying**: by declaration (spec §11.2, A.3; `tasty.md` §6) — a member is atomized once, under
  its declaring owner, which is sound precisely because a TASTy reference names that owner.
  Membership keying would be required only for a discipline certifying classfile linkage, where
  call sites name the receiver — and that surface is scoped out to the profile.
- **Hierarchies**: parents/variance/bounds fold into the type's own atom, so losing a parent is a
  removal and therefore major, with no subtyping reasoning anywhere in the checker (A.1);
  hierarchies spanning modules are kept consistent by used-sets and buildpath validity, not by
  the discipline (A.2).
- **Rigid atoms**: concrete members; each overload (erased-signature key); default-argument
  *existence*; `inline$` accessors.
- **Folded** (addition = major): abstract members of open templates; sealed/enum child lists;
  parents, self-types, variance, bounds, opacity.
- **Replaceable**: `inline`/`transparent inline`/macro bodies, with reference lists for
  used-set closure.
- **Prior art**: TASTy-MiMa's problem taxonomy; tasty-query as extraction substrate.

## 3. Kotlin — `kotlin`

- **Interface carrier**: the `@Metadata` annotation on classfiles (read via kotlinx-metadata)
  for the `jvm` universe; klib metadata for `klib`. One discipline, two carriers — like Scala,
  Kotlin should present one API across universes.
- **Guarantee**: linkage + recompilation on `jvm`; recompilation on `klib`.
- **Rigid atoms**: functions/properties (JVM signature + Kotlin signature both fold into the
  value — nullability and generics live only in metadata but consumers depend on them);
  default-parameter existence (`$default` bridges are real linkage surface); data-class
  generated members (`componentN`, `copy` — note `copy`'s signature changes when *any*
  constructor parameter is added, so constructor parameters of data classes fold into the
  class atom: adding one is correctly major); enum entries fold (exhaustive `when`); sealed
  subclasses fold.
- **Replaceable**: `inline fun` bodies (Kotlin inlining is Scala-like, including `reified`
  generics — inline bodies splice into consumers; `PublishedApi` internals are the `inline$`
  accessor analogue and must be rigid).
- **Suspend coloring**: `suspend` is part of the signature (CPS-transformed with an extra
  `Continuation` parameter) — folds into the atom; adding/removing `suspend` is correctly a
  change, hence major.
- **Prior art**: JetBrains' binary-compatibility-validator — its `.api` dump files are
  precisely an atom listing in textual form; its rules are the folding table.

## 4. TypeScript — `dts`

- **Interface carrier**: `.d.ts` declarations for the `js` universe.
- **Guarantee**: **recompilation only.** There is no linkage to protect — JS resolves
  everything dynamically and "links" regardless; failures surface behaviorally. Rigid atoms
  certify that consumers' type-checking outcomes are preserved. This changes several folding
  decisions relative to nominal languages:
- **Structural typing consequences**: adding a member to an exported `interface` is *breaking*
  for consumers who implement or structurally satisfy it — interfaces consumers may implement
  behave like open traits: their member lists fold. But interfaces used only covariantly
  (return-position "views") tolerate additions. TypeScript cannot see usage direction from the
  declaration alone, so the discipline needs a policy: fold by default (sound), with a
  documented annotation escape (e.g. a `/** @lira sealed-consumer */`-style marker) to opt a
  type into addition-friendly atomization. This is the TS analogue of Rust's
  `#[non_exhaustive]` — an explicit marker changing folding.
- **Variance is the sharp edge**: widening a parameter type is safe (standalone/replaceable
  value change is NOT expressible — parameter and return types fold into the function atom;
  *any* signature change is major). Optional parameters and optional members are the safe
  additive currency: a new *optional* member on an options-bag interface is a standalone atom;
  a new *required* member folds.
- **Union/overload growth**: adding an overload can change inference for existing callers —
  overload lists fold into the function atom (stricter than Scala, where erased-signature
  keying lets overloads stand alone; TS overloads share one runtime function and inference is
  order-sensitive).
- **Toolchain axis**: `.d.ts` readability depends on TypeScript language version — recorded in
  `toolchain`, checked as profile coherence, exactly parallel to TASTy versions.
- **Prior art**: Microsoft's api-extractor (API report files ≈ atom listings);
  `typescript-eslint`'s and semver-ts's breaking-change catalogues.

## 5. Rust — `rmeta`

- **Interface carrier**: rmeta / source (the `crate` universe is source-distributing today);
  for the `native/<triple>` and `wasm-object` universes Rust participates via the C ABI, where
  the *C headers* are the interface and `opaque`/C-discipline rules apply instead.
- **Guarantee**: recompilation. Monomorphization means generic items behave like Scala
  `inline`: their bodies are compiled into consumers. Consequently the replaceable class is
  *large*: every generic function/impl body is a replaceable atom with reference lists; only
  non-generic, non-`#[inline]` items offer anything like linkage stability, and Rust-to-Rust
  distribution recompiles anyway.
- **The `#[non_exhaustive]` lesson**: Rust encodes the folding principle *in the language*.
  An `enum` without `#[non_exhaustive]`: variant list folds — adding a variant is major. With
  `#[non_exhaustive]`: variants are standalone atoms — adding one is minor (consumers were
  forced to write `_` arms). Same for struct fields. The discipline literally reads the
  attribute to select the folding. This is the clearest existing proof that folding-by-policy
  matches real language semantics.
- **Trait system hazards**: adding *any* trait implementation can break downstream inference
  (coherence and ambiguity) — the ecosystem accepts this as minor (cargo-semver-checks
  classifies most impl additions as non-breaking with documented exceptions like
  `Drop`/auto-trait changes). The discipline should follow the ecosystem table rather than
  invent a stricter one: sealed-trait patterns (private supertrait) make trait member lists
  foldable or not, mirroring Scala's open/sealed distinction.
- **Prior art**: cargo-semver-checks — dozens of machine-checked lint rules constituting the
  most complete breaking-change table in any ecosystem; the discipline is largely a
  transcription of it into atomization decisions.

## 6. Java — `classfile`

- **Interface carrier**: classfile signatures (incl. generic `Signature` attributes) in the
  `jvm` universe.
- **Guarantee**: linkage (the JVM's late-binding model is the original motivation for
  binary-compatibility analysis) + recompilation.
- **Rigid atoms**: methods/fields by name + descriptor; sealed classes (JEP 409) fold their
  `permits` list; interfaces fold their abstract-member list (default methods are standalone —
  additive-safe by JVM resolution); enum constants fold (switch exhaustiveness, `values()`).
- **Replaceable**: the one genuine case is `static final` compile-time constants — javac
  inlines them into consumers, so a constant's *value* is a replaceable atom (changing it is
  binary-silent but behaviorally stale — the exact inline-staleness phenomenon, decades old).
- **Prior art**: MiMa; the JLS binary-compatibility chapter (JLS 13) is effectively the
  normative folding table.

## 7. JavaScript — `esm`, else `opaque`

Untyped JS has no declared interface, but ES modules have *statically analyzable export
lists*. A lightweight discipline: each named export is a rigid atom keyed by export name with
an opaque value (content hash of the module chunk it resolves to — coarse but honest);
removing/renaming an export is major, adding one is minor; everything about values/shapes is
uncertified. Where even that is unwanted (CJS with dynamic exports), `opaque/1` applies. This
gives hand-written JS carried in a lira (spec: link-time-materialized `@JSImport` modules,
prelude scripts) a minimal, sound compatibility story.

## 8. Cross-language dependencies

The algebra needs nothing new: a dependency edge is (module, required snapshot), and atoms are
opaque hashes, so a Scala facade library over a TypeScript module, or Scala code calling
Kotlin classfiles, expresses its requirement exactly like a same-language one. Two useful
conventions:

- **Facade libraries** (Scala facades over TS/JS/Kotlin) SHOULD publish `uses` blobs against
  the foreign library, so spanning and staleness work across the language boundary.
- **WIT as the polyglot interface**: in the `component` universe, WIT worlds/interfaces are
  the interface carrier shared by *all* source languages — a natural `wit/1` discipline
  (functions/types in an interface as atoms; WASI's own versioning of worlds is upstream
  prior art). Component-model composition is where Rust↔Scala interop is cleanest, and its
  compatibility story is the same algebra over WIT atoms.

## 9. The host axis, uniformly

Orthogonal to all of the above: every application type carries a **host capability contract**
(universes.md §1) — JDK version, Android API level, browser/DOM baseline, Node version, WASI
preview + world, libc/triple. These are ordinary versioned interfaces and could, in the limit,
be treated with the same machinery (a host contract is a "module" whose atoms are
capabilities; a section's `requires` is a dependency edge against it). That unification is now
worked out in universes.md §5, prompted by the case that forces it — shell commands a library
invokes at runtime, which have no home on the discipline axis at all: they are not content, and
a requirement's polarity is the opposite of an atom's. Inverting the direction so that the
*host* is the module, and `requires` an ordinary dependency edge, needs no new algebra and makes
"runs on any host providing `sh` and `git`" a used-set computation. Until it is applied, the
design keeps host contracts as per-section constraint declarations checked at buildpath
resolution (universes.md §4.1, step 3).

## 10. Summary table

| Language   | Discipline         | Universe(s)        | Guarantee       | Replaceable atoms        | Rule-table prior art          |
| ---------- | ------------------ | ------------------ | --------------- | ------------------------ | ----------------------------- |
| Scala      | `tasty`            | jvm, sjsir, nir    | recomp + linkage| inline/macro bodies      | TASTy-MiMa, MiMa              |
| Kotlin     | `kotlin`           | jvm, klib          | recomp + linkage| inline fun bodies        | binary-compatibility-validator|
| TypeScript | `dts`              | js                 | recompilation   | —                        | api-extractor, semver-ts      |
| Rust       | `rmeta`            | crate              | recompilation   | generic/inline bodies    | cargo-semver-checks           |
| Java       | `classfile`        | jvm                | linkage + recomp| `static final` constants | MiMa, JLS 13                  |
| JavaScript | `esm`              | js                 | export presence | —                        | —                             |
| (WIT)      | `wit`              | component          | recomp + compose| —                        | WASI world versioning         |
| (any)      | `resource`         | all                | name presence   | tracked resource content | —                             |
