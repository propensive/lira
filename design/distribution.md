# Distribution: the LIRA Index

How `.lira` files are published, discovered, and resolved. The design separates three planes:
**hosting** (GitHub Releases serves bytes), a thin **index** (registration, resolution, and a
transparency log — it never proxies artifacts), and **clients** (build tools that verify
everything locally). The index's fast path is a stateless, single-datagram UDP protocol whose
responses are verifiable without any per-response signature.

Prior art drawn on throughout: Go modules (domain-scoped module paths) and its checksum
database sum.golang.org (package transparency log); Certificate Transparency (RFC 6962);
Bluesky's handle verification (DNS TXT record carrying a key binding); ACME DNS-01.

## 1. Principles

1. **The index is untrusted for integrity.** Every answer it gives is verifiable by hash, by
   Merkle proof against a signed tree head, or by signature on the artifact itself. A fully
   compromised index can censor or serve stale answers; it can never forge a release.
2. **The index never serves artifact bytes.** Manifests and payloads come from GitHub Releases
   (verified: release-asset downloads honour `Range` requests — `206 Partial Content`,
   `accept-ranges: bytes` — and the manifest is the head of a `.lira` file by construction,
   spec §5, so `Range: bytes=0-(manifest length−1)` retrieves it without the payload).
   Hosting is pluggable: a node running the *host* role (tool.md §6) is a second byte-serving
   backend beside GitHub Releases. The principle constrains the index — the directory role —
   and stands even when one node happens to hold both roles.
3. **Everything is content-addressed and append-only** — records, proofs, manifests, payloads
   — so caching and mirroring are trivial and unbounded.

## 2. Naming: domain-scoped coordinates, DNS-verified

A module coordinate is `<domain>/<name>`, e.g. `soundness.dev/gossamer-core`. (This resolves
the module-coordinate question left open in the spec.)

**Namespace proof.** The domain owner publishes a TXT record at `_lira.<domain>`:

```text
_lira.soundness.dev.  TXT  "lira1 k=<BASE-256 ML-DSA key fingerprint>"
```

