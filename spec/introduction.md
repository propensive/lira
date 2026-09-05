# LIRA: An Introduction

This document is an informative companion to the LIRA specification
([`lira.md`](lira.md)) and its suite. It defines nothing. Its purpose is to explain the
problems LIRA exists to solve, the abstractions it solves them with, and — most
importantly — how those abstractions change what a developer has to hold in their head
when software built by strangers is composed into something that has to work.

## 1. The Question

Every working developer has lived some version of these afternoons.

A transitive dependency releases version 2.4.1 — a *minor* bump, guaranteed compatible by
convention — and your build breaks. Nothing in your code changed. The library's authors
tightened a type bound they considered an implementation detail, and semantic versioning,
which is a promise rather than a proof, had no way to know.

Or the reverse: the upgrade compiles cleanly everywhere, ships, and throws
`NoSuchMethodError` in production — because a library added a method to a trait, every
*source* still compiled, and a jar compiled months ago against the old shape was linked
against the new one. The compiler had no complaint; the classloader did.

Or the diamond: module A needs C at 2.3, module B needs C at 3.1, and you need A and B.
Somebody's resolver picks a winner, silently, and whether the loser's expectations survive
the substitution is discovered empirically.

Or nothing about the code at all: the artifact is fine, but it lands on a JDK two versions
older than the one it was built against, or a container missing the shared library it
`dlopen`s, or a browser without an API it assumed. "Works on my machine" is a
compatibility failure with the *environment*, and no build tool ever saw it coming.

Or the version that only appears after everything is built and running: a service is
deployed into a cluster, its own tests green, and a consumer three teams away — one whose
existence nobody in the deploying team remembered — starts failing, because the field it
relied on changed meaning or vanished. The runbook said which services must move together.
The runbook was wrong.

These look like five different problems, owned by five different tools — a build tool, a
binary-compatibility linter, a dependency resolver, a container image, a contract-testing
broker. LIRA begins from the observation that they are one problem. Independently-published
software meets, and the question is always: **will these work together?** It is asked at two
moments — at **build time**, when libraries meet on a classpath and must present compatible
interfaces, and at **deploy time**, when running artifacts meet in an environment and must
honor compatible contracts. LIRA is a file format and an algebra designed so that this one
question, at both moments, has a checkable answer.

## 2. Testimony

Why can't version numbers answer it? Because a version number is *testimony*. A human — or
a release script encoding a human's policy — asserts that 2.4.1 is compatible with 2.4.0,
and everyone downstream trusts the assertion, transitively, across hundreds of modules and
thousands of releases. The assertion cannot be checked by the people relying on it, and it
is routinely wrong in both directions: breaking changes ship as minors, and terrified
maintainers ship majors for changes that would have broken nobody.

Worse, the assertion is *ambiguous* even when it is honest, because "compatible" is not one
claim. A change can preserve **linkage** — already-compiled consumers still resolve and run —
while breaking **recompilation** — those consumers' sources no longer compile. Tightening a
type bound does exactly this. A change can preserve recompilation while breaking linkage —
adding a trait method recompiles every consumer cleanly while invalidating the compiled
bytecode of subclasses built earlier. Neither implies the other. And no scheme of any kind
certifies **behavior** — that unchanged interfaces compute unchanged results. A version
number that says "compatible" without saying *at which level* is an equivocation, and the
five afternoons of §1 are what equivocations cost.

The industry's response has been to build checkers around the testimony: binary-compatibility
linters, API-diff tools, contract brokers, schema registries. Each is good at its corner.
But each reinvents, partially and privately, the same underlying objects — what an interface
*is*, what a history of compatible states *is*, what a consumer *actually uses* — and none
of their conclusions travels with the artifact. LIRA's wager is that if those objects are
defined once, precisely, and *shipped inside the artifact as verifiable metadata*, then the
checkers collapse into arithmetic, and the arithmetic can be done by anyone, at any time,
from the files alone.

