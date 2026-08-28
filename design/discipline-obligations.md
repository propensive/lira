# Obligations on Disciplines

A checklist for authors of a new discipline (`foo/1`), collecting in one place what a
discipline must define, obey, and document. Part A summarizes the base specification's
normative requirements (lira.md §11.2) — the authority is the spec; these are pointers.
Part B collects the obligations surfaced by the build-tool design
([`builds.md`](builds.md)), each a candidate for promotion into §11.2 or the discipline
specification template. Part C is convention shared by the existing discipline specs,
worth elevating.

## A. The base requirements (lira.md §11.2, normative)

1. **Domain** — the fixed set of realms atomized; multi-universe domains are bound by
   the cross-section invariant (§9.6).
2. **Claiming** — which tree items are atomized, which claimed atomless; first-match
   over `api` records makes claiming order semantic (L134, L144).
3. **Atomization** — a pure function of the semantic model: no file ordering,
   timestamps, tool version strings, or fresh names (§17).
4. **Keying** — declaration or membership, stated, and sound for the guarantee
   certified.
5. **Replaceability soundness** — everything a replaceable atom's content needs at the
   consumer's compile time is rigid-atomized.
6. **References** — emitted symbolically by atom key; the input to used-set closure
   (§13.4).
7. **Certified guarantees** — only levels actually enforced; ecosystem-linkage
   obligations belong to profiles, not here (§11.5–§11.6).

Plus the identification rules of §11.1: named for the carrier; any canonicalization
change is a new discipline; migration between versions is the dual-declaration bridge
(§11.1's non-normative note).

## B. Obligations from the build-tool design (proposed)

1. **The used-set closure rule** (builds.md §16.1). A discipline's *used-set
   computation* — distinct from requirement 6's reference lists, which concern
   replaceable atoms — MUST close over the atoms of nominal types referenced by used
   members, not only the members themselves. Without it, a supertype change in a
   dependency escapes the consumer's spanning judgment, and hierarchy-dependent
   compatibility silently breaks. *Candidate for §11.2 as a requirement 6 sibling.*

2. **Encoding invariance across producing tools** (builds.md §7.3). Where one carrier
   can be produced by several tool versions (TASTy across compiler minors), atomization
   MUST hash the semantic signature, normalizing encoding differences — otherwise
   byte-level drift between compilers makes cross-built cells of one release disagree
   on API identity and fail L108 spuriously. *A sharpening of §11.2 requirement 3: not
   just independence of a compilation run's artifacts, but of the producing tool's
   encoding generation.*

3. **Temporal guarantee class** (builds.md §12.1–§12.2). A presumption discipline MUST
   state the temporal class of what it certifies — *lifetime* (holds for the process:
   envvars, datasets, instruction sets) or *startup* (verified at start, may lapse:
   files) — because the class travels in-band (`lira-guarantees`) and bounds the
   totality a compiler may claim. Macros never hardcode discipline semantics.

4. **The configuration-versus-liveness law** (builds.md §12.1). A presumption
   discipline may certify only what the environment *configures* — never what the
   world *does*. Erasable partiality is misconfiguration (missing file, unset
   variable, unknown zone, denied permission); failures after the probe (peer down,
   disk full, allocation) are liveness and MUST NOT be certified. This is the
   admission criterion for new presumption kinds.

5. **Presence and format, never values or data plane** (builds.md §12, §15;
   environments.md §10). A presumption discipline atomizes a guarantee's identity and
   predicate — never a secret's value, and never mutable content (a file's evolving
   contents, a broker's topics, a database's schema). The line: "a file exists at this
   path in this format" is platform fact; its contents are the data plane.

6. **Probes are never atomized** (hosts.md §5, the precedent; builds.md §12, §15).
   How a guarantee is checked is implementation, free to improve at patch grade; what
   is guaranteed is interface. A discipline that folded probe text into atoms would
   grade probe wording changes as API changes.

7. **Native-relation coincidence** (spec tels.md §9, the precedent). Where a carrier
   has its own compatibility relation (TEL's signature subsequence), the discipline
   SHOULD construct atomization so LIRA's grade computation *coincides* with it, and
   prove the coincidence — rather than approximating it and living with divergence.
   `tels/1`'s component-plus-ordered-pair encoding is the worked example.

8. **No inter-discipline delegation** (builds.md §16). A discipline never consults
   another discipline: atomization and satisfaction reference only its own carrier and
   atoms. Structured relationships to other-governed content are expressed as edges to
   the modules carrying it ("a predicate that needs an algebra is a module reference
   in disguise"). This is what preserves decidability-from-manifests.

## C. Shared convention worth elevating

- **Out-of-vocabulary constructs are hard atomization errors** (dts.md §5, tasty.md
  §7). A parser that silently skips what it does not recognize reports a *smaller*
  contract than the carrier declares, making every claim computed from it unsound.
- **Version in lockstep with the document** (dts.md §1 et al.): the discipline spec
  states that any change to its canonicalization — however small — is a new
  discipline, never a revision.
- **State what is deliberately not certified** (dts.md §2's "does not certify
  linkage, and MUST NOT be read as doing so"): the negative claim is part of the
  contract.

## Using this checklist

A new `foo/1` should answer A.1–A.7 normatively, B.1–B.2 if it is a signature
discipline, B.3–B.6 if it is a presumption discipline, B.7 if its carrier has a native
compatibility relation, and B.8 and C always. The answers belong in the discipline's
own specification document, in the pattern of [`dts.md`](../spec/dts.md).
