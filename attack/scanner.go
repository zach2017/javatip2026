package main

import (
	"bufio"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

// scanTree walks `root` looking for .java files under src/main/java and
// scans each. It returns the full list of findings and the number of files
// actually scanned.
func scanTree(root string) ([]Finding, int, error) {
	// If the caller pointed at the project root, narrow to src/main/java
	// automatically if it exists. This matches the prompt ("scan on the
	// main/java src files").
	scanRoot := root
	candidate := filepath.Join(root, "src", "main", "java")
	if info, err := os.Stat(candidate); err == nil && info.IsDir() {
		scanRoot = candidate
	}

	var findings []Finding
	nextID := 1
	filesScanned := 0

	err := filepath.WalkDir(scanRoot, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			// Skip common build/output directories just in case the user
			// pointed at the project root and we did not narrow above.
			base := d.Name()
			if base == "target" || base == "build" || base == ".git" || base == "node_modules" {
				return filepath.SkipDir
			}
			return nil
		}
		if !strings.HasSuffix(path, ".java") {
			return nil
		}
		filesScanned++
		fs, err := scanFile(path, &nextID)
		if err != nil {
			fmt.Fprintf(os.Stderr, "warn: %s: %v\n", path, err)
			return nil
		}
		findings = append(findings, fs...)
		return nil
	})

	return findings, filesScanned, err
}