The rest of this introduction walks through the abstractions that make that possible, in
the order a reader can build them up: first the interface as a value, then history, then
the cast of characters around an artifact, then composition at build time, and finally
composition at run time. Each abstraction earns its place by *removing something from the
set of things you have to reason about*.

## 3. The Interface as a Value: Atoms

The foundation is a change of representation. A library's public API is usually something
you can only *inspect* — by reading docs, or diffing source. LIRA makes it a **value**: a
set of **atoms**, where each atom is the hash of one indivisible fragment of the interface —
one method, one field, one exported resource path — canonically encoded so that the same
declaration always hashes to the same atom, on any machine, from any compilation run.

The power is not in the hashing; it is in *where the fragment boundaries are drawn*. LIRA
requires the decomposition to follow the **folding principle**: any declaration whose
*addition* is safe for every consumer stands alone as an atom, and any fragment whose
addition would break a consumer is *folded into the value of its enclosing atom*.

Consider a class. Adding a new public method breaks nobody, so a method is its own atom —
the addition shows up as one new atom in the set. Narrowing an existing method's parameter
type breaks callers, so parameter types are folded *into* the method's atom — the change
replaces one atom with a different one, which reads as a removal plus an addition. The
children of a sealed type are folded into the parent — because adding a case breaks
exhaustive matches — while the methods of an open class stand alone. Every compatibility
rule of the language is encoded, once, in where the folds are.

And then something remarkable follows: **compatible extension is exactly the subset
relation**. A release is a safe upgrade from its predecessor if and only if the old atom
set is a subset of the new one. The engine that checks this has never heard of methods,
parameters, sealed types, or exhaustivity. It computes `⊆`.

Atoms come in two classes. **Rigid** atoms describe shape that compiled consumers link
against; within a compatible series they may be added but never removed or changed.
**Replaceable** atoms describe content that consumers *copy* at their own compile time —
the canonical example is an inline function's body, spliced into the caller. A replaceable
atom may be replaced (same key, new value) without breaking anyone's linkage — but consumers
who copied the old body are now *behaviorally stale* until they recompile, and because the
replacement is recorded, staleness becomes something a tool can compute rather than a
folk-knowledge reason to "rebuild everything just in case."

**What this buys.** You no longer reason about *changes*; you reason about *sets*. "Is this
upgrade safe?" stops being a judgment call about a changelog and becomes a membership
question with a mechanical answer — and the answer is the same for every language, because
each language's expertise was spent once, in deciding where its folds go.

## 4. History You Can Check: Snapshots, Lineages, and Derived Versions

Hash the sorted atom set and you get one hash for the whole interface: the **snapshot**,
the release's *API identity*. Two releases with equal snapshots present byte-for-byte
identical public APIs — a fact, not a claim. Alongside it sits a second identity, the
*implementation identity*: the hash of the release's actual content. The distinction pays
immediately: two releases with the same snapshot and different content are a **patch** of
one another, by definition rather than by assertion.

