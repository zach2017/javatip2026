# CompletableFuture Quick Reference Cheat Sheet

## Dependencies (pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Lombok (@Slf4j) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- Actuator (optional) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

---

## Essential Annotations

| Annotation | Where | Purpose |
|------------|-------|---------|
| `@SpringBootApplication` | Main class | Marks Spring Boot app |
| `@EnableAsync` | Main or Config class | Enables async |
| `@Slf4j` | Any class | Adds `log` variable |
| `@Service` | Service class | Marks as service |
| `@RequiredArgsConstructor` | Any class | Auto constructor |
| `@Configuration` | Config class | Spring config |
| `@Bean` | Method | Creates bean |

---

## AsyncExecutorService Methods

```java
// 1. Return value (platform thread)
executeAsync(Supplier<T>)              // () -> "result"

// 2. Return value (virtual thread - Java 21)
executeAsyncVirtual(Supplier<T>)       // () -> "result"

// 3. No return value
executeAsync(Runnable)                 // () -> { doWork(); }

// 4. Transform input
executeAsync(Function<T,R>, input)     // (x) -> x.toUpperCase(), "hello"

// 5. Consume input
executeAsync(Consumer<T>, input)       // (x) -> log(x), "hello"

// 6. Parallel execution
executeAllAsync(supplier1, supplier2)  // Returns T[]

// 7. Chain operations
executeAndThen(supplier, function)     // Step 1 -> Step 2

// 8. With timeout
executeWithTimeout(supplier, Duration) // Fails if too slow

// 9. With fallback
executeWithFallback(supplier, fallback) // Default on error

// 10. Race (first wins)
executeAnyAsync(supplier1, supplier2)  // First completed
```

---

## Functional Interfaces

| Interface | Signature | Lambda Example |
|-----------|-----------|----------------|
| `Supplier<T>` | `() → T` | `() -> "Hello"` |
| `Runnable` | `() → void` | `() -> log.info("Hi")` |
| `Function<T,R>` | `(T) → R` | `(s) -> s.toUpperCase()` |
| `Consumer<T>` | `(T) → void` | `(s) -> System.out.println(s)` |

---

## Usage Examples

### Inject the service
```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final AsyncExecutorService asyncExecutor;
}
```

### Simple async task
```java
asyncExecutor.executeAsync(() -> {
    log.info("Hello from background!");
});
```

### Get result
```java
CompletableFuture<String> future = asyncExecutor.executeAsync(() -> {
    return "data";
});
String result = future.join();
```

### Virtual threads (I/O)
```java
asyncExecutor.executeAsyncVirtual(() -> {
    return httpClient.get(url);
});
```

### Parallel tasks
```java
asyncExecutor.executeAllAsync(
    () -> api1.call(),
    () -> api2.call(),
    () -> api3.call()
);
```

### With timeout
```java
asyncExecutor.executeWithTimeout(
    () -> slowService.call(),
    Duration.ofSeconds(5)
);
```

### With fallback
```java
asyncExecutor.executeWithFallback(
    () -> riskyCall(),
    (error) -> "default"
);
```

---

## application.yml Essentials

```yaml
spring:
  threads:
    virtual:
      enabled: true  # Enable virtual threads

logging:
  level:
    com.example: DEBUG
  pattern:
    console: "%d [%thread] %-5level %logger{36} - %msg%n"
```

---

## When to Use Which Thread

| Task Type | Thread Type | Method |
|-----------|-------------|--------|
| API calls | Virtual | `executeAsyncVirtual()` |
| Database | Virtual | `executeAsyncVirtual()` |
| File I/O | Virtual | `executeAsyncVirtual()` |
| CPU work | Platform | `executeAsync()` |
| Calculations | Platform | `executeAsync()` |

---

## CompletableFuture Methods

| Method | Description |
|--------|-------------|
| `.join()` | Wait & get result |
| `.get()` | Wait & get (checked ex) |
| `.thenApply(fn)` | Transform result |
| `.thenAccept(fn)` | Use result |
| `.exceptionally(fn)` | Handle error |
| `.orTimeout(t, unit)` | Add timeout |

---

## Error Handling

```java
future
    .thenAccept(result -> log.info("OK: {}", result))
    .exceptionally(error -> {
        log.error("Error: {}", error.getMessage());
        return null;
    });
```

---

## Common Mistakes

| ❌ Wrong | ✅ Right |
|----------|----------|
| Block with `.join()` immediately | Return the future |
| Platform threads for API calls | Virtual threads for I/O |
| No error handling | Use `.exceptionally()` |
| No timeout for external calls | Use `.orTimeout()` |

---

## Files Checklist

- [ ] `pom.xml` - Java 21, Spring Boot 3.2, Lombok
- [ ] `AsyncDemoApplication.java` - `@EnableAsync`
- [ ] `AsyncConfig.java` - Thread pool + virtual threads
- [ ] `AsyncExecutorService.java` - The reusable service
- [ ] `application.yml` - `spring.threads.virtual.enabled: true`
