# Catalog

The companion to [`glossary.md`](glossary.md): where the glossary defines the taxonomy's
*categories*, this catalog names the *instances* — specific tools, forms, contracts,
disciplines and schemas mentioned across the LIRA specification and the build tool
design, each identified by what kind of thing it is. It is the prose precursor of the
machine-readable registry (`universes.tel`, universes.md §6).

## Source forms

Named for their languages; extensions map to them via the tool registry.

| Name | Content |
| --- | --- |
| `scala` | Scala sources |
| `java` | Java sources |
| `kotlin` | Kotlin sources |
| `typescript` | TypeScript sources (compiled into the `js` universe) |
| `rust` | Rust sources |
| `c` | C sources |
| `dockerfile` | Dockerfiles (input to the `docker` packaging edge) |
| `wat` | WebAssembly text — a source form *only* where humans write it; otherwise a rendering of wasm bytes, not a form |

## Universes

Forms whose content composes. `jvm`, `sjsir` and `nir` are in the base schema; the rest
are reserved for schema layers (now shipped as `tels/1` modules).

| Name | Content and interface convention |
| --- | --- |
| `jvm` | Classfiles + TASTy + Kotlin `@Metadata`; carriers: classfile signatures, TASTy, `@Metadata` |
| `sjsir` | Scala.js IR; carrier: TASTy. The multi-egress universe: js-app, wasm-browser, wasi-component |
| `nir` | Scala Native IR; carrier: TASTy. Architecture-agnostic: one cell, N links |
| `js` | JavaScript itself — ES/CJS modules, `.d.ts` or no carrier. JavaScript's own form (reserved) |
| `klib` | Kotlin multiplatform libraries; carrier: Kotlin metadata (reserved) |
| `component` | WASM component-model *libraries*; carrier: WIT (reserved) |
| `native/<triple>` | C-ABI archives, one universe per target triple; carrier: C headers (reserved family) |
| `wasm-object` | Relocatable wasm objects (linking-section symbols); the C/Rust wasm route (reserved) |
| `crate` | Rust source + rmeta (informative only) |

## Application types

Closed forms paired with host contracts; registry objects, never manifest objects.

| Name | Format × host contract |
| --- | --- |
| `jvm-app` | Executable jar × JDK |
| `android-app` | DEX/APK × Android |
| `xeq-bundle` | Jar on a native launcher stub in a polyglot script × OS+shell |
| `native-image/<triple>` | GraalVM native image × OS+libc for the triple |
| `native-exe/<triple>` | Linked native executable × OS+libc for the triple |
| `js-app` | ESM/CJS/script bundle × Node or browser |
| `wasm-browser` | Core wasm + JS glue × browser |
| `wasi-component` | WASM component against a WIT world × WASI 0.2 |
| `wasi-module` | WASI 0.1 module × WASI 0.1 |
| `wasi-oci` | Wasm OCI artifact (packaging of wasi-component) × OCI runtime |
| `oci-image` / `oci-image/<platform>` | Container image (per platform) × container runtime |
| `oci-index` | Multi-arch index over `oci-image/<platform>` members × container runtime |
| `lira-tool` | Jar × `lira.tool` — the build tool's plugin application type |

## Carriers

Interface-bearing artifact kinds within forms; each has a discipline.

TASTy · classfile signatures · Kotlin `@Metadata` · `.d.ts` · WIT · Web IDL · C headers ·
OpenAPI documents · TEL capability listings · TEL schemas.

## Disciplines

| Id | Canonicalizes | Status |
| --- | --- | --- |
| `tasty/1` | TASTy signatures (jvm, sjsir, nir) | spec |
| `classfile/1` | Classfile signatures; certifies linkage | spec |
| `jsig/1` | Java signature surface (stubs; jdk/android contracts) | spec |
| `kotlin-metadata/1` | Kotlin `@Metadata` declaration surface | spec |
| `dts/1` | TypeScript declarations | spec |
| `wit/1` | WIT worlds (component universe, WASI contracts) | spec |
| `webidl/1` | Web IDL (browser contracts) | spec |
| `cheader/1` | C headers (shared-library contracts) | spec |
| `openapi/1` | OpenAPI descriptions; the first `app`-realm discipline | spec |
| `capability/1` | Capability listings — commands, tool availability, Web APIs; the general no-formal-carrier discipline | spec |
| `resource/1` | Resource path claims | spec |
| `opaque/1` | Whole-file rigidity for undisciplined content | spec |
| `environment/1` | Environment topology: bindings + givens, nothing else | spec |
| `tels/1` | TEL schema payloads; grades coincide with TEL's subsequence relation | spec (new) |
| `envvar/1` | Environment-variable presumptions | proposed (builds.md §12) |
| `file/1` | Filesystem-presence presumptions | proposed (builds.md §12) |
| `proto/1` | Protobuf descriptors | anticipated |