A module's history within one compatible series is its **lineage**: the ordered list of
snapshots the series has passed through. And now dependency compatibility has a shockingly
small definition: a dependency requirement names the snapshot its consumer was compiled
against, and a candidate release **satisfies** it if that snapshot appears in the
candidate's lineage. That's all. The candidate carries its own history, the history is
verifiable (each step's subset relation can be recomputed by anyone holding the releases),
and satisfaction is a lookup.

The diamond of §1 dissolves into the same lookup. A needs C at snapshot `x`, B needs C at
snapshot `y`; a single release of C serves both if and only if some published lineage
contains both `x` and `y`. The incompatible-major case is precisely the case where none
does — reported as a fact about histories, not as a resolver's silent choice.

Version numbers do not disappear; they are *demoted* — from input to output. Each lineage
step has a **grade**, computed from the sets: equal sets are a patch, pure extension is a
minor, anything else is a major (and a major is refused unless the publisher explicitly
asks for one — the algebra will not let a breaking change slip into a compatible series by
accident). The version number is then *derived* from the grades: a first release is
`1.0.0`, a minor step increments the minor, and the minor component is required to equal
the count of minor steps in the lineage — so the number is a projection of checkable
history, and a lying version is a detectable error rather than a Friday surprise. Human
names live in **tags** — immutable, signed labels like `jdk-19` — which carry the names
the world already uses without acquiring any algebraic authority.

**What this buys.** Trust moves out of the loop. You no longer trust a maintainer's
judgment about compatibility, or a registry's claim about history: every step of the
history is recomputable, and every decision — satisfaction, diamonds, upgrades — reduces to
membership in a verified list.

## 5. Many Languages, One Algebra: Disciplines and Guarantee Levels

Somebody has to turn a `.class` file, a `.d.ts` declaration, or an OpenAPI document into
atoms, and that somebody must know the language's rules. LIRA quarantines this expertise
into **disciplines**: named, versioned canonicalization procedures — `tasty/1` for Scala's
typed trees, `dts/1` for TypeScript declarations, `wit/1` for WebAssembly interface types,
`webidl/1` for browser APIs, `cheader/1` for C headers, `kmeta/1` for Kotlin's metadata,
`openapi/1` for HTTP surfaces. Everything above the atom level — snapshots, lineages,
grades, satisfaction — is language-blind and shared. A discipline is named for the
*carrier* it canonicalizes, never for a language, because one carrier serves many languages
(three languages emit classfiles) and one language emits many carriers (Kotlin alone has
three).

Disciplines are also where honesty about *guarantee levels* lives. Each discipline states
which levels its atoms certify, and may claim only what its canonical encoding actually
enforces. `tasty/1` certifies recompilation — it atomizes typed trees, and the JVM's
*linkage* surface lives in a lower representation it never sees. A bytecode discipline can
certify linkage and only linkage. The two levels genuinely diverge in both directions
(§2's examples are the spec's own), so LIRA refuses to average them into one claim.
Instead, an **ecosystem profile** — a named, versioned predicate set like `jvm/1` — checks
the invariants the atoms don't carry, and when a release passes the atom algebra as a
minor but fails a linkage predicate, it publishes the shortfall explicitly: `breaks
linkage`. The reading is precise: *consumers who recompile may take this as a minor;
consumers relying on already-compiled artifacts must rebuild.* One release, two audiences,
both told the truth.

Nothing escapes the algebra by being unglamorous. Content no discipline claims falls to
`opaque/1` — one rigid atom per file, so any change is conservatively a major. Resources —
config files, templates — enter through `resource/1`, which can guarantee a path's
*presence* without freezing its bytes, or track its bytes as replaceable content, or
declare a whole directory contractless. There is no category of "stuff the compatibility
system doesn't see."

**What this buys.** Cross-language reasoning without a lowest common denominator. Every
ecosystem gets its own rules at full fidelity — folded into atoms or checked by its
profile — while consumers reason over one algebra and one vocabulary of claims. And every
compatibility statement now names its level, so "compatible" can never again mean four
different things in one sentence.

## 6. Naming the Roles: Universes, Hosts, and Deliverables

Ask a working developer what "platform" means and you will get, in one afternoon, four
incompatible answers: the JVM (a place code *runs*), Scala.js (a compilation *target*),
an executable jar (a *packaging*), and "the web" (an *ecosystem*). The confusion is not
carelessness — the same artifact really does play different parts in different scenes.
LIRA's response is to name the *roles*, at which point the tangle dissolves.

A **format** is just a byte-level encoding — classfile, TASTy, `.d.ts`, WASM — with no
intrinsic role at all; the same format means different things at different points of a
pipeline.

A **universe** is a place where *independently-published libraries compose*: `jvm`, where
classloading composes arbitrary classfile sets; `sjsir` and `nir` for Scala.js and Scala
Native; `js` and `wasmc` (the WASM component universe) reserved for the ecosystems that
compose there. The litmus test is composition, and it is sharp: Android's DEX format fails
it (libraries ship classfiles; dexing happens after the library phase closes), so DEX is
not a universe, however platform-ish it feels.

A **host** is a runtime environment that executes *closed* artifacts — artifacts past
composition, awaiting linking with nothing: the JVM at a JDK version, a browser with its
Web APIs, Node with its builtins, a WASI runtime with its world, an operating system with
its libc. Nothing composes *in* a host; things run *on* it.

A **deliverable** is what a build produces: the pair of a closed artifact format and the
host contract it targets — an executable jar on JDK ≥ 21, an ES-module bundle in a baseline
browser, a WASM component in a WASI 0.2 world. It is a classification, not a thing (and
not a "deployable release," which is a `.lira` file we will meet in §10).

Between these roles run exactly four kinds of edge: *dependencies* compose libraries
within a universe; *joins* merge universes into one application (a bundler linking Scala.js
output with TypeScript modules; a system linker combining native objects with C libraries);
an *egress* closes over a universe's artifacts and produces a deliverable; and
*requirements* point from content to the hosts and services it runs against. No edge leads
back out of a deliverable: closure is terminal.

This taxonomy is what lets a single `.lira` file carry a library *whole*. A release's
content is stored in **sections**, one per universe (per integration — see below), as
overlays on a shared root, so content identical across platforms is stored once and
divergence is visible in the manifest rather than buried in archives. And across every
section, one invariant is enforced at publish time: **the release presents one API
everywhere**. Implementations may differ per universe; interfaces may not — a library whose
API genuinely differs by platform is two modules wearing one name, and LIRA makes it say
so. Because of that invariant, everything upstream — snapshot, lineage, satisfaction —
remains single-valued, and consumers never reason about platforms at all when they reason
about compatibility.

The same trick tames the other multiplicity: a release built against two majors of a
dependency (for consumers who cannot move together) declares two **integrations** —
alternative dependency vectors, forming a matrix with the universes. Integrations, too,
are invisible to compatibility reasoning: every cell presents the same interface, so
choosing one changes what else must be present on your buildpath, never what the module
offers you.

**What this buys.** The word "platform" stops costing you thought. Each thing you deal
with has one role with known edges, multiplicity is contained inside the artifact instead
of multiplying artifacts (no `-js`, `-native`, `-legacy` suffix explosion in the
namespace), and two whole dimensions — platform and integration — are provably irrelevant
to the compatibility questions you ask every day.

## 7. Both Sides of the Arrow: Used-Sets and Spanning

Everything so far describes what a release **provides**. The complementary abstraction
describes what a consumer **needs**: its **used-set**, the set of a dependency's atoms the
consumer actually touches — computed by tooling, not authored, and closed transitively
through the reference lists of any inline content it copied.

The two sides are formal duals, and the duality is load-bearing. A provider evolves safely
when its atom set *grows*; a consumer evolves safely when its used-set *shrinks*; and every
judgment in LIRA is one inclusion, `used ⊆ atoms`, read from one side or the other. (Even
the folding principle of §3 is this duality in miniature: what a consumer *receives* — a
return type — sits covariantly and stands alone; what a consumer must *supply* — a
parameter — sits contravariantly and folds. Position determines polarity, exactly as
variance works in a type system.)

Used-sets convert several folk practices into arithmetic:

- **Spanning.** A module compiled against release A of a dependency is provably valid
  against release B — *including across major versions* — whenever `used ⊆ atoms(B)`. The
  terrifying major upgrade becomes, for many consumers, a checked no-op: the breaking
  changes were all in atoms they never touched. Spanning even works **across modules**:
  a requirement on one provider is provably satisfied by a different one — a rewrite, a
  mock, a competing implementation of a standard — whose atoms cover the used-set.
- **Staleness.** After a dependency's minor release, the consumers that should recompile
  are exactly those whose used-sets intersect the replaced atoms recorded in the step's
  delta. Everyone else provably doesn't care. "Rebuild the world nightly, just in case"
  becomes a query.

**What this buys.** Blast-radius reasoning. Today, "who is affected by this change?" is
answered by pessimism (assume everyone) or optimism (assume nobody); used-sets make it a
set intersection, per consumer, computable from metadata alone — and they give the *needing*
side of the ecosystem a first-class, verifiable artifact for the first time.

## 8. The Buildpath: Composition as Audit

With provides and needs both first-class, build-time composition stops being a search and
becomes an **audit**. A **buildpath** is simply a set of `.lira` files you intend to use
together — unordered, because the rules make ordering irrelevant — and its validity is
decided by a handful of checks, every one of them computable *from manifests alone*,
without decompressing a byte of content: one release per module; no two modules claiming
overlapping package namespaces or the same resource path; every named dependency present;
every requirement satisfied through lineage (or spanned via used-sets); every declared
ecosystem profile's predicates holding across the whole set.

Note what is absent: there is no resolver in the specification. LIRA deliberately audits
the buildpath it is handed rather than deciding which releases to include — that harder
problem (and it is much harder: choices interact) is left to tools, which inherit it from
their own ambitions rather than from the format. Where a release offers integrations, the
choice between them is deterministic — publisher-ranked, consumer-pinnable — and provably
free of interaction between releases, so even that resolution is linear, with a specific
diagnosis when it fails: *this* release, *this* integration, *this* rule.

One more property closes the loop with the world as it is. Each section deterministically
derives a **canonical derivative artifact** — for JVM content, a reproducible jar, byte-for-
byte — whose hash the manifest declares. So a tool holding a directory of *ordinary jars*
can hash each one and look it up: recovering the release it came from, its API identity,
its whole compatibility context, even *which integration* it is — information no naming
convention could carry. Legacy classpaths become queryable objects.

**What this buys.** Composition becomes a judgment you can re-run anywhere — locally, in
CI, at a registry — with byte-identical verdicts, in milliseconds, because it reads
metadata rather than artifacts. The failure modes come with names attached: which rule,
which module, which snapshot.

## 9. The World Outside the Code: Hosts as Contracts

Section 6 named the host; this abstraction *publishes* it. A **host contract** is an
ordinary release whose atoms are capabilities — the JDK's API surface (from the stub
signatures the JDK itself ships), a browser baseline (from the Web IDL the standards
process publishes), Node's builtins (from its `.d.ts`), a WASI world (from its WIT), a
libc (from its headers), or a plain authored list of POSIX commands where no formal
carrier exists. Vendors' marketing names ride along as tags: "compatible with JDK 19" is
a tag resolving to a snapshot, and satisfaction is — once again — lineage membership.
Nothing new was invented: hosts entered the *same* algebra as dependencies.

