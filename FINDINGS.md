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

### F5 — `mxcli check` caught four classes of modelling error before any build

The proposed model was written as MDL and validated with `mxcli check` (syntax +
semantic lint, no execution). It rejected things that would otherwise have
surfaced as a broken `.mpr` or a Studio Pro consistency error:

| Code | What it caught |
|---|---|
| `MDL022` | `CreatedAt: AutoCreatedDate` is silently renamed to the fixed system member `CreatedDate` on write, and system members cannot be bound in a widget. Fixed by using the system names where only an audit trail is wanted, and a plain `DateTime` where the value must appear in a grid (`Finding.RecordedOn`). |
| `MDL-BUTTON01` (CE1571) | A control-bar button passing `$currentObject` — a control bar is not row-scoped, so `$currentObject` is unbound. Fixed by moving the Edit/Open buttons into a grid *column*, which is row-scoped. |
| `MDL-PAGE01` | A page referencing a page created later in the same script. The executor resolves page references in statement order, so it would have failed part-way through `exec`. Fixed by ordering every linked-to page before its caller. |
| `MPR010` | A `DataView` with input fields not wrapped in a layout grid — label/input widths only render correctly inside one. |

Also worth knowing: `DELETE_BEHAVIOR SETNULL` does not exist. The accepted set is
`DELETE_AND_REFERENCES`, `DELETE_BUT_KEEP_REFERENCES`, `DELETE_IF_NO_REFERENCES`,
`CASCADE`, `PREVENT`.

**Not caught by `check`:** icon names. `ICON Atlas_Core.Atlas.database` and
`...Atlas.table` both pass `check` but do not exist in the collection —
`DESCRIBE ICON COLLECTION Atlas_Core.Atlas` shows the real set (`server` and
`data-transfer` were used instead). Verify icons that way rather than guessing.

*Possible mxcli improvement:* resolve `ICON` references against the icon
collection at `check` time, the way page and entity references already are.

### F6 — the 44 remaining `MDL-WIDGET16` infos are expected, not noise

Every named DataGrid 2 column reports "stores no column name, so `colX` is
dropped on write" — attribute columns key on the bound attribute, others on the
caption. So `ALTER PAGE` later must address `ON <grid>.Name`, not
`ON <grid>.colSdName`. Harmless, but it means the MDL names are documentation
only.

### F7 — platform credentials and hub key are present in this environment

`MENDIX_PAT` is set and `mxcli auth status` validates it (`Scheme: pat`,
`Source: env`, `Validated: ok`), so `mxcli marketplace` works without an
interactive login — this supersedes OPEN-2. `MXCLI_HUB_KEY` is also set, so
`mxcli run --hub https://hub.mxcli.org` is available for a browser preview from
this cloud session.

**Gotcha:** `marketplace search` matches on the *packaged* name, not the display
name. `"Database Replication"` (with the space) returns **No results**; the
module is packaged as `DatabaseReplication` and is found by searching
`replication`. Results come from a cached catalog — pass `--refresh` when the
answer matters.

---

## Evaluation findings (Database Replication vs External Database Connector)

Baseline facts gathered before any scenario has been run. The app's `Finding`
entity is where scenario-derived evidence will go; this section holds what is
already established from the marketplace metadata and the product docs.

### E1 — the module is current and platform-supported, not abandoned

From `mxcli marketplace info 160` / `versions 160` (Mendix PAT, live catalog):

| | |
|---|---|
| Content ID | 160, name `DatabaseReplication` |
| Publisher | **Mendix**, support level **Platform** (not community) |
| Latest | **9.3.1**, published **2026-02-18** |
| Minimum Mendix | **10.24.11** |
| License | Apache 2.0 |
| Categories | Communication, Data, Import/Export |

Release cadence is steady (9.0.0 Feb 2025 → 9.1.0 Apr → 9.2.0 Aug → 9.2.1 Nov →
9.3.0 Dec 2025 → 9.3.1 Feb 2026). So "is it dead?" is answered: no.

**Caveat, unverified:** the marketplace records a *minimum* Mendix version only.
9.3.1 declares 10.24.11 and predates this app's 11.13.0 by six months; nothing in
the metadata confirms or denies Mendix 11 compatibility. The marketplace web page
needs a login (`marketplace.mendix.com/link/component/160` redirects to OAuth),
so this must be settled by actually importing the `.mpk` into the 11.13.0 project
— that is scenario 1.

