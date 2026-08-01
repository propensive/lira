# Build the lira tool as an assembly JAR.
assembly:
	mill lira.assembly

# Install a launcher to ~/.local/bin so that executing a `.lira` file (whose interpreter
# directive is `#!/usr/bin/env lira`) resolves this tool.
install: assembly
	mkdir -p ${HOME}/.local/bin
	printf '#!/bin/sh\nexec java -jar %s "$$@"\n' "$(PWD)/out/lira/assembly.dest/out.jar" \
	  > ${HOME}/.local/bin/lira
	chmod +x ${HOME}/.local/bin/lira

run: assembly
	java -jar out/lira/assembly.dest/out.jar

dev:
	mill -w lira.compile

.PHONY: assembly install run dev
