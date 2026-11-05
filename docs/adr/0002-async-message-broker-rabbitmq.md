# ADR 0002: RabbitMQ with Transactional Outbox for Asynchronous Messaging

Status: accepted
Date: 2026-11-04

## Context
The School ERP requires reliable asynchronous processing for:
- Outbound notifications (SMS, email, push) in `F-04`,
- Asynchronous PDF generation (fee receipts, marksheets, ID cards, certificates) in `F-07`,
- Bulk CSV/Excel import and export jobs in `F-10`,
- Fee due reminders, attendance absence alerts, and payment webhook reconciliation in `M-04` and `M-05`.

Because this is a database-per-school (`school<slug>`) SaaS system:
1. Every async message must carry the `tenantSlug` header/payload and bind `TenantContext` before touching any tenant resource (`.agent/rules/01-tenancy.md` Rule 5).
2. Domain state changes and event publication must be atomic per school database (solved via a `transactional_outbox` table inside each tenant schema).
3. Workloads are task/command-oriented with variable processing latency (external SMS gateways, PDF rendering), requiring flexible routing, per-queue concurrency control, delayed retry queues (exponential backoff via dead-letter exchanges), and dead-letter queues (DLQ).

## Decision
We choose **RabbitMQ 3.13** (`spring-boot-starter-amqp`) paired with a **per-tenant Transactional Outbox** table (`outbox_events`) and an **idempotent consumer inbox** table (`processed_events`) in each school database.

## Comparison: RabbitMQ vs Apache Kafka

| Criterion | RabbitMQ 3.13 (Chosen) | Apache Kafka (KRaft) |
|---|---|---|
| **Primary Pattern** | Smart broker / task queues, topic & direct routing, per-message TTL, native retry + DLQ | Distributed append-only log, high-throughput event streaming and log replay |
| **Operational Footprint** | Lightweight (~150 MB RAM in Docker), single node in dev/small prod, simple clustering | Heavy JVM memory footprint (~1–2 GB RAM), partition rebalancing complexity |
| **Retry & Backoff** | Native Dead-Letter Exchanges (DLX) with per-queue delay tiers (`5s`, `30s`, `5m`, `DLQ`) | Requires custom retry topics and consumer pause/resume orchestration |
| **Multi-Tenant Fairness** | Supports routing keys (`tenant.<slug>.<event>`) and priority queues so one school's bulk notice broadcast does not starve another school's OTP SMS | Partition count limits per-tenant isolation; one noisy tenant on a shared partition blocks others |
| **Throughput Fit** | 10k–50k msgs/sec on modest hardware — more than sufficient for hundreds of schools | 100k–1M+ msgs/sec — overkill for ERP transactional workflows |

## Consequences
Positive:
- Low memory and operational overhead for local development (`docker-compose.yml`) and staging/production.
- Built-in management UI (`http://localhost:15672`) for inspecting queues and replaying dead-lettered messages.
- Clean separation between high-priority interactive queues (`erp.notifications.high` for OTP/password reset) and bulk background queues (`erp.notifications.bulk`, `erp.documents.generate`, `erp.imports.process`).

Negative:
- Does not provide long-term event log replay out of the box. Mitigated by keeping `outbox_events` rows in each school database for a configurable retention window (`outbox.retention_days`, default 30 days) so events can be re-published if needed.