Multiple records bind multiple keys; the format reserves further space-separated tokens
(`p=…` policy entries) for the future. A fingerprint is ~32 characters — comfortably inside
the 255-byte TXT string limit. The index verifies the record when a namespace is first
claimed and re-verifies at every publish, querying from **multiple network vantage points**
(multi-perspective validation, as Let's Encrypt does for DNS-01), recording whether the zone
was DNSSEC-signed, and logging every observation as a `NamespaceProof` record (§5). DNS is
thus the *authorization* channel; the transparency log is the *evidence* channel.

**Namespace control ≠ lineage control.** The TXT record authorizes publishing *new modules*
under the domain. *Extending an existing module's lineage* additionally requires the release
manifest to be signed by a key already bound to that module (its original publishing key or a
successor endorsed by one — §3). Consequences:

- A domain transfer or expiry does not let the new owner graft releases onto an existing
  lineage: without a prior key, the best they can do is start a visibly new major lineage,
  and the log makes the discontinuity public.
- Key rotation is an ordinary logged event: the old key endorses the new one.
- Loss of all keys for a module is recoverable only by a new lineage plus an out-of-band
  dispute process — deliberately expensive, because the alternative is an impersonation hole.

## 3. Identity: ML-DSA keys, dual-anchored

Publishers sign manifests with **ML-DSA-65** (spec §15). The publisher's key is anchored
twice, and both anchors are log records:

- **SSH endorsement** (*who*): a signature over the ML-DSA public key made with
  `ssh-keygen -Y sign` by a key listed at `github.com/<user>.keys` — the same mechanism and
  key material as existing CI attestation. This binds the ML-DSA key to a GitHub identity
  using keys GitHub already publishes, bridging the gap that GitHub does not (yet) host
  post-quantum keys.
- **Domain binding** (*where*): the key's fingerprint appears in the namespace TXT record.

## 4. The transparency log

An append-only Merkle tree in the style of RFC 6962, over BLAKE3 with LIRA-epoch domain
separation (`lira/1:leaf`, `lira/1:node` — an empty-string/0x00-prefix scheme consistent with
spec §7.1, replacing RFC 6962's 0x00/0x01 prefixes).

- **Signed tree heads (STH)**: the index periodically signs (ML-DSA-65) the tuple
  (tree size, root hash, time) and publishes it at a well-known HTTPS URL. Clients fetch and
  cache the STH (daily by default), verifying a **consistency proof** from their previously
  cached head — so the log can only ever extend, never rewrite. The expensive post-quantum
  signature is thus amortized over every query made against that head.
- **Witnesses and mirrors**: anyone may mirror the log (bulk range download, §7) and gossip
  STHs — operationally, `lira serve --mirror`/`--witness` (tool.md §6); two inconsistent STHs
  are cryptographic proof of misbehavior. Split-view attacks
  (equivocating between clients) require permanently forking the log per victim, which
  witness gossip makes detectable.
- **Registration-time verification**: before appending a `Release` leaf the index performs
  the registry obligations of spec §16 — Range-fetch the manifest from the named GitHub
  asset; recompute the manifest hash; verify the ML-DSA signature against an endorsed,
  namespace-bound key; re-verify the TXT record; and check the lineage step's grade against
  the previous logged release of the module (rigid-monotonicity — the L110 enforcement point).
  The log therefore carries not just *what was published* but *evidence it was checked*.

## 5. Log records

All records are BinTEL documents under small TEL schemas (siblings of the metadata-blob
schemas in the spec); each is one Merkle leaf.

| Record           | Contents                                                                 |
| ---------------- | ------------------------------------------------------------------------ |
| `KeyEndorsement` | ML-DSA public key; SSH endorsement bytes; GitHub login; or prior-key endorsement for rotation |
| `NamespaceProof` | domain; fingerprints observed; vantage evidence; DNSSEC flag; time       |
| `Release`        | coordinate; version label; snapshot; manifest hash; manifest byte-length; payload hash; total byte-length; artifact location (owner, repo, tag, asset); publisher key fingerprint |
| `Withdrawal`     | reference to a `Release`; reason — advisory only; nothing is ever deleted |
| `Referral`       | another directory's endpoint, STH key fingerprint, and namespace scope: the log this directory relied on when verifying a registration whose dependencies it does not itself serve — verification evidence and client routing hint at once (tool.md §4) |

The `Release` record is deliberately a *bounded* projection of the manifest: it contains no
lineage list and no signatures (both unbounded/large), which is what lets it fit in a
datagram. The full manifest — lineage, dependencies, sections, signatures — lives at the
GitHub asset, one Range request away, hash-pinned by the record.

## 6. The UDP resolution protocol

Stateless request/response: one datagram in, one datagram out. No connection, no encryption;
authenticity comes from content addressing and Merkle proofs (the DNSSEC model, not the TLS
model). Framing: 4-byte protocol magic, version byte, opcode, a 16-byte client nonce echoed
verbatim in the response (off-path spoofing defence), then a BinTEL body.

**Anti-amplification, by construction**: requests MUST be padded to exactly **1232 bytes**
(the DNS/QUIC-conservative safe datagram size) and responses MUST NOT exceed 1232 bytes.
Amplification factor ≤ 1; a reflector gains nothing. Responses that cannot fit set a
truncation flag (DNS TC-bit precedent) directing the client to HTTPS.

Operations:

| Op                | Request                    | Response                                             |
| ----------------- | -------------------------- | ---------------------------------------------------- |
| `RESOLVE`         | coordinate                 | latest `Release` record + leaf index + inclusion proof |
| `RESOLVE-COMPAT`  | coordinate, snapshot hash  | latest release whose lineage contains the snapshot — the buildpath primitive (spec §13.2) |
| `LOOKUP`          | manifest hash or payload hash | `Release` record + proof                          |
| `HEAD`            | —                          | tree size + root hash (signature via HTTPS)          |
| `PROOF`           | leaf index, tree size      | inclusion or consistency proof                       |

`RESOLVE-COMPAT` evaluates lineage membership server-side — necessarily, since mature
lineages (~50 B/release) outgrow datagrams — and its honesty is auditable: the full lineage
is reconstructible from the module's `Release` leaves in the log, so a lying answer is a
provable inconsistency.

**Size budget** at the 1232-byte ceiling:

| Component                                        |     Bytes |
| ------------------------------------------------ | --------: |
| framing (magic, version, op, flags, nonce)       |       ~24 |
| `Release` record (BinTEL)                        |  ~230–300 |
| leaf index + tree size (varints)                 |       ~10 |
| inclusion proof, 32·⌈log₂ n⌉ (n = 10⁶)           |       640 |
| **total**                                        | **~910–975** |

Headroom to n ≈ 10⁸ leaves; beyond that the truncation flag applies. Every response is
verifiable: hash-keyed answers by recomputation, name-keyed answers by proof against the
cached STH. **No response carries a signature**; the only signatures in the system are on
manifests (publishers) and STHs (the index, amortized).

## 7. The HTTPS API

The boring, complete twin. Everything the UDP path serves, plus what doesn't belong on UDP:

- **Registration**: namespace claim, key endorsement, release submission. GitHub OAuth
  (device flow) MAY gate submission as spam control, but the cryptographic chain never
  depends on it — a release is valid because of its signatures and proofs, not its session.
- **Search**: by name substring / prefix — human- and build-tool-facing discovery.
- **STH endpoint** (current + historical heads, consistency proofs), record retrieval by
  hash, **bulk log ranges** for mirrors and witnesses.

## 8. Client resolution flow

A build tool resolving a buildpath:

1. **Resolve** each coordinate (UDP; HTTPS fallback); verify each inclusion proof against the
   cached STH (refreshing the STH with a consistency proof if stale).
2. **Fetch manifests**: `Range: bytes=0-(manifest length−1)` against the GitHub asset; check
   the manifest hash from the record; verify the ML-DSA signature; check the signing key's
   endorsement chain (log records, aggressively cacheable).
3. **Validate the buildpath** from manifests alone (spec §13.3): uniqueness, namespace and
   resource disjointness, closure, lineage-containment compatibility, profile coherence, and
   provider requirements. Where releases
   declare integrations (spec §9.5) this step also *searches* for a valid assignment rather than
   merely checking one; the index is unaffected, since integrations live in the manifest already
   fetched at step 2 and not in the bounded `Release` record.
4. **Download** full artifacts; verify `payload.hash`; materialize sections into the
   content-addressed cache (spec §13.5).

Steps 2–4 hit GitHub/CDN, not the index; the index's per-build cost is a handful of
sub-millisecond datagrams. Organizations can run a caching mirror with zero trust required.

## 9. Security considerations

- **Amplification**: eliminated by the request-padding rule (§6).
- **Off-path response spoofing**: 16-byte echoed nonce; a spoofed answer must also carry a
  valid proof, so the worst achievable is a *stale* (previously true) answer within the
  client's STH window.
- **Equivocation / split view**: consistency proofs + witness gossip (§4).
- **Lineage grafting**: registration-time grade checks + the prior-key rule (§2, §4).
- **Domain expiry/transfer**: namespace-vs-lineage split (§2).
- **DNS spoofing during verification**: multi-vantage queries, DNSSEC recorded when present,
  observations logged — an attacker must fool several vantage points at once and leaves a
  permanent public record if they succeed once.
- **GitHub asset mutation or removal**: all bytes hash-pinned in the log — mutation is
  detected on fetch; removal is an availability problem only, answerable by mirrors (GitHub's
  immutable-releases feature is RECOMMENDED to publishers).
- **Withdrawn releases**: advisory `Withdrawal` records; nothing is deleted, so builds pinned
  to a withdrawn release keep working (loudly).
- **Index compromise**: censorship and staleness only — never forgery (§1).

## 10. Open questions

- STH cadence (fixed interval vs per-append batching) and the witness ecosystem's bootstrap.
- Protocol name and port; whether to register with IANA.
- `RESOLVE-COMPAT` plurality: return only the latest satisfying release, or all candidates?
- Rate limiting and abuse handling on the HTTPS registration path.
- Whether the index should also serve `uses`/delta blob summaries so staleness queries
  (spec §13.4) can be answered without fetching predecessors.
- Mirror/witness protocol details (log range format, gossip envelope) — partially answered by
  the store API of tool.md §7 (`SET-ROOT` commitments plus want/have blob sync); the gossip
  envelope remains open.
