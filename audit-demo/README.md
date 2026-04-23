# Audit Demo — Spring Boot 3.5 / Java 21

Spring Boot app demonstrating:

- A custom `@Audit` annotation backed by an `AuditAction` interface
  (`action()`, `successFormat()`, `failureFormat()`).
- An AOP aspect that wraps **both synchronous and `CompletableFuture`** calls
  and emits records to an `AuditEventPublisher`.
- Publisher fans out to SLF4J (synchronous) **and** H2 via JPA (async).
- A simulated long-running task (`CompletableFuture` + configurable sleep +
  success/fail/timeout/flaky outcomes) to exercise the async audit path.
- A read-only `/audit` controller for browsing persisted events.
- Lombok everywhere (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`).

## Requirements

- Java 21
- Maven 3.9+
- (IDE) Lombok plugin + annotation processing enabled

## Run

```bash
mvn spring-boot:run
```

The app listens on `http://localhost:8080`.

## Endpoints

### Audited (writes audit rows)

| Method | Path              | Audit action   | Notes                                      |
|--------|-------------------|----------------|--------------------------------------------|
| POST   | `/users`          | `USER_CREATE`  | Sync. 400 if `name` blank.                 |
| GET    | `/users/{id}`     | `USER_GET`     | Sync. Fails if id unknown.                 |
| POST   | `/users/process`  | `USER_PROCESS` | **Async** — returns CompletableFuture.     |
| POST   | `/users/fail`     | `USER_CREATE`  | Always throws — demo sync failure path.    |

### Viewer (reads only)

| Method | Path                | Notes                                          |
|--------|---------------------|------------------------------------------------|
| GET    | `/audit`            | Paginated, sorted newest-first.                |
| GET    | `/audit/{id}`       | Single audit record.                           |
| GET    | `/h2-console`       | H2 web console. JDBC URL `jdbc:h2:file:./data/auditdb`, user `sa`, empty password. |

## curl cookbook

### Sync success + failure

```bash
curl -X POST "http://localhost:8080/users?name=Alice"
curl "http://localhost:8080/users/1"

# Sync failures
curl -X POST "http://localhost:8080/users?name="
curl "http://localhost:8080/users/999"
curl -X POST "http://localhost:8080/users/fail"
```

### Async long-running task

All query params have defaults, so the bare POST works:

```bash
# Default: 1500ms, SUCCESS
curl -X POST "http://localhost:8080/users/process"

# Explicit success, 2 seconds
curl -X POST "http://localhost:8080/users/process?name=Alice&durationMs=2000&outcome=SUCCESS"

# Deliberate async failure after 1s
curl -X POST "http://localhost:8080/users/process?name=Bob&durationMs=1000&outcome=FAIL"

# Simulated external-system timeout
curl -X POST "http://localhost:8080/users/process?name=Carol&durationMs=500&outcome=TIMEOUT"

# Flaky — roughly 50/50 success vs failure, fire a few times
for i in 1 2 3 4 5; do
  curl -s -X POST "http://localhost:8080/users/process?name=user$i&durationMs=300&outcome=FLAKY"
  echo
done
```

`outcome` accepts: `SUCCESS`, `FAIL`, `TIMEOUT`, `FLAKY`.

### Browse the audit log

```bash
# Newest first
curl "http://localhost:8080/audit?size=20"

# By action
curl "http://localhost:8080/audit?action=USER_PROCESS"

# Only failures
curl "http://localhost:8080/audit?status=FAILURE"

# Specific row
curl "http://localhost:8080/audit/1"
```

### One-shot demo script

```bash
# Sync
curl -s -X POST "http://localhost:8080/users?name=Alice"   > /dev/null
curl -s      "http://localhost:8080/users/1"               > /dev/null
curl -s -X POST "http://localhost:8080/users/fail"         > /dev/null

# Async
curl -s -X POST "http://localhost:8080/users/process?name=slow&durationMs=1500&outcome=SUCCESS" > /dev/null
curl -s -X POST "http://localhost:8080/users/process?name=nope&durationMs=800&outcome=FAIL"     > /dev/null
curl -s -X POST "http://localhost:8080/users/process?name=flaky&outcome=FLAKY"                  > /dev/null

# Give the async tasks + @Async writer a moment to flush
sleep 3

echo "--- All audit events ---"
curl -s "http://localhost:8080/audit?size=20" | python3 -m json.tool
```

## Expected console output (abridged)

```
AUDIT - action=USER_CREATE  status=SUCCESS method=create   tookMs=3    detail="Created user 'Alice' -> {id=1, name=Alice}"
AUDIT - action=USER_CREATE  status=FAILURE method=forceFail tookMs=0   detail="Failed to create user 'boom': forced failure for 'boom'"
AUDIT - action=USER_PROCESS status=SUCCESS method=process  tookMs=1504 detail="Processed user 'slow' (requested 1500ms, outcome=SUCCESS) -> processed 'slow' after 1500ms"
AUDIT - action=USER_PROCESS status=FAILURE method=process  tookMs=803  detail="Processing failed for 'nope' (requested 800ms, outcome=FAIL): downstream service rejected request for 'nope'"
```

Note `tookMs` on the async rows — it reflects the **actual wall-clock
duration of the future**, not the ~0ms it took the controller to hand off.

## Architecture

```
@Audit(X.class) method
       │
       ▼
   AuditAspect  ── resolves X (AuditAction bean)
       │            if return is CompletableFuture:
       │              attach whenComplete, publish on settle
       │            else:
       │              publish immediately
       ▼
 AuditEventPublisher
     ├── SLF4J "AUDIT" logger             (sync)
     └── AuditEventWriter.saveAsync()     (@Async → H2 via JPA)

GET /audit ──► AuditViewController ──► AuditEventRepository ──► H2
```

### Why an `AuditEventWriter` separate from `AuditEventPublisher`?

Spring's `@Async` only works through the bean proxy. If the publisher called
its own `@Async` save method, self-invocation would bypass the proxy and the
save would run synchronously. Putting the async method on a distinct bean
guarantees it actually runs on the async executor.

## Project layout

```
src/main/java/com/example/audit/
├── AuditDemoApplication.java            # @SpringBootApplication + @EnableAsync
├── annotation/Audit.java
├── action/
│   ├── AuditAction.java                 # action / successFormat / failureFormat
│   └── AuditFormatter.java              # {0}, {result}, {error} substitution
├── actions/
│   ├── CreateUserAudit.java
│   ├── GetUserAudit.java
│   └── ProcessUserAudit.java            # for async long-running task
├── aspect/AuditAspect.java              # handles sync + CompletableFuture
├── persistence/
│   ├── AuditEvent.java                  # JPA @Entity (Lombok @Data/@Builder)
│   └── AuditEventRepository.java
├── publisher/
│   ├── AuditRecord.java                 # immutable record
│   ├── AuditEventPublisher.java         # fans out to SLF4J + writer
│   └── AuditEventWriter.java            # @Async JPA save
├── service/
│   └── UserProcessingService.java       # CompletableFuture + Outcome enum
└── web/
    ├── UserController.java              # audited endpoints
    └── AuditViewController.java         # GET /audit
```
