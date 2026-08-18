# FINDINGS

Durable notes for the next session. Anything surprising, broken, or worked
around goes here, with how it was verified.

## Environment under test

| | |
|---|---|
| mxcli | `ako/mxcli` **main**, commit `6833c3788edf8a5d477b22c2e4a928e2de0edd33` (`mxcli --version` → `6833c37`) |
| Built | from source with `make build` (Go 1.24.7, OpenJDK 21.0.10, ANTLR 4.13.2) |
| Mendix | 11.13.0 (MxBuild cached at `/root/.mxcli/mxbuild/11.13.0`) |
| Theme | ledger |
| Platform | Linux x86_64, Claude Code on the web (ephemeral container) |

---

## Provisioning

### F1 — mxcli must be built from source here, not downloaded (by design)

This repo deliberately does not use the `mendixlabs/mxcli` nightly binary; it
uses `ako/mxcli` main. `go install …@latest` does **not** work: the generated
ANTLR parser is not committed, so the build needs `make grammar` first.

**Working recipe** (verified — produced an 88 MB binary that runs):

```bash
git clone --depth 1 https://github.com/ako/mxcli /workspace/ako/mxcli
curl -fsSL -o /opt/antlr/antlr-4.13.2-complete.jar \
  https://www.antlr.org/download/antlr-4.13.2-complete.jar
printf '#!/bin/sh\nexec java -jar /opt/antlr/antlr-4.13.2-complete.jar "$@"\n' \
  > /usr/local/bin/antlr4 && chmod +x /usr/local/bin/antlr4
ANTLR4_TOOLS_ANTLR_VERSION=4.13.2 make -C /workspace/ako/mxcli build
```

**Note on the ANTLR install.** `mdl/grammar/Makefile` has a `bootstrap` target
that does `pip install antlr4-tools`, which then downloads the jar on first use.
Dropping the pinned `antlr-4.13.2-complete.jar` in place and shimming an
`antlr4` launcher onto `PATH` skips the Python dependency entirely and satisfies
the Makefile's `which antlr4` probe. The version is load-bearing: the Makefile
pins generator **4.13.2** against runtime **4.13.1** in `go.mod`, and a mismatch
produces a parser that does not compile.

**Verified:** `make build` completed with no errors; `./mxcli --version` printed
`mxcli version 6833c37 (2026-08-18T10:27:45Z)`.

### F2 — the generated bootstrap hook had to be rewritten for this fork

`mxcli init` writes `.claude/bootstrap-mxcli.sh` hard-coded to download
`https://github.com/mendixlabs/mxcli/releases/download/nightly/mxcli-<os>-<arch>`.
For a repo pinned to a fork that is wrong: after an idle reap the next session
would silently come back on a *different* mxcli than the one this app was built
with.

**Workaround applied:** rewrote `.claude/bootstrap-mxcli.sh` to clone and
`make build` `ako/mxcli` (overridable via `MXCLI_REPO` / `MXCLI_REF`), with
`MXCLI_TAG` kept as an escape hatch that falls back to downloading a
`mendixlabs` release when the source build breaks. The script installs the
pinned ANTLR jar itself if `antlr4` is not on `PATH`.

*Possible mxcli improvement:* `mxcli init` could take a `--bootstrap-source`
flag (or detect that the running binary was built from a non-`mendixlabs`
remote) instead of always writing the nightly URL.

**Verified:** `sh -n .claude/bootstrap-mxcli.sh` parses. Full cold-start path
(no `./mxcli` present) not yet exercised — see OPEN-1.

### F3 — `mxcli new` refuses a non-empty directory; the documented move works

A git repo always has `.git`, so `mxcli new` cannot write into the repo root.
Created in `./ReplicationLab/` and moved up. The `mxcli` inside the new project
is a **hardlink to the same inode** as the `./mxcli` that created it, so `mv`
refuses it as "the same file" — `rm -f ReplicationLab/mxcli` before the move, as
documented. After the move, `.claude/bootstrap-mxcli.sh` already named
`ReplicationLab.mpr` correctly; no fix-up needed.

### F4 — `--ensure-db` needs no Docker

The container has a `docker` client but **no running daemon**
(`/var/run/docker.sock` absent). `mxcli run --local --setup --ensure-db`
succeeded anyway in 3.4s: it starts a local PostgreSQL directly, created role
`mendix` and database `replicationlab` at `127.0.0.1:5432`. Worth knowing —
"Docker is not running" is not a blocker for the warm loop.

**Verified:** command output `Database ready: replicationlab (user "mendix") at
127.0.0.1:5432`.

---

## Evaluation findings (Database Replication vs External Database Connector)

*Nothing recorded yet — the model has not been built. This section is the point
of the repo; fill it as scenarios run.*

---

## Open questions / not yet verified

- **OPEN-1** — the rewritten bootstrap script's cold path (clone + ANTLR +
  `make build` from a container with no `./mxcli` and no warm Go module cache)
  has not been run end to end. It takes minutes rather than seconds; if a
  SessionStart hook timeout bites, that is where it will show.
- **OPEN-2** — `mxcli marketplace` requires a Personal Access Token
  (`mxcli auth login`). Without one, the Database Replication `.mpk` cannot be
  fetched by CLI and has to be downloaded manually. Not yet attempted.
