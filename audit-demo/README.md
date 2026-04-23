# Audit Demo — Spring Boot 3.5 / Java 21

Spring Boot app demonstrating:

- A custom `@Audit` annotation backed by an `AuditAction` interface
  (`action()`, `successFormat()`, `failureFormat()`).
- An AOP aspect that wraps controller calls and hands records to an
  `AuditEventPublisher`.
- The publisher writes to SLF4J **and** persists to an H2 database
  asynchronously (`@Async`).
- A read-only `/audit` controller to browse persisted events.
- Lombok throughout to cut boilerplate.

## Requirements

- Java 21
- Maven 3.9+
- (IDE) Lombok plugin + annotation processing enabled

## Run

```bash
mvn spring-boot:run
```

The app listens on `http://localhost:8080`.

## Try it

### Trigger audited calls

```bash
# Success
curl -X POST "http://localhost:8080/users?name=Alice"
curl "http://localhost:8080/users/1"

# Failures
curl -X POST "http://localhost:8080/users?name="
curl "http://localhost:8080/users/999"
```

### View persisted audit events

```bash
# Most recent first, paginated
curl "http://localhost:8080/audit?page=0&size=20"

# Filter by action
curl "http://localhost:8080/audit?action=USER_CREATE"

# Filter by status
curl "http://localhost:8080/audit?status=FAILURE"

# Single record
curl "http://localhost:8080/audit/1"
```

### H2 web console

Open <http://localhost:8080/h2-console> and connect with:

- **JDBC URL:** `jdbc:h2:file:./data/auditdb`
- **User:** `sa`
- **Password:** _(blank)_

Query `SELECT * FROM AUDIT_EVENT ORDER BY CREATED_AT DESC;`.

## Console output

```
AUDIT - action=USER_CREATE status=SUCCESS method=create tookMs=3 detail="Created user 'Alice' -> {id=1, name=Alice}"
AUDIT - action=USER_GET    status=SUCCESS method=get    tookMs=1 detail="Fetched user id=1 -> {id=1, name=Alice}"
AUDIT - action=USER_CREATE status=FAILURE method=create tookMs=0 detail="Failed to create user '': name must not be blank"
AUDIT - action=USER_GET    status=FAILURE method=get    tookMs=0 detail="Lookup failed for id=999: not found"
```

## Architecture

```
@Audit(X.class) method
       │
       ▼
   AuditAspect     ── resolves X (AuditAction bean)
       │                formats success/failure message
       ▼
 AuditEventPublisher
     ├── logs to SLF4J "AUDIT" logger     (synchronous)
     └── saves to H2 via JPA repository   (@Async, non-blocking)

GET /audit  ◄── AuditViewController ── AuditEventRepository ── H2
```

Keeping the publisher separate from the aspect means:

- The aspect contains zero I/O — easy to unit-test.
- Adding a second sink (Kafka, Splunk, SNS, log file) only touches
  `AuditEventPublisher.publish()`.
- Persistence failures are swallowed inside `@Async` so an audit outage
  never breaks the request.

## Project layout

```
src/main/java/com/example/audit/
├── AuditDemoApplication.java            # @SpringBootApplication + @EnableAsync
├── annotation/
│   └── Audit.java                       # @Audit(Class<? extends AuditAction>)
├── action/
│   ├── AuditAction.java                 # action / successFormat / failureFormat
│   └── AuditFormatter.java              # {0}, {result}, {error} substitution
├── actions/
│   ├── CreateUserAudit.java             # AuditAction bean
│   └── GetUserAudit.java                # AuditAction bean
├── aspect/
│   └── AuditAspect.java                 # @Around, delegates to publisher
├── persistence/
│   ├── AuditEvent.java                  # JPA @Entity (Lombok @Data/@Builder)
│   └── AuditEventRepository.java        # Spring Data repo
├── publisher/
│   ├── AuditRecord.java                 # immutable record passed from aspect
│   ├── AuditEventPublisher.java         # fans out to SLF4J + writer
│   └── AuditEventWriter.java            # @Async JPA save (separate bean)
└── web/
    ├── UserController.java              # audited endpoints
    └── AuditViewController.java         # GET /audit, GET /audit/{id}
```

## Adding a new auditable operation

1. Create a bean:

   ```java
   @Component
   public class DeleteUserAudit implements AuditAction {
       @Override public String action()        { return "USER_DELETE"; }
       @Override public String successFormat() { return "Deleted user id={0}"; }
       @Override public String failureFormat() { return "Delete failed id={0}: {error}"; }
   }
   ```

2. Annotate the controller method:

   ```java
   @DeleteMapping("/{id}")
   @Audit(DeleteUserAudit.class)
   public void delete(@PathVariable Long id) { ... }
   ```

Done — the aspect, publisher, and H2 row are automatic.
