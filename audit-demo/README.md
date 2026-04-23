# Audit Demo — Spring Boot 3.5 / Java 21

Spring Boot app demonstrating:

- A custom `@Audit` annotation backed by an `AuditAction` interface
  (`action()`, `successFormat()`, `failureFormat()`).
- An AOP aspect that wraps **both synchronous and `CompletableFuture`** calls
  and emits records to an `AuditEventPublisher`.
- Publisher fans out to SLF4J (sync) **and** H2 via JPA (async).
- A simulated long-running task (`CompletableFuture` + configurable sleep +
  success/fail/timeout/flaky outcomes).
- **Spring Security** with in-memory users, role-based route rules, form
  login + HTTP Basic.
- A **Thymeleaf dashboard** with a create-user form, buttons to trigger every
  audited action, and a live view of recent events (ADMIN only).
- Read-only `/audit` REST endpoints for programmatic access.
- Lombok everywhere (`@Value`, `@Builder`, `@Data`, `@RequiredArgsConstructor`,
  `@Slf4j`, `@StandardException`, `@Builder.Default`).

## Requirements

- Java 21
- Maven 3.9+
- (IDE) Lombok plugin + annotation processing enabled

## Run

```bash
mvn spring-boot:run
```

Open <http://localhost:8080/> and sign in.

> **First-run note:** if you ran an earlier version of this project, delete
> the `./data/` directory before starting — the `AUDIT_EVENT` table gained a
> `principal` column and Hibernate's `ddl-auto=update` won't rewrite existing
> rows.

## Demo users

| Username | Password    | Roles          | Can do                                  |
|----------|-------------|----------------|-----------------------------------------|
| admin    | admin123    | ADMIN, USER    | Everything + view `/audit`              |
| operator | operator123 | OPERATOR, USER | Create/process/fail users, view users   |
| viewer   | viewer123   | USER           | View users only (cannot POST)           |

## Authorization rules

| Path                      | Allowed roles       |
|---------------------------|---------------------|
| `/login`, `/css/**`       | anyone              |
| `/h2-console/**`          | authenticated       |
| `/audit/**`               | ADMIN               |
| `POST /users/**`          | ADMIN or OPERATOR   |
| `GET /users/**`           | authenticated       |
| everything else           | authenticated       |

## What to try

### 1. Sign in as `admin` via the web UI

- Go to <http://localhost:8080/>, sign in as **admin / admin123**.
- Fill out **Create user** with a name and submit — you'll see a JSON
  response in the inline iframe.
- Click the buttons: **Process (SUCCESS)**, **Process (FAIL)**, **Process
  (FLAKY)**, **Process (TIMEOUT)**, **Force sync fail**.
- Scroll down — the **Recent audit events** table shows every action,
  colour-coded by status, with the authenticated user recorded.

### 2. Sign in as `operator`

- Same buttons work. But **Recent audit events** is hidden (not an ADMIN).
- Verify this by clicking the raw JSON link — you'll get a 403.

### 3. Sign in as `viewer`

- The Create form is hidden and the action buttons disappear. The viewer
  can only read.
- Try `curl -u viewer:viewer123 http://localhost:8080/audit` — 403.

### 4. Hit the API with curl (HTTP Basic)

```bash
# admin can do everything
curl -u admin:admin123 -X POST "http://localhost:8080/users?name=Alice"
curl -u admin:admin123 "http://localhost:8080/audit?size=10" | python3 -m json.tool

# operator can write but not read audit
curl -u operator:operator123 -X POST \
     "http://localhost:8080/users/process?name=Bob&durationMs=800&outcome=FAIL"
curl -u operator:operator123 "http://localhost:8080/audit"   # 403

# viewer is read-only
curl -u viewer:viewer123 "http://localhost:8080/users/1"     # 200
curl -u viewer:viewer123 -X POST "http://localhost:8080/users?name=x"  # 403
```

## Expected console output

