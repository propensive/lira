# The JVM Ecosystem Profile `jvm/1` — Specification Draft

## Abstract

`jvm/1` is the LIRA ecosystem profile (§11.6) for the JVM. It imposes bytecode-level linkage
predicates over a lineage step, and a toolchain predicate over a buildpath, in addition to the
requirements of the base specification.

It is the normative statement of what LIRA Appendix D sketches informatively.

The profile exists because linkage and recompilation come apart on the JVM more visibly than
anywhere else, and because the obvious remedy — folding bytecode surface into atoms — has a cost
the ecosystem should not pay by default. §3 sets out that argument; the predicates are §5 and §6.

The reference implementation is the `mandible` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the profile identifier: any change
to a predicate defined here is a new profile (`jvm/2`), never a revision of this one (LIRA §11.1,
§11.6).

## 2. Certified Guarantee

The profile certifies **linkage**, for the `jvm` universe. It adds no predicate about
recompilation: a profile adds guarantees and never subtracts them (L129), and in the motivating
ecosystem `tasty/1` already certifies recompilation over the same release.

A release declaring this profile is asserting that it satisfies §5 and §6 against its
predecessor, or that it has recorded in `breaks linkage` the levels it does not (LIRA §12.4).

## 3. Why a Profile and Not a Discipline

An ecosystem could instead register `classfile/1` ([`classfile.md`](classfile.md)), whose atoms
encode the same surface. LIRA §11.6 permits that, and for an ecosystem whose primary contract
genuinely *is* linkage it is the better choice. For the JVM it is the wrong default, for a
reason worth restating precisely:

Atoms feed the snapshot, and the snapshot is API identity (LIRA §12.1). Fusing the linkage level
into that identity means a release whose source-level interface is unchanged — but whose bridge
or mixin-forwarder methods moved, as they routinely do — acquires a *different* API identity.
Dependency satisfaction (LIRA §13.2) is computed against that identity, so the release stops
satisfying the requirements of every consumer, including the great majority who only ever
recompile and whom the change never affected.

Keeping the predicates in a profile keeps the snapshot at the recompilation level, where it is
the useful identity, and records linkage breakage in `breaks linkage`, where it is read by
exactly the consumers a linkage break affects — those pinned to prebuilt bytecode — and by nobody
else.

## 4. Evidence and Timing

Profile predicates are **diachronic**: unlike a discipline's atomization, which is a function of
one release's content, they compare a release against its predecessor. They therefore run only
where the predecessor's content is in hand — at assembly and publish time, and at a registry
(LIRA §16) — and a release that begins a lineage has no step to check.

The evidence for one release is its `jvm` section's content, the dependency classpath of the
integration that section belongs to, and its manifest. A release carrying no `jvm` universe has
no linkage surface, and every predicate of §5 holds vacuously.

## 5. Linkage Predicates

The predicates are stated over the **linkage-only fold** of `classfile/1`'s atom set
([`classfile.md`](classfile.md) §8) — the same rules for membership, visibility and encoding,
omitting generic `Signature` attributes. Signatures are omitted because the JVM resolves on
descriptors alone: including them would report `breaks linkage` for a release that breaks no
linkage, which is a false claim rather than a conservative one.

Let *B* be that atom set for the predecessor and *A* for the release. Then:

1. **No presented member disappears.** Every key in *B* MUST be present in *A*. Because keys are
   `<presenting owner>#<name>:<descriptor>`, this single predicate covers a member being deleted,
   a member changing descriptor, and a member moving to a type that no longer presents it.
2. **No bridge or forwarder disappears.** This is not a separate check: `classfile/1` atomizes
   bridge and mixin-forwarder methods as members in their own right ([`classfile.md`](classfile.md)
   §7), precisely so that predicate 1 covers them. A compiled consumer may have bound to one.
3. **No presented member changes shape.** For every rigid atom whose key is in both *B* and *A*,
   the value hashes MUST be equal. Access flags fold into the value, so this is what catches
   accessibility narrowing (`public` → `protected`, `protected` → `private` being caught by
   predicate 1 instead), a method becoming `final` or `abstract`, an instance member becoming
   `static`, and a change to a `throws` clause.
4. **No class disappears or changes shape.** The same two predicates applied to class atoms:
   supertypes, interfaces and class flags are linkage surface.

Constant values are **not** a linkage predicate; see §7.

## 6. Toolchain Predicate

Every release on a buildpath MUST carry metadata the consumer's compiler can read (LIRA §13.3).
TASTy readability is versioned and not universally backward-compatible, so a buildpath assembled
from releases whose TASTy versions the consuming compiler cannot read is unsatisfiable however
well its atoms line up.

A release that records no `toolchain` entry at all does not satisfy this predicate. The claim is
then uncheckable rather than false, and MUST be reported as a violation rather than assumed.

## 7. Inlined Constants

`static final` fields of constant type, which a compiler may already have copied into consumers'
constant pools (JLS 13.4.9), are **reported separately and are not linkage violations**.

A changed constant leaves every descriptor resolvable, so nothing fails to link. What happens is
that a consumer holding the old value computes with it until it recompiles — and the core algebra
already says so, since `classfile/1` §11 makes these atoms replaceable, which grades the step a
minor and marks the stale used-sets (LIRA §13.4). Repeating that as a linkage break would
overstate the finding.

An implementation SHOULD surface the list of changed constants for reporting.

## 8. Recording a Violation

A release declaring this profile records, per LIRA §12.4, the guarantee levels its lineage step
did not preserve. The `breaks` list is an authorial claim; checking it is not a matter of
computing the list but of confirming that it accounts for what the predicates found:

- a violation at a level this profile certifies that the release does **not** record is
  **L130**: the step does not preserve the level and says nothing about it;
- a violation at a level this profile does **not** certify is outside the profile's authority
  (LIRA §11.6): a profile reporting a finding at a level it does not certify is a broken
  profile, not a broken release.

Recording `breaks linkage` does not make the step a major. That is the point of §3: the release
remains a minor for recompiling consumers, and the record tells consumers pinned to prebuilt
bytecode that they must rebuild.

## 9. On Regenerating Classfiles

A linkage break may appear repairable by recompiling the release's TASTy, since `-from-tasty`
does compile TASTy to classfiles. It is not, and implementations MUST NOT attempt it:

1. Recompiling the *new* TASTy produces the *new* classfile surface. The bridge a compiled
   consumer needs is missing precisely because the new TASTy no longer implies it. Only
   regenerating from the predecessor's TASTy would reproduce the old surface, and that is just
   the predecessor release.
2. Regeneration changes the bytes. Classfiles emitted by a different compiler build differ from
   those shipped, so every blob hash in the section changes, and with it the payload hash and the
   release's implementation identity. LIRA §17's determinism guarantee does not extend to content
   derived at consumption time.
3. `-from-tasty` is a compiler-testing path, not a distribution mechanism, and needs a compiler
   able to read the TASTy version in question.

What derivability does buy is worth stating positively, because it is why a linkage break need
not be fatal: **while TASTy is intact, a linkage break is a recompilation cost, not a wall.** Any
consumer willing to rebuild from source can consume the release; only consumers pinned to
prebuilt bytecode are blocked. That is exactly the distinction LIRA §11.5 draws, and exactly what
`breaks linkage` records.

## 10. Determinism

The predicates are a function of the two releases' content and manifests, and of nothing else.
Two runs over the same pair MUST reach the same verdict.
