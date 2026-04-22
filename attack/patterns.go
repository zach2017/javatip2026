package main

import "regexp"

// ---------------------------------------------------------------------------
// Structural regexes: package, class, class-level annotations
// ---------------------------------------------------------------------------

var (
	rePackage = regexp.MustCompile(`(?m)^\s*package\s+([\w.]+)\s*;`)

	// Captures the first top-level class name declared in the file.
	reClass = regexp.MustCompile(
		`(?m)^\s*(?:public\s+|protected\s+|private\s+)?` +
			`(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+|static\s+)*` +
			`(?:class|interface|enum|record)\s+(\w+)`)

	// Class-level @RequestMapping value (handles value=, path=, or bare string).
	reClassRequestMapping = regexp.MustCompile(
		`@RequestMapping\s*\(\s*(?:\{?\s*)?(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"`)

	// A generic "method signature" detector. This is deliberately permissive.
	// It matches lines like:
	//   public ResponseEntity<User> getUser(@PathVariable Long id) {
	//   private static <T> List<T> parse(String in) throws IOException {
	reMethodSig = regexp.MustCompile(
		`(?m)^\s*(?:@\w+[^\n]*\s+)*` +
			`(?:public|protected|private)\s+` +
			`(?:static\s+|final\s+|synchronized\s+|abstract\s+|default\s+|native\s+)*` +
			`(?:<[^>]+>\s+)?` +
			`[\w\.$<>,\[\]\s\?&]+?\s+` +
			`(\w+)\s*\(`)
)

// ---------------------------------------------------------------------------
// Method-level annotation patterns.
// When one of these is found, the scanner walks forward to find the next
// method signature and attaches the finding to that method.
// ---------------------------------------------------------------------------

type annoPattern struct {
	name          string         // annotation short name (GetMapping, etc.)
	category      string         // Finding.Category
	httpMethod    string         // HTTP verb, if applicable
	severity      string         // default severity for this category
	description   string         // human readable description
	attackVectors []string       // default OWASP-style vectors
	pathExtract   *regexp.Regexp // optional: captures the URI/topic/destination
}

