package zac.demo.futureapp.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import zac.demo.futureapp.service.ChromaDbService;
import zac.demo.futureapp.types.ChromaDbTypes.*;
import zac.demo.futureapp.types.JobSnapshot;

/**
 * REST Controller for ChromaDB operations.
 * 
 * API DESIGN:
 * ===========
 * 
 * This controller uses two patterns:
 * 
 * 1. ASYNC (Long operations) → Returns 202 Accepted + Job ID
 *    - POST /api/chromadb/collections (create)
 *    - POST /api/chromadb/collections/{name}/documents (add docs)
 *    - POST /api/chromadb/collections/{name}/query
 * 
 * 2. SYNC (Fast operations) → Returns 200 OK + Data
 *    - GET /api/chromadb/collections (list)
 *    - GET /api/chromadb/collections/{name} (get one)
 *    - GET /api/jobs/{id} (poll job status)
 * 
 * EXAMPLE FLOW:
 * =============
 * 
 * # 1. Create a collection (async)
 * POST /api/chromadb/collections
 * Body: { "name": "my_docs", "metadata": {} }
 * Response (202): { "jobId": "uuid-123", "status": "PENDING", "checkUrl": "/api/jobs/uuid-123" }
 * 
 * # 2. Check job status
 * GET /api/jobs/uuid-123
 * Response (200): { "id": "uuid-123", "status": "RUNNING" }
 * 
 * # 3. Check again when complete
 * GET /api/jobs/uuid-123
 * Response (200): { "id": "uuid-123", "status": "COMPLETED", "result": { "id": "...", "name": "my_docs" } }
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChromaDbController {

    private final ChromaDbService chromaDbService;

    // ========================================
    // JOB ENDPOINTS
    // ========================================

    /**
     * Get job status by ID.
     * 
     * GET /api/jobs/{jobId}
     * 
     * Poll this endpoint to check if an async operation is complete.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobSnapshot> getJob(@PathVariable UUID jobId) {
        log.debug("Getting job: {}", jobId);
        
        return chromaDbService.getJob(jobId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List recent jobs.
     * 
     * GET /api/jobs?limit=10
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<JobSnapshot>> listJobs(
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("Listing jobs, limit: {}", limit);
        
        return ResponseEntity.ok(chromaDbService.listJobs(limit));
    }

    /**
     * List jobs by prefix (e.g., "chromadb:create").
     * 
     * GET /api/jobs/filter?prefix=chromadb:create&limit=10
     */
    @GetMapping("/jobs/filter")
    public ResponseEntity<List<JobSnapshot>> listJobsByPrefix(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("Listing jobs with prefix: {}", prefix);
        
        return ResponseEntity.ok(chromaDbService.listJobsByPrefix(prefix, limit));
    }

    // ========================================
    // COLLECTION ENDPOINTS
    // ========================================

    /**
     * Create a new collection (async).
     * 
     * POST /api/chromadb/collections
     * Body: { "name": "my_collection", "metadata": { "description": "..." } }
     * 
     * Returns 202 Accepted with job ID.
     */
    @PostMapping("/chromadb/collections")
    public ResponseEntity<JobResponse> createCollection(@RequestBody CreateCollectionRequest request) {
        log.info("Creating collection: {}", request.name());
        
        UUID jobId = chromaDbService.startCreateCollection(request.name(), request.metadata());
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new JobResponse(jobId, "PENDING", "/api/jobs/" + jobId));
    }

    /**
     * List all collections (sync).
     * 
     * GET /api/chromadb/collections
     */
    @GetMapping("/chromadb/collections")
    public ResponseEntity<List<CollectionResponse>> listCollections() {
        log.debug("Listing collections");
        
        return ResponseEntity.ok(chromaDbService.listCollections());
    }

    /**
     * Get a collection by name (sync).
     * 
     * GET /api/chromadb/collections/{name}
     */
    @GetMapping("/chromadb/collections/{name}")
    public ResponseEntity<CollectionResponse> getCollection(@PathVariable String name) {
        log.debug("Getting collection: {}", name);
        
        try {
            return ResponseEntity.ok(chromaDbService.getCollection(name));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete a collection (async).
     * 
     * DELETE /api/chromadb/collections/{name}
     */
    @DeleteMapping("/chromadb/collections/{name}")
    public ResponseEntity<JobResponse> deleteCollection(@PathVariable String name) {
        log.info("Deleting collection: {}", name);
        
        UUID jobId = chromaDbService.startDeleteCollection(name);
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new JobResponse(jobId, "PENDING", "/api/jobs/" + jobId));
    }

    // ========================================
    // DOCUMENT ENDPOINTS
    // ========================================

    /**
     * Add documents to a collection (async).
     * 
     * POST /api/chromadb/collections/{name}/documents
     * Body: {
     *   "ids": ["doc1", "doc2"],
     *   "documents": ["Hello world", "Goodbye world"],
     *   "metadatas": [{"source": "file1"}, {"source": "file2"}]
     * }
     */
    @PostMapping("/chromadb/collections/{name}/documents")
    public ResponseEntity<JobResponse> addDocuments(
            @PathVariable String name,
            @RequestBody AddDocumentsRequest request) {
        log.info("Adding {} documents to collection: {}", request.ids().size(), name);
        
        UUID jobId = chromaDbService.startAddDocuments(
            name,
            request.ids(),
            request.documents(),
            request.metadatas()
        );
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new JobResponse(jobId, "PENDING", "/api/jobs/" + jobId));
    }

    /**
     * Add a single document (convenience endpoint).
     * 
     * POST /api/chromadb/collections/{name}/documents/single
     * Body: { "id": "doc1", "document": "Hello world", "metadata": {} }
     */
    @PostMapping("/chromadb/collections/{name}/documents/single")
    public ResponseEntity<JobResponse> addSingleDocument(
            @PathVariable String name,
            @RequestBody SingleDocumentRequest request) {
        log.info("Adding single document to collection: {}", name);
        
        UUID jobId = chromaDbService.startAddDocument(
            name,
            request.id(),
            request.document(),
            request.metadata()
        );
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new JobResponse(jobId, "PENDING", "/api/jobs/" + jobId));
    }

    /**
     * Delete documents from a collection (async).
     * 
     * DELETE /api/chromadb/collections/{name}/documents
     * Body: { "ids": ["doc1", "doc2"] }
     */
    @DeleteMapping("/chromadb/collections/{name}/documents")
    public ResponseEntity<JobResponse> deleteDocuments(
            @PathVariable String name,
            @RequestBody DeleteDocumentsRequest request) {
        log.info("Deleting {} documents from collection: {}", request.ids().size(), name);
        
        UUID jobId = chromaDbService.startDeleteDocuments(name, request.ids());
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new JobResponse(jobId, "PENDING", "/api/jobs/" + jobId));
    }

    // ========================================
    // QUERY ENDPOINTS
    // ========================================

    /**
     * Query for similar documents (async).
     * 
     * POST /api/chromadb/collections/{name}/query
     * Body: { "queryText": "What is AI?", "nResults": 5 }
     */
    @PostMapping("/chromadb/collections/{name}/query")
    public ResponseEntity<JobResponse> query(
            @PathVariable String name,
            @RequestBody SimpleQueryRequest request) {
        log.info("Querying collection {} for: '{}'", name, request.queryText());
        
        UUID jobId = chromaDbService.startQuery(
            name,
            request.queryText(),
            request.nResults() != null ? request.nResults() : 5
        );
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new JobResponse(jobId, "PENDING", "/api/jobs/" + jobId));
    }

    /**
     * Advanced query with filters (async).
     * 
     * POST /api/chromadb/collections/{name}/query/advanced
     */
    @PostMapping("/chromadb/collections/{name}/query/advanced")
    public ResponseEntity<JobResponse> advancedQuery(
            @PathVariable String name,
            @RequestBody AdvancedQueryRequest request) {
        log.info("Advanced query in collection: {}", name);
        
        UUID jobId = chromaDbService.startQueryAdvanced(
            name,
            request.queryTexts(),
            request.nResults() != null ? request.nResults() : 5,
            request.where()
        );
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new JobResponse(jobId, "PENDING", "/api/jobs/" + jobId));
    }

    // ========================================
    // COMPOSITE ENDPOINTS
    // ========================================

    /**
     * Create collection with initial documents (async).
     * 
     * POST /api/chromadb/collections/with-documents
     */
    @PostMapping("/chromadb/collections/with-documents")
    public ResponseEntity<JobResponse> createWithDocuments(
            @RequestBody CreateWithDocumentsRequest request) {
        log.info("Creating collection {} with {} documents", request.name(), request.docIds().size());
        
        UUID jobId = chromaDbService.startCreateCollectionWithDocuments(
            request.name(),
            request.collectionMetadata(),
            request.docIds(),
            request.documents(),
            request.docMetadatas()
        );
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new JobResponse(jobId, "PENDING", "/api/jobs/" + jobId));
    }

    // ========================================
    // REQUEST/RESPONSE DTOs
    // ========================================

    public record JobResponse(
        UUID jobId,
        String status,
        String checkUrl
    ) {}

    public record SingleDocumentRequest(
        String id,
        String document,
        Map<String, Object> metadata
    ) {}

    public record SimpleQueryRequest(
        String queryText,
        Integer nResults
    ) {}

    public record AdvancedQueryRequest(
        List<String> queryTexts,
        Integer nResults,
        Map<String, Object> where
    ) {}

    public record CreateWithDocumentsRequest(
        String name,
        Map<String, Object> collectionMetadata,
        List<String> docIds,
        List<String> documents,
        List<Map<String, Object>> docMetadatas
    ) {}
}
