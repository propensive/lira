# The JVM Bytecode Discipline `classfile/1` — Specification Draft

## Abstract

`classfile/1` is the LIRA discipline for the JVM's **linkage** contract. It atomizes the
declaration surface of Java classfiles — the representation the JVM actually resolves against —
so that a release which breaks an already-compiled consumer registers as a break in the atom
algebra itself.

It exists because `tasty/1` cannot make this claim and does not try to. TASTy is a *higher*
representation than the one the JVM links against, so a discipline over it certifies
recompilation and holds `.class` files atomless (`tasty.md` §3). The two levels diverge in both
directions (LIRA Appendix D.1), and a format that certifies one and calls it "compatible" is
equivocating.

Registering this discipline has a cost, and an ecosystem should read §14 before doing so: for
most JVM ecosystems the `jvm/1` profile ([`jvm.md`](jvm.md)) is the better instrument, and it
computes the same predicates from the same rules without paying that cost.

The reference implementation is the `mandible` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline
(`classfile/2`), never a revision of this one (LIRA §11.1).

## 2. Scope and Guarantee

The discipline certifies **linkage** and nothing else: within a lineage, a consumer compiled
against release *A* continues to resolve and load against every later release, with no
recompilation.

It does **not** certify recompilation, and MUST NOT be read as doing so. Bytecode has erased the
type arguments, variance, implicit specificity and type aliases that decide whether a consumer's
*next compile* succeeds. That level is `tasty/1`'s in a Scala ecosystem, and belongs to a
declaration-surface discipline in any other.

## 3. Domain and the Cross-Section Invariant

The domain is the single universe `{jvm}`. Classfiles have no counterpart in `sjsir` or `nir`,
so there is nothing for the cross-section invariant (LIRA §9.6) to compare against; a
single-universe domain is exactly the case LIRA §11.2 requirement 1 exempts from it.

## 4. Content Claiming

The discipline claims `**/*.class`, atomized. It claims nothing else.

**Claiming order is load-bearing.** LIRA §11.2 partitions content by first match, and `tasty/1`
claims `.class` files *atomless*. A registry that lists `tasty/1` first therefore leaves this
discipline nothing to atomize, silently. A release registering both MUST list `classfile/1`
first.

## 5. Extraction

Atomization reads the classfile's **declaration surface**: the constant-pool-resolved names,
descriptors, access flags, supertypes and attributes listed in §7. It never reads the `Code`
attribute.

The following never enter the model, because none is linkage surface and any of them may differ
between two builds of identical sources: `Code`, `SourceFile`, `LineNumberTable`,
`LocalVariableTable`, `StackMapTable`, `BootstrapMethods`, the `Deprecated` marker, and every
runtime and invisible annotation attribute. Annotations are recompilation surface; a discipline
certifying that level carries them.

Extraction requires the module's **dependency classpath**, because membership keying (§6) walks
the supertype closure and most of that closure lives in dependencies rather than in the release
being atomized. The buildpath supplies it at assembly and publish time, the only times
atomization runs (LIRA §16, step 4).

## 6. Keys and Membership

Keying is by **membership**. A JVM call site names the *receiver*, not the declarer:
`invokevirtual Derived.m()` resolves through `Derived` whether or not `Derived` declares `m`, so
the linkage surface of a type includes every member it presents. Declaration keying would be
unsound for a discipline certifying linkage, which is precisely the case LIRA §11.2 requirement
4 rules out.

Keys are:

- a class: its JVM internal name, e.g. `gossamer/Text`;
- a method: `<owner>#<name>:<descriptor>`, e.g. `gossamer/Text#length:()I`;
- a field: `<owner>.<name>:<descriptor>`, e.g. `gossamer/Text.EMPTY:Lgossamer/Text;`.

`<owner>` is the **presenting** type, not the declaring one. For each atomized class *C*, the
presented member set is *C*'s own visible members followed by the visible members of its
supertype closure — superclass first, then interfaces, breadth-first — with the first occurrence
of each `<name>:<descriptor>` winning. That order is how an override, or a redeclaration, shadows
the member it replaces.

