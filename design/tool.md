# The `lira` Tool

The unified command-line tool for everything LIRA: inspecting and verifying `.lira` files,
producing and versioning releases, maintaining a local content-addressed store with LRU
retention, resolving and fetching dependencies, publishing, and running as a network node for a
LIRA directory. The spec fixes only one behavior (§5.3: the tool's default action when invoked
on a `.lira` file must at minimum present the manifest, rendered as canonical TEL); everything
else here is design.

The organizing principle: **one program, one content-addressed store, composable roles.** The
CLI, the developer's background daemon, a company's caching mirror, and the public directory
node are the same binary distinguished only by configuration. The store (§2), retention (§3),
transports (§4), commands (§5), roles (§6), and the network API (§7) are all consequences of
that principle.

This document is the tool's operational design; [`distribution.md`](distribution.md) remains
the protocol and trust design. Where they meet, this document names abstract operations and
defers wire formats.

## 1. Principles

1. **One store.** Every byte the tool retains — cached, pinned, published, derived — lives in
   one content-addressed store, keyed by the spec's domain-separated hashes (spec §7.1).
   Integrity is recomputation; there is no trusted metadata to corrupt.
2. **Roles, not programs.** Serving the store to a build tool on loopback and serving it to
   the internet are the same code path behind different listeners. A node composes optional
   roles — host, directory, mirror, witness (§6) — over the store.
3. **Local and network resolution are one code path.** A directory of `.lira` files on disk, a
   private company mirror, and the public directory are all *sources* (§4); buildpath
   validation (spec §13.3) neither knows nor cares which answered.
4. **Published content is never silently evicted.** LRU retention (§3) applies only to content
   the tool cached opportunistically; anything pinned — explicitly, by a project, or by a
   serving role — is a GC root.
5. **Three Merkle structures, kept distinct.** The content DAG (manifest → payload → blobs →
   derivatives) answers *give me these bytes*; the transparency log (distribution.md §4)
   answers *prove this was published*; the set commitment (§7) answers *what do you have that
   I lack*. Each has its own domain-separated hashing and its own API operations; blurring
   them is a design error.

## 2. The Store

### 2.1 Location and Tiers

The user store is a single root, `$LIRA_STORE`, defaulting to `~/.local/share/lira/store`.
Deliberately *not* `$XDG_CACHE_HOME`: a cache directory is deletable by contract, but the
store holds pinned and published content whose loss is not merely a performance event. The
cost of this choice is that opportunistically cached content also escapes cache-cleaning
tools; the journal-driven LRU discipline of §3 is the compensation.

A read-only **system store** MAY sit below it — `/var/lib/lira/store`, or wherever an
administrator prefers — for machine-wide provisioning: shared build machines, CI images, a
distribution's packaged host contracts. Lookup falls through a ranked list of tiers,
uppermost writable, lower tiers read-only; ingest, pinning, the journal, and GC touch only
the top. The precedents are Git's alternates and the Nix and pnpm content stores.

Lower-tier objects are normally read in place — no copy, no link. Promotion into the user
tier happens only when durability demands it: pinning an object that exists only in a lower
tier promotes it, so a package uninstall cannot pull bytes out from under a project. On the
same filesystem, promotion is a reflink where the filesystem supports one (APFS `clonefile`,
btrfs/XFS) and a hard link otherwise — safe precisely because objects are immutable, stored
mode `0444`, and re-verified on read (§2.4); across filesystems it is a copy. The same
ladder (reflink, hard link, copy) applies when `lira jar` links a derivative out into a
build directory.

### 2.2 Layout

`.lira` files are **decomposed** on ingest. Spec §5 fixes the byte layout — the four-byte
magic, the BinTEL manifest (self-delimiting), the Brotli payload — so the original file
reconstructs byte-identically from its parts, and storing parts once buys deduplication
everywhere it matters: a re-signed
manifest (spec §12.5) shares its payload with its predecessor; successive releases share most
of their blobs; a section's canonical derivative is stored once however many buildpaths use
it.

