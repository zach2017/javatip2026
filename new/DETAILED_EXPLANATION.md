# How the Async Job Tracking System Works

## Complete Step-by-Step Explanation

---

## Table of Contents

1. [Overview - The Big Picture](#1-overview---the-big-picture)
2. [The Problem This Solves](#2-the-problem-this-solves)
3. [How It Works - Flow Diagram](#3-how-it-works---flow-diagram)
4. [Component Breakdown](#4-component-breakdown)
5. [Code Walkthrough - HelloService](#5-code-walkthrough---helloservice)
6. [Adding ChromaDB Service](#6-adding-chromadb-service)
7. [Complete Working Example](#7-complete-working-example)

---

# 1. Overview - The Big Picture

## What This System Does

This is a **"Fire and Track"** async job system that:

1. **Accepts a request** (e.g., "create a database")
2. **Returns immediately** with a Job ID (HTTP 202 Accepted)
3. **Runs the work in the background**
4. **Allows polling** to check job status

## Why This Pattern?

```
Traditional (Blocking):
Client Request → Wait 30 seconds → Response
Problem: Client times out, bad user experience

Fire-and-Track (This Pattern):
Client Request → Immediate Response (Job ID) → Poll for status
Benefit: Fast response, no timeouts, track progress
```

---

# 2. The Problem This Solves

## Scenario: Long-Running API Calls

Imagine you need to:
- Create a ChromaDB database (takes 10+ seconds)
- Process a large file (takes 30+ seconds)
- Call multiple external APIs (takes 15+ seconds)

## The Problem

```
HTTP Request → Long Operation → HTTP Timeout (30s default)
                                    ↓
                              CLIENT ERROR!
```

## The Solution

```
HTTP Request → Return Job ID immediately (202 Accepted)
                    ↓
            Background: Do the work
                    ↓
            Client: Poll /jobs/{id} for status
                    ↓
            When done: Get result from job
```

---

# 3. How It Works - Flow Diagram

## Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT REQUEST                               │
│                    POST /api/chromadb/create                         │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         CONTROLLER                                   │
│  1. Receives request                                                 │
│  2. Calls service.startCreateChromaDbJob()                          │
│  3. Gets back a UUID immediately                                     │
│  4. Returns HTTP 202 with UUID                                       │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         SERVICE                                      │
│  1. Calls jobTracker.submit("chromadb:create", () -> ...)           │
│  2. JobTracker generates UUID                                        │
│  3. JobTracker stores job with status "PENDING"                      │
│  4. JobTracker starts the async work                                 │
│  5. Returns UUID immediately (doesn't wait)                          │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │                       │
                    ▼                       ▼
┌──────────────────────────┐    ┌──────────────────────────────────────┐
│    IMMEDIATE RETURN      │    │        BACKGROUND WORK               │
│                          │    │                                      │
│  Client gets:            │    │  1. AsyncExecutorService runs task   │
│  {                       │    │  2. Calls external API (ChromaDB)    │
│    "jobId": "uuid-123",  │    │  3. Waits for response               │
│    "status": "PENDING"   │    │  4. Updates job status to COMPLETED  │
│  }                       │    │  5. Stores result in job             │
└──────────────────────────┘    └──────────────────────────────────────┘
                                                    │
                                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       CLIENT POLLS                                   │
│                                                                      │
│  GET /api/jobs/{uuid-123}                                           │
│                                                                      │
│  Response (while running):     Response (when done):                │
│  {                             {                                    │
│    "jobId": "uuid-123",          "jobId": "uuid-123",              │
│    "status": "RUNNING",          "status": "COMPLETED",            │
│    "progress": 50                "result": { ... }                 │
│  }                             }                                    │
└─────────────────────────────────────────────────────────────────────┘
```

---

# 4. Component Breakdown

## The Three Key Components

### Component 1: AsyncExecutorService

**Purpose:** Runs code in the background using threads

**Key Methods:**
| Method | What It Does |
|--------|--------------|
| `executeAsyncVirtual()` | Run on virtual thread (good for API calls) |
| `executeAndThen()` | Chain two operations |
| `executeAnyAsync()` | Race multiple tasks, first wins |
| `executeAllAsync()` | Run multiple in parallel |
| `executeWithTimeout()` | Fail if too slow |
| `executeWithFallback()` | Default value on error |

**Example:**
```java
// This returns immediately with a CompletableFuture
CompletableFuture<String> future = asyncExecutorService.executeAsyncVirtual(() -> {
    // This runs in background
    return callExternalApi();
});
```

---

### Component 2: JobTracker

**Purpose:** Tracks jobs, stores status, allows polling

**Key Methods:**
| Method | What It Does |
|--------|--------------|
| `submit(name, futureSupplier)` | Start a job, return UUID |
| `get(jobId)` | Get job status by ID |
| `list(limit)` | List recent jobs |

**How submit() works:**
```java
public UUID submit(String name, Supplier<CompletableFuture<?>> futureSupplier) {
    // 1. Generate unique ID
    UUID jobId = UUID.randomUUID();
    
    // 2. Create job record with PENDING status
    Job job = new Job(jobId, name, Status.PENDING);
    jobs.put(jobId, job);
    
    // 3. Start the async work
    CompletableFuture<?> future = futureSupplier.get();
    
    // 4. When future completes, update job status
    future.whenComplete((result, error) -> {
        if (error != null) {
            job.setStatus(Status.FAILED);
            job.setError(error.getMessage());
        } else {
            job.setStatus(Status.COMPLETED);
            job.setResult(result);
        }
    });
    
    // 5. Return ID immediately (don't wait for future)
    return jobId;
}
```

---

### Component 3: HelloService (Business Logic)

**Purpose:** Contains the actual business logic, uses the other two components

**Pattern:**
```java
public UUID startSomeJob(String input) {
    return jobTracker.submit(
        "job-name:" + input,           // Job name for tracking
        () -> asyncExecutorService.executeAsyncVirtual(() -> {
            // Actual work happens here
            return doSomething(input);
        })
    );
}
```

---

# 5. Code Walkthrough - HelloService

Let me explain each method in detail:

## Method 1: startHelloJob

```java
public UUID startHelloJob(String name) {
    return jobTracker.submit(
        "hello:" + name,                    // ← Job name (for logging/tracking)
        () -> asyncExecutorService.executeAsyncVirtual(() -> {
            sleep(Duration.ofSeconds(2));   // ← Simulates slow work
            return "Hello, " + capitalize(name) + "!";  // ← The result
        })
    );
}
```

**What happens:**
1. `jobTracker.submit()` is called
2. JobTracker creates a new job with status PENDING
3. JobTracker generates a UUID
4. The lambda `() -> asyncExecutorService.executeAsyncVirtual(...)` is executed
5. `executeAsyncVirtual()` starts the work on a virtual thread
6. UUID is returned **immediately** (work continues in background)
7. After 2 seconds, the job completes and status changes to COMPLETED

**Timeline:**
```
T=0ms:   Request received
T=1ms:   Job created, UUID returned to client
T=2000ms: Background work completes
T=2001ms: Job status updated to COMPLETED
```

---

## Method 2: startComposeHelloJob

```java
public UUID startComposeHelloJob(String name) {
    return jobTracker.submit(
        "compose:" + name,
        () -> asyncExecutorService.executeAndThen(
            // Step 1: First operation
            () -> {
                sleep(Duration.ofSeconds(1));
                return "hello " + name;
            },
            // Step 2: Takes result of Step 1, does more
            base -> asyncExecutorService.executeAsyncVirtual(() -> {
                sleep(Duration.ofSeconds(1));
                return base + " (composed)";
            })
        )
    );
}
```

**What happens:**
1. Step 1 runs: returns "hello John" after 1 second
2. Step 2 takes "hello John", adds " (composed)"
3. Final result: "hello John (composed)"

**Timeline:**
```
T=0ms:    Request received, UUID returned
T=1000ms: Step 1 completes with "hello John"
T=2000ms: Step 2 completes with "hello John (composed)"
```

---

## Method 3: startRaceJob

```java
public UUID startRaceJob() {
    return jobTracker.submit(
        "race",
        () -> asyncExecutorService.executeAnyAsync(
            () -> { sleep(Duration.ofSeconds(3)); return "Slow hello"; },
            () -> { sleep(Duration.ofSeconds(1)); return "Fast hello"; },   // ← WINS!
            () -> { sleep(Duration.ofSeconds(2)); return "Medium hello"; }
        )
    );
}
```

**What happens:**
1. All three tasks start simultaneously
2. "Fast hello" finishes first (1 second)
3. Result is "Fast hello" - other tasks are abandoned
4. Useful for: calling multiple servers, use fastest response

**Timeline:**
```
T=0ms:    All 3 tasks start in parallel
T=1000ms: "Fast hello" wins, job completes
          (Other tasks may still be running but are ignored)
```

---

## Methods 4 & 5: Job Queries

```java
// Get a specific job by ID
public Optional<JobSnapshot> getJob(UUID jobId) {
    return jobTracker.get(jobId);
}

// List recent jobs
public List<JobSnapshot> listJobs(int limit) {
    return jobTracker.list(limit);
}
```

**JobSnapshot contains:**
```java
public record JobSnapshot(
    UUID id,
    String name,
    Status status,      // PENDING, RUNNING, COMPLETED, FAILED
    Object result,      // The result when completed
    String error,       // Error message if failed
    Instant createdAt,
    Instant completedAt
) {}
```

---

# 6. Adding ChromaDB Service

Now let's add a real API call to create a ChromaDB collection.

## What is ChromaDB?

ChromaDB is a vector database for AI/ML applications. We'll call its API to:
- Create collections
- Add documents
- Query for similar documents

## The ChromaDB API

**Endpoint:** `POST http://localhost:8000/api/v1/collections`

**Request:**
```json
{
  "name": "my_collection",
  "metadata": {
    "description": "My documents"
  }
}
```

**Response:**
```json
{
  "id": "collection-uuid",
  "name": "my_collection",
  "metadata": { "description": "My documents" }
}
```

---

# 7. Complete Working Example

I'll now create all the files needed:

1. **ChromaDbService.java** - The new service
2. **ChromaDbClient.java** - HTTP client for ChromaDB API
3. **ChromaDbController.java** - REST endpoints
4. **JobTracker.java** - Job tracking implementation
5. **Types** - Request/Response DTOs