Two members are excluded from inheritance: constructors (`<init>`, which are not inherited) and
static members of *interfaces* (which implementors do not inherit, JLS 8.4.8). `<clinit>` is
never resolvable surface and is excluded everywhere.

**An unresolvable supertype is a hard error** (LIRA §11.2 requirement 3). Its member set is
unknown, so the presented set of everything beneath it is understated, and an understated
interface is a compatibility claim made over content nobody read.

## 7. Visibility

A member is consumer surface iff `ACC_PUBLIC` or `ACC_PROTECTED` is set. Package-private and
private members produce no atoms: no consumer outside the runtime package can resolve against
them.

Accessibility narrowing is nonetheless caught, because access flags fold into every atom's value
(§8): `protected` → `private` removes an atom, and `public` → `protected` changes one.

A class's accessibility is read from the `InnerClasses` entry describing *itself* where one
exists, and from its own `access_flags` otherwise. A nested class's `access_flags` record the
*outer* view — a `protected` member class has neither `ACC_PUBLIC` nor `ACC_PROTECTED` there —
so trusting them would drop protected nested classes from the interface.

Synthetic *classes* — lambda holders, `package-info`, anonymous helpers — are excluded: no
consumer's source can name them. Synthetic and bridge *methods* are **included**, as their own
atoms: a compiled consumer may have bound to one, and its removal is exactly the linkage break
this discipline exists to catch.

## 8. Canonical Encoding

Atom values are hashes over a deterministic tag-length-value encoding, on the same principles as
`tasty.md` §7: unsigned LEB128 lengths, length-prefixed UTF-8 strings, single-character
constructor tags, and every list sorted unless its order is itself semantic.

**Flag folding.** Access flags fold as a fixed-order bit set, chosen per element kind, because
the JVM reuses bit values across kinds (`0x0040` is `ACC_BRIDGE` on a method and `ACC_VOLATILE`
on a field):

- class: `ACC_PUBLIC`, `ACC_PROTECTED`, `ACC_FINAL`, `ACC_INTERFACE`, `ACC_ABSTRACT`,
  `ACC_SYNTHETIC`, `ACC_ANNOTATION`, `ACC_ENUM`;
- method: `ACC_PUBLIC`, `ACC_PRIVATE`, `ACC_PROTECTED`, `ACC_STATIC`, `ACC_FINAL`, `ACC_BRIDGE`,
  `ACC_VARARGS`, `ACC_ABSTRACT`, `ACC_SYNTHETIC`;
- field: `ACC_PUBLIC`, `ACC_PRIVATE`, `ACC_PROTECTED`, `ACC_STATIC`, `ACC_FINAL`, `ACC_VOLATILE`,
  `ACC_TRANSIENT`, `ACC_SYNTHETIC`, `ACC_ENUM`.

`ACC_SUPER`, `ACC_SYNCHRONIZED`, `ACC_NATIVE`, `ACC_STRICT` and `ACC_MANDATED` are excluded: none
is resolvable surface, and each varies with an implementation no consumer can observe through
linkage.

**Folds.** A `fold` is either **full** or **linkage-only**; they differ in exactly one respect,
that the linkage-only fold omits generic `Signature` attributes. The discipline uses the full
fold; the `jvm/1` profile uses the linkage-only one. The reason is asymmetric exposure:
`Signature` is not linkage surface — the JVM resolves on descriptors alone — but folding it into
a *discipline*'s atoms only ever over-reports, costing a needless major, which is the safe
direction when atoms are API identity, while folding it into a *profile*'s findings would
publish `breaks linkage` for a release that breaks none, which is a false claim rather than a
conservative one.

## 9. Rigid Atoms: Classes

One rigid atom per visible, non-synthetic class. The value folds, in order:

1. the class's internal name;
2. the class flag bit set (§8), taken from the accessibility of §7;
3. the superclass's internal name, or an absence marker;
4. the interface list, sorted by name — the JVM resolves default methods by the class hierarchy,
   not by the order of the `interfaces` array;