```text
store/
  manifest/aa/<hash>     raw manifest bytes (the head of the file, after the magic)
  payload/aa/<hash>      the compressed payload, whole, keyed by payload.hash (spec §8.4)
  blob/aa/<hash>         individual decompressed blobs, keyed under lira/1:blob
  derivative/aa/<hash>   canonical derivative artifacts, keyed under lira/1:derivative
  log/<directory>/       cached STHs, log records, consistency state, per directory
  coord/<directory>/<domain>/<name>   cached Release records (evidence-carrying; LRU + TTL)
  pin/<name>             named references to manifest hashes — the GC roots
  journal                append-only access log for LRU accounting (compacted by gc)
```

Two-character fan-out directories keep any one directory small. All writes are
write-to-temporary then rename-into-place, so a crashed ingest leaves no partial object and
concurrent processes (CLI, daemon, node) share the store with only advisory locking around the
journal and GC.

**Keying subtleties.** Two of the tiers cannot be keyed naively:

- *Manifests.* The spec's `lira/1:manifest` domain is the **signing** domain — canonical
  BinTEL with signature fields removed (spec §15.2) — so it identifies the manifest's
  *content*, not its bytes: two formattings of one manifest share it. The store tier is keyed
  by a hash of the raw manifest bytes under its own domain, `lira/1:manifest-bytes` (spec
  §7.1) — which is also what a `Release` record pins (distribution.md §5) and what a
  `Range`-fetched head must be checked against.
- *Payloads.* `payload.hash` is computed over the **decompressed** stream (spec §8.4), while
  the stored object is the compressed envelope. The key therefore does not recompute from the
  stored bytes directly; verification decompresses. This is correct — the envelope
  participates in no identity (spec §8.1) — but it makes payload verification the one
  non-trivial `fsck` step.

### 2.3 Whole Payloads *and* Loose Blobs

Storing the compressed payload whole *and* its blobs individually is deliberate redundancy.
The whole payload is what `.lira` reconstruction, re-serving, and `payload.hash` pinning need;
Brotli recompression is not byte-deterministic across encoder versions and settings, so
discarding the envelope and recompressing on demand would break the identity everything pins.
The loose blobs are what deduplication, materialization, and want/have sync (§7) need. The
blob tier is derived data: `gc` may evict loose blobs of a release whose payload is retained
and re-extract them on demand.

A release that arrived as an increment (spec [`increment.md`](../spec/increment.md)) breaks
the first half of this: the receiver reconstructs the decompressed stream and never sees the
publisher's envelope. The store therefore distinguishes a **published** envelope — the bytes
the publisher uploaded, hash-pinned by the `Release` record's total byte-length
(distribution.md §5) — from a **local** one, the store's own encoding of a reconstructed
stream. Both live under `payload/<hash>`, since the key is the decompressed stream's; a local
envelope is marked as such, satisfies every verification the store performs (§2.4 — stamps
are over the decompressed stream and are unaffected), reconstructs a `.lira` file that is
the same release, but is never offered as the published asset, never checked against the
record's total byte-length, and is replaced by the published envelope if one is later
fetched. A node serving the host or mirror role that holds only a local envelope for a
release serves its blobs and its manifest, and fetches the published envelope before serving
*that*.

### 2.4 Integrity

- **Eager on write**: every object is hashed before it is linked into place; nothing enters
  the store unverified.
- **Eager on read**: objects are rehashed as they are read for use — a derivative handed to
  a build, blobs streamed into a materialization, anything served to a peer. BLAKE3 runs at
  memory bandwidth, so the cost is small; the benefit is that a corrupted disk, a tampered
  lower tier (§2.1), or a bad promotion is a loud error at the point of use, never a silent
  hazard. Payloads, whose key is over the decompressed stream (§2.2), hash during
  decompression.
