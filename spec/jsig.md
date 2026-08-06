# The Java Signature Discipline `jsig/1` — Specification Draft

## Abstract

`jsig/1` is the LIRA discipline for the **declared signature surface** of Java classfiles: the
discipline of host contracts whose carrier is API stubs — the JDK's `ct.sym` signature files,
`android.jar`'s stub classes — and of any content whose contract is *what can be compiled
against*, rather than the linkage of a lineage of shipped bytecode.

It exists because `classfile/1` cannot serve here and says so ([`classfile.md`](classfile.md),
[`hosts.md`](hosts.md) §3): its domain is `{jvm}` and its guarantee is **linkage** — a claim
about already-compiled consumers resolving against the very bytecode a release ships. A host
contract's stubs ship no bytecode anyone links against; what they promise is that a consumer's
sources compile, and that the named surface is *present* on the host. `jsig/1` makes exactly
that claim, over exactly the same canonical encoding.

The reference implementation is the `mandible` module of Soundness; this document is the
normative transcription of its rules.

## 1. Status

This document is a working draft, versioned in lockstep with the discipline identifier: any
change to the canonicalization defined here — however small — is a new discipline (`jsig/2`),
never a revision of this one (LIRA §11.1).

## 2. Scope and Guarantee

The discipline certifies **recompilation**, and presence on the terms of `capability/1`
([`hosts.md`](hosts.md) §5): within a lineage, consumers' sources still compile against every
later release, and every capability a consumer's used-set names is present on any host whose
contract's atom set contains it. It does not certify linkage: there is no shipped bytecode
whose binary contract it could be describing.

## 3. Domain and Content Claiming

The domain is `{jvm, host}`: host contracts carried as signature stubs are the motivating case,
and the `jvm` inclusion admits a library whose interface carrier genuinely is Java signatures
(a stub-published API, a signature-only artifact).

The discipline claims `**/*.sig` and `**/*.class`, atomized. The `.sig` extension is the JDK's
own spelling for signature classfiles (`ct.sym`); the format is the classfile format, without
`Code`. Claiming order beside other classfile-reading disciplines follows `classfile.md` §4's
rule: order decides, and a registry lists the discipline that carries the contract first.

## 4. Extraction, Keys and Encoding

Extraction, visibility, membership keying, the canonical encoding and determinism are those of
[`classfile.md`](classfile.md) §5–§10 and §13, with the **full fold** (generic `Signature`
attributes included — they are precisely the recompilation surface this discipline certifies),
and one difference of obligation:

- **An unresolvable supertype is a boundary, not an error, iff it lies outside the claimed
  content.** `classfile.md` §6 hard-errors on an unresolvable supertype because a library's
  closure lives on its dependency classpath, which the buildpath supplies. A host contract *is*
  the closure: a surface harvested whole resolves its own supertypes, and one harvested
  partially would understate presented sets — so producers MUST harvest closures whole, and an
  implementation MUST still fail on a supertype that is claimed content yet unreadable. What it
  MUST NOT do is fail on `java.lang.Object`'s absence from a contract that is not the JDK's:
  a supertype outside the claimed content contributes nothing to presented sets, exactly as a
  metadata-less supertype contributes nothing to `kotlin-metadata/1` ([`kotlin.md`](kotlin.md)
  §5).

## 5. Why Not `classfile/1` With a Different Registry Entry

The two disciplines share an encoding and differ in claim, and LIRA §11.2 requirement 7 is why
they cannot be one: a discipline MUST NOT claim a guarantee level it does not enforce, in
either direction. Registering `classfile/1` over stubs would publish a linkage claim over
bytecode that does not exist; registering `jsig/1` over a shipped-bytecode library would
publish only recompilation where the ecosystem's contract is linkage. The name states the
claim.

## 6. The Contracts This Discipline Carries (Informative)

- **`java.base`** and its sibling platform modules: harvested from `ct.sym`, which carries
  the signature surface of every release back to JDK 8 — one modern JDK yields the whole set
  of lineages, one contract module per platform module (hosts.md §3, "Granularity"). Marketing
  names ride as coordinated tags (LIRA §12.6): every module contract harvested from JDK 19
  carries `tag jdk-19`, and the platform release is the tag-coordinated set.
- **`android`**: harvested from `android.jar` per API level; `tag android-37` and kin.
- **`scalajs-javalib`**: the JDK subset Scala.js reimplements, under this same discipline —
  which is what makes cross-contract spanning ([`hosts.md`](hosts.md) §7) decide JVM/JS
  portability by set inclusion.
