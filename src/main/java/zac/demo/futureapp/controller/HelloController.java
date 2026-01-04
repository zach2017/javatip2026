package zac.demo.futureapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import zac.demo.futureapp.service.*;
import zac.demo.futureapp.types.*;


/**
 * REST controller demonstrating "fire-and-track" async jobs.
 *
 * Instead of returning a CompletableFuture that keeps the HTTP request open,
 * we immediately return 202 Accepted with a jobId, and expose a status endpoint.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hello")
public class HelloController {

    private final HelloService helloService;

    /**
     * Start a long-running "hello" job and return immediately.
     * POST /api/hello/start?name=Zac
     */
    @GetMapping("/start")
    public ResponseEntity<JobAcceptedResponse> startHello(@RequestParam(defaultValue = "world") String name) {
        log.info("Received request: startHello name={}", name);
        UUID jobId = helloService.startHelloJob(name);

        return ResponseEntity
                .accepted()
                .body(JobAcceptedResponse.from(jobId, "/api/hello/jobs/" + jobId));
    }

    /**
     * Start a composed multi-step job and return immediately.
     * POST /api/hello/compose/start?name=Zac
     */
    @PostMapping("/compose/start")
    public ResponseEntity<JobAcceptedResponse> startCompose(@RequestParam(defaultValue = "world") String name) {
        log.info("Received request: startCompose name={}", name);
        UUID jobId = helloService.startComposeHelloJob(name);

        return ResponseEntity
                .accepted()
                .body(JobAcceptedResponse.from(jobId, "/api/hello/jobs/" + jobId));
    }

    /**
     * Start a "race" job (first of multiple wins) and return immediately.
     * POST /api/hello/race/start
     */
    @PostMapping("/race/start")
    public ResponseEntity<JobAcceptedResponse> startRace() {
        log.info("Received request: startRace");
        UUID jobId = helloService.startRaceJob();

        return ResponseEntity
                .accepted()
                .body(JobAcceptedResponse.from(jobId, "/api/hello/jobs/" + jobId));
    }

    /**
     * Check job status and (when finished) see the result or error.
     * GET /api/hello/jobs/{jobId}
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobSnapshot> getJob(@PathVariable UUID jobId) {
        return helloService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Optional: list recent jobs (best-effort, in-memory).
     * GET /api/hello/jobs
     */
    @GetMapping("/jobs")
    public ResponseEntity<?> listJobs(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(helloService.listJobs(limit));
    }
}