- **`lira fsck`**: full re-audit — rehash every object, including those never read,
  decompress and check payloads, quarantine mismatches.
- **Verification stamps**: install-grade verification (spec §16, steps 0–3, 5, 8) is a
  property of an immutable object, so its outcome is recorded once, as a stamp keyed by
  manifest hash, never invalidated. Signature trust (L123) is *not* stamped — trust
  configuration can change — and is re-evaluated from cached log records, which is cheap.

### 2.5 Derivatives Are the Materialization Cache

The `derivative/` tier *is* the cache of spec §13.5, stored exactly as spec §13.6 recommends:
a cache entry *is* the canonical derivative artifact. `lira jar` becomes "materialize into the
store, then link or copy out"; repeated builds hit the cache; and any bare JAR on disk is
findable by rehashing under `lira/1:derivative` and looking the result up (`lira id`, §5) —
recovering the release, its API identity, and which integration it is.

## 3. Retention: Pins, LRU, and GC

**Pins are GC roots; reachability is the content DAG.** Pinning a manifest retains its
payload, its blobs, and (by policy) its derivatives. Pins arise three ways:

- **explicitly** — `lira pin <coordinate|hash>`;
- **from projects** — `lira pin --project` reads a build's resolved release set (ultimately a
  lockfile of implementation identities, spec §6) and pins its closure;
- **from roles** — a node serving the host or mirror role implicitly pins everything it
  serves. Published content is exempt from LRU by definition: availability is the role's whole
  job (principle 4).

Everything unpinned is subject to **LRU eviction under a byte budget** (`store.budget`).
Recency comes from the append-only journal, compacted at GC time — deliberately a flat file,
not a database; filesystem `atime` is rejected as unreliable. Writers append with `O_APPEND`
and no lock; records are self-delimiting lines, and compaction skips torn or interleaved
tails. This is sound because the journal is **advisory**: it informs eviction order and
nothing else, so the worst outcome of a lost record is that an object looks less recently
used than it was. Concurrent CLI, daemon, and node traffic therefore threatens nothing. The **eviction unit is the
release closure** — manifest, payload, loose blobs not shared with a retained release,
derivatives — never a lone blob, so a cached release is never half-present. Loose blobs of a
retained payload are evictable ahead of the closure (§2.3) since they are re-derivable.

`lira gc` = compact the journal, compute reachability from pins and roles, evict
least-recently-used closures until under budget, drop `coord/` records past their TTL.

## 4. Sources and Transports

Two abstractions carry the local/network unification.

A **source** answers resolution questions: coordinate → release, with whatever evidence it
can offer. Three implementations:

- the **local store** — answers from `coord/` records and ingested manifests; offline-first;
- a **directory** — the index of distribution.md, reached over some transport, answering with
  inclusion proofs against a cached STH;
- a **plain directory of `.lira` files** — scanned, verified at install grade, and treated as
  a published set with no proofs: trust is local by fiat. This is vendoring, and it is not a
  degraded mode: `lira resolve --from ./lib`, buildpath validation, and materialization run
  the same code as against the public directory. The `published` argument of
  `Buildpath.publishable` is fed identically from either.

A **transport** moves bytes and proofs: the local filesystem; HTTPS (the index API and GitHub
`Range` requests of distribution.md §§6–8); the single-datagram UDP fast path; and a peer node
speaking the store API of §7. Sources are configured as ranked lists, so resolution falls
through — local store, LAN mirror, public directory — and every answer is verified the same
way regardless of who gave it (distribution.md, principle 1).

**Staleness has a safe direction.** Resolution answers a *latest* question, and every answer
is hash-pinned, so a stale local answer can never be a forged release — only an old one, or
one whose advisory `Withdrawal` has not yet been seen. The tool therefore treats local
answers as usable within a freshness window (default: an STH no older than seven days,
matching the daily fetch cadence of distribution.md §4), falls back to the network when the
window is exceeded, and falls back to the stale answer *loudly* when the network is
unreachable. `--fresh` demands a directory round-trip; `--offline` accepts any local answer
with a warning.