// annoPatterns is searched in order. The match regex below (reAnyAnnotation)
// pulls out the annotation name; then we look up metadata here.
var annoPatterns = map[string]annoPattern{
	"GetMapping": {
		name:          "GetMapping",
		category:      "http_endpoint",
		httpMethod:    "GET",
		severity:      "high",
		description:   "HTTP GET endpoint, reachable over the network",
		attackVectors: []string{"injection", "broken_access_control", "idor", "information_disclosure"},
		pathExtract:   regexp.MustCompile(`@GetMapping\s*\(\s*(?:\{\s*)?(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"`),
	},
	"PostMapping": {
		name:          "PostMapping",
		category:      "http_endpoint",
		httpMethod:    "POST",
		severity:      "high",
		description:   "HTTP POST endpoint that accepts request bodies",
		attackVectors: []string{"injection", "csrf", "mass_assignment", "xxe", "deserialization"},
		pathExtract:   regexp.MustCompile(`@PostMapping\s*\(\s*(?:\{\s*)?(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"`),
	},
	"PutMapping": {
		name:          "PutMapping",
		category:      "http_endpoint",
		httpMethod:    "PUT",
		severity:      "high",
		description:   "HTTP PUT endpoint that mutates server state",
		attackVectors: []string{"injection", "broken_access_control", "mass_assignment"},
		pathExtract:   regexp.MustCompile(`@PutMapping\s*\(\s*(?:\{\s*)?(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"`),
	},
	"DeleteMapping": {
		name:          "DeleteMapping",
		category:      "http_endpoint",
		httpMethod:    "DELETE",
		severity:      "high",
		description:   "HTTP DELETE endpoint that destroys resources",
		attackVectors: []string{"broken_access_control", "idor", "csrf"},
		pathExtract:   regexp.MustCompile(`@DeleteMapping\s*\(\s*(?:\{\s*)?(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"`),
	},
	"PatchMapping": {
		name:          "PatchMapping",
		category:      "http_endpoint",
		httpMethod:    "PATCH",
		severity:      "high",
		description:   "HTTP PATCH endpoint that partially updates resources",
		attackVectors: []string{"injection", "mass_assignment", "broken_access_control"},
		pathExtract:   regexp.MustCompile(`@PatchMapping\s*\(\s*(?:\{\s*)?(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"`),
	},
	"RequestMapping": {
		name:          "RequestMapping",
		category:      "http_endpoint",
		httpMethod:    "ANY",
		severity:      "high",
		description:   "Generic HTTP endpoint (method-agnostic mapping)",
		attackVectors: []string{"injection", "broken_access_control", "method_confusion"},
		pathExtract:   regexp.MustCompile(`@RequestMapping\s*\(\s*(?:\{\s*)?(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"`),
	},

	// --- Actuator / management ----------------------------------------------
	"Endpoint": {
		name:          "Endpoint",
		category:      "actuator_endpoint",
		severity:      "critical",
		description:   "Custom Spring Boot Actuator endpoint - often exposes internal state",
		attackVectors: []string{"information_disclosure", "authentication_bypass", "rce_via_actuator"},
		pathExtract:   regexp.MustCompile(`@Endpoint\s*\(\s*id\s*=\s*"([^"]*)"`),
	},
	"RestControllerEndpoint": {
		name:          "RestControllerEndpoint",
		category:      "actuator_endpoint",
		severity:      "critical",
		description:   "REST Actuator endpoint - direct HTTP access to management surface",
		attackVectors: []string{"information_disclosure", "authentication_bypass"},
		pathExtract:   regexp.MustCompile(`@RestControllerEndpoint\s*\(\s*id\s*=\s*"([^"]*)"`),
	},
	"ReadOperation": {
		name:          "ReadOperation",
		category:      "actuator_endpoint",
		httpMethod:    "GET",
		severity:      "high",
		description:   "Actuator read operation exposed via GET",
		attackVectors: []string{"information_disclosure"},
	},
	"WriteOperation": {
		name:          "WriteOperation",
		category:      "actuator_endpoint",
		httpMethod:    "POST",
		severity:      "critical",
		description:   "Actuator write operation - state-changing management action",
		attackVectors: []string{"privilege_escalation", "remote_code_execution"},
	},
	"DeleteOperation": {
		name:          "DeleteOperation",
		category:      "actuator_endpoint",
		httpMethod:    "DELETE",
		severity:      "critical",
		description:   "Actuator delete operation - destructive management action",
		attackVectors: []string{"privilege_escalation", "denial_of_service"},
	},

	// --- Messaging / WebSocket ----------------------------------------------
	"MessageMapping": {
		name:          "MessageMapping",
		category:      "websocket_endpoint",
		severity:      "high",
		description:   "STOMP/WebSocket message handler - accepts client frames",
		attackVectors: []string{"injection", "broken_access_control", "csrf_websocket"},
		pathExtract:   regexp.MustCompile(`@MessageMapping\s*\(\s*(?:\{\s*)?"([^"]*)"`),
	},
	"SubscribeMapping": {
		name:          "SubscribeMapping",
		category:      "websocket_endpoint",
		severity:      "high",
		description:   "STOMP/WebSocket subscription handler",
		attackVectors: []string{"information_disclosure", "broken_access_control"},
		pathExtract:   regexp.MustCompile(`@SubscribeMapping\s*\(\s*(?:\{\s*)?"([^"]*)"`),
	},
	"KafkaListener": {
		name:          "KafkaListener",
		category:      "message_listener",
		severity:      "medium",
		description:   "Kafka topic consumer - entry point for messages from brokers",
		attackVectors: []string{"injection", "deserialization", "poisoned_message"},
		pathExtract:   regexp.MustCompile(`@KafkaListener\s*\([^)]*topics\s*=\s*(?:\{\s*)?"([^"]*)"`),
	},
	"RabbitListener": {
		name:          "RabbitListener",
		category:      "message_listener",
		severity:      "medium",
		description:   "RabbitMQ queue consumer - entry point for AMQP messages",
		attackVectors: []string{"injection", "deserialization", "poisoned_message"},
		pathExtract:   regexp.MustCompile(`@RabbitListener\s*\([^)]*queues\s*=\s*(?:\{\s*)?"([^"]*)"`),
	},
	"JmsListener": {
		name:          "JmsListener",
		category:      "message_listener",
		severity:      "medium",
		description:   "JMS queue/topic consumer",
		attackVectors: []string{"injection", "deserialization", "poisoned_message"},
		pathExtract:   regexp.MustCompile(`@JmsListener\s*\([^)]*destination\s*=\s*"([^"]*)"`),
	},
	"SqsListener": {
		name:          "SqsListener",
		category:      "message_listener",
		severity:      "medium",
		description:   "AWS SQS queue consumer",
		attackVectors: []string{"injection", "deserialization", "poisoned_message"},
		pathExtract:   regexp.MustCompile(`@SqsListener\s*\(\s*(?:value\s*=\s*)?(?:\{\s*)?"([^"]*)"`),
	},
	"EventListener": {
		name:          "EventListener",
		category:      "event_listener",
		severity:      "low",
		description:   "Spring ApplicationEvent listener - internal event surface",
		attackVectors: []string{"internal_only"},
	},
	"Scheduled": {
		name:          "Scheduled",
		category:      "scheduled_task",
		severity:      "low",
		description:   "Scheduled background task - timing-triggered entry point",
		attackVectors: []string{"denial_of_service_if_heavy", "time_based"},
	},
	"Async": {
		name:          "Async",
		category:      "async_execution",
		severity:      "low",
		description:   "Async-executed method - runs on a separate thread pool",
		attackVectors: []string{"thread_pool_exhaustion"},
	},

	// --- gRPC (common third-party annotation) -------------------------------
	"GrpcService": {
		name:          "GrpcService",
		category:      "grpc_service",
		severity:      "high",
		description:   "gRPC service bean - exposes RPC methods over the network",
		attackVectors: []string{"injection", "broken_access_control", "deserialization"},
	},
}

