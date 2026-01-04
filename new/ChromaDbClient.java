package zac.demo.futureapp.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import zac.demo.futureapp.types.ChromaDbTypes.*;

/**
 * HTTP client for ChromaDB API.
 * 
 * HOW THIS WORKS:
 * ===============
 * 
 * This client uses Java's HttpClient to make REST calls to ChromaDB.
 * 
 * ChromaDB runs on http://localhost:8000 by default.
 * You can change this via application.yml:
 * 
 *   chromadb:
 *     url: http://localhost:8000
 *     timeout: 30s
 * 
 * AVAILABLE METHODS:
 * ==================
 * 
 * - createCollection(name, metadata) → CollectionResponse
 * - getCollection(name) → CollectionResponse
 * - deleteCollection(name) → boolean
 * - listCollections() → List<CollectionResponse>
 * - addDocuments(collection, request) → AddDocumentsResponse
 * - query(collection, request) → QueryResponse
 * - deleteDocuments(collection, request) → DeleteResponse
 * 
 * EXAMPLE USAGE:
 * ==============
 * 
 * // Create a collection
 * CollectionResponse coll = chromaDbClient.createCollection("my_docs", Map.of());
 * 
 * // Add documents
 * chromaDbClient.addDocuments("my_docs", AddDocumentsRequest.single("doc1", "Hello world"));
 * 
 * // Query
 * QueryResponse results = chromaDbClient.query("my_docs", new QueryRequest("hello", 5));
 */
@Slf4j
@Component
public class ChromaDbClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;

    public ChromaDbClient(
            ObjectMapper objectMapper,
            @Value("${chromadb.url:http://localhost:8000}") String baseUrl,
            @Value("${chromadb.timeout:30s}") Duration timeout) {
        
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        
        // Create HTTP client with virtual thread executor (Java 21)
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        
        log.info("ChromaDB client initialized: url={}, timeout={}", baseUrl, timeout);
    }

    // ========================================
    // Collection Operations
    // ========================================

    /**
     * Create a new collection.
     * 
     * @param name     Collection name (must be unique)
     * @param metadata Optional metadata
     * @return The created collection
     */
    public CollectionResponse createCollection(String name, java.util.Map<String, Object> metadata) {
        log.info("Creating collection: {}", name);
        
        var request = new CreateCollectionRequest(name, metadata != null ? metadata : java.util.Map.of());
        
        return post("/api/v1/collections", request, CollectionResponse.class);
    }

    /**
     * Get a collection by name.
     * 
     * @param name Collection name
     * @return The collection, or throws if not found
     */
    public CollectionResponse getCollection(String name) {
        log.info("Getting collection: {}", name);
        
        return get("/api/v1/collections/" + name, CollectionResponse.class);
    }

    /**
     * Delete a collection.
     * 
     * @param name Collection name
     * @return true if deleted
     */
    public boolean deleteCollection(String name) {
        log.info("Deleting collection: {}", name);
        
        delete("/api/v1/collections/" + name);
        return true;
    }

    /**
     * List all collections.
     * 
     * @return List of collections
     */
    public List<CollectionResponse> listCollections() {
        log.info("Listing collections");
        
        // ChromaDB returns array directly
        CollectionResponse[] collections = get("/api/v1/collections", CollectionResponse[].class);
        return List.of(collections);
    }

    // ========================================
    // Document Operations
    // ========================================

    /**
     * Add documents to a collection.
     * 
     * @param collectionName Collection to add to
     * @param request        Documents to add
     * @return Add result
     */
    public AddDocumentsResponse addDocuments(String collectionName, AddDocumentsRequest request) {
        log.info("Adding {} documents to collection: {}", request.ids().size(), collectionName);
        
        // First get the collection to get its ID
        CollectionResponse collection = getCollection(collectionName);
        
        // ChromaDB uses collection ID in the path
        post("/api/v1/collections/" + collection.id() + "/add", request, Void.class);
        
        return new AddDocumentsResponse(true, request.ids().size());
    }

    /**
     * Query a collection for similar documents.
     * 
     * @param collectionName Collection to query
     * @param request        Query parameters
     * @return Query results
     */
    public QueryResponse query(String collectionName, QueryRequest request) {
        log.info("Querying collection: {} for {} results", collectionName, request.nResults());
        
        CollectionResponse collection = getCollection(collectionName);
        
        return post("/api/v1/collections/" + collection.id() + "/query", request, QueryResponse.class);
    }

    /**
     * Delete documents from a collection.
     * 
     * @param collectionName Collection name
     * @param request        Documents to delete
     * @return Delete result
     */
    public DeleteResponse deleteDocuments(String collectionName, DeleteDocumentsRequest request) {
        log.info("Deleting {} documents from collection: {}", request.ids().size(), collectionName);
        
        CollectionResponse collection = getCollection(collectionName);
        
        post("/api/v1/collections/" + collection.id() + "/delete", request, Void.class);
        
        return new DeleteResponse(true, request.ids().size());
    }

    // ========================================
    // HTTP Helpers
    // ========================================

    private <T> T get(String path, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            return handleResponse(response, responseType);
            
        } catch (Exception e) {
            log.error("GET {} failed: {}", path, e.getMessage());
            throw new ChromaDbException("GET " + path + " failed: " + e.getMessage(), e);
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("POST {} body: {}", path, jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            return handleResponse(response, responseType);
            
        } catch (Exception e) {
            log.error("POST {} failed: {}", path, e.getMessage());
            throw new ChromaDbException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    private void delete(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new ChromaDbException("DELETE failed with status " + response.statusCode());
            }
            
        } catch (ChromaDbException e) {
            throw e;
        } catch (Exception e) {
            log.error("DELETE {} failed: {}", path, e.getMessage());
            throw new ChromaDbException("DELETE " + path + " failed: " + e.getMessage(), e);
        }
    }

    private <T> T handleResponse(HttpResponse<String> response, Class<T> responseType) {
        int status = response.statusCode();
        String body = response.body();
        
        log.debug("Response status: {}, body: {}", status, body);

        if (status >= 400) {
            String errorMsg = "ChromaDB error (status " + status + "): " + body;
            log.error(errorMsg);
            throw new ChromaDbException(errorMsg);
        }

        if (responseType == Void.class || body == null || body.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(body, responseType);
        } catch (Exception e) {
            log.error("Failed to parse response: {}", e.getMessage());
            throw new ChromaDbException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    // ========================================
    // Exception
    // ========================================

    public static class ChromaDbException extends RuntimeException {
        public ChromaDbException(String message) {
            super(message);
        }

        public ChromaDbException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