**Hosting is pluggable.** GitHub Releases is one transport backend for artifact bytes; a
node's own store (the host role, §6) is another. Nothing in the trust story changes with the
backend, because nothing in verification ever depended on where bytes came from — hash-pinned
records and log proofs carry it all. This resolves the apparent tension with distribution.md
§1 ("the index never serves artifact bytes"): that principle constrains the *directory role*,
and hosting is a different role, even when one node happens to hold both.

**Trust is explicit, bootstrapped, and shallow.** A directory becomes a source only by an
explicit act — `lira trust <endpoint>` — recorded in per-user configuration with the
directory's STH key and namespace scope. Project configuration never adds trust; it may only
restrict (pin versions, demand proofs, forbid sources). The tool ships with the canonical
public directory pre-trusted, so the common case — a private directory whose modules depend
on public ones — needs no inference at all: private modules resolve through the private
directory, and their public dependencies through the already-trusted public one.

Where inference is genuinely needed — chains of private directories, such as a partner's —
it takes the form of **referrals, surfaced rather than silently followed**. A directory that
verifies a registration whose dependencies live in another directory necessarily relied on
that directory's log to do so (the registration-time obligation of distribution.md §4); it
records that reliance as a `Referral` record — endpoint, STH key fingerprint, namespace
scope — which is simultaneously evidence of its own diligence and a routing hint to clients.
When a client first needs a referred directory, nothing extends silently: the tool prompts
once, and the resulting trust is attenuated three ways — **resolve-only** (never a
publishing target by inference), **scoped** to the referred namespaces, and **one hop**
(referrals from referred directories are not followed; going deeper requires another
explicit act). Provenance is recorded, so revoking trust in a directory cascades over
everything trusted via it. Two trusted directories claiming the same namespace is not
resolved by ranking: it is surfaced as an error, because a verifiable disagreement is
exactly the evidence the transparency design exists to produce.

## 5. Command Surface

Grouped below; *exists* marks commands already implemented in `src/cli/lira.LiraTool.scala`.
Groups are nouns only where a noun genuinely collects (`cache`, `key`, `ns`, `log`); everything
else stays a flat verb.

### Artifact commands (all exist)

