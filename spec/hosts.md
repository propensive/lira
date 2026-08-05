# Host Contracts and Requirements — Specification Draft

## Abstract

A library does not only link against other libraries; it also assumes an **environment**. A JVM
library assumes the Java standard library; browser JavaScript assumes the DOM; Node code assumes
Node's builtins; an Android application assumes the platform classes of an API level; a WASM
component assumes a WASI world; a program that shells out assumes the commands it invokes. These
assumptions are not dependencies — nothing on a buildpath supplies them — yet a library that
makes them is unusable wherever they fail, and a format whose files claim to know their own
compatibility must be able to say so.

This document specifies the **host axis**: how a host's capability interface is published as a
**host contract** — an ordinary `.lira` release whose atoms are capabilities — and how a
library's sections declare **requirements** against contracts, satisfied by exactly the algebra
that satisfies dependencies. No new compatibility machinery is introduced; the design's whole
content is that the polarity of the existing machinery, inverted, is correct for hosts
(LIRA §4.1, and the derivation in [`universes.md`](../design/universes.md) §5).

It also specifies **`capability/1`**, the discipline for host contracts with no formal carrier.

## 1. Status

This document is a working draft. It is normative for the `host` world (LIRA §9.4), the
`requires` mechanism (LIRA §13.2–§13.3, §14), and the `capability/1` discipline; the labels
**L135**, **L136** and **L137** are defined in the base specification and elaborated here.

## 2. Motivation: Requirements Are Not Dependencies

The obvious encoding — a discipline atomizing a library's requirements as its own atoms — fails
three ways ([`universes.md`](../design/universes.md) §5.1): there is no content to atomize, since
command and API availability is a property of the environment rather than of any blob (LIRA
**L127** makes such a discipline undeclarable); the polarity is inverted, since rigid atoms are
monotonic because *addition is safe*, while adding a requirement breaks consumers whose
environment lacks it; and the claim is not derivable, since no atomizer can decide from bytecode
which commands a program will invoke.

Invert the direction and every objection dissolves. **The host is the module.** A host contract
publishes what a host *provides*, which is content, with the correct polarity — a host gaining a
capability is a minor step; one losing a capability is a major, beginning a new lineage, which is
the actual compatibility behavior of every runtime environment — and it is verifiable by
recomputation like any other release. The library's side of the edge is then a **requirement**:
a reference to a contract, satisfied by lineage membership, exactly like a dependency (LIRA
§13.2) — and *only* the library's side is authorial (§9).

## 3. Host Contracts Are Releases

A host contract is an ordinary `.lira` release: a module with a name, a lineage, `api` records,
a payload, and signatures. Everything in the base specification applies to it unchanged —
identity (§6), hashing (§7), atom listings (§10.4), snapshots and grades (§12), verification
(§16). Its distinguishing feature is *what its atoms describe*: not a library's interface but a
host's capability surface.

The contract's content — the carrier from which its atoms are recomputed — lives in its single
`host` section (§4), and is whatever representation the host's interface is best expressed in:

- a **TEL capability listing** for contracts with no formal grammar — POSIX commands, tool
  availability — atomized by `capability/1` (§5);
- **`.d.ts` declarations** for JavaScript runtimes — Node's builtins, Deno's globals — atomized
  by `dts/1`, whose domain is every world ([`dts.md`](dts.md) §3);
- **Web IDL** for browsers, atomized by `webidl/1` ([`webidl.md`](webidl.md)), whose folding
  decisions differ instructively from `dts/1`'s because the IDL declares the usage direction
  TypeScript cannot;
- **WIT** for WASI runtimes — the world a component assumes — atomized by `wit/1`
  ([`wit.md`](wit.md));
- **C headers** for environment-supplied shared libraries — a libc, `libcrypto`, any `dlopen`ed
  dependency — atomized by `cheader/1` ([`cheader.md`](cheader.md));