### E2 — the two approaches are not the same shape of thing

This is the crux of "could we do it out of the box". From the product docs:

| | Database Replication (module) | External Database Connector (platform) |
|---|---|---|
| Ships with | Marketplace `.mpk` + **Mx Model Reflection** dependency | **Built into Studio Pro 11.13+** — nothing to download |
| Data location | **Copies rows into Mendix persistable entities** | **Queries live**, maps to entities per query; no copy kept |
| Databases | SQL Server 2005+, Oracle, PostgreSQL, AS400, DB2, DMS2, Informix, + any JDBC | **Five only**: Microsoft SQL, MySQL, Oracle, PostgreSQL, Snowflake |
| Direction | Import (read into Mendix) | **Read *and* write** — SELECT, INSERT, UPDATE, DELETE, stored procedures |
| Mapping | Table→entity, column→attribute, **reference mapping**, multi-table joins, custom SQL per attribute | One entity generated from one query's result shape |
| Sync semantics | Four import modes: create-and-synchronize, synchronize-existing-only, create-per-row, create-new-only | None — you write the microflow |
| Incremental | Yes, by constraining on the last successful import's timestamp | Not provided; do it yourself in the query/microflow |
| Scheduling | Scheduled import actions, chained in sequence | Plain Mendix scheduled events calling your microflow |
| Extras | Object events during import, microflow-based value formatting, DB time-zone conversion, import statistics, non-persistable targets | Design-time connection/query testing in Studio Pro |
| Known limits | **No CLOB support**; no mapping inheritance for generalization subtypes; custom queries are database-dialect-specific | **Primitive column types only**; parameters only usable as filter values (prepared statements); no certificate auth for PostgreSQL on macOS |

### E3 — provisional answer to "can we do it out of the box?"

**Partly, and the gap is the replication engine, not the connectivity.**

- Connectivity, query authoring and entity generation: the External Database
  Connector covers these, and covers them *better* in one respect — it writes
  back, which the replication module does not.
- What it does **not** give you is everything that makes replication
  *replication*: matching an incoming row to an existing object, the four
  create/synchronize modes, incremental loads keyed on the last successful run,
  reference resolution between mapped tables, chained scheduled imports, and
  import statistics. All of that would be hand-built in microflows, per table.
- Two hard constraints point opposite ways: the connector supports only **five**
  database engines (so AS400/DB2/Informix rules it out immediately), while the
  module has **no CLOB support** (so large-text columns rule *it* out).

**This is a docs-derived hypothesis, not a measured result.** It is exactly what
the scenarios in this app exist to confirm or break.

### Scenarios to run (in order)

1. Import `DatabaseReplication` 9.3.1 + `Mx Model Reflection` into the 11.13.0
   project — does a module with `Min Mendix 10.24.11` even load on 11.13? (E1)
2. Full load of the demo customers table into `DemoCustomer` via the module.
3. The same full load via the External Database Connector + a hand-written
   microflow — and count how much microflow it took. (E3)
4. Incremental re-load after changing rows at source: module's timestamp
   constraint vs hand-rolled.
5. Reference mapping: orders → customers, both ways.
6. A CLOB/large-text column, to confirm the module's stated limitation. (E2)
7. A write-back (INSERT/UPDATE at source) — connector only; establishes the
   capability the module lacks.

---

## Open questions / not yet verified

- **OPEN-1** — the rewritten bootstrap script's cold path (clone + ANTLR +
  `make build` from a container with no `./mxcli` and no warm Go module cache)
  has not been run end to end. It takes minutes rather than seconds; if a
  SessionStart hook timeout bites, that is where it will show.
- **OPEN-2** — *resolved by F7*: `MENDIX_PAT` is set in this environment, so
  `mxcli marketplace` works. Note the env var is environment-provided; a fresh
  environment without it falls back to `mxcli auth login`.
- **OPEN-3** — whether `DatabaseReplication` 9.3.1 (min Mendix 10.24.11) works on
  Mendix 11.13.0. Marketplace metadata records no maximum, and the module page
  needs a login. Settle it by importing the `.mpk` — scenario 1 above.
- **OPEN-4** — the domain model, security and pages are written as MDL under
  `mdl/` and pass `mxcli check`, but have **not been executed**. Nothing in the
  `.mpr` reflects them yet.