A release's sections then declare what they **require** of their surroundings — and only
what they require: a library whose JVM implementation shells out to POSIX tools carries
that requirement on its `jvm` section alone, and its browser consumers never hear about
it. Requirements travel with used-sets too, so a library that needs only twelve libc
symbols is satisfied by any host covering those twelve — portability as a proof rather
than a hope.

Requirements are the one thing in a manifest that cannot be recomputed from content — no
analysis can verify what code will *need at runtime* — so LIRA labels them honestly as
authorial and gives them their own verification moment: **probing**, at install or launch,
where the environment is checked against the requirement the way a payload is checked
against a hash. Build-time validation, publish-time recomputation, run-time probing: three
moments, each verifying what only it can.

**What this buys.** "Works on my machine" becomes a statement with a truth value. The
environment's side of every interaction is a versioned, published, checkable object, so
the compatibility question between code and world is decided by the same arithmetic as the
question between code and code — before anything is deployed to find out the hard way.

## 10. The Second Composition: Services and Environments

Now the turn that doubles the algebra's reach. LIRA observes that **a running service is a
host to its consumers**: its versioned capability interface is its network API. Nothing of
a provider service ever composes into a consumer's artifact — the provider is *environment*,
exactly as a JDK is. So deployment needs no second theory; it needs the first theory,
transposed.

