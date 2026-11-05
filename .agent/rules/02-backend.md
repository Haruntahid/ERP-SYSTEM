# Backend Standards

## Structure
Package by module, never by technical layer at the top level:
`com.schoolerp.<module>` with sub packages `api`, `application`, `domain`, `infrastructure`.
- api: controllers, request/response DTOs.
- application: use case services, transactions, orchestration.
- domain: entities, value objects, domain rules, repository interfaces.
- infrastructure: JPA, external gateways (SMS, payment).
Modules talk to each other through application services or domain events, never through another module's repositories.
shared.* contains only cross cutting code (tenancy, crypto, error, config).

## API
- Prefix `/api/v1`. Tenant APIs under `/api/v1/<module>`, platform APIs under `/api/v1/platform`.
- Never expose JPA entities. Use record DTOs and validate input (jakarta.validation).
- All list endpoints are paginated (page, size, sort) with an upper bound on size.
- Errors use RFC 7807 ProblemDetail. Throw ApiException with the right HttpStatus.
- Document with OpenAPI annotations.

## Data
- Flyway only. ddl-auto stays `none`.
- Soft delete (`deleted_at`) for academic and financial records.
- Money is DECIMAL(12,2) with an explicit currency, never float or double.
- Index every foreign key and every column used in filters.
- Audit sensitive changes (marks, fees, results, roles) into audit_log.

## Patterns
- Strategy for configurable behavior (grading calculation, fee rules, payment gateways).
- State machine for lifecycles (admission, invoice, exam result: draft, moderated, published, locked).
- Domain events plus an outbox table for reliable side effects (notifications, receipts).
- Idempotency key on payment and bulk operations.
- Constructor injection only. No field injection.

## Testing
- Unit tests for domain logic. Integration tests with Testcontainers (MySQL) for repositories and APIs.
- Each module has a tenant isolation test.
- Test names describe behavior.
