# Fever: the Scala compiler service

Fever is the Scala half of the build tool: the compiler wrapper that Fury delegates Scala
edges to, and the long-running service that performs one-off compilations and other
source-code operations — `scalac`, but resident and faster — including, in time, most of
the work an LSP server does. It is a Pyrocosm-family tool like [Fury](fury.md), developed
beside it in this repository and separated when their interfaces have grown apart.

The reason it is a separate tool is stated once and governs every placement decision
below: **Fury is language-agnostic; anything that knows what a `.scala` file is belongs
in Fever.** Fury sees Fever only as one tool among others, reached through the tool
contract; Fever sees Fury only as the source of build configuration.

## 1. What Fever is

Three things, sharing one library:

- **`fever.compile`** — a library with no process assumptions: it drives scalac through
  Soundness's anthology (`Scalac`, `ScalacDriver`, the `classfile`, `sjsir` and `nir`
  edges) and exposes compile and query operations. Compiles are **cold**: each
  invocation compiles its inputs afresh against its context cells and holds no
  incremental analysis between steps. A resident Fever keeps a warm JVM and loaded
  compiler classes, which is not incremental state; memoization in Fury is the only
  incrementality, and the warm-equals-cold law of builds.md §14.5 is satisfied
  trivially. Incremental compilation is revisited only if compile times demand it.
- **`fever.tool`** — the `lira.tool` implementation (builds.md §14.3): `descriptor`,
  which must extract a document equal to the published `scalac.tool.tel`, and `invoke`,
  which maps a BinTEL invocation onto a compile. This is what Fury loads.
- **The `fever` daemon and CLI** — `pyrocosm.Tool.standard` over an Ethereal daemon:
  `compile` for ad hoc use, `lsp`, `quit`, `install`. The daemon is long-running by
  design; it hosts `fever.compile` sessions for editors and for builds dispatched to it.

## 2. One Fever per Scala version

Fever depends on the compiler's API, so **each Scala version has its own Fever
release**, built against that compiler. Fury supports multiple Scala versions by
resolving the Fever release that matches the `scalac` version locked in `build.lock`:
the registry ([`fury.md`](fury.md) §7) maps the built-in tool name `scalac` to the Fever
coordinate family, and the selector chooses the release. A build using two Scala
versions uses two Fevers, and Fever's release identity enters every Scala step's
`inputs/1` digest and the cell's section-scoped Tool record, alongside the compiler's
own identity — closing the omission noted at fury.md's memoization step.

Fever is therefore published and loaded **exactly as a third-party `lira-tool` release**
(fury.md §6): verified at install grade, checked for loadability from manifests, loaded in
its own classloader. Fury contains no special case for its own compiler wrapper, and the
third-party mechanism is built when Fever first compiles anything.

The repository layout for several concurrent versions is deferred until a second Scala
version is needed; until then Fever builds against the one pinned compiler from a single
`src/fever` module.

## 3. The contract Fever implements

The `lira.tool` contract lives in this repository's `lira-tool` module and is built on
anthology's own `Edge`, `Toolchain`, `Universe` and `Component` types rather than
duplicating them; where anthology's types fall short of `tool.schema.tel` (input and
context roles, employed and emitted disciplines, setting effects, parameterized edges),
anthology is extended, filed as Soundness issues.

The ABI is **BinTEL**: `Invocation` and `Outcome` are canonical BinTEL documents under
schemas published beside `tool.schema.tel`, and the Scala trait is a thin typed view over
them. Consequences Fever relies on:

- the same payload crosses a method call, a UNIX socket to the Fever daemon, or the
  swarm channel, so where Fever runs relative to Fury is a deployment choice, not a
  contract difference;
- Fury loads each tool release in a classloader whose parent exposes only the JDK and
  the contract, so Fever's Scala, Soundness and anthology versions are independent of
  Fury's — necessary, since Fever tracks compiler versions and Fury does not;
- hashing the invocation document is the natural derivation of the step identity.

Settings reach the compiler only through the descriptor's classified keys: a table from
TEL setting names to scalac flags, each `affects output` or not, seeded from
`scalac.tool.tel`; an unknown key is refused, per the hashability law (builds.md §3.1).
Diagnostics are anthology `Notice`s mapped to the contract's `Diagnostic`. Used-set
emission from TASTy via degustation, for the guarantees loop of builds.md §12, waits for
presumptions to be in scope.

