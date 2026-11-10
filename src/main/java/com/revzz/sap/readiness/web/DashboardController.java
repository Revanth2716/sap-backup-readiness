package com.revzz.sap.readiness.web;

import com.revzz.sap.readiness.domain.ReadinessFinding;
import com.revzz.sap.readiness.domain.Severity;
import com.revzz.sap.readiness.domain.SystemReadiness;
import com.revzz.sap.readiness.service.AuditService;
import com.revzz.sap.readiness.service.ReadinessService;
import com.revzz.sap.readiness.support.ReadinessChartRenderer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * The readiness dashboard.
 *
 * <p>The first screen answers one question: which system is least ready to
 * survive a disaster, and why. Everything else is one click away.</p>
 */
@Controller
public class DashboardController {

    private final ReadinessService readinessService;
    private final AuditService auditService;

    public DashboardController(ReadinessService readinessService, AuditService auditService) {
        this.readinessService = readinessService;
        this.auditService = auditService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<SystemReadiness> readiness = readinessService.readiness();
        List<ReadinessFinding> findings = readinessService.allFindings();

        model.addAttribute("readiness", readiness);
        model.addAttribute("findings", findings);
        model.addAttribute("scoreChart", ReadinessChartRenderer.readinessBars(readiness));
        model.addAttribute("systemCount", readiness.size());
        model.addAttribute("criticalCount", readinessService.countBySeverity(Severity.CRITICAL));
        model.addAttribute("highCount", readinessService.countBySeverity(Severity.HIGH));
        model.addAttribute("averageScore", readinessService.averageScore());
        model.addAttribute("healthyCount", readinessService.countBySeverity(Severity.INFO));
        model.addAttribute("findingCount", findings.size());
        model.addAttribute("audit", auditService.recent(8));
        return "overview";
    }
}
