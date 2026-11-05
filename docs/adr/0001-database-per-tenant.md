# ADR 0001: Database per school

Status: accepted

## Context
The product is sold to many schools and universities. Some will demand strong data isolation, independent backups and the ability to leave with their data.

## Decision
Each school has its own MySQL database and its own database user. A master database holds the catalog. One application deployment serves all schools and routes each request to the right database.

## Consequences
Positive:
- Physical isolation, a missing WHERE clause cannot leak data across schools.
- Per school backup, restore, export and deletion.
- A heavy or large school can be moved to its own server by changing its JDBC URL.

Negative:
- Schema migrations must run on every database (handled at startup, needs a rolling strategy at scale).
- Many connection pools. Mitigated with lazy small pools, later LRU eviction and a proxy.
- Cross school analytics needs a separate reporting pipeline.

## Alternatives considered
Shared database with a tenant_id column (cheaper, weaker isolation) and schema per tenant (middle ground). Rejected in favor of isolation and portability.
