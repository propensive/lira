# Fury: the build tool, grown incrementally

Fury is the build tool that consumes and produces `.lira` files, designed in
[`builds.md`](builds.md) and audited there against the format. This document is its
**roadmap**: the ordered ladder of increments by which `fury` grows from a tiny CLI into
the tool `builds.md` describes, with the design item each increment depends on and the
design assumption each one tests. Steps are chosen one at a time, cross-checked here; the
ladder is the record of what each step unlocks and what it is allowed to assume.

Fury lives in this repository beside the `lira` tool while LIRA and Fury co-evolve, and
moves to its own repository later. The two are **separate binaries sharing library code**:
`lira` reads, verifies and serves LIRA files ([`tool.md`](tool.md)); `fury` builds them.
This settles the relationship [`gaps.md`](gaps.md) §4 left open.

The first bootstrap target is this repository's own `lira` module: one module, `jvm`
universe, Soundness bundle dependencies. It is a stepping stone to Soundness's roadmap
item *tool-4* — `fury build` builds Soundness from a clean checkout, attestation-equal to
the Mill build — which is the milestone's criterion, not its first step.

## 1. What the first milestone assumes

The scope that reaches the milestone without design work beyond what the steps below
name:

- one universe, `jvm`; the `scalac` and `javac` edges only, the embedded compiler only;
- no `integration` or `option` axes (so no `scala` compiler-line axis, §7.3, and no
  `tasty/1` semantic-atomization dependency);
- `presume`/`guarantee` and the `topology` block are parsed and ignored with a warning
  (the presumption disciplines of §12 have no specifications yet — `gaps.md` §2);
