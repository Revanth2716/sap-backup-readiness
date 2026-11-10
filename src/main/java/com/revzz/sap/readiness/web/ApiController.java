package com.revzz.sap.readiness.web;

import com.revzz.sap.readiness.domain.CapacityForecast;
import com.revzz.sap.readiness.domain.ReadinessFinding;
import com.revzz.sap.readiness.domain.SystemProfile;
import com.revzz.sap.readiness.domain.SystemReadiness;
import com.revzz.sap.readiness.service.ReadinessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for reporting and integrations.
 *
 * <p>Read-only on purpose: the API exposes the same readiness picture the
 * screens show, so a monitoring script or a ticket system can consume it
 * without scraping HTML.</p>
 *
 * <p>Every endpoint except the health check needs a signed-in session. The
 * data describes a production landscape's recovery position, so it is not
 * something to serve to anyone who asks.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final ReadinessService readinessService;

    public ApiController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    /** The health check stays open - a load balancer has to be able to reach it. */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<String, Object>();
        health.put("status", "UP");
        health.put("systems", readinessService.systems().size());
        health.put("averageScore", readinessService.averageScore());
        return health;
    }

    @GetMapping(value = "/readiness", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> readiness(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(readinessService.readiness());
    }

    @GetMapping(value = "/systems", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> systems(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(readinessService.systems());
    }

    @GetMapping(value = "/systems/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> system(@PathVariable String name, HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        SystemReadiness readiness = readinessService.assess(name);
        if (readiness == null) {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("error", "no such system");
            body.put("system", name);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        return ResponseEntity.ok(readiness);
    }

    @GetMapping(value = "/findings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> findings(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(readinessService.allFindings());
    }

    @GetMapping(value = "/capacity", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> capacity(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        List<CapacityForecast> forecasts = new java.util.ArrayList<CapacityForecast>();
        for (SystemProfile profile : readinessService.systems()) {
            SystemReadiness readiness = readinessService.assess(profile);
            if (readiness != null && readiness.getForecast() != null) {
                forecasts.add(readiness.getForecast());
            }
        }
        return ResponseEntity.ok(forecasts);
    }

    /**
     * The report as Markdown.
     *
     * <p>The content type is set on the response rather than with
     * {@code produces}, so that the 401 body can still be written as JSON -
     * otherwise an anonymous caller would get a 500 instead of a 401.</p>
     */
    @GetMapping("/report.md")
    public ResponseEntity<Object> report(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown", java.nio.charset.StandardCharsets.UTF_8));
        return new ResponseEntity<Object>(readinessService.renderReport(), headers,
                org.springframework.http.HttpStatus.OK);
    }

    /**
     * Rejects an anonymous caller.
     *
     * @return null when the caller is signed in, otherwise a 401 with a JSON body
     */
    private ResponseEntity<Object> requireSession(HttpSession session) {
        if (CurrentUser.isSignedIn(session)) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", "authentication required");
        body.put("hint", "sign in at /login and send the session cookie");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
