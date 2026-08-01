# LIRA — Library IR Archive

A language-agnostic artifact format: one `.lira` file carries every compiled representation of
a library release (e.g. JVM classfiles + TASTy + Scala.js IR + Scala Native IR), deduplicated,
with a human-readable [TEL](https://github.com/propensive/tel) manifest, verifiable
API-derived versioning, and quantum-safe signatures. Executing a `.lira` file invokes the
`lira` tool on it.

Focus languages: **Scala**, **Kotlin**, **TypeScript**, **Rust**; also Java and JavaScript.

- [`spec/lira.md`](spec/lira.md) — the format specification (working draft).
- [`design/universes.md`](design/universes.md) — the taxonomy of formats, universes, hosts and
  application types; the pipeline DAG; what belongs in a `.lira` file.
- [`design/compatibility.md`](design/compatibility.md) — per-language compatibility
  (discipline) designs.
- [`design/distribution.md`](design/distribution.md) — the index service: DNS-verified
  namespaces, transparency log, single-packet UDP resolution, GitHub Releases hosting.

Status: specification and implementation in progress. The language-blind core — container,
compatibility algebra, buildpath validation, signing, and canonical derivative artifacts — is
implemented as the [`reliquary`](https://github.com/propensive/soundness) module of Soundness;
the Scala discipline (`degustation`) and the `lira` command-line tool (in this repository,
built on Soundness) are next.
