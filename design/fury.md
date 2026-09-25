# Fury: the build tool, grown incrementally

Fury is the build tool that consumes and produces `.lira` files, designed in
[`builds.md`](builds.md) and audited there against the format. This document is its
**design record and roadmap**: the shape of the tool, the decisions taken (§13, kept so
they are never relitigated), and the ordered ladder of increments by which `fury` grows
from a tiny CLI into the tool `builds.md` describes, with the design item each increment
depends on and the assumption each tests.

Fury is deliberately **language-agnostic**. Everything that knows what a `.scala` file is
lives in [Fever](fever.md), the Scala compiler service, which Fury reaches only through the
tool contract of builds.md §14.3. Both are Pyrocosm-family tools — `pyrocosm.Tool.standard`
for the command line, an Ethereal daemon, the Configurator cascade for configuration, the
`Tool.Web` hook for a browser front-end — developed in this repository beside the `lira`
tool and separated into their own repositories when their interfaces have grown apart.
The three are **separate binaries sharing library code**: `lira` reads, verifies and serves
LIRA files ([`tool.md`](tool.md)); `fury` builds them; `fever` compiles Scala for `fury` and
for editors. This settles the relationship [`gaps.md`](gaps.md) §4 left open.

The first bootstrap target is this repository's own `lira` module: one module, `jvm`
universe, Soundness bundle dependencies. It is a stepping stone to Soundness's roadmap
item *tool-4* — `fury build` builds Soundness from a clean checkout, attestation-equal to
the Mill build — which is the milestone's criterion, not its first step.

## 1. Shape

Fury is designed to be fast and parallel. A core **engine** plans a build and executes it by
delegating every step to a **tool**, never doing a tool's work itself. It runs as a
**daemon**, one per user, resident in the background: builds never interact with one
another, but the daemon knows the load on the machine and schedules across every build it
is running. Work can be dispatched to another instance of Fury on another machine — because
that machine has an architecture the build needs, or to share the load — and the machines
that have accepted each other form a **swarm**, between which project sources are kept in
sync in the background so that a dispatched build does not pay the latency of transferring
them. A **web interface** lists builds, one entry per run, in the manner of a CI system.

Four facts make the rest of this document short:

- **The DAG is static.** A build file plus one invocation determines a DAG of steps
  before anything runs; no step's specification may change, or become conditional, as a
  consequence of an earlier step's outputs. The whole graph, and every step's identity,
  is therefore known at planning time — which is what makes *where* to run each step a
  planning decision (§3) rather than a runtime one.
- **A step is one edge invocation at one cell** — one tool edge (builds.md §14.2) run at
  one (module, universe, integration case, option case) — identified by its `inputs/1`
  digest (builds.md §3.1), computable before running. Two steps with the same identity
  are the same step, on any machine. Steps are the unit of memoization, of dispatch and of
  reporting.
- **Steps exchange only hashes.** Inputs and outputs are trees in the content-addressed
  store (builds.md §14.5); a worker that lacks a blob fetches it by hash. Nothing else
  crosses a process boundary.
- **The tool contract is BinTEL.** The invocation a tool receives and the outcome it returns
  are canonical BinTEL documents, so the same payload crosses a method call, a UNIX socket
  and the swarm channel; in-process versus out-of-process is a deployment choice per tool,
  not a contract difference.

## 2. Components

Fury's code is one package, `fury`, with these parts; the placement rule is that a part
which knows what a step, a store object, a peer or a run is belongs here, and a part which
knows a language belongs in a tool.

| Part | Responsibility |
| --- | --- |
| `cli` | `Tool.standard` dispatch: `build`, `check`, `resolve`, `lock`, `extract`, `workspace`, `watch`, `swarm …`, `log`; completions |
| `model` | typed decoding of `build.tel`, `local.tel`, `build.lock`, `registry.tel`; the milestone's refuse/ignore lint (§9); the precedence layering (§11) |
| `plan` | invocation → static DAG of steps; hyperpath resolution and the covering-edge rule (builds.md §14.6); step identities; placement (§3) |
| `engine` | executes a DAG: memo lookup, workspace materialization and teardown, dispatch to workers, the shared scheduler, the run log (§5) |
| `tools` | the registry (§7) and the `lira.tool` host: verification, loadability, classloading of tool releases (§6) |
| `worker` | the worker abstraction: accepts a BinTEL invocation, returns a BinTEL outcome; implementations are the in-process plugin, a local tool daemon over a UNIX socket, and a swarm member |
| `swarm` | membership, keys, the Fury protocol, capability advertisement, source sync (§8) |
| `web` | the `Tool.Web` front-end: the run list, per-run DAG, swarm status (§10) |
| `launcher` | the `@main`, wrapped by burdock's `externalize`; Fury's own Ethereal daemon |

Shared with `lira`: the store, the derivative and memo tiers, hashing domains and manifest
types, as the published `lira-core` library; and the `lira.tool` contract as the published
`lira-tool` module (§6).