```
AUDIT - action=USER_CREATE  status=SUCCESS user=admin    method=create    tookMs=3    detail="Created user 'Alice' -> UserResponse(id=1, name=Alice)"
AUDIT - action=USER_PROCESS status=SUCCESS user=operator method=process   tookMs=1504 detail="Processed user 'slow' (requested 1500ms, outcome=SUCCESS) -> processed 'slow' after 1500ms"
AUDIT - action=USER_PROCESS status=FAILURE user=operator method=process   tookMs=803  detail="Processing failed for 'nope' (requested 800ms, outcome=FAIL): downstream service rejected request for 'nope'"
AUDIT - action=USER_CREATE  status=FAILURE user=admin    method=forceFail tookMs=0    detail="Failed to create user 'boom': forced failure for 'boom'"
```

Note the `user=...` field — pulled from the Spring `SecurityContext` inside
the aspect and persisted to the `PRINCIPAL` column.

## Endpoints

### Audited (write audit rows)

| Method | Path              | Audit action   | Roles            |
|--------|-------------------|----------------|------------------|
| POST   | `/users`          | `USER_CREATE`  | ADMIN, OPERATOR  |
| GET    | `/users/{id}`     | `USER_GET`     | authenticated    |
| POST   | `/users/process`  | `USER_PROCESS` | ADMIN, OPERATOR  |
| POST   | `/users/fail`     | `USER_CREATE`  | ADMIN, OPERATOR  |

### Viewer

| Method | Path            | Roles  |
|--------|-----------------|--------|
| GET    | `/audit`        | ADMIN  |
| GET    | `/audit/{id}`   | ADMIN  |
| GET    | `/h2-console`   | authenticated (H2 has its own login) |
| GET    | `/dashboard`    | authenticated |

## Architecture

```
@Audit(X.class) method
       │
       ▼
   AuditAspect   ── resolves X (AuditAction bean)
       │            reads principal from SecurityContext
       │            if return is CompletableFuture:
       │              attach whenComplete, publish on settle
       │            else:
       │              publish immediately
       ▼
 AuditEventPublisher
     ├── SLF4J "AUDIT" logger             (sync)
     └── AuditEventWriter.saveAsync()     (@Async → H2 via JPA)

GET /audit         ──► AuditViewController   (REST, ADMIN only)
GET /dashboard     ──► HomeController        (Thymeleaf, authenticated)
```

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
│   └── ProcessUserAudit.java
├── aspect/AuditAspect.java              # sync + CompletableFuture + principal
├── persistence/
│   ├── AuditEvent.java                  # JPA @Entity, Lombok @Data/@Builder
│   └── AuditEventRepository.java
├── publisher/
│   ├── AuditRecord.java                 # record
│   ├── AuditEventPublisher.java         # @Slf4j(topic="AUDIT")
│   └── AuditEventWriter.java            # @Async JPA save
├── security/
│   └── SecurityConfig.java              # in-memory users + role rules
├── service/
│   └── UserProcessingService.java       # CompletableFuture, Outcome enum
└── web/
    ├── UserController.java              # audited endpoints
    ├── AuditViewController.java         # GET /audit
    ├── HomeController.java              # Thymeleaf / dashboard
    └── dto/
        ├── UserResponse.java            # @Value @Builder
        └── ProcessResponse.java         # @Value @Builder + @Builder.Default

src/main/resources/
├── application.properties
├── static/css/app.css
└── templates/
    ├── login.html
    └── dashboard.html
```

## Notes on the Lombok footprint

- Service & aspect & publisher & controllers: `@RequiredArgsConstructor`
  + `@Slf4j` — removes constructor boilerplate and manual logger setup.
- Publisher uses `@Slf4j(topic = "AUDIT")` to stick with the dedicated
  logger category without a manual `LoggerFactory` call.
- DTOs use `@Value @Builder`, with `@Builder.Default` for default field
  values — the Lombok-idiomatic equivalent of default constructor args,
  which Java itself doesn't support.
- Simulated timeout uses `@StandardException` to generate all four
  canonical exception constructors in one annotation.
- Entity uses `@Data @Builder @NoArgsConstructor @AllArgsConstructor` —
  the standard JPA + builder combo.
