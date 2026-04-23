package com.example.audit.web;

import com.example.audit.persistence.AuditEventRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the HTML dashboard and login page. These are view controllers
 * (Thymeleaf), distinct from the @RestController endpoints.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AuditEventRepository auditRepository;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        var recent = auditRepository.findAll(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("recentEvents", recent.getContent());
        return "dashboard";
    }
}