| Command                                    | Semantics                                                        |
| ------------------------------------------ | ---------------------------------------------------------------- |
| `lira <file.lira>`                         | print the manifest (spec §5.3's minimum obligation)              |
| `lira verify <file.lira>`                  | install-grade verification                                       |
| `lira jar <universe> <file.lira>`          | canonical derivative JAR — now materializing *via* the store §2.5 |
| `lira assign <file> [<prev>] [--major]`    | derive the next version (spec §12.5)                             |
| `lira diff <prev> <next> [--blob <file>]`  | what changed between two releases, and its grade (§5.1 below)    |
| `lira delta <module> <from> <to> [--out <file>]` | write a delta carrying the cached release `<to>` relative to `<from>` (spec increment.md §8) |
| `lira atoms <file> [--realm …]`            | the atom listing of a release, or of a bare artifact (§5.2)      |
| `lira harvest jdk\|android …`              | host-contract lineages from `ct.sym` / `android.jar` (§5.3)      |

#### 5.1 `diff`

`Grade.between` is the whole compatibility question and answers it without a listing — the check
is set arithmetic over atoms (spec §10.3). The listing answers the reader's *next* question: the
atoms added, removed and replaced, per discipline, grouped by the owner each discipline
decomposes its own keys into (`Discipline.decompose`). A rigid atom whose value moved reads as a
removal *and* an addition, since that is what it is (§10.2); only a replaceable atom is reported
as replaced.

`LiraDelta` is deliberately not the source of the listing: it records value hashes alone, because
a verifier checking a lineage step holds both releases and needs no keys. `--blob` writes that
record for the machine-checkable case; the listing is computed from the two atomizations.

Sections are summarized, not listed per atom: L108 requires every section of a release to present
identical atoms, so a per-section breakdown would repeat itself. What a reader wants is which
(realm, integration) cells the release still carries, and which it gained or lost.

#### 5.2 `atoms`

The same question asked of two kinds of input, and the difference is load-bearing. A `.lira` file
*declares* its atoms in the Atoms metadata blobs its `api` records name (spec §10.4), so the
listing is read, not recomputed, and is exactly what a consumer's verifier will compare against.
A bare artifact — a jar, a `.class`, a `.d.ts`, a `.idl` — declares nothing, so its atoms are
computed on the spot, answering instead what this content *would* contribute were it published.
The output says which of the two it is.

Computing them takes what atomization always takes (§11.2): `--realm`, since a discipline out of
its domain claims nothing at all, and `--classpath` for the membership-keyed disciplines, since a
type's presented surface includes what it inherits. Neither is guessable from the bytes. Where a
discipline *would* have claimed the content in another realm, the listing says so rather than
letting an `opaque/1` fallback read as an answer.

`--discipline <id>` restricts the listing to one discipline — the way to see a jar under `jsig/1`,
which tolerates supertypes outside the claimed content, rather than `classfile/1`, which fails on
them by design. `--owner <prefix>` restricts it to keys under one owner.

#### 5.3 `harvest`

`harvest` produces host-contract lineages from vendor-published carriers (spec hosts.md §3):
today `jdk` from `ct.sym` and `android` from `android.jar`s, as hardcoded kinds. Every
harvestable carrier in hosts.md's registry wants the same treatment, and the design that
admits them is to make the source specification an artifact rather than configuration.

**Harvest descriptors.** Where a carrier comes from is a source of truth, so it belongs in
neither tool nor project configuration — §9's stance, extended: configuration never adds
sources of truth. It belongs in a **harvest descriptor**: a TEL document, conforming to a
`lira-harvest` schema, stating everything a harvest needs — the contract family and its
module-naming scheme, the harvester (name and version, below), the discipline the emitted
`host` sections declare, and the acquisition: an advisory locator plus, optionally, a
content-hash **pin** of the acquired material. The locator/pin split is the `artifact` pin of
services.md §4.2, reapplied: identity by hash, bytes fetched from wherever bytes are best
fetched. The command then generalizes without changing shape: `lira harvest jdk` becomes name
resolution to a descriptor shipped with the tool — one per registry contract (hosts.md §11) —
and `lira harvest ./contract.tel` takes an explicit descriptor for anything else. A descriptor
with a pinned acquisition makes the harvest **reproducible**: re-running it must land on
identical atoms, so re-harvesting is a recomputation check, on the verification standing
everything else in the format enjoys.

**Stewardship.** The definitive object is the signed contract release — not the descriptor,
and not the upstream source. Atoms recompute from the shipped carrier, so a published
contract's content claims are checkable by anyone holding the release; what is genuinely
authorial, and therefore what stewardship *is*, is the naming residue hosts.md §3 identifies:
module granularity, the tag scheme, and sanctioning majors (`+<tag>`, L110). The descriptor is
the middle layer — provenance — and publishing it beside the contract modules records how the
carrier was obtained: with a pinned acquisition it is the seed of the attestation layer
services.md defers. Governance stays deliberately open (universes.md §5.6); bootstrap
stewardship under a neutral namespace is low-stakes by construction, because content claims
verify by recomputation, cross-module spanning leaves consumers free to adopt a better
publisher's modules provably, and handover is a signing-key succession, not a format event.
Vendor adoption is the desired end state.

**The harvester interface.** The implementation has already discovered it implicitly —
`CtSym.releases` / `CtSym.modules` and `HostArchive.surface` feeding the shared `emit` are its
two instances. Named, a **harvester** is three stages and a declaration:

- **`acquire(descriptor) → material`** — the only impure stage: locate `ct.sym`, download a
  `webref` snapshot, run `deno types`, read jars. Material lands content-addressed in the
  store (§2), and the stage is skipped entirely when the descriptor pins material already
  held.
- **`releases(material) → ordered [(tag, release material)]`** — enumeration of the *vendor's*
  release axis, ordered by the vendor's succession, which is what lineage assembly consumes.
  Necessarily separate from acquisition, and `ct.sym` is the proof: one acquisition yields
  every JDK release, while Android is one jar per release.
- **`modules(release) → {module → carrier tree}`** — the granularity mapping (hosts.md §3):
  per contract module, the carrier content of its single `host` section. Android returns a
  singleton; the JDK fans out per platform module.
- **`discipline`** — what the emitted sections declare (`jsig/1`, `dts/1`, `webidl/1`,
  `wit/1`, `cheader/1`).

Everything downstream is generic and is exactly what `emit` already does: group by module
across vendor releases, assemble lineages, grade each step, demand `+<tag>` sanction on
majors, derive versions, stamp the shared tag, sign (spec §15). Two
properties are load-bearing rather than accidental:

1. **Purity after acquisition.** `releases` and `modules` are pure functions of the material
   bytes. This single constraint is what makes pinned descriptors reproducible and re-harvest
   a verification act.
2. **Tag coordination by construction.** Because the release axis is enumerated before the
   module fan-out, every module contract from one vendor release carries the same tag
   automatically — hosts.md §3's coordination requirement falls out of the interface shape
   rather than needing a rule.

Harvesters version like disciplines, on spec §11.1's reasoning: a change to release
enumeration or module mapping changes which contracts exist and what their lineages say, so it
is `jdk/2`, never a revision of `jdk/1`, and the descriptor names the harvester version it was
written against. The boundary to hold: harvesters are tool plugins, not spec objects. The
carrier→atoms direction (disciplines) is normative because verifiers depend on it; the
world→carrier direction needs only reproducibility, which the descriptor schema and the purity
rule supply — so the `lira-harvest` schema belongs, eventually, in the spec's extensibility
seam (spec §14), while the interface stays here.

### Store commands (new)

| Command                        | Semantics                                                        |
| ------------------------------ | ---------------------------------------------------------------- |
| `lira cache add <file…>`       | ingest: decompose, verify eagerly, deduplicate                   |
| `lira cache ls`                | list cached releases with pin state, size, recency               |
| `lira cache rm <hash>`         | evict a closure explicitly                                       |
| `lira cache path <hash>`       | print an object's store path (the scripting hook)                |
| `lira pin` / `unpin <ref>`     | manage GC roots; `--project` pins a build's resolved set         |
| `lira gc [--budget <bytes>]`   | compact journal, evict LRU closures to budget                    |
| `lira fsck`                    | full store re-audit; quarantine mismatches                       |

### Resolution commands (new)

| Command                                            | Semantics                                        |
| --------------------------------------------------- | ------------------------------------------------ |
| `lira resolve <coord>[@<ver>] [--compat <snapshot>] [--from <source>]` | release record + evidence; `--compat` is the spec §13.2 primitive |
| `lira fetch <coord\|hash>`                          | resolve, download the closure into the store, print the payload hash; prefers an increment (§7) against a held release where the source offers one |
| `lira add <file…>`                                  | ingest whole `.lira` files, or deltas, reconstructed against their cached base and verified (spec increment.md §7); `cache add` is the same |
| `lira id <artifact>`                                | rehash under the appropriate domain, look up the release (§2.5) |

### Identity commands (new)

`lira key gen|ls|endorse|rotate` — ML-DSA keypairs, SSH endorsement, rotation-by-endorsement
(distribution.md §3). `lira ns check <domain>` — verify the `_lira.` TXT record locally before
claiming; `lira ns claim <domain>` — submit the namespace claim.

`lira trust <directory> [--via <referrer>]` / `lira trust ls` / `lira untrust <directory>` —
the explicit trust acts of §4: `--via` records referral provenance (and applies the referral
attenuations); `trust ls` shows the trust graph with provenance; `untrust` cascades over
everything trusted via the revoked directory.

### Publishing commands (new)

`lira publish <file.lira> [--to <host>]` — verify at install grade, upload bytes to the hosting
backend (a GitHub release asset or a host-role node), submit the `Release` record to the
directory, confirm inclusion. `lira withdraw <coord>@<ver> --reason <text>` — the advisory
record of distribution.md §5; nothing is deleted.

### Node commands (new)

`lira serve [--host] [--directory] [--mirror <origin>] [--witness]` — run a node with the
selected roles (§6), foreground. `lira sync <peer>` — one-shot want/have reconciliation
against a peer (§7): mirror catch-up and LAN cache priming are the same operation.

### Log commands (new)

`lira log head [<directory>]` — fetch, verify, and cache the STH (consistency-proved against
the previous cached head). `lira log proof <hash>` — inclusion proof for a record. `lira log
audit` — cross-check cached STHs and witness gossip for equivocation.

### Housekeeping (all exist)

`lira install` (shell completions), `lira help`, `lira quit` (daemon shutdown).

## 6. Roles

A node is the tool running with one or more roles enabled, all over the one store:

- **host** — serves store objects by hash: manifests, payloads, blobs, derivatives (§7). On
  loopback this is the developer's own cache made available to build tools and IDEs; on the
  network it is a hosting backend, the peer of GitHub Releases in §4.
- **directory** — registration, resolution, and the transparency log: distribution.md's index,
  restated as a role. All of that document's obligations (registration-time verification, §4;
  the UDP and HTTPS surfaces, §§6–7) attach to this role unchanged.
