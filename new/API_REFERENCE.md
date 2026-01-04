# ChromaDB API Reference

## Quick Start

### 1. Start ChromaDB

```bash
# Run ChromaDB in Docker
docker run -p 8000:8000 chromadb/chroma
```

### 2. Start Your App

```bash
mvn spring-boot:run
```

### 3. Create a Collection

```bash
# Create collection (returns job ID)
curl -X POST http://localhost:8080/api/chromadb/collections \
  -H "Content-Type: application/json" \
  -d '{"name": "my_docs", "metadata": {"description": "My documents"}}'

# Response:
# {
#   "jobId": "550e8400-e29b-41d4-a716-446655440000",
#   "status": "PENDING",
#   "checkUrl": "/api/jobs/550e8400-e29b-41d4-a716-446655440000"
# }
```

### 4. Check Job Status

```bash
curl http://localhost:8080/api/jobs/550e8400-e29b-41d4-a716-446655440000

# Response (when complete):
# {
#   "id": "550e8400-e29b-41d4-a716-446655440000",
#   "name": "chromadb:create:my_docs",
#   "status": "COMPLETED",
#   "result": {
#     "id": "collection-uuid",
#     "name": "my_docs",
#     "metadata": {"description": "My documents"}
#   }
# }
```

---

## Complete API Reference

### Job Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/jobs/{jobId}` | Get job status by ID |
| GET | `/api/jobs?limit=20` | List recent jobs |
| GET | `/api/jobs/filter?prefix=chromadb:create` | List jobs by prefix |

### Collection Endpoints

| Method | Endpoint | Async? | Description |
|--------|----------|--------|-------------|
| POST | `/api/chromadb/collections` | ✅ Yes | Create collection |
| GET | `/api/chromadb/collections` | ❌ No | List all collections |
| GET | `/api/chromadb/collections/{name}` | ❌ No | Get collection by name |
| DELETE | `/api/chromadb/collections/{name}` | ✅ Yes | Delete collection |

### Document Endpoints

| Method | Endpoint | Async? | Description |
|--------|----------|--------|-------------|
| POST | `/api/chromadb/collections/{name}/documents` | ✅ Yes | Add multiple documents |
| POST | `/api/chromadb/collections/{name}/documents/single` | ✅ Yes | Add single document |
| DELETE | `/api/chromadb/collections/{name}/documents` | ✅ Yes | Delete documents |

### Query Endpoints

| Method | Endpoint | Async? | Description |
|--------|----------|--------|-------------|
| POST | `/api/chromadb/collections/{name}/query` | ✅ Yes | Simple query |
| POST | `/api/chromadb/collections/{name}/query/advanced` | ✅ Yes | Query with filters |

### Composite Endpoints

| Method | Endpoint | Async? | Description |
|--------|----------|--------|-------------|
| POST | `/api/chromadb/collections/with-documents` | ✅ Yes | Create collection + add docs |

---

## Request/Response Examples

### Create Collection

**Request:**
```bash
POST /api/chromadb/collections
Content-Type: application/json

{
  "name": "my_documents",
  "metadata": {
    "description": "My document embeddings",
    "model": "text-embedding-ada-002"
  }
}
```

**Response (202 Accepted):**
```json
{
  "jobId": "uuid-123",
  "status": "PENDING",
  "checkUrl": "/api/jobs/uuid-123"
}
```

---

### Add Documents

**Request:**
```bash
POST /api/chromadb/collections/my_documents/documents
Content-Type: application/json

{
  "ids": ["doc1", "doc2", "doc3"],
  "documents": [
    "Machine learning is a subset of artificial intelligence.",
    "Deep learning uses neural networks with many layers.",
    "Natural language processing handles human language."
  ],
  "metadatas": [
    {"source": "wikipedia", "topic": "ml"},
    {"source": "wikipedia", "topic": "dl"},
    {"source": "wikipedia", "topic": "nlp"}
  ]
}
```

**Response (202 Accepted):**
```json
{
  "jobId": "uuid-456",
  "status": "PENDING",
  "checkUrl": "/api/jobs/uuid-456"
}
```

---

### Add Single Document