Reused from Soundness and Pyrocosm rather than written: `acyclicity` for the DAG,
`parasite` for structured concurrency, `surveillance` for file watching, `coaxial`,
`telekinesis` and `perihelion` for sockets, HTTP and WebSockets, `enigmatic` for ML-DSA
keys, `pyrocosm-cli` and `pyrocosm-web` for the command line and the front-end. No tool
in the family has a DAG scheduler, a peer protocol or a sync protocol; those are new.

## 3. Steps, workers and placement

The **worker** is the load-bearing interface. Everything the engine does with a step —
memoize it, run it here, send it to Fever, send it to another machine — is one operation:
hand a BinTEL invocation (step identity, edge and tool identity, input and context tree
hashes, output-affecting settings, presumptions, cell coordinates) to a worker, and receive
a BinTEL outcome (output tree hashes, used-sets, diagnostics, resource statistics) as a
stream of events. A worker that lacks an input blob fetches it by hash from the dispatcher;
the dispatcher fetches outputs it lacks afterwards. Memo hits on a worker are as good as
local hits.

**Placement** — where each step runs — is a first-class, iteratively improvable decision.
Because the DAG is static, the graph, its input sizes, the memo state, each worker's
have-set and each worker's advertised load are all known before anything runs, so the
planner can choose by cost: the transfer of inputs and outputs (blobs a worker already
holds cost nothing); **chaining** dependent steps on one worker so intermediate trees never
move; and **critical-path** steps run locally so that no transfer latency sits on the
longest path. The first implementation is greedy — local unless incapable or overloaded —
behind an interface that exposes the graph, the have-sets and the loads, so that better
policies replace it without touching the engine. Every completed step records how it
behaved under load, and the policy's objective is always the **minimum finish time** of
the build.

## 4. Spaces and memoization

builds.md §14.5's three spaces apply unchanged: the project root is authored content only
and read-only to tools; the store holds every output, intermediates included, as trees;
the workspace is a per-step ephemeral view, retained and named on failure. A step's
outputs cache under its identity in the store's memo tier, so an unchanged step is a hit
with no tool run — the only incrementality there is. **Compiles are cold**: tools hold no
private incremental state between steps, so the warm-equals-cold law is trivially met and
the `lira.tool` contract needs no retained-state hook. This is revisited only if compile
times demand it.

**All build products and by-products live in the centralised store** — outputs, memo
entries, run logs — and a local setting may redirect the store into the project directory
for a project that wants its outputs beside its sources. That setting is a machine fact
(§11), never `build.tel`.

## 5. The engine

1. **Plan**: a static `Dag[Step]`; steps depend on the steps producing their input and
   context trees and on tool-provider steps; dependency cells resolved from the store or
   the index are leaves. Codegen edges (§12) are ordinary steps whose output is a source
   form.
2. **Memo pass**: each step is annotated hit or miss by identity; hits are pruned with
   their outputs immediately available.
3. **One shared scheduler** across every build the daemon is running: identical steps in
   concurrent builds are deduplicated by identity, so "builds never interact" means no
   shared mutable state, not no shared results. Admission is governed by a
   **cost-weighted budget**: the daemon reads OS metrics (load average, memory, cores),
   each swarm member advertises its load, and the run log's per-step statistics feed a
   heuristic — developed iteratively, greedy at first — for when and where to start each
   step. Priority follows the longest remaining path.
4. **Workers** (§3) run admitted steps; the engine materializes workspaces for local
   workers and sends only hashes to remote ones.
5. **Events**: every state change — planned, memo hit, admitted, dispatched to, started,
   diagnostic, done, failed, cancelled — is appended to the run's log; completion events
   carry the step's resource statistics (wall and CPU time, peak memory, load and
   concurrency at start, worker, bytes transferred). The CLI renderer, the web interface
   and the swarm status all read this log.
6. **Failure**: a failed step skips its dependants; independent steps continue, so one
   build reports every failure it can. Fail-fast is an occasion-class flag. There are **no
   step timeouts** — a run is cancelled explicitly or not at all, and the interface shows
   elapsed time.
7. **`fury watch`** re-runs the command on source change: the daemon recomputes the DAG,
   cancels running steps whose identity changed, keeps the rest, and starts a new run.
   Cancellation reaches remote workers through the protocol. Watching starts only with an
   explicit `watch` invocation in a project root; a build alone never starts one.

## 6. Tools and plugins

Tools reach Fury by one path, whether built in, LIRA-distributed, or Fever itself:

1. **Declaration** — `tool <name> [<module> [<selector>]]` in a toolchain; a distributed
   tool is resolved through the index like a dependency and pinned in `build.lock`.
2. **Verification** — install-grade on ingest, like any release. **Loadability is
   deployability** (builds.md §11): the tool's `requires lira.tool` snapshot must lie in the
   lineage of the contract Fury provides, or be spanned by the tool's used-set over it;
   decided from manifests before any code is loaded, and reported with both snapshots
   named when it fails. No further trust act is required: signature, lock and loadability
   suffice, under the directory trust `lira trust` already established.
