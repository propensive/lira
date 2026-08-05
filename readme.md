# LIRA — Library IR Archive

A language-agnostic artifact format: one `.lira` file carries every compiled representation of
a library release (e.g. JVM classfiles + TASTy + Scala.js IR + Scala Native IR), deduplicated,
with a human-readable [TEL](https://github.com/propensive/tel) manifest, verifiable
API-derived versioning, and quantum-safe signatures. Executing a `.lira` file invokes the
`lira` tool on it.

Focus languages: **Scala**, **Kotlin**, **TypeScript**, **Rust**; also Java and JavaScript.

- [`spec/lira.md`](spec/lira.md) — the format specification (working draft).
- [`spec/tasty.md`](spec/tasty.md) — the normative Scala discipline.
- [`spec/classfile.md`](spec/classfile.md) — the normative JVM bytecode discipline.
- [`spec/dts.md`](spec/dts.md) — the normative TypeScript declaration discipline.
- [`spec/jvm.md`](spec/jvm.md) — the normative JVM ecosystem profile.
- [`spec/hosts.md`](spec/hosts.md) — host contracts, the `host` world, `requires`, and the
  `capability/1` discipline.
- [`spec/webidl.md`](spec/webidl.md) — the normative Web IDL discipline, for browser host
  contracts.
- [`spec/wit.md`](spec/wit.md) — the normative WIT discipline, for WASI worlds and, ahead, the
  `component` universe.
- [`spec/cheader.md`](spec/cheader.md) — the normative C header discipline, for shared-library
  host contracts.
- [`spec/kotlin.md`](spec/kotlin.md) — the normative Kotlin metadata discipline.
- [`design/universes.md`](design/universes.md) — the taxonomy of formats, universes, hosts and
  application types; the pipeline DAG; what belongs in a `.lira` file.
- [`design/compatibility.md`](design/compatibility.md) — per-language compatibility
  (discipline) designs.
- [`design/integrations.md`](design/integrations.md) — one release carrying several dependency
  vectors: the section matrix, the one-API rule, and buildpath resolution as a search.
- [`design/distribution.md`](design/distribution.md) — the index service: DNS-verified
  namespaces, transparency log, single-packet UDP resolution, GitHub Releases hosting.

Status: specification and implementation in progress. The language-blind core — container,
compatibility algebra, buildpath validation, signing, and canonical derivative artifacts — is
implemented as the [`reliquary`](https://github.com/propensive/soundness) module of Soundness,
and the Scala discipline (`tasty/1`) as its `degustation` module; the `lira`
command-line tool (in this repository, built on Soundness) is next.