- the registry is hardcoded from [`catalog.md`](catalog.md); `universes.tel` comes later;
- the memoization key lives store-side (§14.5's "outputs cache under that key"), not yet
  in the manifest as `inputs/1`.

A consequence worth stating: `build.tel`'s worked `module core` does **not** build in this
scope. It exercises presumptions and the `scala` axis deliberately, as a specimen; the
milestone's build file is the `lira` module's own.

What v1 **refuses** (an error, because it would affect identity or cannot be implemented):
any universe other than `jvm`; any axis, and any `set` selecting a case; `assemble` and
`artifact`; tools other than `scalac`/`javac`, tools with a `module` or `edge` blocks; child
toolchains; unknown `set` keys or `flag`s (a setting that cannot be classified cannot be
hashed — the hashability law, §3.1); an `include` no source satisfies; `run`/`watch` in
an invoked command; an `extract` destination matched by a source glob (§14.5's
feedback-loop lint). What it **ignores** with a warning: `presume`, `guarantee`,
`topology`, `host`, `require`, `repository`, side-effect settings.

## 2. Layout

```
build.mill     shared settings trait; modules core, lira, fury
src/core/      package lira — shared library: the store, derivatives, memo
src/cli/       package lira — the lira tool
src/fury/      package fury — everything Fury
schemas/       build/lock/tool/guarantees schemas and scalac.tool.tel, as fury's resources
```

The later move is `src/fury` and `schemas/` to the new repository plus a `build.mill`
split; nothing in `src/core` is Fury-specific.

## 3. The ladder

Each step is a runnable `fury` with one more capability. *Reuses* names the existing
code; *Needs* the design item that must be settled first; *Tests* the assumption the step
can falsify. Sizes are rough new lines.

**0. Skeleton (~150).** `fury help`, `quit`, `install` as an Ethereal daemon, separate from
`lira`'s; the `core` module split; the store moved into it. Reuses the CLI scaffolding of
`lira.LiraTool.scala` (private to `lira`, so copied). Needs nothing. Tests only the layout.

**1. `fury check` (~500).** Parse and validate `build.tel` against `build.schema.tel`
(loaded from resources), decode to a typed model, print it; apply the §1 refuse/ignore
lint. Reuses stratiform's schema validation and the hand-decoding style of
`Lira.Manifest.decode` (stratiform's typed record derivation cannot type this schema yet:
flat records only, no custom validators). Needs nothing. Tests whether hand-decoding is
tolerable at this schema's size.

**2. `fury wrap` (~250).** `fury wrap <module> <version> <jar> [--requires …]`: a Maven jar
becomes a versioned `.lira` release in the store and in a directory source; a script wraps
the `lira` module's 29 dependency jars in POM order, so the closure is real. Reuses the
`lira atoms` zip expansion and `LiraAssembler`. Needs: §1 to record this adapter as the
concrete "stage0 from prebuilt". Tests, by spiking `lira atoms` over a bundle-sized jar
first: `tasty/1` at scale, and whether `classfile/1`'s membership keying resolves
supertypes across bundles.

**3. `fury resolve` (~350).** Resolve every `include` through pins, directory sources and
the store to a set of cells; run the buildpath validity rules (§13.3); print the judgment
and advisories. Reuses reliquary's `Buildpath`. Needs: the precedence order as one
normative list (§9.1 gap 5; the list is in `gaps.md` §3), and a `local.tel` schema
covering `pin`, `source <directory>` and `store`, with `local.tel`'s per-user status
decided. Tests `Buildpath` on real releases and the four-homes model against a user-global
file.

**4. `fury lock` (~200).** Write and read `build.lock` deterministically; later, `build`
refuses a stale lock without `--update`. Needs: §5 question 3 answered (is the lock more
than Release records and inclusion proofs?), then `lock.schema.tel`. Tests that answer.

**5. `fury compile` (~250).** In-process `scalac`/`javac` into a per-invocation workspace
(§14.5), diagnostics printed, the workspace retained on failure and named. Reuses
anthology's compiler drivers and the store's derivative tier for the classpath. Needs: the
hardcoded registry with the setting-to-flag table (TEL identifiers are lowercase kebab,
so compiler flags such as `-Ycc-new` are named settings, each classified `affects output`).
Tests, by compiling twice and comparing bytes: §17's byte determinism — anthology records
that each run pickles a fresh UUID into TASTy, so the honest wording may be
*atom-deterministic*. Also surfaces anthology gaps: no compiler-version accessor; a warm
compiler session is block-confined and cannot outlive a daemon request.

**6. `fury build` (~200).** Assemble step 5's output into a development release with
dependency records (L118 build pins for development-release dependencies), ingest it, print
its identities; `lira verify` passes. Needs: **section-scoped `Tool` records, the `Setting`
record and LIRA tool identity** applied to the spec (§6 item 3; `gaps.md` §1) and to
reliquary's manifest type, which today also lacks the spec's `source` record. This is
where `gaps.md` §5's hand-derivation happens against real output: compare the emitted
manifest with what spec §14 says it must be, for `module lira` in code and `module core`
on paper. Tests L108, L127 and L141 on a real module, and the Tool-record design itself.

**7. Memoization (~200).** The canonical input-identity document of §3.1 (sorted source
hashes, tool identity with output-affecting settings, dependency payload hashes) hashed
under a Fury domain; a `memo/` reference tier in the store, treated as a non-root by `gc`.
A second `fury build` is a hit with no compiler run; touching a source misses; reverting
hits. Needs: the `lira-inputs` document shape. Tests the hashability law in practice; the
compiler's own jar hash and the JDK version are known omissions to close.

**8. `fury extract` (~100).** `extract <module> jar <path>` from the derivative tier and
`extract <module> lira <path>`, with the feedback-loop lint. Needs nothing new.

**9. Self-host (~80).** This repository's own `build.tel`, `local.tel` and committed
`build.lock`; `make lira` optionally through `fury`; the specimen build file moves under
`design/`. Tests the whole chain on a real project.

## 4. Soundness prerequisites

The changes the ladder needs in Soundness are filed as issues there, by the step that
needs them: [#2026](https://github.com/propensive/soundness/issues/2026) compiler version
accessor, [#2027](https://github.com/propensive/soundness/issues/2027) diagnostics dropped
by `ScalacEdges`, [#2028](https://github.com/propensive/soundness/issues/2028) retained
compiler sessions, [#2029](https://github.com/propensive/soundness/issues/2029) TASTy UUIDs
versus §17, and [#2025](https://github.com/propensive/soundness/issues/2025) the
`Materializer` cache (step 5); [#2022](https://github.com/propensive/soundness/issues/2022)
the manifest `source` record and [#2023](https://github.com/propensive/soundness/issues/2023)
section-scoped `Tool` records (step 6); [#2024](https://github.com/propensive/soundness/issues/2024)
a hash domain for the input identity (step 7); optionally
[#2030](https://github.com/propensive/soundness/issues/2030) typed record derivation for
nested schemas (step 1). [#2031](https://github.com/propensive/soundness/issues/2031) ties
them to the Soundness roadmap's tool-4.

## 5. Beyond the milestone

Unordered; each is a design round of its own, mostly already sketched in `builds.md`:
tool-4 on Soundness (multi-module, parity with its Mill build); the `sjsir` and `nir`
universes; the `scala` integration axis (§7.3, needing `tasty/1` semantic atomization);
`presume`/`guarantee` once `envvar/1` and `file/1` are specified (§12); `universes.tel`
replacing the hardcoded registry; the `lira.tool` plugin trait (§11, §14.3); `run` and the
deployment round (§10); publishing.

## 6. Milestone acceptance

Per step: the sentence in bold, plus `fury` compiling under the same strict flags as
`lira`, `make lira` still building, and `tel validate --llm` passing on every `.tel` file
touched. For the milestone as a whole:

1. the wrap script yields 29 releases and `lira verify` passes on them;
2. `fury lock` writes `build.lock`; `fury build` ingests a development release of `lira`;
   `lira verify` on the extracted file reports install grade;
3. `lira atoms` on the release (declared) equals `lira atoms --realm jvm --classpath …` on
   the extracted jar (computed) — the L141 re-atomization check, by hand until `lira
   verify` has a publish grade;
4. a second `fury build` is a memo hit; a touched source misses; a revert hits again;
5. the extracted jar runs `lira.main help`, and `lira id` resolves it back to the release;
6. two forced rebuilds agree on the payload hash, or §17 is amended per step 5;
7. `gaps.md` tier 1 is empty and `handover.md` has become a pointer.
