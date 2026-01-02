# Java Spring Boot CompletableFuture Tutorial

## A Step-by-Step Guide to Async Programming with Java 21

---

# Table of Contents

1. [What is This Tutorial About?](#1-what-is-this-tutorial-about)
2. [Prerequisites](#2-prerequisites)
3. [Key Concepts Explained Simply](#3-key-concepts-explained-simply)
4. [Project Setup](#4-project-setup)
5. [The pom.xml File Explained](#5-the-pomxml-file-explained)
6. [Application Configuration](#6-application-configuration)
7. [Step-by-Step Code Walkthrough](#7-step-by-step-code-walkthrough)
8. [How to Use the AsyncExecutorService](#8-how-to-use-the-asyncexecutorservice)
9. [Running the Application](#9-running-the-application)
10. [Testing the API](#10-testing-the-api)
11. [Common Patterns and Best Practices](#11-common-patterns-and-best-practices)
12. [Troubleshooting](#12-troubleshooting)

---

# 1. What is This Tutorial About?

This tutorial teaches you how to build a **reusable async service** in Spring Boot that:

- Runs any code in the background (async)
- Uses Java 21's new **Virtual Threads** for better performance
- Can be injected into any service in your application
- Handles timeouts, fallbacks, parallel execution, and more

## What You Will Learn

- What `CompletableFuture` is and why you need it
- How to create a generic async service
- How to use Java 21 virtual threads
- How to configure Spring Boot for async operations
- How to handle errors, timeouts, and fallbacks

---

# 2. Prerequisites

## Software Requirements

| Software | Version | Purpose |
|----------|---------|---------|
| Java JDK | 21+ | Required for virtual threads |
| Maven | 3.6+ | Build tool |
| IDE | Any (IntelliJ, Eclipse, VS Code) | Code editing |

## Knowledge Requirements

- Basic Java understanding
- Basic Spring Boot knowledge
- Understanding of what a REST API is

---

# 3. Key Concepts Explained Simply

## What is Async Programming?

**Synchronous (Normal) Code:**
```
Task 1 starts → Task 1 finishes → Task 2 starts → Task 2 finishes
Total time: Task 1 time + Task 2 time
```

**Asynchronous Code:**
```
Task 1 starts → Task 2 starts → Both finish around the same time
Total time: Max(Task 1 time, Task 2 time)
```

### Key Point
- **Sync** = One thing at a time (blocking)
- **Async** = Multiple things at once (non-blocking)

---

## What is CompletableFuture?

`CompletableFuture` is Java's way of handling async operations.

### Simple Analogy
Think of it like ordering food at a restaurant:
- **Sync**: You order, wait at the counter until food is ready, then sit down
- **Async (CompletableFuture)**: You order, get a buzzer, sit down, buzzer rings when food is ready

### Basic Example
```java
// This runs in the background and returns a "promise" of a result
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return "Hello World";
});

// Later, get the result (this waits if not ready yet)
String result = future.join();
```

---

## What are Virtual Threads (Java 21)?

### The Problem with Regular Threads
- Regular threads are "expensive" (memory, CPU)
- You can only have thousands of them
- Not good for I/O operations (waiting for APIs, databases)

### Virtual Threads Solution
- Lightweight threads managed by Java
- You can have millions of them
- Perfect for I/O operations

### When to Use Which?

| Thread Type | Best For | Example |
|------------|----------|---------|
| Platform (Regular) | CPU-heavy work | Math calculations, image processing |
| Virtual | I/O operations | API calls, database queries, file reading |

---

## What is Lombok @Slf4j?

Lombok is a library that reduces boilerplate code.

**Without Lombok:**
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyService {
    private static final Logger log = LoggerFactory.getLogger(MyService.class);
    
    public void doSomething() {
        log.info("Hello!");
    }
}
```

**With Lombok @Slf4j:**
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyService {
    public void doSomething() {
        log.info("Hello!");
    }
}
```

### Key Point
- `@Slf4j` automatically creates a `log` variable for you
- Use `log.info()`, `log.debug()`, `log.error()` to log messages

---

# 4. Project Setup

## Project Structure

```
async-demo/
├── pom.xml                          # Maven configuration
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/asyncdemo/
│       │       ├── AsyncDemoApplication.java    # Main app
│       │       ├── config/
│       │       │   ├── AsyncConfig.java         # Thread config
│       │       │   ├── AsyncProperties.java     # Properties
│       │       │   ├── GlobalExceptionHandler.java
│       │       │   └── WebConfig.java           # CORS
│       │       ├── controller/
│       │       │   └── HelloController.java     # REST API
│       │       ├── runner/
│       │       │   └── AsyncDemoRunner.java     # Demo on startup
│       │       └── service/
│       │           ├── AsyncExecutorService.java # REUSABLE!
│       │           └── HelloService.java        # Example
│       └── resources/
│           └── application.yml                  # Configuration
```

## File Purposes

| File | Purpose |
|------|---------|
| `pom.xml` | Defines dependencies (what libraries to use) |
| `AsyncDemoApplication.java` | Entry point - starts the app |
| `AsyncConfig.java` | Configures thread pools |
| `AsyncExecutorService.java` | **THE MAIN REUSABLE SERVICE** |
| `HelloService.java` | Example of how to use it |
| `HelloController.java` | REST endpoints to test |
| `application.yml` | App settings |

---

# 5. The pom.xml File Explained

## Complete pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- Spring Boot Parent - provides default configurations -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <!-- Your project info -->
    <groupId>com.example</groupId>
    <artifactId>async-demo</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>async-demo</name>
    <description>CompletableFuture Demo with Spring Boot</description>

    <!-- Java version - MUST be 21 for virtual threads -->
    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web - for REST APIs -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Lombok - for @Slf4j logging annotation -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Actuator - for health checks and monitoring -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <!-- Don't include Lombok in final JAR -->
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Dependencies Breakdown

| Dependency | What It Does | Why We Need It |
|------------|--------------|----------------|
| `spring-boot-starter-web` | Web server, REST support | To create REST APIs |
| `lombok` | Reduces boilerplate code | For `@Slf4j`, `@RequiredArgsConstructor` |
| `spring-boot-starter-actuator` | Health checks, metrics | For monitoring |
| `spring-boot-starter-test` | Testing utilities | For unit tests |

## Important Notes

- **Java 21 is required** for virtual threads
- **Spring Boot 3.2+** has built-in virtual thread support
- **Lombok is optional** but highly recommended

---

# 6. Application Configuration

## application.yml Explained

```yaml
# Application name
spring:
  application:
    name: async-demo
  
  # IMPORTANT: Enable virtual threads for Spring MVC
  threads:
    virtual:
      enabled: true

  # JSON formatting
  jackson:
    serialization:
      indent-output: true          # Pretty print JSON
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false
    default-property-inclusion: non_null

# Server settings
server:
  port: 8080                       # Port to run on
  servlet:
    context-path: /                # Base URL path
  tomcat:
    threads:
      max: 200                     # Max threads
      min-spare: 10                # Min threads

# Logging
logging:
  level:
    root: INFO                     # Default log level
    com.example.asyncdemo: DEBUG   # Our app: more detail
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

# Health check endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

# Custom settings for our async executor
async:
  executor:
    core-pool-size: 4              # Minimum threads
    max-pool-size: 8               # Maximum threads
    queue-capacity: 100            # Waiting queue size
    thread-name-prefix: "Async-"   # Thread naming
```

## Key Configuration Points

| Setting | Purpose |
|---------|---------|
| `spring.threads.virtual.enabled: true` | **Enables Java 21 virtual threads** |
| `server.port: 8080` | App runs on port 8080 |
| `logging.level.com.example.asyncdemo: DEBUG` | See detailed logs for our code |
| `async.executor.*` | Custom thread pool settings |

---

# 7. Step-by-Step Code Walkthrough

## Step 1: Main Application Class

**File: `AsyncDemoApplication.java`**

```java
package com.example.asyncdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication  // Marks this as a Spring Boot app
@EnableAsync            // Enables async processing
public class AsyncDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncDemoApplication.class, args);
    }
}
```

### Key Points
- `@SpringBootApplication` - Tells Spring this is the main class
- `@EnableAsync` - **Required** to enable async features

---

## Step 2: Async Configuration

**File: `AsyncConfig.java`**

```java
package com.example.asyncdemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Traditional thread pool - use for CPU-bound tasks
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        log.info("Creating Traditional Async Executor");
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);           // Min threads
        executor.setMaxPoolSize(8);            // Max threads
        executor.setQueueCapacity(100);        // Queue size
        executor.setThreadNamePrefix("Async-"); // Thread naming
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        return executor;
    }

    /**
     * Virtual threads - use for I/O-bound tasks (Java 21)
     */
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        log.info("Creating Virtual Thread Executor");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    @Slf4j
    static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Async error in '{}': {}", method.getName(), ex.getMessage(), ex);
        }
    }
}
```

### Key Points

| Bean | Purpose | When to Use |
|------|---------|-------------|
| `asyncExecutor` | Platform thread pool | CPU work (calculations) |
| `virtualThreadExecutor` | Virtual threads | I/O work (API calls, DB) |

### Thread Pool Settings Explained

| Setting | Meaning |
|---------|---------|
| `corePoolSize: 4` | Always keep 4 threads ready |
| `maxPoolSize: 8` | Never use more than 8 threads |
| `queueCapacity: 100` | Hold up to 100 waiting tasks |

---

## Step 3: The AsyncExecutorService (THE MAIN SERVICE)

This is the **reusable service** you can inject anywhere.

**File: `AsyncExecutorService.java`**

```java
package com.example.asyncdemo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Service
public class AsyncExecutorService {

    private final Executor platformExecutor;
    private final Executor virtualExecutor;

    // Constructor injection of both executors
    public AsyncExecutorService(
            @Qualifier("asyncExecutor") Executor platformExecutor,
            @Qualifier("virtualThreadExecutor") Executor virtualExecutor) {
        this.platformExecutor = platformExecutor;
        this.virtualExecutor = virtualExecutor;
    }

    // ============================================
    // METHOD 1: Execute with return value (Supplier)
    // ============================================
    public <T> CompletableFuture<T> executeAsync(Supplier<T> supplier) {
        log.info("Submitting async task with return value");
        return CompletableFuture.supplyAsync(() -> {
            log.info("Running on thread: {}", Thread.currentThread());
            return supplier.get();
        }, platformExecutor);
    }

    // ============================================
    // METHOD 2: Execute with return value (Virtual Thread)
    // ============================================
    public <T> CompletableFuture<T> executeAsyncVirtual(Supplier<T> supplier) {
        log.info("Submitting async task (virtual thread)");
        return CompletableFuture.supplyAsync(() -> {
            log.info("Running on virtual thread: {}", Thread.currentThread());
            return supplier.get();
        }, virtualExecutor);
    }

    // ============================================
    // METHOD 3: Execute without return value (Runnable)
    // ============================================
    public CompletableFuture<Void> executeAsync(Runnable runnable) {
        log.info("Submitting async task (no return)");
        return CompletableFuture.runAsync(() -> {
            log.info("Running on thread: {}", Thread.currentThread().getName());
            runnable.run();
        }, platformExecutor);
    }

    // ============================================
    // METHOD 4: Transform input to output (Function)
    // ============================================
    public <T, R> CompletableFuture<R> executeAsync(Function<T, R> function, T input) {
        log.info("Submitting async task with input");
        return CompletableFuture.supplyAsync(() -> {
            log.info("Running on thread: {}", Thread.currentThread().getName());
            return function.apply(input);
        }, platformExecutor);
    }

    // ============================================
    // METHOD 5: Consume input (Consumer)
    // ============================================
    public <T> CompletableFuture<Void> executeAsync(Consumer<T> consumer, T input) {
        log.info("Submitting async consumer task");
        return CompletableFuture.runAsync(() -> {
            log.info("Running on thread: {}", Thread.currentThread().getName());
            consumer.accept(input);
        }, platformExecutor);
    }

    // ============================================
    // METHOD 6: Run multiple in parallel
    // ============================================
    @SafeVarargs
    public final <T> CompletableFuture<T[]> executeAllAsync(Supplier<T>... suppliers) {
        log.info("Submitting {} parallel tasks", suppliers.length);
        
        @SuppressWarnings("unchecked")
        CompletableFuture<T>[] futures = new CompletableFuture[suppliers.length];
        
        for (int i = 0; i < suppliers.length; i++) {
            futures[i] = executeAsync(suppliers[i]);
        }

        return CompletableFuture.allOf(futures)
            .thenApply(v -> {
                @SuppressWarnings("unchecked")
                T[] results = (T[]) new Object[futures.length];
                for (int i = 0; i < futures.length; i++) {
                    results[i] = futures[i].join();
                }
                return results;
            });
    }

    // ============================================
    // METHOD 7: Chain operations
    // ============================================
    public <T, R> CompletableFuture<R> executeAndThen(
            Supplier<T> supplier, 
            Function<T, R> function) {
        log.info("Submitting chained task");
        return executeAsync(supplier)
            .thenApplyAsync(function, platformExecutor);
    }

    // ============================================
    // METHOD 8: Execute with timeout
    // ============================================
    public <T> CompletableFuture<T> executeWithTimeout(
            Supplier<T> supplier, 
            Duration timeout) {
        log.info("Submitting task with timeout: {}", timeout);
        return executeAsync(supplier)
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    // ============================================
    // METHOD 9: Execute with fallback on error
    // ============================================
    public <T> CompletableFuture<T> executeWithFallback(
            Supplier<T> supplier, 
            Function<Throwable, T> fallback) {
        log.info("Submitting task with fallback");
        return executeAsync(supplier)
            .exceptionally(fallback);
    }

    // ============================================
    // METHOD 10: Race - first one wins
    // ============================================
    @SafeVarargs
    public final <T> CompletableFuture<T> executeAnyAsync(Supplier<T>... suppliers) {
        log.info("Submitting {} racing tasks", suppliers.length);
        
        @SuppressWarnings("unchecked")
        CompletableFuture<T>[] futures = new CompletableFuture[suppliers.length];
        
        for (int i = 0; i < suppliers.length; i++) {
            futures[i] = executeAsync(suppliers[i]);
        }

        return CompletableFuture.anyOf(futures)
            .thenApply(result -> {
                @SuppressWarnings("unchecked")
                T typedResult = (T) result;
                return typedResult;
            });
    }
}
```

---

## Method Reference Card

| Method | Input | Output | Use When |
|--------|-------|--------|----------|
| `executeAsync(Supplier)` | `() -> value` | `Future<T>` | You need to return something |
| `executeAsyncVirtual(Supplier)` | `() -> value` | `Future<T>` | I/O work (API, DB) |
| `executeAsync(Runnable)` | `() -> {}` | `Future<Void>` | Fire and forget |
| `executeAsync(Function, input)` | `(x) -> y` | `Future<R>` | Transform data |
| `executeAsync(Consumer, input)` | `(x) -> {}` | `Future<Void>` | Process data |
| `executeAllAsync(suppliers...)` | Multiple | `Future<T[]>` | Parallel execution |
| `executeAndThen(supplier, func)` | Two steps | `Future<R>` | Chain operations |
| `executeWithTimeout(supplier, dur)` | + timeout | `Future<T>` | Time limit |
| `executeWithFallback(supplier, fb)` | + fallback | `Future<T>` | Error handling |
| `executeAnyAsync(suppliers...)` | Multiple | `Future<T>` | First wins |

---

# 8. How to Use the AsyncExecutorService

## Inject It Into Any Service

```java
@Service
@RequiredArgsConstructor  // Lombok creates constructor
public class MyService {

    private final AsyncExecutorService asyncExecutorService;
    
    // Now use it!
}
```

## Example 1: Simple Background Task

```java
public void sendEmail() {
    asyncExecutorService.executeAsync(() -> {
        log.info("Sending email in background...");
        emailService.send(email);
        log.info("Email sent!");
    });
    // Returns immediately, email sends in background
}
```

## Example 2: Get a Result

```java
public CompletableFuture<User> getUserAsync(Long id) {
    return asyncExecutorService.executeAsync(() -> {
        log.info("Fetching user {}...", id);
        return userRepository.findById(id);
    });
}

// Usage:
CompletableFuture<User> future = getUserAsync(123L);
User user = future.join(); // Wait for result
```

## Example 3: Virtual Threads for API Calls

```java
public CompletableFuture<String> callExternalApi() {
    return asyncExecutorService.executeAsyncVirtual(() -> {
        log.info("Calling external API...");
        return restTemplate.getForObject("https://api.example.com/data", String.class);
    });
}
```

## Example 4: Transform Data

```java
public CompletableFuture<String> processName(String name) {
    return asyncExecutorService.executeAsync(
        (String input) -> input.toUpperCase(),
        name
    );
}
```

## Example 5: Run Multiple Tasks in Parallel

```java
public CompletableFuture<Object[]> fetchAllData() {
    return asyncExecutorService.executeAllAsync(
        () -> userService.getUsers(),
        () -> orderService.getOrders(),
        () -> productService.getProducts()
    );
    // All three run at the same time!
}
```

## Example 6: With Timeout

```java
public CompletableFuture<String> fetchWithTimeout() {
    return asyncExecutorService.executeWithTimeout(
        () -> slowExternalService.call(),
        Duration.ofSeconds(5)  // Fail if takes > 5 seconds
    );
}
```

## Example 7: With Fallback on Error

```java
public CompletableFuture<String> fetchWithFallback() {
    return asyncExecutorService.executeWithFallback(
        () -> {
            // This might fail
            return riskyOperation();
        },
        (error) -> {
            log.error("Failed: {}", error.getMessage());
            return "default value";  // Return this on error
        }
    );
}
```

---

# 9. Running the Application

## Option 1: Using Maven

```bash
cd async-demo
mvn spring-boot:run
```

## Option 2: Build and Run JAR

```bash
cd async-demo
mvn clean package
java -jar target/async-demo-1.0.0.jar
```

## Expected Output

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.0)

Starting Async Demo Examples (Java 21)
========================================
--- Example 1: Simple Hello (Platform Thread) ---
Submitting async task (no return value)
Starting async execution on thread: Async-1
Hello from async task!
...
========================================
REST API available at: http://localhost:8080/api/hello
```

---

# 10. Testing the API

## Available Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/hello` | Simple async test |
| GET | `/api/hello/virtual` | Virtual thread test |
| GET | `/api/hello/greet?name=John` | Greet with name |
| GET | `/api/hello/greet/virtual?name=John` | Greet (virtual) |
| GET | `/api/hello/transform?name=John` | Transform name |
| GET | `/api/hello/parallel` | Run 3 tasks in parallel |
| GET | `/api/hello/parallel/virtual` | Parallel (virtual) |
| GET | `/api/hello/chain?name=John` | Chain 2 operations |
| GET | `/api/hello/timeout?name=John&timeoutMs=1000` | With timeout |
| GET | `/api/hello/fallback?name=John&fail=false` | With fallback |
| GET | `/api/hello/race` | First task wins |

## Test with cURL

```bash
# Simple hello
curl http://localhost:8080/api/hello

# Greet with name
curl "http://localhost:8080/api/hello/greet?name=John"

# Parallel execution
curl http://localhost:8080/api/hello/parallel

# With timeout
curl "http://localhost:8080/api/hello/timeout?name=Test&timeoutMs=2000"

# Test fallback (fail=true triggers error)
curl "http://localhost:8080/api/hello/fallback?name=Test&fail=true"
```

## Test with Browser

Just open these URLs in your browser:

- http://localhost:8080/api/hello
- http://localhost:8080/api/hello/greet?name=YourName
- http://localhost:8080/api/hello/parallel

---

# 11. Common Patterns and Best Practices

## When to Use Virtual Threads vs Platform Threads

| Scenario | Use | Why |
|----------|-----|-----|
| HTTP API calls | Virtual | I/O bound, waiting for response |
| Database queries | Virtual | I/O bound, waiting for DB |
| File reading/writing | Virtual | I/O bound |
| Image processing | Platform | CPU intensive |
| Complex calculations | Platform | CPU intensive |
| Encryption | Platform | CPU intensive |

## Best Practice 1: Always Handle Errors

```java
CompletableFuture<String> future = asyncExecutorService.executeAsync(() -> {
    return riskyOperation();
});

future
    .thenAccept(result -> log.info("Success: {}", result))
    .exceptionally(error -> {
        log.error("Failed: {}", error.getMessage());
        return null;
    });
```

## Best Practice 2: Use Timeouts for External Calls

```java
asyncExecutorService.executeWithTimeout(
    () -> externalApiCall(),
    Duration.ofSeconds(10)
);
```

## Best Practice 3: Don't Block the Main Thread

```java
// ❌ BAD - blocks the thread
String result = asyncExecutorService.executeAsync(() -> "data").join();

// ✅ GOOD - returns immediately
return asyncExecutorService.executeAsync(() -> "data");
```

## Best Practice 4: Use Virtual Threads for Many Concurrent I/O Tasks

```java
// Making 100 API calls - virtual threads shine here
List<CompletableFuture<String>> futures = urls.stream()
    .map(url -> asyncExecutorService.executeAsyncVirtual(() -> 
        restTemplate.getForObject(url, String.class)))
    .toList();
```

---

# 12. Troubleshooting

## Issue: "Could not autowire. No beans of type 'Executor' found"

**Solution:** Make sure `AsyncConfig.java` has `@Configuration` and `@EnableAsync`

## Issue: "java.lang.UnsupportedOperationException: Virtual threads not supported"

**Solution:** Make sure you're using Java 21:
```bash
java -version
# Should show: openjdk version "21"
```

## Issue: Tasks not running in parallel

**Solution:** Check if you're calling `.join()` or `.get()` immediately:
```java
// ❌ This runs sequentially (waiting for each result)
String r1 = task1().join();
String r2 = task2().join();

// ✅ This runs in parallel
CompletableFuture<String> f1 = task1();
CompletableFuture<String> f2 = task2();
String r1 = f1.join();
String r2 = f2.join();
```

## Issue: Logs not showing thread names

**Solution:** Check `application.yml` has the pattern:
```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

---

# Quick Reference Card

## Functional Interfaces

| Interface | Signature | Example |
|-----------|-----------|---------|
| `Supplier<T>` | `() -> T` | `() -> "Hello"` |
| `Runnable` | `() -> void` | `() -> log.info("Hi")` |
| `Function<T,R>` | `(T) -> R` | `(s) -> s.toUpperCase()` |
| `Consumer<T>` | `(T) -> void` | `(s) -> log.info(s)` |

## CompletableFuture Methods

| Method | Description |
|--------|-------------|
| `.join()` | Wait and get result (throws unchecked exception) |
| `.get()` | Wait and get result (throws checked exception) |
| `.thenApply(fn)` | Transform result |
| `.thenAccept(fn)` | Consume result |
| `.thenRun(fn)` | Run after completion |
| `.exceptionally(fn)` | Handle errors |
| `.orTimeout(time, unit)` | Fail after timeout |

---

# Summary

## What We Built

1. **AsyncExecutorService** - A reusable service that can run any code asynchronously
2. Support for both **platform threads** (CPU work) and **virtual threads** (I/O work)
3. Methods for **parallel execution**, **chaining**, **timeouts**, **fallbacks**, and **racing**

## Key Takeaways

- Use `@EnableAsync` in your main class
- Use `@Slf4j` for easy logging
- Use virtual threads for I/O operations (API calls, database)
- Use platform threads for CPU operations (calculations)
- Always handle errors with `.exceptionally()` or try-catch
- Use timeouts for external service calls

## Next Steps

1. Copy `AsyncExecutorService.java` to your project
2. Copy `AsyncConfig.java` to configure thread pools
3. Add dependencies to your `pom.xml`
4. Inject and use in your services!

---

*Tutorial created for Java 21 + Spring Boot 3.2*
