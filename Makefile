# Publish the library to the local ~/.ivy2 (the launcher resolves `lira-core` from there; burdock
# will NOT externalize a locally-published copy unless its bytes match a release asset).
publishLocal:
	./mill __.publishLocal

# Build the invocation-point `launcher` module as a plain (clean, no shell-preamble) assembly JAR.
# `launcher` depends on lira-core as a PUBLISHED coordinate resolved from ~/.ivy2/local, so the
# library is published there FIRST — otherwise the launcher silently builds against whatever was
# last published (a release's jar, say, whose bytes then externalize to that release's download,
# and local changes never reach the executable). `clean lira.launcher` for the same reason: the
# coordinate is fixed, so Mill's cached resolution would not notice the fresh publish.
# One `./mill` invocation per launcher, deliberately: asked for two launcher assemblies at once,
# Mill 1.1.5 produces only the first jar (observed with `fury.launcher.assembly
# fever.launcher.assembly`), and xeq will happily wrap a jar that does not exist.
assembly: publishLocal
	./mill clean lira.launcher fury.launcher fever.launcher
	./mill lira.launcher.assembly
	./mill fury.launcher.assembly
	./mill fever.launcher.assembly

# Publish lira to GitHub Releases: the lira-core jar first, then — once its digest is indexed — the
# repackaged `lira` executables, added to the same release. See release-launcher.sh in
# propensive/.github (run through etc/shared) for the two-step ordering and its verification.
release:
	./etc/shared release-launcher.sh lira "lira-core" $(VERSION)

# Repackage the launcher assembly into a self-fetching launcher with Burdock. The
# `burdock.externalize` macro wrapping `LiraTool.run()` (in src/launcher/lira_launcher.scala) has
# already embedded `META-INF/burdock.deps` at compile time; running the repackager rewrites the JAR
# in place so published dependencies become on-demand `Burdock-Require` URLs and unpublished ones
# are inlined from `~/.cache/burdock`.
#
# Three publication homes are consulted: Maven Central (hashes resolved via deps.dev) for the
# third-party dependencies, and — via the `--github` hints — the release assets of the lira,
# Soundness and proscala repositories, whose per-jar SHA-256 digests the repackager matches against
# the classpath. The Soundness jars synced into ~/.ivy2/local are the release assets byte-for-byte,
# and the proscala release publishes the same jars its tarball carries, so both the components and
# the fork toolchain externalize; lira-core externalizes only once released (`make release`), and
# is inlined otherwise. Pyrocosm is deliberately NOT among the hints: lira does not depend on it.
# Set GITHUB_TOKEN to lift the API rate limit.
lira.jar: assembly
	cp out/lira/launcher/assembly.dest/out.jar lira.jar
	java -cp lira.jar soundness.repackage --github propensive/lira,propensive/soundness,propensive/proscala

# Package the repackaged JAR as a native executable for this machine with the pinned `xeq` builder
# script (fetched into dist/xeq and verified against etc/xeq.tsv). This replaces the older
# `java -Dbuild.executable=lira -jar lira.jar` step, which produced an Ethereal-wrapped executable
# without burdock externalization; the released executables are built exactly this way.
lira: lira.jar xeq-fetch
	dist/xeq build --jar lira.jar --out lira

# The same path for fury and fever (design/fury.md §14): repackage each launcher's assembly, then
# build a native executable from it. Pyrocosm IS among the hints here, since both depend on it.
fury.jar: assembly
	cp out/fury/launcher/assembly.dest/out.jar fury.jar
	java -cp fury.jar soundness.repackage --github propensive/lira,propensive/soundness,propensive/proscala,propensive/pyrocosm

fever.jar: assembly
	cp out/fever/launcher/assembly.dest/out.jar fever.jar
	java -cp fever.jar soundness.repackage --github propensive/lira,propensive/soundness,propensive/proscala,propensive/pyrocosm

fury: fury.jar xeq-fetch
	dist/xeq build --jar fury.jar --out fury

fever: fever.jar xeq-fetch
	dist/xeq build --jar fever.jar --out fever

install-fury: fury
	-./fury quit 2>/dev/null || true
	rm -f ${HOME}/.local/bin/fury
	cp fury ${HOME}/.local/bin/

install-fever: fever
	-./fever quit 2>/dev/null || true
	rm -f ${HOME}/.local/bin/fever
	cp fever ${HOME}/.local/bin/

# Fetch the pinned `xeq` builder script into dist/xeq.
xeq-fetch:
	./etc/shared xeq-fetch.sh

# Install the tool onto the PATH, which is what the `#!/usr/bin/env lira` interpreter directive of
# a `.lira` file resolves. Remove-then-copy, NOT a bare `cp`: overwriting the existing file reuses
# its inode, and macOS caches code-signing state per vnode — after an in-place rewrite every exec
# of the launcher is killed with SIGKILL until the file is replaced. Deleting first makes the copy
# a fresh inode.
install: lira
	rm -f ${HOME}/.local/bin/lira
	cp lira ${HOME}/.local/bin/

# Print a manifest through the native launcher (a quick smoke test). Ethereal applications must be
# started through their launcher, not `java -jar`.
run: lira
	./lira $(ARGS)

# Compile and run the test suite with fume, which discovers the suite from the assembly named in
# .pyrocosm/fume/config.tel (relative to this directory). Extra selection terms go in TESTS, e.g.
# `make test TESTS='tag:delta'`. `make test-plain` is a fume-less fallback, and is what CI runs:
# `lira.runTests` (src/test/lira_test_main.scala) drives `Tests.invoke` in-process, since a probably
# `Suite` has had no `main` of its own since Soundness 0.65.0.
test:
	./mill lira.test.assembly
	fume run -c out/lira/test/assembly.dest/out.jar $(TESTS)

test-plain:
	./mill lira.test.assembly
	java -cp out/lira/test/assembly.dest/out.jar lira.runTests

# Install every library pinned in etc/refs — releases and snapshots alike, transitively — into the
# local ivy repository, as CI does, so the build resolves exactly the pinned jars rather than
# whatever a sibling checkout's `publishLocal` last installed under the same version. A snapshot not
# yet on GitHub is built from the sibling checkout named by the pin's commit.
sync-deps:
	./etc/shared sync-deps.sh

# Check every source against Consequent Style and the project's own rules with flair (the release
# pinned in etc/tools; `make tools` installs it), as configured in .pyrocosm/flair/config.tel.
# Findings are warnings, so CI does not run this; PATHS restricts the check to files beneath them.
check:
	flair check $(PATHS)

# Install the commands pinned in etc/tools (fume, flair) through their releases' installers.
tools:
	./etc/shared tools.sh

# Publish HEAD's library as a snapshot — a `snapshot-<hex>` pre-release named by the filtered tree
# of the commit, at version `<liraVersion>-<hex>` — for a dependent repository to pin in its
# etc/refs before the next release. `LOCAL=1` stages and installs without publishing. The last line
# printed is the pin. See snapshot.sh in propensive/.github.
snapshot:
	./etc/shared snapshot.sh lira "$$(sed -n 's/.*val liraVersion = "\(.*\)".*/\1/p' build.mill)"

# Delete snapshot pre-releases older than DAYS (default 60) days.
snapshot-prune:
	./etc/shared snapshot-prune.sh lira $(DAYS)

dev:
	./mill -w __.compile

.PHONY: publishLocal assembly release xeq-fetch install run test test-plain sync-deps check tools snapshot snapshot-prune dev fury fever install-fury install-fever
