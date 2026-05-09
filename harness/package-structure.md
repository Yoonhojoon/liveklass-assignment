# Package Structure Harness

Spring Boot code must stay domain-centered and easy to navigate.

## Rule

Use domain package first, then component packages inside the domain.

```text
com.liveklass.demo
  common
    response        # reusable response DTOs, error response contracts
  notification
    config          # notification-specific Spring configuration
    controller      # REST controllers and controller advice
    dto             # request/response DTOs only
    domain          # entities, enums, domain state
    repository      # Spring Data repositories and DB queries
    service         # use cases, transactions, retry/state services
    sender          # sender port and adapters
    worker          # polling/scheduled worker orchestration
```

## Boundaries

- Common response structures go under `common.response`, not inside a domain controller package.
- Do not expose JPA entities as API responses; use `notification.dto`.
- Keep transaction/business logic in `notification.service`.
- Keep DB query ownership in `notification.repository`.
- Keep external/mock sender boundary in `notification.sender`.
- Keep scheduler/polling orchestration in `notification.worker`.
- Avoid reverting to broad layer packages such as `notification.application` or `notification.infrastructure` when a clearer component package exists.