A **deployable release** is a `.lira` file carrying a closed artifact — an executable jar
stored directly, or a container image *pinned* by its registry digest — together with the
two things that matter about it: the surface it **serves**, published as its own atoms
(recomputed from the interface description it ships, an OpenAPI document say — this is
*checked*, not testimony), and the capabilities it **requires** of its environment: the
platform it runs on, the services it calls, each with a used-set of what it actually
touches. The service is its own contract; its lineage is that contract's history; every
grade and guarantee level of §4–§5 applies verbatim. Wire compatibility turns out to be
linkage transposed ("no recompilation" reads "no redeployment"), and regeneration —
clients rebuilt from the new description still build — is recompilation transposed.

An **environment** is the buildpath's runtime counterpart: a set of deployable releases
and host contracts intended to *run* together. Where a buildpath is judged and then
closed over, an environment is judged and then *lived in* — so its statement is itself a
signed, versioned release, authored by an **operator** (the format's other signing role
beside the publisher), whose manifest states desired state in three record families:
**grants** — the platform contracts the environment supplies (its Kubernetes, its managed
database, the vendor API nobody here deploys); **deploys** — the releases intended to run,
each pinned to exact bits; and **bindings** — the addresses at which providers answer.

The environment deliberately *drops* the buildpath's uniqueness rule — two releases of one
service running concurrently is the normal state of a rolling deployment, not an error —
and replaces it with the binding: the address disambiguates at run time what uniqueness
disambiguated at build time. Validity quantifies over every concurrently-serving release,
which is exactly the mid-rollout guarantee you want: during the overlap, every consumer
must be satisfied by *both* the predecessor and the successor. A **deploy** is then just a
transition between environment states, and a release is *deployable* precisely when the
states the transition passes through — including the overlap — are valid. Decided from
manifests, before anything moves.

