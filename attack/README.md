# spring-attack-surface

A zero-dependency Go scanner that maps the attack surface of a Java Spring
Boot application. Target stack: **Java 21**, **Spring Framework 6**,
**Spring Boot 3.5.13**.

The scanner walks `src/main/java`, identifies every externally reachable
entry point (HTTP, WebSocket, Actuator, messaging, scheduled, etc.) plus
dangerous internal sinks (file IO, outbound network, deserialization,
process exec, reflection, SQL, etc.), and emits a YAML report that a
downstream diagram tool can consume to render a threat model.

Standard library only - `go build` produces a single static binary.

---

## Build

```sh
cd spring-attack-surface
go build -o spring-attack-surface .
```

## Run

```sh
# Point it at a Spring Boot project root (or directly at src/main/java)
./spring-attack-surface -src /path/to/springboot-project -out attack-surface.yml
```

If you pass a project root, the scanner auto-descends into `src/main/java`
and skips `target/`, `build/`, `.git/`, and `node_modules/`.

### Flags

| Flag   | Default               | Description                                   |
| ------ | --------------------- | --------------------------------------------- |
| `-src` | `.`                   | Project root or `src/main/java` directory    |
| `-out` | `attack-surface.yml`  | Output YAML file                              |

---

## What it detects

### External entry points (method-level annotations)

| Annotation                              | Category            | Severity | Notes                                    |
| --------------------------------------- | ------------------- | -------- | ---------------------------------------- |
| `@GetMapping`                           | http_endpoint       | high     | Path joined with class `@RequestMapping` |
| `@PostMapping`                          | http_endpoint       | high     | Body, CSRF, mass assignment              |
| `@PutMapping`                           | http_endpoint       | high     | State mutation                           |
| `@DeleteMapping`                        | http_endpoint       | high     | IDOR, CSRF                               |
| `@PatchMapping`                         | http_endpoint       | high     | Partial updates                          |
| `@RequestMapping` (method-level)        | http_endpoint       | high     | Method-agnostic                          |
| `@Endpoint` / `@RestControllerEndpoint` | actuator_endpoint   | critical | Actuator surface                         |
| `@ReadOperation`                        | actuator_endpoint   | high     | Actuator GET                             |
| `@WriteOperation`                       | actuator_endpoint   | critical | Actuator state change                    |
| `@DeleteOperation`                      | actuator_endpoint   | critical | Actuator destructive                     |
| `@MessageMapping` / `@SubscribeMapping` | websocket_endpoint  | high     | STOMP/WebSocket                          |
| `@KafkaListener`                        | message_listener    | medium   | Topic consumer                           |
| `@RabbitListener`                       | message_listener    | medium   | AMQP queue consumer                      |
| `@JmsListener`                          | message_listener    | medium   | JMS consumer                             |
| `@SqsListener`                          | message_listener    | medium   | AWS SQS                                  |
| `@EventListener`                        | event_listener      | low      | Internal ApplicationEvent                |
| `@Scheduled`                            | scheduled_task      | low      | Timer-triggered                          |
| `@Async`                                | async_execution     | low      | Thread-pool exec                         |
| `@GrpcService`                          | grpc_service        | high     | gRPC RPC surface                         |

### Internal sinks (inline patterns)

| Sink                                                                   | Category         | Attack vectors                                  |
| ---------------------------------------------------------------------- | ---------------- | ----------------------------------------------- |
| `FileInputStream` / `FileReader` / `RandomAccessFile` / `Files.read*`  | file_io_disk     | path_traversal, information_disclosure          |
| `FileOutputStream` / `FileWriter` / `Files.write` / `Files.copy`       | file_io_disk     | arbitrary_file_write, path_traversal            |
| `MultipartFile` / `transferTo`                                         | file_upload      | unrestricted_upload, path_traversal             |
| `ByteArrayOutputStream` / `StringBuilder`                              | memory_buffer    | denial_of_service, memory_exhaustion            |
| `@Cacheable` / `ConcurrentHashMap` / `HashMap`                         | memory_cache     | cache_poisoning, memory_exhaustion              |
| `RestTemplate` / `WebClient` / `HttpClient` / `HttpURLConnection`      | network_outbound | ssrf, credential_leak                           |
| `new URL(...)` / `URI.create(...)`                                     | network_outbound | ssrf, open_redirect                             |
| `new ServerSocket(...)` / `new DatagramSocket(...)`                    | network_inbound  | unauthenticated_access, protocol_attack, DoS    |
| `ObjectInputStream.readObject` / `XMLDecoder` / SnakeYAML              | deserialization  | insecure_deserialization, remote_code_execution |
| `Runtime.exec` / `ProcessBuilder`                                      | process_exec     | command_injection, remote_code_execution        |
| `Class.forName` / `Method.invoke`                                      | reflection       | unsafe_reflection, remote_code_execution        |
| `DocumentBuilderFactory` / `SAXParserFactory` / `XMLInputFactory`      | xml_parser       | xxe, ssrf                                       |
| `createNativeQuery` / `createQuery` / `jdbcTemplate.*`                 | sql_query        | sql_injection                                   |
| `@Query` with string concatenation                                     | sql_query        | sql_injection (critical)                        |
| `SpelExpressionParser` / `.parseExpression`                            | template_engine  | expression_injection, remote_code_execution     |
| `MessageDigest(MD5/SHA-1)` / `Cipher(DES/RC4/AES-ECB)`                 | crypto           | weak_crypto                                     |