- **API stubs** for the JDK and for Android (`android.jar`'s surface), atomized by a
  classfile-signature discipline whose domain includes `host` — noting that `classfile/1` will
  not serve, since its domain is `{jvm}` ([`classfile.md`](classfile.md) §3) and its guarantee
  is linkage against a lineage of shipped bytecode, not presence of a platform surface. This is
  a future discipline, and the near-term JDK contract is a `capability/1` listing.

Content claiming inside a `host` section follows LIRA §11.2 unchanged, including the claiming
order (**L134**) and the `opaque/1` fallback; **L127** applies unchanged, so a host contract can
declare only disciplines whose domain includes `host`.

**Versioning.** The derived-version algebra (LIRA §12.5) applies unchanged, and marketing
versions do not: "JDK 21" and "API level 26" are facts about the host vendor's numbering, not
about the lineage. A publisher maps them onto modules and lineages as a naming decision — one
module `jdk` whose majors track surface removals, or one module per vendor major — and the
algebra cares only that removals begin new lineages. The `version` hint field carries the human
label as it does everywhere else: without authority.

## 4. The `host` World

The `host` world is the one world that is not a universe (LIRA §4.1): independently-published
libraries do not compose in it, and its sections are never materialized onto any artifact path
(LIRA §13.5) and never consumed by any egress or join. It exists so that a host contract's
content is *ordinary content* — held in a tree, deduplicated in the payload, hashed, atomized,
verified and signed by the machinery every release already gets — rather than a parallel
structure with parallel rules.

**L135** (defined at LIRA §9.4) fixes the shape: a release carrying a `host` section is a host
contract; it carries exactly that one section, declares no integrations and no dependencies, and
its section carries no `requires` records. The exclusions are not arbitrary: an integration is
an alternative dependency vector and a contract has no dependencies to vary; and a contract
requiring a host would make satisfaction recursive for no identified need. Contract composition
— a browser contract aggregating WebIDL modules, a WASI world importing interfaces — is real,
and a future schema layer MAY relax the dependency exclusion to express it; until then an
aggregate contract is published whole.

## 5. The `capability/1` Discipline

`capability/1` is the discipline of host contracts with no formal carrier. Its domain is the
single world `{host}`; its keying is by declaration; it emits only rigid atoms and no reference
lists; it certifies **presence**, on the same terms as `resource/1` — which is the recompilation
level for content addressed by name, and the only level that "the command exists" can mean.

**Claiming.** The discipline claims the single tree item at the path `capabilities`: a TEL
document conforming to the `lira-capabilities` schema:

```text
tel 1.0

name lira-capabilities

record Capability
  field name Identifier            # bare name, or name:variant (see below)
  field version String optional    # a version predicate, e.g. >= 2.30
  field probe String optional      # an advisory probe (§9); no authority, enters no atom

document
  field capability Capability optional repeatable   # sorted by ascending name; no duplicates
```

A contract declaring `capability/1` whose section lacks the `capabilities` item, whose document
fails the schema, whose rows are unsorted or duplicated, is invalid at assembly and publish
time, on the same terms as a malformed Atoms blob.

**Atomization.** One rigid atom per `capability` row. The key is the capability's name; the
canonical encoding is the UTF-8 bytes of the name, then `0x00`, then — where a version predicate
is declared — `0x01` followed by the predicate's UTF-8 bytes, or the single byte `0x00` where
none is. The `probe` field enters no atom: it participates in implementation identity (it is
bytes in the payload) but never in API identity, so editing a probe is a patch.

**Version predicates.** A predicate folds into the atom's value, so a contract *tightening* a
minimum (`git >= 2.30` → `git >= 2.40`) changes the atom — a removal plus an addition, hence
major — which is correct: consumers satisfied by the old floor may not be satisfied by the new
one. A contract *loosening* a predicate is equally a major by this rule, which is conservative
but sound, and predicates SHOULD therefore be chosen sparingly.

**Variants.** A bare name promises **POSIX-conformant behavior only**: `sh` means a POSIX `sh`.
Implementations that differ materially are different capabilities and take variant-qualified
names — `sed:gnu`, `awk:bsd` — exactly as `native/<triple>` parameterizes a universe rather than
pretending one native target is another. Coarse, honest, and the common portable case is the
short one.

## 6. Requirements

A library section MAY carry `requires` records (LIRA §14): each names a host contract's module
and a required snapshot, optionally with a Uses blob against the contract and a human-readable
version hint. A `requires` record naming a module whose releases are not host contracts is
invalid (**L137**, LIRA §13.3) — checkable wherever the named module's manifest is in hand,
since a host contract is recognizable by its `host` section.

Requirements sit on **sections**, not on releases, because needs genuinely differ per universe
and per integration: a library may shell out only in its `jvm` implementation, or touch the DOM
only in its `sjsir` one. This does not collide with the cross-section API invariant (LIRA §9.6):
**L108** constrains what a release *presents*, requirements are not API, and two sections
presenting one interface while needing different things of their environments is ordinary, not a
violation.

A section with no `requires` records imposes nothing on any host. That is the important default,
and it is not a formality: a library whose sections depend only on other libraries — the
pure-library case — runs wherever its universes' artifacts run, with nothing to probe and
nothing to satisfy, and its manifest says so by silence. Requirements are for the libraries that
genuinely assume an environment, and only those.

## 7. Satisfaction and Spanning

A host contract release `H` **satisfies** a requirement `(module, api)` iff `H` is a release of
that module and the required snapshot `api` appears in `H`'s lineage — LIRA §13.2's rule,
verbatim (**L136**, LIRA §13.3 rule 7).

Where the requirement carries a Uses blob — the contract atoms the section actually depends on —
satisfaction extends by **spanning**, and in a form dependency spanning does not need:

- **Across majors**: `H` satisfies the requirement whenever `used ⊆ atoms(H)`, even when the
  required snapshot appears in no lineage of `H` — LIRA §13.4, unchanged.
- **Across contracts**: a contract `H′` of a *different module* satisfies the requirement
  whenever `used ⊆ atoms(H′)`. This is sound because atoms are content-addressed and
  module-blind: two contracts atomizing equivalent capability surfaces under the same discipline
  produce identical atom hashes, and different disciplines can never alias (LIRA §7.1). Without
  a Uses blob there is no cross-contract satisfaction: lineage membership is per-module by
  definition.

Cross-contract spanning is the mechanism for the multi-host library, and the motivating instance
is worth spelling out. Scala.js reimplements a subset of the Java standard library; suppose that
subset is published as a contract, under the same signature discipline as the JDK contract. A
library that declares `requires` on the JDK contract with a Uses blob naming only
`java.lang.String` and kin is then *provably* satisfied by the Scala.js contract too — `used ⊆
atoms(scalajs-javalib)` — while a sibling library whose used-set touches `java.nio` provably is
not. "This module runs on the JVM, Scala.js and Android; that one is JVM-only" stops being a
README sentence and becomes set inclusion, computed from manifests, per section, with no
assertion by anyone.

Publishers SHOULD therefore emit Uses blobs on `requires` records wherever tooling can compute
them: a requirement without one is satisfiable only by its named contract's lineage, which is
the single-host case.

## 8. Requirements Are Not Dependencies: the Boundary

Requirements and dependencies share one satisfaction algebra and differ in three normative
respects. Tools MUST NOT conflate them:

1. **Who supplies the content.** A dependency names content the *buildpath* must supply, and
   which composes into the application. A requirement names capability the *environment* must
   supply; the contract describes it but contributes nothing to any artifact.
2. **Materialization.** A dependency's sections are materialized and handed to egresses and
   joins (LIRA §13.5). A required contract is never materialized: its `host` section is read for
   atoms at validation time and nothing else, and a contract on a buildpath contributes no
   namespace claims, no resources, and no content to rules 2–3 of LIRA §13.3.
3. **Verification moment.** Every claim a dependency edge rests on is recomputable at publish
   time or decidable from manifests at resolution time. A requirement adds a third moment —
   probing the actual environment (§9) — and its authorship is a claim of a different kind.

## 9. The Third Verification Moment

Everything else in LIRA is verified at publish time by recomputation from the payload, or at
resolution time from manifests. A `requires` claim is neither, in one specific place: **no
verifier can decide that code needs what it declares.** Command strings are assembled at runtime
from configuration and input; reflection and dynamic loading hide API use from any static
reading. The declaration is irreducibly an assertion about the code, and a reader who assumes
`requires` was checked *against the code* has misread the format. What is machine-checked is
everything around the assertion: the contract's atoms are recomputed from its payload (LIRA
§16), satisfaction is decided from manifests (**L136**), and the *environment* is checked at the
third moment:

