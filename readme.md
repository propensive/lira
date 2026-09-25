# LIRA — Library IR Archive

A language-agnostic artifact format: one `.lira` file carries every compiled representation of
a library release (e.g. JVM classfiles + TASTy + Scala.js IR + Scala Native IR), deduplicated,
with a canonical binary [TEL](https://github.com/propensive/tel) manifest (BinTEL) — rendered
as readable TEL text by any conforming tool — verifiable
API-derived versioning, and quantum-safe signatures. The same format and algebra extend to
deploy time: a deployable service publishes one `.lira` release carrying its closed artifact
(or an OCI pin to one) whose atoms are the network surface it serves, and deployability into a
running environment is checked from manifests alone. A `.lira` file is data, never executable;
the `lira` tool presents, verifies, and analyses it.

Focus languages: **Scala**, **Kotlin**, **TypeScript**, **Rust**; also Java and JavaScript.

- [`spec/introduction.md`](spec/introduction.md) — an informative introduction: the problems,
  the abstractions, and how they fit together; start here.
- [`spec/lira.md`](spec/lira.md) — the format specification (working draft).
- [`spec/tasty.md`](spec/tasty.md) — the normative Scala discipline.
- [`spec/classfile.md`](spec/classfile.md) — the normative JVM bytecode discipline.
- [`spec/dts.md`](spec/dts.md) — the normative TypeScript declaration discipline.
- [`spec/openapi.md`](spec/openapi.md) — the normative OpenAPI discipline, for HTTP service
  contracts and deployable self-descriptions.
- [`spec/jvm.md`](spec/jvm.md) — the normative JVM ecosystem profile.
- [`spec/hosts.md`](spec/hosts.md) — host contracts, the `host` realm, `requires`, and the
  `capability/1` discipline.
- [`spec/services.md`](spec/services.md) — deployable releases, the `app` realm, environment
  validity, and deployment.
- [`spec/environments.md`](spec/environments.md) — environment releases, the `env` realm,
  bindings and addresses, the `environment/1` discipline, and provisioning.
- [`spec/tels.md`](spec/tels.md) — the normative TEL schema discipline `tels/2`, by which
  LIRA's own extension layers and any TEL schema are published and versioned.
- [`spec/webidl.md`](spec/webidl.md) — the normative Web IDL discipline, for browser host
  contracts.
- [`spec/wit.md`](spec/wit.md) — the normative WIT discipline, for WASI worlds and, ahead, the
  `wasmc` universe.
- [`spec/cheader.md`](spec/cheader.md) — the normative C header discipline, for shared-library
  host contracts.
- [`spec/kotlin.md`](spec/kotlin.md) — the normative Kotlin metadata discipline.
- [`spec/jsig.md`](spec/jsig.md) — the normative Java signature-surface discipline, for the
  `jdk` and `android` host contracts.
- [`design/universes.md`](design/universes.md) — the taxonomy of formats, universes, hosts and
  deliverables; the pipeline DAG; what belongs in a `.lira` file.
- [`design/compatibility.md`](design/compatibility.md) — per-language compatibility
  (discipline) designs.
- [`design/integrations.md`](design/integrations.md) — one release carrying several dependency
  vectors: the section matrix, the one-API rule, and buildpath resolution as a search.
- [`design/distribution.md`](design/distribution.md) — the index service: DNS-verified
  namespaces, transparency log, single-packet UDP resolution, GitHub Releases hosting.
- [`design/junctures.md`](design/junctures.md) — the derivation of the format's
  primitive: one edge, exercised at a juncture, resolved in a medium; the buildpath and
  the environment as two species of composition; TEL acceptances beside LIRA resolution.
- [`design/execution.md`](design/execution.md) — the derivation, now adopted as
  [`spec/environments.md`](spec/environments.md): extending the algebra to long-running
  execution, with the environment manifest as desired state, bindings and addresses, drift,
  and the runtime as a third consumer of manifests.
- [`design/builds.md`](design/builds.md) — the planned build tool audited against the
  format: `.lira` files as verifiable build caches, source-or-artifact substitution, variant
  packaging, and the build-to-deployment continuum.
- [`design/tool.md`](design/tool.md) — the unified `lira` tool: the content-addressed store,
  cache and retention, the command surface, and node roles.
- [`design/fury.md`](design/fury.md) — the Fury build tool: its shape (engine, daemon, swarm,
  web interface), the decisions taken, and the ladder of increments by which it grows.
- [`design/fever.md`](design/fever.md) — Fever, the Scala compiler service Fury delegates to:
  one release per Scala version, the tool contract it implements, and the LSP.

Status: specification and implementation in progress. The language-blind core — container,
compatibility algebra, buildpath validation, signing, and canonical derivative artifacts — is
implemented as the [`reliquary`](https://github.com/propensive/soundness) module of Soundness,
and the Scala discipline (`tasty/1`) as its `degustation` module; the `lira`
command-line tool (in this repository, built on Soundness) covers the artifact commands today,
with its full design — store, cache, and node — in [`design/tool.md`](design/tool.md).

## Building the `lira` tool

The tool is a Mill build over three modules: `core`, the command surface and the store, published
as `dev.propensive:lira-core`; `launcher`, the one-line invocation point; and `test`. It depends on
Soundness alone — `reliquary` for the format, `degustation` for the Scala discipline — and is not
a Pyrocosm application. The Soundness release it builds against is pinned in
[`etc/refs`](etc/refs), and the tools it runs (fume, flair) in [`etc/tools`](etc/tools).

```sh
make sync-deps   # install the pinned Soundness release into ~/.ivy2/local
make tools       # install fume and flair
make test        # run the suite with fume  (make test-plain uses plain java)
make lira        # build the native executable for this machine
make install     # copy it to ~/.local/bin, which is what a `.lira` file's `#!` line resolves
make check       # check the sources with flair
```

A release — the `lira-core` jar, then the per-platform executables built from it — is published to
GitHub Releases by `make release VERSION=X.Y.Z`, after bumping `liraVersion` in `build.mill`,
exactly as fume, flair, flame and tel are released.

Two commands are narrower than [`design/tool.md`](design/tool.md) describes, because the
disciplines they need are specified but not yet implemented in Soundness: `lira atoms` names
`classfile/1`, `jsig/1`, `tasty/1` and `capability/1` only, and `lira harvest` takes `jdk` and
`android` but not `dts` or `wit`. Both are marked in the source, to be restored as reliquary gains
each discipline.