## Tools

Built-in unless noted. Each carries edges; edge ids default to output forms.

| Name | Edges (input → output) | Notes |
| --- | --- | --- |
| `scalac` | scala(+java)→jvm; scala→sjsir; scala→nir | Components per edge: `scala-js`, `scala-native` |
| `javac` | java→jvm | |
| `kotlinc` | kotlin→jvm; kotlin→klib | |
| `tsc` | typescript→js | |
| `rustc` | rust→native/<triple>; rust→wasm-object | |
| `sjs-linker` | sjsir→js-app; sjsir→wasm-browser; sjsir→wasi-component | One implementation, three egresses; component output is library content when the world isn't runnable |
| `wasm-ld` | wasm-object→wasi-module | |
| `jar` | jvm→jvm-app | |
| `d8` | jvm→android-app | |
| `native-image` | jvm→native-image/<triple> | GraalVM; join edge from `native` |
| `xeq` | jvm-app→xeq-bundle | Packaging edge |
| `docker` | dockerfile+context→oci-image/<platform> | Packaging edge; frozen environment-of-one (builds.md §15) |
| `oci` | wasi-component→wasi-oci; oci-image/*→oci-index | Packaging edges |
| `clang` | c→native/<triple> | The parameterized-edge motivating case |
| `myscalac` | scala(+java)→jvm | Example LIRA-distributed tool (`example/scalac-fork`, artifact `lira-tool`) |

## Edge components

Separately-versioned constituents of edges: `scala-js` (scalac/sjsir),
`scala-native` (scalac/nir).

## Host contracts

| Name | Contract of | Notes |
| --- | --- | --- |
| `java.base`, `java.rmi`, `java.sql`, … | JDK platform modules | Vendor-named (dots); coordinated by tags (`jdk-19`); stewarded namespace (e.g. adoptium.net) |
| `jdk`, `android` | Java platform surfaces | `jsig/1` |
| `nodejs` | Node builtins | `dts/1` natural carrier |
| `browser-baseline` | Web APIs | `webidl/1` |
| `wasi` | WASI capability interface — WASIp2 is a lineage point here | `wit/1` |
| `posix` | POSIX shell and userland | `capability/1`; variant capabilities like `sed:gnu`, `awk:bsd` |
| `openssl`, `glibc-x86-64-linux`, … | Shared libraries; libc per triple | `cheader/1`; triples parameterize modules, not the format |
| `scalajs-javalib` | Scala.js's Java library surface | shared `jsig/1` enables cross-contract spanning |
| `lira.tool` | The build tool's plugin ABI (a Scala trait) | `tasty/1`; `lira.tool-wit` is the potential WIT twin |
| `main/config` | Generated per-topology config contract carrying guarantee atoms | presumption disciplines |

## Presumption kinds

Registry words mapping (many-to-one) to disciplines:

- Current: `envvar` → `envvar/1` · `command` → `capability/1` · `file` → `file/1` ·
  `dataset` → (dataset discipline, to come)
- Projected configuration-class: `locale`, `isa`, `device`, `net`, `tty`
- Admission-gate (deploy-constrain only, never reach the compiler): `memory`, `disk`,
  `clock`, `privilege`

## Non-universe realms

`host` (contracts) · `app` (deployables) · `env` (environments).

## Datasets

Versioned data artifacts guaranteeable via the `dataset` kind: tzdata, Unicode/ICU
tables, CA trust roots, the MIME database.

## TEL schemas and modules

| Name | What |
| --- | --- |
| `specification.tel/tels:1.0.0` | The tels meta-schema, version-pinned in the TEL spec |
| `name lira` (spec §14) | The manifest schema |
| `lira-tree`, `lira-atoms`, `lira-uses`, `lira-delta` | Metadata-blob schemas |
| `lira-capabilities` | Capability listing schema (hosts.md §5) |
| `build` (`build.schema.tel`) | The build-file schema, registered locally; publishable as a `tels/1` module |
| `tool` (`tool.schema.tel`) | Tool descriptors, extracted at publish (builds.md §14.3); registered; worked instance `scalac.tool.tel` |

## Overloaded words, disambiguated

- **WASM** is four different things: two universes (`wasm-object`, `component`), several
  application types (`wasm-browser`, `wasi-component`, `wasi-module`, `wasi-oci`), and a
  closed-artifact format. Never a single node.
- **JAR** is two: the canonical derivative artifact of a `jvm` section (automatic,
  §13.6) and the executable-jar application type `jvm-app` (an egress product).
- **WASIp2** is a host contract — never a format, never a universe.
- **JavaScript** is a universe (`js`), not a source form: the universe is its form.