3. **Planning without loading** — the descriptor is `tool.tel` in the release's tree, so
   edges, roles, forms and setting classifications are read manifest-side; the DAG and
   every step identity exist before any plugin runs.
4. **Loading** — the `lira-tool` jar is loaded in a **classloader per tool release** whose
   parent exposes only the JDK and the `lira.tool` contract classes. Because the contract
   is BinTEL, the invocation crosses the loader boundary as bytes: a plugin's Scala,
   Soundness and anthology versions are independent of Fury's.
5. **Invocation** — through the worker abstraction; a plugin available on a swarm member is
   a capability that member advertises by tool release identity.
6. **Identity** — the tool's coordinate and implementation identity enter every step's
   `inputs/1` digest and the cell's section-scoped Tool record; a development-release tool
   makes consuming releases unpublishable (the toolchain analogue of L118, builds.md §11).
7. **Bootstrap** — a tool built in the same build adds tool-provider steps to the DAG; the
   self-hosting case resolves by stage0 from the prebuilt release (builds.md §11).

The contract itself is the `lira-tool` module of this repository, built on anthology's
`Edge`, `Toolchain`, `Universe` and `Component` types rather than duplicating them —
accepting, for now, a dependency on a Scala-oriented library; anthology may later gain
polyglot flexibility, and where its types fall short of `tool.schema.tel` it is extended,
filed as Soundness issues. **Fever takes exactly this path**: it is a `lira-tool` release
per Scala version, and the registry maps the built-in name `scalac` to the Fever
coordinate family, so the plugin mechanism is built the first time Fury compiles anything.

Of the three trust tiers of builds.md §11, **raw local binaries are deferred**: the first
milestones know built-in and LIRA-distributed tools only. Sandboxing is likewise deferred;
the WIT twin of builds.md §14.3 needs no contract change because the payload is already
BinTEL.

## 7. The registry

The machine-readable registry universes.md §6 proposed as `universes.tel` is
**`registry.tel`** — the name reflects that universes are one of its sections — under a
`registry.schema.tel`, with [`catalog.md`](catalog.md) as its prose precursor. One document
holds: **forms** (source forms with their extensions; universes with their carriers and
disciplines; deliverables with their closed format and host contract; parameterized
families with the parameter's domain); **parameters** (`triple`, `platform`, and the
mapping between them for builds.md §15.2's backward flow); **kinds** (presumption kind →
discipline, many-to-one); **tools** (built-in tools, each descriptor a sibling
`<name>.tool.tel` under `tool.schema.tel`, referenced by name — `scalac.tool.tel` exists —
and distributed tool families with their selector-to-release rule); **hosts** (contract
families with default stewards and tag schemes); and **schemas** (the TEL schemas the tool
ships, with coordinates). Edge kinds are not stored: compiler, egress, join and packaging
derive from the node kinds at an edge's ends (builds.md §14.2).

Fury reads it from resources and decodes its whole tool registry from it; `lira` reads the
same file for its extension and kind tables. It is publishable as a `tels/2` module plus a
data release, the shipped copy being stage0. **Extension is by published registry layers
only**: a build names one by coordinate (`registry acme.dev/forms 1`), it is locked like a
dependency and merged additively, redefinition being a lint on L107's precedent. No
registry entry may appear directly in `build.tel`.

The milestone below hardcodes the registry's content from `catalog.md`; `registry.tel`
replaces that as a later step.

## 8. The swarm and source sync

- **Identity and membership.** Each daemon has a dedicated **ML-DSA swarm keypair**
  (`enigmatic`), generated on first use, separate from any publishing key; a member is
  identified by its fingerprint. `fury swarm connect <host>[:port]` presents the
  initiator's public key; the receiving daemon matches a pending `fury swarm invite` token
  or its user runs `fury swarm accept <fingerprint>`; thereafter both sides reconnect by
  key. Membership is **per user** and lives in Fury's own configuration (§11); nothing
  about a swarm appears in project files. Discovery is by explicit host only, with a fixed
  default port; mDNS advertisement is a liked later step.
- **Topology.** A **star around the initiator**, for now: the machine that ran `connect` is
  the hub and members talk only to it. Members are identified by fingerprint, never by
  hub-assigned ids, and dispatch messages are hub-agnostic, so a later move to a clique —
  gossiped membership, any-to-any dispatch — is additive. **The hub is also a worker**;
  the placement policy decides how much runs there, critical-path steps preferring it.
- **Capacity.** Each member advertises, on connect and on a heartbeat, its host triple, OS
  and cores, its current load, the universes and tool releases it can serve, its free store
  space, and a **concurrency budget** it is willing to offer — possibly zero while its user
  is busy. Placement matches steps to capabilities and budgets.
- **Protocol.** **One Fury protocol** for everything on the wire — advertisement, dispatch,
  events, cancellation, and blob transfer — over **TLS with self-signed certificates bound
  to the swarm key** (each certificate verified against the accepted fingerprint; no CA),
  with BinTEL bodies in a length-prefixed framing and TEL schemas for the messages. It does
  not reuse tool.md §7's store network API on the wire, though that API's hash-keyed,
  want/have model of blob exchange is the conceptual reference. Local channels — a tool
  daemon such as Fever, an LSP query — use UNIX domain sockets with the same framing.
