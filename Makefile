# Build the tool as an assembly JAR.
assembly:
	mill lira.assembly

# Package the assembly and the Ethereal launcher into a single self-contained executable:
# the first invocation starts a background JVM daemon, and all later invocations attach to
# it over a socket, for millisecond startup and live tab-completions.
lira: assembly
	cp out/lira/assembly.dest/out.jar lira.jar
	java -Dbuild.executable=lira -jar lira.jar

# Install to ~/.local/bin so that executing a `.lira` file (whose interpreter directive is
# `#!/usr/bin/env lira`) resolves this tool; then `lira install` adds shell tab-completions.
install: lira
	mkdir -p ${HOME}/.local/bin
	-${HOME}/.local/bin/lira quit 2>/dev/null || true
	sleep 2
	cp lira ${HOME}/.local/bin/lira

dev:
	mill -w lira.compile

.PHONY: assembly install dev