---

## Output schema

The YAML report has two top-level blocks: `metadata` and `attack_surface`.

```yaml
metadata:
  scanned_path: /abs/path/to/project
  scan_time: "2026-04-22T11:10:51Z"
  java_version: 21
  spring_framework_version: 6.x
  spring_boot_version: 3.5.13
  files_scanned: 3
  total_findings: 38
  by_category:
    actuator_endpoint: 4
    deserialization: 4
    http_endpoint: 9
    # ...
  by_severity:
    critical: 8
    high: 20
    medium: 6
    low: 4

attack_surface:
  - id: 1
    category: http_endpoint
    entry_point: "POST /api/users/restore"
    http_method: POST
    package: com.example.demo.web
    class: UserController
    method: restore
    file: /abs/path/.../UserController.java
    line: 70
    severity: critical
    description: HTTP POST endpoint that accepts request bodies
    snippet: "@PostMapping(\"/restore\")  -->  public Object restore(@RequestBody byte[] blob)..."
    attack_vectors:
      - injection
      - csrf
      - mass_assignment
      - xxe
      - deserialization
  - id: 2
    category: deserialization
    entry_point: "deserialization://UserController#restore"
    package: com.example.demo.web
    class: UserController
    method: restore
    file: /abs/path/.../UserController.java
    line: 72
    severity: critical
    description: Java native deserialization - classic RCE gadget chain sink
    snippet: "ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(blob));"
    attack_vectors:
      - insecure_deserialization
      - remote_code_execution
  # ...
```

### Entry-point URI scheme

The `entry_point` field is a canonical address suitable for use as a
diagram node label:

| Scheme               | Example                                            |
| -------------------- | -------------------------------------------------- |
| HTTP                 | `POST /api/users/{id}`                             |
| WebSocket            | `ws://chat/{room}`                                 |
| Actuator             | `actuator://appinfo`                               |
| Message queue        | `mq://orders.created`                              |
| Event listener       | `event://OrderEvents#onApplicationEvent`           |
| Scheduled            | `schedule://OrderEvents#syncJob`                   |
| Async                | `async://OrderEvents#fireAndForget`                |
| gRPC                 | `grpc://UserService`                               |
| Internal sink        | `deserialization://UserController#restore`         |

---

## How the diagram app can consume this

The findings are pre-sorted (severity desc then category then file then line)
and every record has a stable `id` within a single report. A reasonable
rendering pipeline:

1. **Nodes**: one per finding. Color by `severity`. Shape by `category`
   (e.g. box for HTTP, cylinder for file_io_disk, cloud for
   network_outbound, skull icon for critical).
2. **Groups**: cluster by `class` or `package`.
3. **Edges**: within a single class, link every entry_point finding
   (external) to every internal-sink finding in the same `method`. That
   produces request-flow edges like
   `POST /api/users/restore -> deserialization://UserController#restore`.
4. **Legend**: build from `metadata.by_category` and
   `metadata.by_severity`.

---

## Sample

`sample/` contains three intentionally bad Spring Boot 3.5.13 files that
exercise most detection paths. Running the scanner against them:

```sh
./spring-attack-surface -src ./sample -out sample-report.yml
```

produces `sample-report.yml` (included in this directory) with 38
findings across 17 categories and 4 severity levels.

---

## Design notes & limitations

- **Regex-based**, not an AST parser. It is fast, zero-dependency, and
  accurate for idiomatic Spring Boot code; pathological layouts (deeply
  nested lambdas with method-shaped arguments, annotations inside string
  literals) can produce false positives or misses.
- **Comment stripping preserves line numbers** so findings point at the
  same lines shown in your IDE.
- **Brace-depth tracker ignores braces inside string/char literals**,
  which keeps the "am I inside the class body?" check reliable.
- **Method attribution for inline sinks** uses the most recent method
  signature seen at class-level brace depth. For sinks inside anonymous
  inner classes or lambdas the attribution falls back to the outer
  method, which is usually what you want for threat modeling anyway.
- **Class-level annotations are filtered out** of the method-level
  annotation buffer (braceDepth == 0 annotations are treated as class
  metadata only), so a class `@RequestMapping` does not incorrectly
  attach to the first method below it.

## Extending

Add a new sink: append to `inlinePatterns` in `patterns.go`. Add a new
annotation-driven entry point: add an entry to the `annoPatterns` map.
No other code changes are required.