- **mirror / witness** — replicates another node's published set via set-root comparison and
  want/have (§7), and/or gossips STHs so equivocation is detectable.

The compositions are the deployment stories: a developer's laptop is an implicit loopback
host; a company mirror is host + mirror; the public directory is directory, optionally + host;
a full independent replica is all four.

## 7. The Store API

The network face of the store, named abstractly; wire formats are deferred to the protocol
documents. Everything is verifiable by the caller — by recomputation for hash-keyed answers,
by proof against a cached STH for log-backed ones — so, as in distribution.md, no response
carries a signature.

- **`GET <domain>:<hash>`** — fetch any object by domain-separated hash: blob, manifest bytes,
  payload, derivative, log record. The universal primitive; self-verifying by recomputation
  (payloads by decompress-then-hash, §2.2).
- **`WANT/HAVE`** — the caller names a target payload hash and the blob hashes it already
  holds; the responder streams the missing blobs. This falls out of the format rather than
  being engineered: a payload is a sorted, duplicate-free sequence of blobs (L103), so the
  have-set intersects in one merge pass, and cross-version delta fetch is free because
  successive releases share most blobs. Belongs on streaming transports only; the 1232-byte
  UDP ceiling (distribution.md §6) excludes it from the datagram path.
- **`INCREMENT <base>:<target>`** — the caller names two payload hashes, the base it holds
  and the target it wants; the responder streams an increment file (spec
  [`increment.md`](../spec/increment.md)), or declines. Where want/have skips the blobs two
  releases share, an increment additionally compresses each changed blob against its
  predecessor, which for compiled code is most of the remaining bytes. The responder may
  serve a stored increment the publisher produced or compute one on demand from its loose
  blobs — an increment has no identity (L156), so the two are indistinguishable and equally
  good — and the caller verifies the result as a payload (L155), so a responder is trusted
  for nothing. Streaming transports only, as want/have.