// scanFile reads one Java source file, extracts structural context, and
// produces every Finding implied by the patterns defined in patterns.go.
func scanFile(path string, nextID *int) ([]Finding, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	// Strip comments but preserve line numbers for accurate reporting.
	clean := stripCommentsPreserveLines(string(raw))

	ctx := fileContext{path: path}

	// Pass 1: structural metadata (package, class, class-level annotations).
	if m := rePackage.FindStringSubmatch(clean); m != nil {
		ctx.packageName = m[1]
	}
	if m := reClass.FindStringSubmatch(clean); m != nil {
		ctx.className = m[1]
	} else {
		// Nothing to report for a file with no class/interface/enum/record.
		return nil, nil
	}
	// Class-level annotations - we only care about a handful for routing the
	// findings produced by the method scan.
	for _, name := range []string{
		"RestController", "Controller", "RestControllerAdvice", "ControllerAdvice",
		"Service", "Repository", "Component", "Configuration",
	} {
		if strings.Contains(clean, "@"+name) {
			ctx.classAnnots = append(ctx.classAnnots, name)
			switch name {
			case "RestController":
				ctx.isRestCtrl = true
				ctx.isController = true
			case "Controller":
				ctx.isController = true
			case "RestControllerAdvice", "ControllerAdvice":
				ctx.isControllerAdv = true
			case "Component":
				ctx.isComponent = true
			case "Configuration":
				ctx.isConfig = true
			}
		}
	}
	if m := reClassRequestMapping.FindStringSubmatch(clean); m != nil {
		ctx.classBasePath = m[1]
	}

	// Pass 2: line-walked scan for method-level annotations, method sigs,
	// and inline sink patterns.
	var findings []Finding
	lines := strings.Split(clean, "\n")

	// Buffer of annotations seen since the last method boundary.
	type pendingAnno struct {
		name string
		line int
		src  string
	}
	var pending []pendingAnno

	// Track enclosing method for inline findings. A small state machine:
	// - When a method signature line is detected, currentMethod is set.
	// - When brace depth returns to the class level, currentMethod clears.
	var (
		currentMethod     string
		methodStartBrace  int
		braceDepth        int
		inMethod          bool
	)

	for i, line := range lines {
		lineNum := i + 1
		trimmed := strings.TrimSpace(line)

		// ---- Collect pending annotations at this line --------------------
		// Only inside the class body (depth>=1). At depth 0 the annotation
		// is class-level and must not leak into the next method finding.
		if braceDepth >= 1 {
			if m := reAnyAnnotation.FindStringSubmatch(trimmed); m != nil {
				pending = append(pending, pendingAnno{
					name: m[1],
					line: lineNum,
					src:  trimmed,
				})
			}
		}

		// ---- Detect a method signature -----------------------------------
		// We only treat this as a method if brace depth is at the class
		// level (depth == 1). That filters out lambdas and inner-class
		// method noise reasonably well.
		if braceDepth >= 1 && looksLikeMethodSig(trimmed) {
			if name := extractMethodName(line); name != "" {
				currentMethod = name
				inMethod = true
				methodStartBrace = braceDepth
				// Attach findings from any pending method-level annotations.
				for _, pa := range pending {
					meta, ok := annoPatterns[pa.name]
					if !ok {
						continue
					}
					findings = append(findings, buildEndpointFinding(
						ctx, meta, pa, name, lineNum, line, nextID))
				}
				pending = nil
			}
		}

		// ---- Inline sink/source patterns ---------------------------------
		for _, ip := range inlinePatterns {
			if ip.re.MatchString(line) {
				findings = append(findings, Finding{
					ID:            *nextID,
					Category:      ip.category,
					EntryPoint:    describeInlineEntry(ip.category, ctx, currentMethod),
					Package:       ctx.packageName,
					Class:         ctx.className,
					Method:        currentMethod,
					File:          path,
					Line:          lineNum,
					AttackVectors: append([]string{}, ip.attackVectors...),
					Severity:      ip.severity,
					Description:   ip.description,
					Snippet:       strings.TrimSpace(line),
				})
				*nextID++
			}
		}

		// ---- Update brace depth for the next line ------------------------
		braceDepth += countOutsideStrings(line, '{') - countOutsideStrings(line, '}')
		if inMethod && braceDepth < methodStartBrace {
			currentMethod = ""
			inMethod = false
		}

		// Clear pending annotations on blank separators so stray annotations
		// on fields do not leak onto unrelated method detections.
		if trimmed == "" || strings.HasPrefix(trimmed, "}") {
			// Field annotations (like @Autowired) usually precede a field
			// line that does not match our method signature regex; the
			// pending buffer naturally gets overwritten by the next
			// annotation block. Nothing to do here.
		}
	}

	return findings, nil
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

// looksLikeMethodSig is a cheap prefilter so we do not run the expensive
// method-signature regex against every line.
func looksLikeMethodSig(trimmed string) bool {
	if trimmed == "" {
		return false
	}
	if !strings.Contains(trimmed, "(") {
		return false
	}
	// Must start with a visibility modifier or a return-type-like token.
	if strings.HasPrefix(trimmed, "public ") ||
		strings.HasPrefix(trimmed, "private ") ||
		strings.HasPrefix(trimmed, "protected ") {
		return reMethodSig.MatchString(trimmed)
	}
	return false
}

// extractMethodName pulls the method identifier out of a signature line.
func extractMethodName(line string) string {
	m := reMethodSig.FindStringSubmatch(line)
	if m == nil {
		return ""
	}
	// Filter out a few known keywords that the permissive regex might catch.
	name := m[1]
	switch name {
	case "if", "for", "while", "switch", "catch", "return":
		return ""
	}
	return name
}

// buildEndpointFinding constructs a Finding for a method-level annotation
// such as @GetMapping, @KafkaListener, or @Scheduled.
func buildEndpointFinding(
	ctx fileContext,
	meta annoPattern,
	pa struct {
		name string
		line int
		src  string
	},
	methodName string,
	methodLine int,
	methodSrc string,
	nextID *int,
) Finding {
	// Extract the path/topic if this annotation defines one.
	path := ""
	if meta.pathExtract != nil {
		if m := meta.pathExtract.FindStringSubmatch(pa.src); m != nil {
			path = m[1]
		}
	}

	entry := buildEntryPoint(meta, ctx, path, methodName)

	// For HTTP endpoints inside a @ControllerAdvice, widen severity note.
	desc := meta.description
	if ctx.isControllerAdv && meta.category == "http_endpoint" {
		desc += " (handler defined inside @ControllerAdvice - global scope)"
	}

	f := Finding{
		ID:            *nextID,
		Category:      meta.category,
		EntryPoint:    entry,
		Package:       ctx.packageName,
		Class:         ctx.className,
		Method:        methodName,
		File:          ctx.path,
		Line:          methodLine,
		HTTPMethod:    meta.httpMethod,
		AttackVectors: append([]string{}, meta.attackVectors...),
		Severity:      meta.severity,
		Description:   desc,
		Snippet:       strings.TrimSpace(pa.src + "  -->  " + strings.TrimSpace(methodSrc)),
	}
	*nextID++
	return f
}

// buildEntryPoint renders a human-readable entry string like
// "POST /api/users/{id}" or "kafka://orders.created".
func buildEntryPoint(meta annoPattern, ctx fileContext, path, method string) string {
	switch meta.category {
	case "http_endpoint":
		full := joinPaths(ctx.classBasePath, path)
		if full == "" {
			full = "(unresolved)"
		}
		verb := meta.httpMethod
		if verb == "" {
			verb = "ANY"
		}
		return fmt.Sprintf("%s %s", verb, full)
	case "actuator_endpoint":
		if path != "" {
			return "actuator://" + path
		}
		return "actuator://" + method
	case "websocket_endpoint":
		return "ws://" + strings.TrimPrefix(path, "/")
	case "message_listener":
		// path here holds topic/queue/destination name
		return "mq://" + path
	case "event_listener":
		return "event://" + ctx.className + "#" + method
	case "scheduled_task":
		return "schedule://" + ctx.className + "#" + method
	case "async_execution":
		return "async://" + ctx.className + "#" + method
	case "grpc_service":
		return "grpc://" + ctx.className
	}
	return meta.category + "://" + method
}

// describeInlineEntry produces an EntryPoint string for inline (sink) findings.
func describeInlineEntry(category string, ctx fileContext, method string) string {
	if method == "" {
		return category + "://" + ctx.className + " (class scope)"
	}
	return category + "://" + ctx.className + "#" + method
}

// joinPaths safely concatenates a class-level base path with a method-level
// path, handling leading/trailing slashes.
func joinPaths(base, sub string) string {
	base = strings.TrimRight(base, "/")
	sub = strings.TrimLeft(sub, "/")
	switch {
	case base == "" && sub == "":
		return "/"
	case base == "":
		return "/" + sub
	case sub == "":
		return base
	default:
		return base + "/" + sub
	}
}

// stripCommentsPreserveLines removes Java // and /* */ comments from the
// source while keeping the total line count identical. This preserves the
// line numbers the user will see in their IDE.
func stripCommentsPreserveLines(content string) string {
	// Block comments first. Replace each block comment with an equal number
	// of newlines so line numbers stay aligned.
	blockRe := regexp.MustCompile(`(?s)/\*.*?\*/`)
	content = blockRe.ReplaceAllStringFunc(content, func(s string) string {
		n := strings.Count(s, "\n")
		if n == 0 {
			return ""
		}
		return strings.Repeat("\n", n)
	})

	// Line comments - scan each line and strip // suffix unless inside a
	// string literal. A light-weight string tracker is sufficient here;
	// pathological cases (escaped quotes at very end of line) are rare.
	var out strings.Builder
	scanner := bufio.NewScanner(strings.NewReader(content))
	scanner.Buffer(make([]byte, 1024*1024), 1024*1024)
	first := true
	for scanner.Scan() {
		if !first {
			out.WriteByte('\n')
		}
		first = false
		out.WriteString(stripLineComment(scanner.Text()))
	}
	return out.String()
}

func stripLineComment(line string) string {
	inStr := false
	inChar := false
	esc := false
	for i := 0; i < len(line)-1; i++ {
		c := line[i]
		if esc {
			esc = false
			continue
		}
		if c == '\\' && (inStr || inChar) {
			esc = true
			continue
		}
		if !inChar && c == '"' {
			inStr = !inStr
			continue
		}
		if !inStr && c == '\'' {
			inChar = !inChar
			continue
		}
		if !inStr && !inChar && c == '/' && line[i+1] == '/' {
			return line[:i]
		}
	}
	return line
}

// countOutsideStrings counts occurrences of ch outside of string/char
// literals. This keeps our brace-depth tracker from being thrown off by
// literals like "{" or '}'.
func countOutsideStrings(s string, ch byte) int {
	n := 0
	inStr := false
	inChar := false
	esc := false
	for i := 0; i < len(s); i++ {
		c := s[i]
		if esc {
			esc = false
			continue
		}
		if (inStr || inChar) && c == '\\' {
			esc = true
			continue
		}
		if !inChar && c == '"' {
			inStr = !inStr
			continue
		}
		if !inStr && c == '\'' {
			inChar = !inChar
			continue
		}
		if !inStr && !inChar && c == ch {
			n++
		}
	}
	return n
}