- **Dispatch.** A step message carries what §3 lists; the worker fetches missing blobs from
  the dispatcher, runs the step in a local workspace, ingests outputs, and streams events
  back; the dispatcher then fetches what it lacks. A member that disappears mid-step has
  its steps rescheduled; a worker whose dispatcher disappears **finishes the step and keeps
  its outputs** as memo entries for collection on reconnect.
- **Source sync.** The daemon watches a project root (`surveillance`) once `fury watch` has
  been invoked there — the same watcher that drives re-runs (§5) — and on change, after a
  debounce, rehashes the affected files into the project's content-addressed tree and
  pushes new blobs to **capable members only**: those whose advertised capabilities can
  serve at least one of the project's steps. Sync is a prefetch, never a correctness
  requirement: a step whose tree is missing on a worker still fetches on demand.

## 9. What the first milestone assumes

The scope that reaches the milestone without design work beyond what the steps below
name:

- one universe, `jvm`; the `scalac` and `javac` edges only; `scalac` through Fever, loaded
  in-process by the plugin path of §6;
- no `integration` or `option` axes (so no `scala` compiler-line axis, builds.md §7.3, and
  no `tasty/1` semantic-atomization dependency);
- `presume`/`guarantee` and the `topology` block are parsed and ignored with a warning
  (the presumption disciplines of builds.md §12 have no specifications yet — `gaps.md` §2);