The payoffs land directly on §1's worst afternoon:

- The consumer nobody remembered is *found by closure*: removing or breaking a provider
  fails validity, and the judgment names the objecting consumers.
- A contract change that `breaks linkage` is exactly a **coordinated deploy** — safe for
  consumers who redeploy against the new description, unsafe for consumers already
  running — named in the manifest rather than discovered in an incident, with the set of
  consumers that must move *computed* from used-set intersections.
- Consumer-driven contract testing becomes set inclusion — what a broker establishes by
  recording and replaying traffic, the manifests establish by arithmetic, per consumer,
  per provider release, without running a test. A mock or staging stand-in provably
  serves exactly the consumers whose used-sets it covers.
- Drift — the running world diverging from the desired state — is detected by probing and
  answered by re-judgment, with a vocabulary for saying precisely what is wrong: which
  module, which snapshot, which address.

**What this buys.** The deploy-time question becomes the build-time question, answered by
the same three objects — atoms, lineages, used-sets — from signed manifests, continuously,
for as long as anything runs. The schema linter, the contract broker, the registry, and
the runbook were each a corner of this; here they are one judgment, and its bookkeeping is
the format's.

## 11. One File

All of this metadata travels *inside* the artifact it describes. A `.lira` file is: a
four-byte magic (`βιβλ` — the Greek root of *library*), a manifest in canonical binary
form (rendered as readable text by any conforming tool, and shown as text throughout the
specification), and a compressed payload of content-addressed blobs — every distinct byte
string stored once, however many platforms and integrations share it. The manifest is the
release's *complete* compatibility interface: identities, lineage, atoms listings,
dependencies, requirements, sections. Every judgment in this introduction reads manifests
only; payloads are for compilers.