- **`SET-ROOT`** — a node commits its published set as a Merkle tree over the sorted object
  hashes it serves, and answers difference queries against the root. Mirrors sync by root
  comparison plus want/have for the difference. This is the third Merkle structure of
  principle 5, with its own domain tags, `lira/1:set-leaf` and `lira/1:set-node` (spec §7.1).
- **Log operations** — `HEAD`, `PROOF`, bulk ranges: exactly distribution.md §§6–7, not
  restated here.

## 8. Daemon and Node

Same binary, separate processes, shared store.

The **Ethereal daemon** stays what it is: a per-user background JVM that the CLI attaches to
over a socket for millisecond startup and live tab-completions. It additionally serves build
tools and IDEs over a **UNIX domain socket** (`$XDG_RUNTIME_DIR/lira/api.sock`, falling back
to a socket beside the store), speaking a small framed local IPC — the *operations* of §7
(get, want/have, resolve, materialize) but not their wire format. A UNIX domain socket is
the right fit: filesystem permissions are the authentication (mode `0700` on the directory —
no port, no token, no confused-deputy exposure to other local users), and it keeps the
network protocol surface out of the local path entirely. It dies with the login session, and
that is fine.

**`lira serve`** is a foreground, long-running process — systemd/launchd-friendly, explicit
lifecycle, run as a service user — for any role exposed beyond loopback. A tab-completion
accelerator that dies with the login session is the wrong host for a public network service:
lifecycle, privilege, and resource limits all differ. But both processes are thin shells over
the same store, source, and transport code, so "the local daemon is the same code as a public
node, differently configured" holds where it matters.