- the registry is hardcoded from [`catalog.md`](catalog.md); `registry.tel` comes later;
- the memoization key lives store-side (builds.md §14.5's "outputs cache under that key"),
  not yet in the manifest as `inputs/1`;
- one machine: the swarm, sync and web tracks begin after the spine (§13).

A consequence worth stating: `build.tel`'s worked `module core` does **not** build in this
scope. It exercises presumptions and the `scala` axis deliberately, as a specimen; the
milestone's build file is the `lira` module's own.

What v1 **refuses** (an error, because it would affect identity or cannot be implemented):
any universe other than `jvm`; any axis, and any `set` selecting a case; `assemble` and
`artifact`; tools other than `scalac`/`javac`, tools with a `module` or `edge` blocks; child
toolchains; unknown `set` keys or `flag`s (a setting that cannot be classified cannot be
hashed — the hashability law, builds.md §3.1); an `include` no source satisfies; `run`,
`watch` and `generate` in an invoked command or module; an `extract` destination matched
by a source glob (builds.md §14.5's feedback-loop lint); a registry layer. What it
**ignores** with a warning: `presume`, `guarantee`, `topology`, `host`, `require`,
`repository`, `arg`, side-effect settings.

## 10. The web interface

Served by Fury's daemon through `Tool.Web` when configuration says `serve`, built on
`pyrocosm-web`, with the pyrocosm model so the terminal renderer can show the same board.
**Runs are the primary record, ordered by start time**: every run knows the tree hash of
its inputs, its command and selections, its start and end, per-step outcomes and where
each step ran. The list groups runs by tree hash by default — one entry per source tree
state, in the manner of CI per commit — and grouping is configurable, disambiguating by
project name or root path where needed; no fixed project identity is baked into the key.
`--force` starts an ordinary run that bypasses memo hits for the selected modules. Runs
are BinTEL event logs stored as objects in a `build/` tier of the store, LRU-evictable
under the store budget and never pinned; `fury log` reads the same objects. Pages: the run
list, a run's DAG with step states, diagnostics and retained workspace paths, and the
swarm's members, budgets, loads and in-flight steps, live over WebSocket.

## 11. Configuration: files with one role each

Every configuration file has **one clear role, and every setting in it must be justified
by that role**. Three files follow:

| File | Role | Justified contents |
| --- | --- | --- |
| `.pyrocosm/fury/config.tel`, `~/.config/fury/config.tel` (the Pyrocosm cascade) | how the **Fury program** behaves on this machine | daemon settings: `serve`, `port`, web options, logging; swarm membership and keys — facts about where Fury runs work, never about what a build produces |
| `~/.config/lira/local.tel` (user-global, shared by `lira` and `fury`) | who this **person** is and what this **machine** provides, for every project | `signer` and key path; `store` path, tiers and budget; the machine's `given` and `guarantee`; the scheduler `budget` |
| project `local.tel` (gitignored) | this **checkout's** deviations from the shared build | `repository` origin overrides; `pin`; default `set` selections; `downstream`; the store-redirect setting; checkout-specific `given`/`guarantee` |

The **precedence order** builds.md §9.1 left implied, lowest to highest: tool built-ins
(the registry) → user-global `local.tel` → `build.tel` → project `local.tel` → command
block → command line. Legality follows builds.md §7's classification: identity-affecting
facts only in `build.tel`; side-effect settings in any layer; selections in the local
files and the occasion layers; the local files may refine `environment local` with system
or occasion content only. A value in a layer not permitted to hold it is an error, never
an override. Fury's `config.tel` sits outside this order, since nothing in it affects a
build's outcome.

`local.tel` gains a `pin <coordinate> <origin>` record (path, commit or `.lira` file;
compiles to a build pin, so L118 fences publication with no new rule), the `store` and
`budget` records above, and its own `local.schema.tel`. The committed `local.tel` is a
specimen: at the self-hosting step it moves beside the specimen `build.tel` under
`design/`, and `local.tel` at the root is gitignored.

## 12. Build-file surface settled here

- **Every `artifact` line is named**: `artifact <name> <app-type> [<filename>]`, names
  unique per module. Selection is by name only — `fury build app --artifact linux-x86`, or
  `artifact linux-x86` under `build` in a command — and a module with several artifacts
  and no selection is an error, not a build of all; there is no host-triple guessing.
  `extract` takes the artifact name as its entry beside the container-format names.
- **`build.lock`** holds, for every (coordinate, selector) resolution touched across the
  declared space — dependencies, tools and their components, host contracts — a `Release`
  record with its inclusion proof and the STH it was proved against, written
  deterministically under `lock.schema.tel`. `fury build` **refuses a stale lock** — a
  selector with no entry, or an entry whose selector changed — unless `--update`; a newly
  added dependency is an error until `fury lock --update <coordinate>` samples the world
  for it. Unreferenced entries are pruned on write. `--lock <file>` selects an alternate
  lock, occasion-class. Pins overlay the lock without touching it.
- **Codegen** is declared: a module carries `generate <tool>` for each source-to-source
  step, so the chain is visible in the file, while the resolver — written to find the
  chain from source forms alone — verifies the declaration as a pin. Dropping the
  requirement later relaxes a lint. Generated sources are a step's output tree in the
  store, fed to the next step as an input tree; they never touch the project root, so the
  feedback-loop lint and the authored-only `source` record are unaffected. Same-form
  edges (annotation processing, source expanders) are excluded for now.
- **Command arguments are declared**: `arg <name> [<default>]` under a command, referenced
  as `$name` in `run` lines and side-effect `set` values, invoked as `fury test only=Foo`;
  undeclared arguments are errors, and there is no raw passthrough tail. Arguments are
  occasion-class and enter no identity unless declared `arg <name> option <axis>`, which
  makes them a selection with a CLI `set`'s standing.
- **Registry layers**: `registry <coordinate> <selector>` at the document level (§7).

## 12a. Scripts: single-file builds

Independently of projects, Fury runs **scripts**: a single source file prefixed with a
lightweight TEL header that describes it, executable directly through an interpreter
directive:

```text
#!/usr/bin/env fury

language scala
  # configuration options, in Fury's own vocabulary

##

def main(using Runtime): Unit =
  Out.println("Hello world")
```

The shape is the `.lira` file's own (spec §5.2): a TEL head, a line consisting of exactly
`##`, then the body — here source text rather than a payload — so the split needs no
parsing of either half, and the interpreter directive is tolerated as the first line.

**The header** is a subset of the build file's options that still make sense for one
source file, in the same vocabulary, under its own `script.schema.tel`: `language` names
the source form (the only required field); beneath it the tool settings and flags a
module could carry, `include` for dependencies by coordinate and selector, `require` for
host contracts, and an optional `artifact` where the form has more than one runnable
deliverable. It compiles to a **synthetic build**: one project, one module whose single
source is the body, the default toolchain, and one `run` command — so a script is an
ordinary DAG of one or two steps and takes every path a project takes, with nothing
special-cased after the header is read. Anything not meaningful for one file (axes,
universes beyond the default, topologies, extraction) is absent from the schema rather
than ignored.

**Identity and caching.** Fury hashes the whole file first: a file it has seen maps
directly to a cached runnable in the store, and nothing is planned. A file it has not
seen is planned and its steps memoized as usual, so a header edit that leaves the
`inputs/1` identity unchanged is still a hit. The runnable entity — a deliverable in the
store — is cached under both keys, then executed from a workspace with the script's
arguments and the invoking environment. A cached runnable is never rebuilt unless the
file or its resolved world changes; `--force` applies as to any run.

**A capability of tools, not of Fury.** What a runnable entity is, and what the source
must provide to be one, differs per language: a Scala script defines
`def main(using Runtime): Unit`; another form may want a different signature or none.
Fury therefore knows nothing about entry points. The `lira.tool` contract gains an
**optional side-trait** (never an abstract member, per the contract's evolution rule)
through which a tool declares that it can turn a single source of a given form into a
runnable deliverable, names the convention the source must follow, and performs the
compilation on invocation; the descriptor records the capability so `tool.tel` advertises
it. Tools choose whether to provide it, most will not, and Fever is expected to be the
only implementation for some time (fever.md §6a). A script whose language no applied
tool supports as a script is an error naming the form.

**Invocation.** `fury <file>` where the file's head is a script header, exactly as
`lira <file>` presents a `.lira` file; the interpreter directive makes `./hello` do the
same. Arguments after the file go to the run, as declared arguments do for commands.
Scripts run through the daemon like everything else, so the second run of a script is a
cache lookup and an exec.

## 13. Decisions recorded

Kept here so they are not relitigated; the section that explains each is in brackets.

1. Fury and Fever are two tools, two daemons, two packages, developed here (intro; fever.md).
2. Fury is language-agnostic; Scala lives in Fever (intro).
3. The DAG is static; edges, not the DAG, are dispatched; placement is a policy behind a
   stable interface, judged by finish time (§1, §3).
4. A step is one edge invocation per cell, identified by `inputs/1` (§1).
5. The tool contract is BinTEL (§1); the `lira-tool` module is built on anthology (§6).
6. Compiles are cold; no tool-private incremental state (§4).
7. All products and by-products live in the centralised store; a local setting may
   redirect it into the project (§4).
8. One daemon per user; load awareness from OS metrics and advertised load (§1, §5).
9. One shared scheduler with a cost-weighted budget; failed steps skip dependants and
   independent steps continue; no timeouts (§5).
10. `watch` is explicit; it re-runs and cancels invalidated steps; one watcher feeds sync (§5, §8).
11. Plugins: one path for all tools; classloader per release; no trust act beyond
    signature, lock and loadability; local binaries deferred; Fever is an ordinary
    `lira-tool` release, one per Scala version (§6; fever.md).
12. `registry.tel`, one document with sibling `.tool.tel` descriptors, extended only by
    published layers (§7).
13. Swarm: dedicated ML-DSA keypair with explicit accept; TLS bound to it; star around the
    initiator (clique later, messages hub-agnostic); hub is a worker; advertised budgets;
    one Fury protocol; sync to capable members only; explicit host, mDNS later;
    membership per user; orphaned steps finish and keep outputs (§8).
14. Runs by start time, grouped by tree hash, in a `build/` store tier (§10).
15. Configuration files have one role each, with the allocation of §11; the six-layer
    precedence order (§11); `local.tel` gitignored at self-hosting (§11).
16. Named artifacts; stale-lock error; declared codegen, no same-form edges; declared
    command arguments (§12).
17. The Fury–Fever relationship — how independently they run, over which channels — is
    kept flexible; the LSP route is undecided by choice (fever.md §4–5).
18. Scripts: a single-file build is a TEL header, `##`, and a source body, compiled to a
    synthetic one-module build; runnables are cached by whole-file hash and by step
    identity; turning a source into a runnable is an optional capability a tool declares
    through a side-trait of the contract, which Fury never special-cases (§12a).

## 14. Layout

```
build.mill           shared settings; modules lira.core, lira.tool, lira.launcher, lira.test,
                     fury.core, fury.launcher, fever.core, fever.launcher
src/core/            package lira — the lira tool and the store it shares (lira-core)
src/tool/            package lira.tool — the plugin contract (lira-tool)
src/launcher/        lira's @main
src/fury/core/       package fury — everything Fury (fury-core)
src/fury/launcher/   fury's @main
src/fever/core/      package fever — everything Fever (fever-core); one module until a
                     second Scala version is needed
src/fever/launcher/  fever's @main
src/test/
schemas at the root: build, tool, guarantees, local, lock, registry; scalac.tool.tel
```

Fury's and Fever's modules are rooted at `src/fury/` and `src/fever/` because Mill derives a
module's directory from its name, and a second `core` at `src/core` would collide with
lira's. Splitting `lira-core` into a store library and a separate `lira` CLI module is
deferred: `fury-core` depends on `lira-core` as it stands, which costs nothing but the CLI
classes on its classpath.

`fury` and `fever` use `pyrocosm.Tool.standard`, so `pyrocosm-cli` (and `pyrocosm-web` for
Fury) join `etc/refs`, which pins Soundness at the version Pyrocosm was built against;
`lira` stays as it is, not a Pyrocosm tool. Every publishing module is published locally
before the launchers assemble (`__.publishLocal`), the Makefile gains `fury` and `fever`
targets on the same repackage-then-`xeq` path as `lira`, and CI compiles every module. The
later move is `src/fury/` and `src/fever/` to their own repositories, leaving `lira-core` and
`lira-tool` as published dependencies.

## 15. The ladder

Each step is a runnable `fury` with one more capability. *Reuses* names the existing
code; *Needs* the design item that must be settled first; *Tests* the assumption the step
can falsify. Sizes are rough new lines. The **spine** is sequential; the four **tracks**
after it proceed independently.

**0. Skeleton (~200).** The layout of §14: the `lira-tool` module, `fury` and `fever` on
`Tool.standard` with their own Ethereal daemons, `about`, `quit`, `install`; Makefile and
CI covering every module. Reuses `pyrocosm.Tool.standard` and the
CLI scaffolding of `lira.LiraTool.scala`. Needs nothing. Tests only the layout.

**1. `fury check` (~500).** Parse and validate `build.tel` against `build.schema.tel`
(loaded from resources), decode to a typed model, print it; apply the §9 refuse/ignore
lint. Reuses stratiform's schema validation and the hand-decoding style of
`Lira.Manifest.decode` (stratiform's typed record derivation cannot type this schema yet:
flat records only, no custom validators). Needs nothing. Tests whether hand-decoding is
tolerable at this schema's size.

**2. `fury wrap` (~250).** `fury wrap <module> <version> <jar> [--requires …]`: a Maven jar
becomes a versioned `.lira` release in the store and in a directory source; a script wraps
the `lira` module's dependency jars in POM order, so the closure is real. Reuses the
`lira atoms` zip expansion and `LiraAssembler`. Needs: builds.md §1 to record this adapter
as the concrete "stage0 from prebuilt". Tests, by spiking `lira atoms` over a bundle-sized
jar first: `tasty/1` at scale, and whether `classfile/1`'s membership keying resolves
supertypes across bundles.

**3. `fury resolve` (~350).** Resolve every `include` through pins, directory sources and
the store to a set of cells; run the buildpath validity rules (spec §13.3); print the
judgment and advisories. Reuses reliquary's `Buildpath`. Needs: the precedence order and
`local.tel` schema of §11. Tests `Buildpath` on real releases and the four-homes model
against a user-global file.

**4. `fury lock` (~200).** Write and read `build.lock` deterministically under
`lock.schema.tel`; `build` refuses a stale lock without `--update` (§12). Needs
`lock.schema.tel`. Tests the lock's content and the staleness rule.

**5. The worker and Fever (~400).** Fury computes a one-step DAG for a `jvm` module and
hands a BinTEL invocation to `fever.tool`, loaded in-process by the plugin path of §6,
into a per-step workspace (builds.md §14.5); diagnostics printed; the workspace retained on
failure and named. This step establishes the `lira.tool` contract in code, its BinTEL
schemas, the worker abstraction, and Fever's first edge. Reuses anthology's compiler
drivers and the store's derivative tier for the classpath. Needs: the hardcoded registry
with the setting-to-flag table (TEL identifiers are lowercase kebab, so compiler flags such
as `-Ycc-new` are named settings, each classified `affects output`). Tests, by compiling
twice and comparing bytes: spec §17's byte determinism — anthology records that each run
pickles a fresh UUID into TASTy, so the honest wording may be *atom-deterministic*. Also
surfaces anthology gaps: no compiler-version accessor.

**6. `fury build` (~200).** Assemble step 5's output into a development release with
dependency records (L118 build pins for development-release dependencies), ingest it, print
its identities; `lira verify` passes. Needs: **section-scoped `Tool` records, the `Setting`
record and LIRA tool identity** applied to the spec (builds.md §6 item 3; `gaps.md` §1) and
to reliquary's manifest type, which today also lacks the spec's `source` record. This is
where `gaps.md` §5's hand-derivation happens against real output. Tests L108, L127 and
L141 on a real module, and the Tool-record design itself.

**7. Memoization (~200).** The canonical input-identity document of builds.md §3.1 hashed
under a Fury domain; a `memo/` reference tier in the store, treated as a non-root by `gc`.
A second `fury build` is a hit with no compiler run; touching a source misses; reverting
hits. Needs: the `lira-inputs` document shape. Tests the hashability law in practice; the
compiler's jar hash, Fever's release identity and the JDK version are the inputs to close.

**8. `fury extract` (~100).** `extract <module> <entry> <path>` from the derivative tier and
by artifact name, with the feedback-loop lint. Needs the named-artifact schema change of
§12.

**9. Self-host (~80).** This repository's own `build.tel`, `local.tel` and committed
`build.lock`; `make lira` optionally through `fury`; the specimen build and local files
move under `design/`; `local.tel` gitignored. Tests the whole chain on a real project.

**Track A — engine.** Multi-module DAGs; the shared scheduler; the run log with resource
statistics; the cost-weighted budget, greedy first; retained workspaces and `fury
workspace`; `fury watch` with cancellation; `--force`.

**Track B — swarm** (after A's scheduler). Keys, invite and accept; capability
advertisement and heartbeat; the Fury protocol over TLS with BinTEL framing; the remote
worker with blob fetch; then watcher-driven sync to capable members; placement policies
beyond greedy.

**Track C — web** (after A's run log). The run list, the run page with its DAG, live
updates; the swarm page.

**Track D — Fever LSP** (after step 5, independent of A–C). `fever lsp` on Exegesis over
`fever.compile`; the routing decision of fever.md §5; editor glue as in `tel`.

**Track E — scripts** (after step 7; independent of A–D). `script.schema.tel`; the head
and body split with the interpreter directive tolerated; the synthetic build; the
contract's script side-trait and its descriptor field; Fever's implementation for Scala
(fever.md §6a); the whole-file-hash fast path in the store; `fury <file>` and
`#!/usr/bin/env fury`. Tests: a hello-world script runs, its second run compiles nothing,
a header-only edit that changes no input is still a hit, and a script in an unsupported
language fails naming the form.

## 16. Soundness prerequisites

The changes the ladder needs in Soundness are filed as issues there, by the step that
needs them: [#2026](https://github.com/propensive/soundness/issues/2026) compiler version
accessor, [#2027](https://github.com/propensive/soundness/issues/2027) diagnostics dropped
by `ScalacEdges`, [#2029](https://github.com/propensive/soundness/issues/2029) TASTy UUIDs
versus spec §17, and [#2025](https://github.com/propensive/soundness/issues/2025) the
`Materializer` cache (step 5); [#2022](https://github.com/propensive/soundness/issues/2022)
the manifest `source` record and [#2023](https://github.com/propensive/soundness/issues/2023)
section-scoped `Tool` records (step 6); [#2024](https://github.com/propensive/soundness/issues/2024)
a hash domain for the input identity (step 7); optionally
[#2030](https://github.com/propensive/soundness/issues/2030) typed record derivation for
nested schemas (step 1). [#2028](https://github.com/propensive/soundness/issues/2028)
retained compiler sessions no longer gates anything, since compiles are cold.
[#2031](https://github.com/propensive/soundness/issues/2031) ties them to the Soundness
roadmap's tool-4.

Filed for this design (2026-09-25), by the step or track each gates:
[#2065](https://github.com/propensive/soundness/issues/2065) anthology's edge model
carrying `tool.schema.tel`'s information model and
[#2066](https://github.com/propensive/soundness/issues/2066) forms as data (step 5 and
the registry); [#2067](https://github.com/propensive/soundness/issues/2067) validator
rejections carrying their diagnostic and focus and
[#2068](https://github.com/propensive/soundness/issues/2068) E306 attribution (step 1's
reports); [#2069](https://github.com/propensive/soundness/issues/2069) accrual
accumulators without `Hazard`; [#2070](https://github.com/propensive/soundness/issues/2070)
classloaders with a chosen parent (step 5's plugin loading) and
[#2071](https://github.com/propensive/soundness/issues/2071) the capability leak on
resource reads; [#2072](https://github.com/propensive/soundness/issues/2072) TLS pinned to
a fingerprint, [#2073](https://github.com/propensive/soundness/issues/2073) certificates
bound to an ML-DSA key and [#2074](https://github.com/propensive/soundness/issues/2074)
BinTEL framing over `Duplex` (track B); [#2075](https://github.com/propensive/soundness/issues/2075)
the memo tier and `lira-inputs` document (step 7);
[#2076](https://github.com/propensive/soundness/issues/2076) debounced watch batches
(track B and `watch`). In Pyrocosm: [#28](https://github.com/propensive/pyrocosm/issues/28)
additional schema-validated configuration documents (step 3),
[#29](https://github.com/propensive/pyrocosm/issues/29) schema-validated `config.tel`,
[#30](https://github.com/propensive/pyrocosm/issues/30) daemon-owned state for `Tool.Web`,
[#31](https://github.com/propensive/pyrocosm/issues/31) a graph block and
[#32](https://github.com/propensive/pyrocosm/issues/32) a server-driven live list (track C).

## 17. Beyond the milestone

Unordered; each is a design round of its own, mostly already sketched in `builds.md`:
tool-4 on Soundness (multi-module, parity with its Mill build); scripts (§12a, track E);
the `sjsir` and `nir` universes; the `scala` integration axis (builds.md §7.3, needing `tasty/1` semantic
atomization); `presume`/`guarantee` once `envvar/1` and `file/1` are specified (§12);
`registry.tel` replacing the hardcoded registry (§7); the `run` settings model and the
deployment round (builds.md §10); publishing; remote store sharing beyond the swarm; raw
local binaries; same-form codegen edges; the LSP route; the multi-version Fever layout;
mDNS discovery; incremental compilation if ever needed.

## 18. Milestone acceptance

Per step: the sentence in bold, plus `fury` and `fever` compiling under the same strict
flags as `lira`, `make lira` still building, and every `.tel` file touched validating
against its schema — through the test suite, which reconstructs every shipped schema with
stratiform and validates each specimen against it (the `tel` command's own validator is
newer than the installed release). For the milestone as a whole:

1. the wrap script yields the `lira` module's dependency releases and `lira verify` passes
   on them;
2. `fury lock` writes `build.lock`; `fury build` ingests a development release of `lira`;
   `lira verify` on the extracted file reports install grade;
3. `lira atoms` on the release (declared) equals `lira atoms --realm jvm --classpath …` on
   the extracted jar (computed) — the L141 re-atomization check, by hand until `lira
   verify` has a publish grade;
4. a second `fury build` is a memo hit; a touched source misses; a revert hits again;
5. the extracted jar runs `lira.main help`, and `lira id` resolves it back to the release;
6. two forced rebuilds agree on the payload hash, or spec §17 is amended per step 5;
7. Fever was loaded by the plugin path of §6 and nothing in `fury` names Scala;
8. `gaps.md` tier 1 is empty and `handover.md` has become a pointer.