**Request:**
```bash
POST /api/chromadb/collections/my_documents/documents/single
Content-Type: application/json

{
  "id": "doc4",
  "document": "Reinforcement learning learns from rewards and punishments.",
  "metadata": {"source": "textbook", "topic": "rl"}
}
```

---

### Query Similar Documents

**Request:**
```bash
POST /api/chromadb/collections/my_documents/query
Content-Type: application/json

{
  "queryText": "What is artificial intelligence?",
  "nResults": 5
}
```

**Job Result (when complete):**
```json
{
  "id": "uuid-789",
  "status": "COMPLETED",
  "result": {
    "ids": [["doc1", "doc2"]],
    "documents": [["Machine learning is...", "Deep learning uses..."]],
    "distances": [[0.15, 0.28]]
  }
}
```

---

### Advanced Query with Filters

**Request:**
```bash
POST /api/chromadb/collections/my_documents/query/advanced
Content-Type: application/json

{
  "queryTexts": ["What is machine learning?"],
  "nResults": 3,
  "where": {
    "source": "wikipedia"
  }
}
```

---

### Create Collection with Documents

**Request:**
```bash
POST /api/chromadb/collections/with-documents
Content-Type: application/json

{
  "name": "quick_start_collection",
  "collectionMetadata": {
    "description": "Quick start demo"
  },
  "docIds": ["intro1", "intro2"],
  "documents": [
    "Welcome to ChromaDB!",
    "This is a vector database."
  ],
  "docMetadatas": [
    {"chapter": "intro"},
    {"chapter": "intro"}
  ]
}
```

---

### Check Job Status

**Request:**
```bash
GET /api/jobs/uuid-123
```

**Response (Running):**
```json
{
  "id": "uuid-123",
  "name": "chromadb:create:my_documents",
  "status": "RUNNING",
  "result": null,
  "error": null,
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": null
}
```

**Response (Completed):**
```json
{
  "id": "uuid-123",
  "name": "chromadb:create:my_documents",
  "status": "COMPLETED",
  "result": {
    "id": "collection-uuid",
    "name": "my_documents",
    "metadata": {"description": "My document embeddings"}
  },
  "error": null,
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": "2024-01-15T10:30:02Z"
}
```

**Response (Failed):**
```json
{
  "id": "uuid-123",
  "name": "chromadb:create:my_documents",
  "status": "FAILED",
  "result": null,
  "error": "Collection already exists",
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": "2024-01-15T10:30:01Z"
}
```

---

### List Jobs

**Request:**
```bash
GET /api/jobs?limit=10
```

**Response:**
```json
[
  {
    "id": "uuid-123",
    "name": "chromadb:create:my_documents",
    "status": "COMPLETED",
    ...
  },
  {
    "id": "uuid-456",
    "name": "chromadb:add:my_documents:3",
    "status": "RUNNING",
    ...
  }
]
```

---

## Job Status Values

| Status | Meaning |
|--------|---------|
| `PENDING` | Job created, work not started |
| `RUNNING` | Work in progress |
| `COMPLETED` | Success, result available |
| `FAILED` | Error, see error field |

---

## Polling Best Practices

```javascript
// JavaScript example
async function pollJob(jobId, maxAttempts = 30, intervalMs = 1000) {
  for (let i = 0; i < maxAttempts; i++) {
    const response = await fetch(`/api/jobs/${jobId}`);
    const job = await response.json();
    
    if (job.status === 'COMPLETED') {
      return job.result;
    }
    
    if (job.status === 'FAILED') {
      throw new Error(job.error);
    }
    
    // Still running, wait and try again
    await new Promise(r => setTimeout(r, intervalMs));
  }
  
  throw new Error('Job timed out');
}

// Usage
const jobId = await createCollection('my_docs');
const result = await pollJob(jobId);
console.log('Collection created:', result);
```

---

## Error Handling

All endpoints return standard HTTP status codes:

| Code | Meaning |
|------|---------|
| 200 | Success (sync operations) |
| 202 | Accepted (async operations) |
| 400 | Bad request |
| 404 | Not found |
| 500 | Server error |

For async operations, check the job status for detailed errors:

```json
{
  "status": "FAILED",
  "error": "ChromaDB error (status 400): Collection 'my_docs' already exists"
}
```