**Probing, at install or launch time.** Whether the actual host honors the contract the
requirement was satisfied against is decidable exactly there — `command -v git` for a shell
capability, `'IntersectionObserver' in window` for a Web API (feature detection, the idiom the
web has always used, here made machine-readable), an API-level query on Android. A capability's
advisory `probe` field (§5) makes the check data-driven where present; probes are suggestions to
the probing tool, carry no authority, and MUST be treated as untrusted input, never executed
with elevated privilege. Probing tools SHOULD check the aggregated requirement set (§10) once,
before the code runs, and report failures against the host the user actually has — which is the
whole payoff: the failure surfaces at launch with a named, versioned cause, not mid-execution
with a stack trace.

What the format buys is therefore not verification of the authorial claim but **precision and
timing**. An ecosystem profile MAY add a best-effort publish-time linter over call sites
(process execution, known platform entry points), sound in one direction only: it can catch an
omission, never prove a declaration list complete.

## 10. Transitive Aggregation

A buildpath's effective requirement set, for a target and assignment (LIRA §13.3), is the union
of the `requires` records of every selected section. Tools MUST aggregate before judging:
requirements on one contract module from several releases are jointly satisfiable iff some
release of that contract satisfies each — by lineage membership, requirements on two snapshots
resolve exactly as diamond dependencies do (some lineage contains both, LIRA §13.3 rule 5); by
spanning, the union of the used-sets must be contained in one contract's atom set. The
aggregated set — "this application, on this target, needs a host providing these capabilities" —
is the application's host contract in all but publication, and SHOULD be reported as such; a
probing tool (§9) consumes it whole.

