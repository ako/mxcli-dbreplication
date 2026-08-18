# ReplicationLab

A Mendix app used as an **evaluation harness**, not as a product.

## Why this repo exists

> The goal is to evaluate the Mendix marketplace database replication module.
> I want to know what it does, what its strengths are, what its weaknesses are,
> and if we could incorporate the functionality out of the box in Mendix using
> the external database connector.

Everything in this app exists to answer those four questions with evidence
rather than opinion: run real replication scenarios against a real external
database, record what each approach could and could not do, and end up with a
written comparison of the **Database Replication** marketplace module against
the **External Database Connector** that ships with the platform.

## What it keeps track of

*(Not specified in the brief — chosen to serve the evaluation goal. Change
freely; nothing downstream depends on these names yet.)*

- **SourceDatabase** — an external database under test, and how to reach it.
  Has many **SourceTable**s (the tables we point a replication at).
- **EvaluationScenario** — one thing being tested (full load, incremental load,
  association mapping, schema drift, …), tagged with the approach it exercises:
  Database Replication module, External Database Connector, or both.
- **ScenarioRun** — one execution of a scenario: when, how long, how many rows,
  what happened. This is where "is it fast enough" gets an answer.
- **Finding** — the actual deliverable. A strength, weakness, limitation or
  workaround, tied to a scenario and an approach, so the final write-up is
  assembled from recorded evidence instead of memory.
- **Replicated demo entities** — real targets to replicate *into*, so the
  scenarios move actual rows.

## Who logs in

*(Not specified in the brief — chosen.)*

- **Administrator** — full access; manages source databases and connection settings.
- **Evaluator** — creates and runs scenarios, records findings; cannot change
  connection settings.
- **Viewer** — read-only access to scenarios, runs and findings.

## How it was built

| | |
|---|---|
| Mendix version | **11.13.0** |
| Theme | **ledger** (dense, data-heavy — suits tables of runs and findings) |
| mxcli | built from source: **`ako/mxcli` main**, commit `6833c37` |
| Database | local PostgreSQL, database `replicationlab`, provisioned by `mxcli run --local --ensure-db` |

`mxcli` is **not** the published `mendixlabs` nightly here. This repo pins
`ako/mxcli` main and builds it from source; `.claude/bootstrap-mxcli.sh` does
that automatically when the binary is missing (see FINDINGS.md).

## Working on it

```bash
./mxcli run --local --setup --ensure-db -p ReplicationLab.mpr  # prerequisites + database
./mxcli run --local -p ReplicationLab.mpr                      # boot at http://localhost:8080/
```

The `SessionStart` hook in `.claude/settings.json` runs the first of those
automatically, so a fresh clone bootstraps itself. The `mxcli` binary is
git-ignored (~88 MB) — the bootstrap script is what fetches it back.

See **FINDINGS.md** for the running log of what worked, what broke, and the
evaluation results as they accumulate.