5. the class `Signature` attribute, or an absence marker (full fold only);
6. **iff the class is not `final`**: the sorted key list of the abstract methods in its presented
   set.

Rule 6 transposes rule 5 of `tasty.md` §8. On a class open to subclassing, *adding* an abstract
method breaks every existing implementor, so the abstract member set folds into the class's own
atom and the addition grades major. On a `final` class nothing can implement it, the fold is
empty, and the same addition is pure extension.

## 10. Rigid Atoms: Members

One rigid atom per presented, visible member, except the constants of §11. The value folds, in
order:

1. a kind tag, distinguishing a method from a field;
2. **the presenting owner's internal name**;
3. the member's name;
4. its descriptor;
5. the flag bit set for its kind (§8);
6. its `Signature` attribute, or an absence marker (full fold only);
7. its `Exceptions` list, sorted by name — the attribute's own order is source order, which is
   not contractual;
8. its `ConstantValue`, or an absence marker.

**Item 2 is not redundant with the key, and omitting it would be unsound.** A snapshot is the
hash of the set of *distinct* value hashes (LIRA §12.1). Two membership atoms differing only by
key would therefore collapse into one, and removing an inherited member from one of several
presenting types would leave API identity unchanged. Folding the presenting owner into the value
is the one obligation membership keying imposes that declaration keying does not.

## 11. Replaceable Atoms: Inlined Constants

A `static final` field of primitive or `String` type whose `ConstantValue` a compiler may already
have copied into consumers' constant pools (JLS 13.4.9) yields a **replaceable** atom rather than
a rigid one, with an empty reference list.

Such a field's *value* is not a linkage contract: every descriptor remains resolvable when it
changes. It is churn a consumer absorbs by recompiling, which is exactly what a replaceable atom
means — a minor event that marks consumers whose used-sets contain the atom as stale (LIRA
§13.4). This is the same shape `resource/1` uses for tracked content (LIRA §11.4).

Replaceability soundness (LIRA §11.2 requirement 5) is trivial: a constant refers to nothing.

## 12. Reference Lists

Empty. The only replaceable atoms this discipline emits are the constants of §11, which refer to
nothing, so no used-set closure arises from it.

## 13. Determinism

`java.lang.classfile` and every other classfile reader yields fields, methods and attributes in
*file* order, which is an artifact of the compilation run rather than of the interface. Every
list this document folds is therefore sorted before encoding, except where §9 and §10 name the
order as semantic.

Two compilations of identical sources by identical toolchains MUST yield identical atom sets
(LIRA §17).

## 14. Cross-Section Policy, and When Not To Register This Discipline

The domain is a single universe, so the cross-section invariant (LIRA §9.6) is vacuous for this
discipline: `.sjsir` and `.nir` sections carry no classfiles to compare against, and a release
carrying only those universes must not declare it (L127).

The larger question is whether to register it at all. LIRA §11.6 sets out the trade-off, and it
is genuine:

- Atoms feed the snapshot, and the snapshot is API identity (LIRA §12.1). Registering this
  discipline fuses the linkage level into that identity, so a release whose source-level
  interface is untouched but whose bridge or forwarder methods moved acquires a *different*
  snapshot — breaking dependency satisfaction (LIRA §13.2) for every consumer, including those
  who only ever recompile and were never affected.
- The `jvm/1` profile ([`jvm.md`](jvm.md)) computes the same predicates from the same rules and
  records what it finds in `breaks linkage` (LIRA §12.4) instead, where it is read by exactly the
  consumers a linkage break affects and by nobody else.

**Register `classfile/1` where linkage is the ecosystem's primary contract** — where consumers
are ordinarily shipped prebuilt bytecode and recompilation is not an option. **Declare the
`jvm/1` profile otherwise**, which is most JVM ecosystems, and certainly any that also carries a
recompilation-level discipline such as `tasty/1`.
