package main

// Finding represents a single attack surface entry point discovered in the source.
// It is the primary record written to the YAML report.
type Finding struct {
	ID            int      // Sequential ID, stable within a single report
	Category      string   // e.g. http_endpoint, file_io, deserialization
	EntryPoint    string   // Human readable entry description (e.g. "POST /api/users")
	Package       string   // Fully qualified Java package
	Class         string   // Java class name
	Method        string   // Enclosing method (may be "" for class-level findings)
	File          string   // Absolute or relative path of the source file
	Line          int      // 1-based line number
	HTTPMethod    string   // GET, POST, etc. (only populated for HTTP endpoints)
	AttackVectors []string // OWASP-style threat categories relevant to this surface
	Severity      string   // low | medium | high | critical
	Description   string   // Short explanation of why this is attack surface
	Snippet       string   // Trimmed line of source code that triggered the match
}

// Report is the top-level document written to YAML.
type Report struct {
	ScannedPath   string
	ScanTime      string
	JavaVersion   string
	SpringVersion string
	BootVersion   string
	TotalFindings int
	FilesScanned  int
	ByCategory    map[string]int
	BySeverity    map[string]int
	Findings      []Finding
}

// fileContext holds state derived while scanning a single Java file.
type fileContext struct {
	path            string
	packageName     string
	className       string
	classAnnots     []string // raw class-level annotations
	classBasePath   string   // value of class-level @RequestMapping (if any)
	isController    bool
	isRestCtrl      bool
	isConfig        bool
	isComponent     bool
	isControllerAdv bool
}