Verification is re-execution. Blob hashes, tree shapes, atomizations, snapshots, lineage
grades, profile predicates, derivative hashes — a verifier recomputes the construction
bottom-up, and a registry must do so before accepting a release, because that recomputation
is what makes manifests trustworthy for everyone downstream who reads *only* manifests.
Nothing in the format is trusted testimony, with two labelled exceptions — requirements
(checked instead by probing, §9) and source-identity claims (checked instead by
independent rebuild) — and signatures (post-quantum by default) bind the whole under the
publisher's, and an environment's under the operator's, key. Production is deterministic:
same toolchain, same inputs, byte-identical file — which is what makes the implementation
identity meaningful, caches sound, and independent reproduction possible at all.

**What this buys.** No side-channels. There is no database that knows things the artifact
doesn't, no wiki page that explains what the jar really needs, no CI job whose green tick
you have to trust. The artifact carries its own case, and anyone can re-try it.

## 12. The Edges of the Map

Abstractions earn trust by admitting what they do not cover, and LIRA is explicit about
its edges. **Behavior** is never certified: no hash scheme can promise that unchanged
signatures compute unchanged results, so patch and minor grades bound interface and
copied-content change, and behavior remains the publisher's signed word — mitigable by
attestation of test evidence, which LIRA anticipates but does not specify. **Probing** is
advisory: it detects, it never licenses. **Dependency resolution proper** — choosing which
releases to put on a buildpath — is left to tools, deliberately, because it is where
search complexity lives. **Reconciliation** — making the running world match the judged
one — is the orchestrator's business; LIRA supplies the desired state and the predicate,
and moves no process. And the specification even marks where its central structure stops:
content whose readers and writers *both* evolve against retained data — a message topic's
history — carries both polarities at once, and a single lineage is honestly the wrong
shape for it.

## 13. The Shape of the Whole

The arc, restated in one paragraph. The question *will these work together?* is today
answered by testimony — version numbers, changelogs, runbooks, tribal memory — and §1's
afternoons are what testimony costs. LIRA replaces testimony with arithmetic, by a chain
of abstractions each of which removes a class of reasoning: **atoms** turn interfaces into
values, so compatibility is set inclusion; **lineages** turn history into a verifiable
list, so satisfaction is membership and versions are projections; **disciplines** and
**guarantee levels** let every language keep its own rules while claims stay honest and
comparable; **universes, hosts, and deliverables** dissolve "platform" into roles, so
multi-platform multiplicity stays inside one artifact and out of your reasoning;
**used-sets** make the needing side first-class, so blast radius, spanning, and
substitution are computations; the **buildpath** makes build-time composition an audit
over manifests; **host contracts** bring the environment into the same algebra; and the
**environment** transposes all of it to run time, where a running service is a host, a
deploy is a judged transition, and the consumer nobody remembered is a closure failure
with a name. One question, two moments, three objects — atoms, lineages, used-sets — and
a single small algebra, carried in the artifact, checkable by anyone.

## Reading the Specification Suite

The normative core is [`lira.md`](lira.md): container, atoms, disciplines, snapshots,
lineages, buildpath, and the manifest schema. Around it: [`hosts.md`](hosts.md) (host
contracts and requirements), [`services.md`](services.md) (deployable releases and
environment validity), [`environments.md`](environments.md) (environment releases,
bindings, provisioning), and one document per discipline — [`tasty.md`](tasty.md),
[`classfile.md`](classfile.md), [`dts.md`](dts.md), [`wit.md`](wit.md),
[`webidl.md`](webidl.md), [`cheader.md`](cheader.md), [`jsig.md`](jsig.md),
[`kotlin.md`](kotlin.md), [`openapi.md`](openapi.md), [`tels.md`](tels.md) — with the JVM
ecosystem profile in [`jvm.md`](jvm.md). A reader who has followed this introduction can
start anywhere; the specification's §4 taxonomy and §10.5 polarity note are the two
passages that most repay early reading.
