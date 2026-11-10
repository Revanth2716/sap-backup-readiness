package com.revzz.sap.readiness.web;

import com.revzz.sap.readiness.service.AuditService;
import com.revzz.sap.readiness.service.ReadinessService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

/**
 * The continuity report.
 *
 * <p>The report is the deliverable: a document that can be attached to a
 * service review, pasted into a risk register or mailed to a customer. It is
 * rendered as Markdown so that it stays readable as plain text long after
 * the application is gone.</p>
 */
@Controller
@RequireRole({"ANALYST", "OPS_ADMIN"})
public class ReportController {

    private final ReadinessService readinessService;
    private final AuditService auditService;

    public ReportController(ReadinessService readinessService, AuditService auditService) {
        this.readinessService = readinessService;
        this.auditService = auditService;
    }

    @GetMapping("/report")
    public String report(Model model, HttpServletRequest request) {
        String markdown = readinessService.renderReport();
        model.addAttribute("markdown", markdown);
        model.addAttribute("lineCount", markdown.split("\n").length);
        model.addAttribute("audit", auditService.recent(10));

        auditService.record(CurrentUser.username(request.getSession(false)), "REPORT_VIEWED",
                "Continuity report opened");
        return "report";
    }

    /** The same report as a downloadable Markdown document. */
    @GetMapping("/report.md")
    public ResponseEntity<String> download(HttpServletRequest request) {
        String markdown = readinessService.renderReport();

        auditService.record(CurrentUser.username(request.getSession(false)), "REPORT_EXPORTED",
                "Continuity report exported as Markdown");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", "continuity-readiness-report.md");
        return new ResponseEntity<String>(markdown, headers, org.springframework.http.HttpStatus.OK);
    }
}