## 11. Contract Registry (Informative)

The contracts the focus ecosystems want first, with their natural carriers:

| Contract           | Surface                                   | Carrier / discipline               |
| ------------------ | ----------------------------------------- | ---------------------------------- |
| `jdk`              | Java standard library                     | capability listing now; classfile-signature discipline later (§3) |
| `android`          | `android.jar` per API level               | as `jdk`; majors track removals, minors track API levels |
| `nodejs`           | Node builtins and globals                 | `.d.ts` / `dts/1`                  |
| `browser-baseline` | interoperable Web APIs ("Baseline")       | Web IDL / `webidl/1`               |
| `wasi`             | a WASI world (0.2+)                       | WIT / `wit/1`                      |
| `openssl`          | `libcrypto`'s declared surface            | C header / `cheader/1`             |
| `posix`            | POSIX shell and userland commands         | `capability/1`                     |
| `scalajs-javalib`  | the JDK subset Scala.js reimplements      | same discipline as `jdk` — which is what makes cross-contract spanning (§7) decide JVM/Scala.js/Android portability |

Who publishes these — a registry-blessed set, the platform vendors, or the community — is a
governance question this specification deliberately does not answer
([`universes.md`](../design/universes.md) §5.6). The format's only stake is that whoever does
signs them, and that their lineages then tell the truth by construction.