Concurrent store access is safe by construction: objects are immutable and land by atomic
rename (§2.2); only the journal and GC take advisory locks; a GC never evicts an object a
concurrent materialization holds pinned.

## 9. Configuration

Minimal, and TEL, naturally: the store path, lower store tiers (ranked paths, §2.1), and
byte budget; the ranked source list (directories and mirrors, with per-source trust: proofs
required or local fiat); key locations; and a stanza per enabled role (listeners, origins to
mirror, witness peers). A project's checked-in configuration never adds sources or trust; it
may only restrict (§4).

## 10. Open Questions

1. **System-store provisioning**: who writes the lower tier — the OS package manager,
   `lira` itself under elevated privileges, or both — and whether `harvest` should be able
   to target it directly. The descriptor design of §5.3 narrows the second half: a harvest
   driven by a pinned descriptor is a reviewable, reproducible object an administrator can
   sanction, so a privileged `harvest` targeting the system store becomes an auditable act
   rather than an open-ended one. Who holds the privilege remains open.
2. **Referral depth**: one hop is the design (§4); whether real partner-chain topologies
   ever justify more, or whether the explicit-act-per-hop friction is exactly right.
3. **Increments against a have-set**: spec increment.md names one base by payload hash and
   references its records by ordinal. A variant that names priming context by blob hash
   would let an `update` draw on any blob in the caller's loose-blob tier — a receiver
   holding releases _n_−1 and _n_−2 but not _n_ — at 32 bytes per reference instead of one
   or two; whether real update patterns ever leave a consumer without the immediate
   predecessor is the question.
4. **Who produces increments**: the publisher at publish time, uploaded beside the `.lira`
   asset for the lineage predecessor and perhaps a few releases back; or a host-role node on
   demand, from loose blobs, cached by (base, target) pair. Both are valid (an increment has
   no identity); the first suits GitHub-hosted assets, the second suits nodes. Producing one
   needs an encoder that can continue from a preloaded window: the tool carries a port of
   pneumatic-brotli's encoder with that mode (`lira.Priming`), which belongs in pneumatic
   itself once it gains one (its output already depends on the window alone, so the mode is a
   preloaded hash chain and an offset, not a new algorithm); applying one needs nothing
   beyond an RFC 7932 decoder.