// reAnyAnnotation captures annotation name at the start of a trimmed line.
// We only care about the identifier portion for lookup; argument parsing is
// handled by each annoPattern's pathExtract regex.
var reAnyAnnotation = regexp.MustCompile(`^\s*@(\w+)\b`)

// ---------------------------------------------------------------------------
// Inline (sink/source) patterns - matched anywhere in a method body.
// These represent secondary attack surface: dangerous operations a request
// eventually reaches.
// ---------------------------------------------------------------------------

type inlinePattern struct {
	category      string
	re            *regexp.Regexp
	severity      string
	description   string
	attackVectors []string
}

var inlinePatterns = []inlinePattern{
	// --- Disk IO ------------------------------------------------------------
	{
		category:      "file_io_disk",
		re:            regexp.MustCompile(`\b(?:new\s+FileInputStream|new\s+FileReader|new\s+RandomAccessFile)\b`),
		severity:      "medium",
		description:   "Reads a file from local disk - sink for path traversal if path is user-controlled",
		attackVectors: []string{"path_traversal", "information_disclosure"},
	},
	{
		category:      "file_io_disk",
		re:            regexp.MustCompile(`\b(?:new\s+FileOutputStream|new\s+FileWriter|new\s+PrintWriter\s*\(\s*new\s+File)\b`),
		severity:      "high",
		description:   "Writes a file to local disk - sink for arbitrary file write / path traversal",
		attackVectors: []string{"path_traversal", "arbitrary_file_write"},
	},
	{
		category:      "file_io_disk",
		re:            regexp.MustCompile(`\bFiles\.(?:readString|readAllBytes|readAllLines|newInputStream|newBufferedReader|lines)\b`),
		severity:      "medium",
		description:   "NIO file read - path traversal sink when path derived from input",
		attackVectors: []string{"path_traversal", "information_disclosure"},
	},
	{
		category:      "file_io_disk",
		re:            regexp.MustCompile(`\bFiles\.(?:write|writeString|newOutputStream|newBufferedWriter|copy|move|delete|createFile|createDirectory|createDirectories)\b`),
		severity:      "high",
		description:   "NIO file write/move/delete - arbitrary file write sink",
		attackVectors: []string{"path_traversal", "arbitrary_file_write"},
	},
	{
		category:      "file_upload",
		re:            regexp.MustCompile(`\bMultipartFile\b`),
		severity:      "high",
		description:   "Multipart file upload parameter - entry for malicious uploads",
		attackVectors: []string{"unrestricted_upload", "path_traversal", "malware_upload"},
	},
	{
		category:      "file_upload",
		re:            regexp.MustCompile(`\btransferTo\s*\(`),
		severity:      "high",
		description:   "MultipartFile.transferTo() - writes upload to disk; validate destination",
		attackVectors: []string{"path_traversal", "arbitrary_file_write"},
	},

	// --- In-memory storage / buffers ---------------------------------------
	{
		category:      "memory_buffer",
		re:            regexp.MustCompile(`\bnew\s+ByteArrayOutputStream\s*\(\s*\)`),
		severity:      "low",
		description:   "Unbounded in-memory byte buffer - DoS sink if data is attacker-controlled",
		attackVectors: []string{"denial_of_service", "memory_exhaustion"},
	},
	{
		category:      "memory_buffer",
		re:            regexp.MustCompile(`\bnew\s+(?:StringBuilder|StringBuffer)\s*\(\s*\)`),
		severity:      "low",
		description:   "Unbounded in-memory string buffer",
		attackVectors: []string{"denial_of_service", "memory_exhaustion"},
	},
	{
		category:      "memory_cache",
		re:            regexp.MustCompile(`@Cacheable\b|\bConcurrentHashMap\s*<|\bnew\s+HashMap\s*<`),
		severity:      "low",
		description:   "In-memory cache/map - poisoning or memory-growth concerns",
		attackVectors: []string{"cache_poisoning", "memory_exhaustion"},
	},

	// --- Outbound network ---------------------------------------------------
	{
		category:      "network_outbound",
		re:            regexp.MustCompile(`\b(?:RestTemplate|WebClient|HttpClient\.newHttpClient|HttpURLConnection|OkHttpClient|Feign)\b`),
		severity:      "medium",
		description:   "Outbound HTTP client - SSRF sink if URL is user-controlled",
		attackVectors: []string{"ssrf", "credential_leak"},
	},
	{
		category:      "network_outbound",
		re:            regexp.MustCompile(`\bnew\s+URL\s*\(|\bURI\.create\s*\(`),
		severity:      "medium",
		description:   "URL/URI constructed from string - potential SSRF source",
		attackVectors: []string{"ssrf", "open_redirect"},
	},

	// --- Inbound raw sockets ------------------------------------------------
	{
		category:      "network_inbound",
		re:            regexp.MustCompile(`\bnew\s+(?:ServerSocket|DatagramSocket)\s*\(`),
		severity:      "high",
		description:   "Raw inbound socket listener - direct network entry point",
		attackVectors: []string{"unauthenticated_access", "protocol_attack", "denial_of_service"},
	},

	// --- Deserialization ----------------------------------------------------
	{
		category:      "deserialization",
		re:            regexp.MustCompile(`\bnew\s+ObjectInputStream\s*\(|\breadObject\s*\(\s*\)`),
		severity:      "critical",
		description:   "Java native deserialization - classic RCE gadget chain sink",
		attackVectors: []string{"insecure_deserialization", "remote_code_execution"},
	},
	{
		category:      "deserialization",
		re:            regexp.MustCompile(`\bXMLDecoder\s*\(`),
		severity:      "critical",
		description:   "XMLDecoder - trivially exploitable for RCE",
		attackVectors: []string{"insecure_deserialization", "remote_code_execution"},
	},
	{
		category:      "deserialization",
		re:            regexp.MustCompile(`\bnew\s+Yaml\s*\(\s*\)|\bYaml\.load\s*\(`),
		severity:      "high",
		description:   "SnakeYAML default load - can instantiate arbitrary classes pre-2.0",
		attackVectors: []string{"insecure_deserialization", "remote_code_execution"},
	},

	// --- Process execution --------------------------------------------------
	{
		category:      "process_exec",
		re:            regexp.MustCompile(`\bRuntime\.getRuntime\s*\(\s*\)\s*\.exec\b|\bnew\s+ProcessBuilder\b`),
		severity:      "critical",
		description:   "OS command execution - command injection sink",
		attackVectors: []string{"command_injection", "remote_code_execution"},
	},

	// --- Reflection ---------------------------------------------------------
	{
		category:      "reflection",
		re:            regexp.MustCompile(`\bClass\.forName\s*\(|\.getDeclaredMethod\s*\(|\.getMethod\s*\(.*\)\s*\.invoke\b`),
		severity:      "high",
		description:   "Reflection - arbitrary class/method resolution if name is user-controlled",
		attackVectors: []string{"unsafe_reflection", "remote_code_execution"},
	},

	// --- XML parsing (XXE) --------------------------------------------------
	{
		category:      "xml_parser",
		re:            regexp.MustCompile(`\bDocumentBuilderFactory\.newInstance\b|\bSAXParserFactory\.newInstance\b|\bXMLInputFactory\.newInstance\b|\bXMLReader\b`),
		severity:      "high",
		description:   "XML parser - XXE sink unless external entities are explicitly disabled",
		attackVectors: []string{"xxe", "ssrf", "information_disclosure"},
	},

	// --- SQL / JPA ----------------------------------------------------------
	{
		category:      "sql_query",
		re:            regexp.MustCompile(`\bcreateNativeQuery\s*\(|\bcreateQuery\s*\(`),
		severity:      "high",
		description:   "JPA query construction - SQL injection sink if query built via concatenation",
		attackVectors: []string{"sql_injection"},
	},
	{
		category:      "sql_query",
		re:            regexp.MustCompile(`\bjdbcTemplate\.(?:query|update|execute|queryForObject|queryForList)\b`),
		severity:      "high",
		description:   "JdbcTemplate call - SQL injection sink if SQL is concatenated",
		attackVectors: []string{"sql_injection"},
	},
	{
		category:      "sql_query",
		re:            regexp.MustCompile(`@Query\s*\(\s*value\s*=\s*"[^"]*\+|@Query\s*\(\s*"[^"]*\+`),
		severity:      "critical",
		description:   "@Query with string concatenation - almost certainly SQL injectable",
		attackVectors: []string{"sql_injection"},
	},

	// --- Template injection -------------------------------------------------
	{
		category:      "template_engine",
		re:            regexp.MustCompile(`\bSpelExpressionParser\b|\.parseExpression\s*\(`),
		severity:      "critical",
		description:   "SpEL expression parsing - RCE sink if expression is user-controlled",
		attackVectors: []string{"expression_injection", "remote_code_execution"},
	},

	// --- Crypto / secrets ---------------------------------------------------
	{
		category:      "crypto",
		re:            regexp.MustCompile(`\bMessageDigest\.getInstance\s*\(\s*"(?i:md5|sha-?1)"`),
		severity:      "medium",
		description:   "Weak hash algorithm (MD5/SHA-1)",
		attackVectors: []string{"weak_crypto", "hash_collision"},
	},
	{
		category:      "crypto",
		re:            regexp.MustCompile(`\bCipher\.getInstance\s*\(\s*"(?i:des|rc4|aes/ecb)`),
		severity:      "high",
		description:   "Weak or misconfigured cipher (DES/RC4/AES-ECB)",
		attackVectors: []string{"weak_crypto", "plaintext_recovery"},
	},
}