## 4. The relationship with Fury, kept flexible

How independently the two run, and over which channels, is deliberately left open, and
the design above is what makes leaving it open free. The channels that remain available:

- **In-process**: Fury loads `fever.tool` as a plugin — the fastest path, unsandboxed
  (builds.md §14.3's trade-off), and the first to be implemented.
- **The Fever daemon as a local worker**: Fury dispatches Scala steps to a resident
  Fever over a UNIX domain socket with the same BinTEL framing the swarm uses. This is
  the path that shares a daemon between builds and editors.
- **Remote**: a Fever on a swarm member is a capability that member advertises by tool
  release identity; placement treats it like any other capability.

Fury needs no knowledge of Fever beyond the registry mapping and the contract; Fever
needs Fury's build configuration — which module a file belongs to, its context cells,
its settings, its Scala version — and obtains it through whichever route the LSP
decision (§5) settles.

## 5. The LSP

Fever should ultimately do the LSP work, or most of it, informed by Fury. The routing is
**undecided by choice**, since the LSP matters later; three routes stay open and
`fever.compile` stays process-agnostic so that any of them remains possible:

1. the editor connects to Fever, which queries Fury (a read-only plan query over Fury's
   socket) for the module's context cells;
2. Fever recomputes the plan itself from `build.tel`, using Fury's model and planner as
   libraries — no runtime coupling, at the risk of two planners drifting;
3. **Fury brokers the LSP session** to the right Fever — attractive because Fury already
   knows which module, which Scala version and therefore which Fever release a file
   belongs to, and there is one Fever per version.

The model for the server itself is `tel`'s LSP on Exegesis inside an Ethereal daemon,
with editor glue as in that repository.

## 6. Where Fever sits in the ladder

Fever's first appearance is fury.md's step 5: Fury computes a one-step DAG and sends a
BinTEL invocation to `fever.tool`, receiving diagnostics and an output tree hash. That
step establishes the `lira.tool` contract in code and its BinTEL schemas. The LSP is a
separate track, independent of the engine, swarm and web tracks.

Soundness prerequisites, beyond those fury.md §4 already files: #2027 (diagnostics
dropped by `ScalacEdges`) and #2026 (a compiler-version accessor) are needed at step 5;
#2028 (retained compiler sessions) no longer gates anything, since compiles are cold.

## 6a. Scripts

Fury's single-file scripts (fury.md §12a) reach Fever through the contract's optional
script side-trait, which Fever implements for the `scala` form and is expected to be the
only implementation of for some time. Fever's side of the convention:

- **Entry point.** The body must define `def main(using Runtime): Unit` at the top level;
  Fever's descriptor states this convention, and a body without it is a compile error
  naming it. `Runtime` is the capability bundle a script receives from Fury — its
  arguments, environment, working directory and standard streams — so scripts never
  reach for ambient state.
- **Compilation.** The body is compiled as one module through Fever's ordinary `jvm`
  edge, against the dependencies the header's `include`s resolve to, with the header's
  settings and flags; Fever then produces a runnable deliverable (a `jvm-app` by default)
  with a generated launcher that constructs the `Runtime` and calls `main`.
- **The script is a module.** Nothing in Fever distinguishes a script's compilation from
  a project's: the synthetic build Fury derives from the header is what Fever sees, so
  the script path adds only the entry-point check and the launcher, and every diagnostic,
  memo hit and identity rule applies unchanged.

## 7. Open questions

1. The LSP route (§5), and with it whether Fury gains any notion of hosting a service.
2. The multi-version repository layout (§2).
3. Whether the per-version Fever family needs a naming convention in the registry beyond
   the selector (`fever` for 3.9 and another for 3.10 resolve by the compiler version;
   whether the coordinate carries the version or the tag does is a distribution.md §2
   question).
4. What Fever offers beyond compilation and the LSP — formatting, semantic rendering as
   in flame's REPL, TASTy inspection — and which of those are edges (graded, in the
   descriptor) versus daemon commands (not).
5. The `Runtime` a script's `main` receives (§6a): which capabilities it bundles, whether
   it is Fever's type or a small published contract module of its own so that scripts
   compile against a lineage rather than a Fever release.
