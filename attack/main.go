// Command spring-attack-surface scans a Java Spring Boot project (Java 21 /
// Spring Framework 6 / Spring Boot 3.5.13) for externally reachable entry
// points and dangerous internal sinks (file/network IO, deserialization,
// process exec, reflection, SQL, etc.), then emits a YAML report intended
// to be consumed by a downstream diagramming tool.
//
// Usage:
//
//	spring-attack-surface -src /path/to/project [-out report.yml]
//
// The scanner will automatically descend to src/main/java if the path
// supplied is a project root.
package main

import (
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

func main() {
	srcFlag := flag.String("src", ".", "Path to Spring Boot project (or src/main/java directory)")
	outFlag := flag.String("out", "attack-surface.yml", "Output YAML file path")
	flag.Parse()

	absSrc, err := filepath.Abs(*srcFlag)
	if err != nil {
		die("could not resolve -src path: %v", err)
	}
	if info, err := os.Stat(absSrc); err != nil || !info.IsDir() {
		die("-src must be an existing directory: %s", absSrc)
	}

	fmt.Fprintf(os.Stderr, "scanning: %s\n", absSrc)
	findings, filesScanned, err := scanTree(absSrc)
	if err != nil {
		die("scan failed: %v", err)
	}

	report := Report{
		ScannedPath:   absSrc,
		ScanTime:      time.Now().UTC().Format(time.RFC3339),
		JavaVersion:   "21",
		SpringVersion: "6.x",
		BootVersion:   "3.5.13",
		FilesScanned:  filesScanned,
		TotalFindings: len(findings),
		ByCategory:    countBy(findings, func(f Finding) string { return f.Category }),
		BySeverity:    countBy(findings, func(f Finding) string { return f.Severity }),
		Findings:      findings,
	}

	out, err := os.Create(*outFlag)
	if err != nil {
		die("could not create output file: %v", err)
	}
	defer out.Close()
	if err := writeYAML(out, report); err != nil {
		die("writing YAML: %v", err)
	}

	fmt.Fprintf(os.Stderr, "scanned %d file(s), %d finding(s) -> %s\n",
		filesScanned, len(findings), *outFlag)
}

func countBy(findings []Finding, key func(Finding) string) map[string]int {
	m := map[string]int{}
	for _, f := range findings {
		m[key(f)]++
	}
	return m
}

func die(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "error: "+format+"\n", args...)
	os.Exit(1)
}
