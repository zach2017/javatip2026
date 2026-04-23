package com.example.audit.web;

import com.example.audit.actions.CreateUserAudit;
import com.example.audit.actions.GetUserAudit;
import com.example.audit.annotation.Audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

    private final Map<Long, String> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @PostMapping
    @Audit(CreateUserAudit.class)
    public Map<String, Object> create(@RequestParam String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        long id = ids.incrementAndGet();
        store.put(id, name);
        log.debug("stored user id={} name={}", id, name);
        return Map.of("id", id, "name", name);
    }

    @GetMapping("/{id}")
    @Audit(GetUserAudit.class)
    public Map<String, Object> get(@PathVariable Long id) {
        String name = store.get(id);
        if (name == null) {
            throw new IllegalStateException("not found");
        }
        return Map.of("id", id, "name", name);
    }
}
