package com.example.demo.web;

import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.jpa.repository.Query;

/**
 * Sample controller exercising many attack-surface patterns.
 * This is intentionally bad code - it exists only as a scanner fixture.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, byte[]> cache = new HashMap<>();

    @GetMapping("/{id}")
    public String getUser(@PathVariable Long id) {
        // SQL injection sink
        return jdbcTemplate.queryForObject(
            "SELECT name FROM users WHERE id = " + id, String.class);
    }

    @PostMapping
    public String createUser(@RequestBody String json) throws IOException {
        // In-memory accumulator - DoS potential
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(json.getBytes());
        return "ok";
    }

    @PutMapping("/{id}/avatar")
    public String uploadAvatar(@PathVariable Long id, @RequestParam MultipartFile file)
            throws IOException {
        // Arbitrary file write via transferTo
        Path dest = Paths.get("/var/app/uploads", file.getOriginalFilename());
        file.transferTo(dest);
        return "stored";
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM users WHERE id = " + id);
    }

    @GetMapping("/export")
    public byte[] exportUser(@RequestParam String path) throws IOException {
        // Path traversal sink
        return Files.readAllBytes(Paths.get(path));
    }

    @PostMapping("/fetch")
    public String fetchRemote(@RequestParam String url) {
        // SSRF sink
        RestTemplate rt = new RestTemplate();
        return rt.getForObject(url, String.class);
    }

    @PostMapping("/restore")
    public Object restore(@RequestBody byte[] blob) throws Exception {
        // Classic Java deserialization - RCE gadget chain
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(blob));
        return ois.readObject();
    }

    @PostMapping("/run")
    public String run(@RequestParam String cmd) throws IOException {
        // Command injection sink
        Process p = Runtime.getRuntime().exec(cmd);
        return String.valueOf(p.hashCode());
    }

    @GetMapping("/reflect")
    public String reflect(@RequestParam String clazz) throws Exception {
        // Unsafe reflection
        return Class.forName(clazz).getName();
    }

    @Query(value = "SELECT * FROM users WHERE name = '" + "?" + "' OR 1=1", nativeQuery = true)
    public List<String> dangerousQuery() {
        return List.of();
    }
}
