package zac.demo.futureapp.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import zac.demo.futureapp.client.ChromaDbClient;
import zac.demo.futureapp.types.ChromaDbTypes.*;
import zac.demo.futureapp.types.JobSnapshot;

/**
 * ChromaDbService - Async operations for ChromaDB.
 * 
 * HOW THIS WORKS:
 * ===============
 * 
 * This service wraps ChromaDB operations with the "fire-and-track" pattern:
 * 
 * 1. Client calls startCreateCollection("my_collection")
 * 2. Service returns a UUID immediately (no waiting)
 * 3. The actual ChromaDB API call runs in the background
 * 4. Client polls getJob(uuid) to check status
 * 5. When complete, result contains the created collection
 * 
 * WHY THIS PATTERN:
 * =================
 * 
 * - ChromaDB operations can be slow (especially with large embeddings)
 * - HTTP requests have timeouts (30s default)
 * - Users get immediate feedback (job ID)
 * - Progress can be tracked
 * - Errors are captured and stored
 * 
 * EXAMPLE FLOW:
 * =============
 * 
 * // 1. Start the job
 * POST /api/chromadb/collections
 * Body: { "name": "my_docs" }
 * Response (202): { "jobId": "uuid-123", "status": "PENDING" }
 * 
 * // 2. Poll for status
 * GET /api/jobs/uuid-123
 * Response: { "status": "RUNNING" }
 * 
 * // 3. Poll again
 * GET /api/jobs/uuid-123
 * Response: { 
 *   "status": "COMPLETED",
 *   "result": { "id": "coll-id", "name": "my_docs" }
 * }
 * 
 * AVAILABLE METHODS:
 * ==================
 * 
 * Job Starters (return UUID immediately):
 * - startCreateCollection(name, metadata) → UUID
 * - startAddDocuments(collection, documents) → UUID
 * - startQuery(collection, queryText, nResults) → UUID
 * - startDeleteCollection(name) → UUID
 * 
 * Sync Operations (for simple/fast operations):
 * - listCollections() → List<CollectionResponse>
 * - getCollection(name) → CollectionResponse
 * 
 * Job Queries:
 * - getJob(jobId) → Optional<JobSnapshot>
 * - listJobs(limit) → List<JobSnapshot>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChromaDbService {

    private final AsyncExecutorService asyncExecutorService;
    private final JobTracker jobTracker;
    private final ChromaDbClient chromaDbClient;

    // ========================================
    // COLLECTION OPERATIONS
    // ========================================

    /**
     * Start a job to create a new ChromaDB collection.
     * 
     * HOW IT WORKS:
     * 1. This method returns a UUID immediately
     * 2. The actual API call runs in the background on a virtual thread
     * 3. Client polls getJob(uuid) to check status
     * 
     * @param name     Collection name (must be unique in ChromaDB)
     * @param metadata Optional metadata for the collection
     * @return Job ID (UUID) - poll getJob(id) for status
     * 
     * EXAMPLE:
     * --------
     * UUID jobId = chromaDbService.startCreateCollection("my_docs", Map.of(
     *     "description", "My document embeddings",
     *     "model", "text-embedding-ada-002"
     * ));
     * 
     * // Later...
     * JobSnapshot job = chromaDbService.getJob(jobId).orElseThrow();
     * if (job.isSuccess()) {
     *     CollectionResponse result = (CollectionResponse) job.result();
     *     log.info("Created collection: {}", result.id());
     * }
     */
    public UUID startCreateCollection(String name, Map<String, Object> metadata) {
        log.info("Starting create collection job: {}", name);
        
        return jobTracker.submit(
            "chromadb:create:" + name,  // Job name for tracking
            () -> asyncExecutorService.executeAsyncVirtual(() -> {
                // This runs in background on a virtual thread
                log.info("Creating ChromaDB collection: {}", name);
                CollectionResponse result = chromaDbClient.createCollection(name, metadata);
                log.info("Created collection: {} with id: {}", name, result.id());
                return result;
            })
        );
    }

    /**
     * Start a job to delete a collection.
     * 
     * @param name Collection name to delete
     * @return Job ID
     */
    public UUID startDeleteCollection(String name) {
        log.info("Starting delete collection job: {}", name);
        
        return jobTracker.submit(
            "chromadb:delete:" + name,
            () -> asyncExecutorService.executeAsyncVirtual(() -> {
                log.info("Deleting ChromaDB collection: {}", name);
                boolean result = chromaDbClient.deleteCollection(name);
                log.info("Deleted collection: {}", name);
                return result;
            })
        );
    }

    /**
     * List all collections (sync - usually fast).
     * 
     * This is synchronous because listing is typically fast.
     * If you need async, wrap in startXxx pattern.
     */
    public List<CollectionResponse> listCollections() {
        return chromaDbClient.listCollections();
    }

    /**
     * Get a collection by name (sync).
     */
    public CollectionResponse getCollection(String name) {
        return chromaDbClient.getCollection(name);
    }

    // ========================================
    // DOCUMENT OPERATIONS
    // ========================================

    /**
     * Start a job to add documents to a collection.
     * 
     * HOW IT WORKS:
     * 1. Returns immediately with a job ID
     * 2. Documents are added in the background
     * 3. ChromaDB generates embeddings automatically
     * 
     * @param collectionName Target collection
     * @param ids            Document IDs (must be unique)
     * @param documents      Document texts
     * @param metadatas      Optional metadata for each document
     * @return Job ID
     * 
     * EXAMPLE:
     * --------
     * UUID jobId = chromaDbService.startAddDocuments(
     *     "my_docs",
     *     List.of("doc1", "doc2", "doc3"),
     *     List.of(
     *         "Machine learning is a subset of AI",
     *         "Deep learning uses neural networks",
     *         "Natural language processing handles text"
     *     ),
     *     List.of(
     *         Map.of("source", "wiki", "topic", "ml"),
     *         Map.of("source", "wiki", "topic", "dl"),
     *         Map.of("source", "wiki", "topic", "nlp")
     *     )
     * );
     */
    public UUID startAddDocuments(
            String collectionName,
            List<String> ids,
            List<String> documents,
            List<Map<String, Object>> metadatas) {
        
        log.info("Starting add documents job: {} docs to {}", ids.size(), collectionName);
        
        return jobTracker.submit(
            "chromadb:add:" + collectionName + ":" + ids.size(),
            () -> asyncExecutorService.executeAsyncVirtual(() -> {
                log.info("Adding {} documents to collection: {}", ids.size(), collectionName);
                
                var request = new AddDocumentsRequest(ids, documents, metadatas);
                AddDocumentsResponse result = chromaDbClient.addDocuments(collectionName, request);
                
                log.info("Added {} documents to collection: {}", result.count(), collectionName);
                return result;
            })
        );
    }

    /**
     * Start a job to add a single document.
     * 
     * Convenience method for adding one document.
     */
    public UUID startAddDocument(String collectionName, String id, String document, Map<String, Object> metadata) {
        return startAddDocuments(
            collectionName,
            List.of(id),
            List.of(document),
            List.of(metadata != null ? metadata : Map.of())
        );
    }

    // ========================================
    // QUERY OPERATIONS
    // ========================================

    /**
     * Start a job to query similar documents.
     * 
     * HOW IT WORKS:
     * 1. Returns job ID immediately
     * 2. Query runs in background
     * 3. ChromaDB finds most similar documents using embeddings
     * 
     * @param collectionName Collection to search
     * @param queryText      Text to find similar documents for
     * @param nResults       Number of results to return
     * @return Job ID
     * 
     * EXAMPLE:
     * --------
     * UUID jobId = chromaDbService.startQuery(
     *     "my_docs",
     *     "What is artificial intelligence?",
     *     5
     * );
     * 
     * // After job completes:
     * QueryResponse result = (QueryResponse) job.result();
     * for (QueryResult doc : result.getResults()) {
     *     log.info("Found: {} (distance: {})", doc.document(), doc.distance());
     * }
     */
    public UUID startQuery(String collectionName, String queryText, int nResults) {
        log.info("Starting query job: '{}' in {}", queryText, collectionName);
        
        return jobTracker.submit(
            "chromadb:query:" + collectionName,
            () -> asyncExecutorService.executeAsyncVirtual(() -> {
                log.info("Querying collection {} for: '{}'", collectionName, queryText);
                
                var request = new QueryRequest(queryText, nResults);
                QueryResponse result = chromaDbClient.query(collectionName, request);
                
                log.info("Query returned {} results", result.getResults().size());
                return result;
            })
        );
    }

    /**
     * Start a job with advanced query options.
     */
    public UUID startQueryAdvanced(
            String collectionName,
            List<String> queryTexts,
            int nResults,
            Map<String, Object> whereFilter) {
        
        log.info("Starting advanced query job in {}", collectionName);
        
        return jobTracker.submit(
            "chromadb:query:" + collectionName,
            () -> asyncExecutorService.executeAsyncVirtual(() -> {
                var request = new QueryRequest(
                    queryTexts,
                    nResults,
                    whereFilter,
                    List.of("documents", "metadatas", "distances")
                );
                return chromaDbClient.query(collectionName, request);
            })
        );
    }

    // ========================================
    // DELETE DOCUMENT OPERATIONS
    // ========================================

    /**
     * Start a job to delete documents by ID.
     */
    public UUID startDeleteDocuments(String collectionName, List<String> ids) {
        log.info("Starting delete documents job: {} docs from {}", ids.size(), collectionName);
        
        return jobTracker.submit(
            "chromadb:delete-docs:" + collectionName,
            () -> asyncExecutorService.executeAsyncVirtual(() -> {
                var request = new DeleteDocumentsRequest(ids);
                return chromaDbClient.deleteDocuments(collectionName, request);
            })
        );
    }

    // ========================================
    // COMPOSITE OPERATIONS
    // ========================================

    /**
     * Start a job that creates a collection AND adds initial documents.
     * 
     * This chains two operations:
     * 1. Create the collection
     * 2. Add the documents
     * 
     * Uses executeAndThen for chaining.
     */
    public UUID startCreateCollectionWithDocuments(
            String name,
            Map<String, Object> collectionMetadata,
            List<String> docIds,
            List<String> documents,
            List<Map<String, Object>> docMetadatas) {
        
        log.info("Starting create collection with {} documents: {}", docIds.size(), name);
        
        return jobTracker.submit(
            "chromadb:create-with-docs:" + name,
            () -> asyncExecutorService.executeAndThen(
                // Step 1: Create collection
                () -> {
                    log.info("Step 1: Creating collection {}", name);
                    return chromaDbClient.createCollection(name, collectionMetadata);
                },
                // Step 2: Add documents (runs after Step 1 completes)
                collection -> asyncExecutorService.executeAsyncVirtual(() -> {
                    log.info("Step 2: Adding {} documents to {}", docIds.size(), name);
                    var request = new AddDocumentsRequest(docIds, documents, docMetadatas);
                    chromaDbClient.addDocuments(name, request);
                    
                    // Return a combined result
                    return Map.of(
                        "collection", collection,
                        "documentsAdded", docIds.size()
                    );
                })
            )
        );
    }

    // ========================================
    // JOB QUERIES
    // ========================================

    /**
     * Get job status by ID.
     */
    public Optional<JobSnapshot> getJob(UUID jobId) {
        return jobTracker.get(jobId);
    }

    /**
     * List recent jobs.
     */
    public List<JobSnapshot> listJobs(int limit) {
        return jobTracker.list(limit);
    }

    /**
     * List jobs filtered by name prefix.
     */
    public List<JobSnapshot> listJobsByPrefix(String prefix, int limit) {
        return jobTracker.list(limit).stream()
            .filter(job -> job.name().startsWith(prefix))
            .toList();
    }
}
