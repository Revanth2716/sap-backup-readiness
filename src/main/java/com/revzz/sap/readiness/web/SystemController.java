package com.revzz.sap.readiness.web;

import com.revzz.sap.readiness.domain.BackupRecord;
import com.revzz.sap.readiness.domain.CapacitySample;
import com.revzz.sap.readiness.domain.RestoreTest;
import com.revzz.sap.readiness.domain.SystemReadiness;
import com.revzz.sap.readiness.repo.ContinuityRepository;
import com.revzz.sap.readiness.service.AuditService;
import com.revzz.sap.readiness.service.BackupSyncService;
import com.revzz.sap.readiness.service.CapacityService;
import com.revzz.sap.readiness.service.ReadinessService;
import com.revzz.sap.readiness.support.ReadinessChartRenderer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One system, in detail.
 *
 * <p>The detail screen is where a BASIS team actually works: the backup
 * history, the restore test evidence, the capacity trend, and the findings
 * with the action each one needs. It is also the only place a restore test
 * can be recorded - recording the evidence is the write path of this
 * application.</p>
 */
@Controller
public class SystemController {

    /** How many backup rows the detail screen shows. */
    private static final int RECENT_BACKUPS = 12;

    private final ReadinessService readinessService;
    private final CapacityService capacityService;
    private final ContinuityRepository repository;
    private final AuditService auditService;
    private final BackupSyncService syncService;

    public SystemController(ReadinessService readinessService, CapacityService capacityService,
                            ContinuityRepository repository, AuditService auditService,
                            BackupSyncService syncService) {
        this.readinessService = readinessService;
        this.capacityService = capacityService;
        this.repository = repository;
        this.auditService = auditService;
        this.syncService = syncService;
    }

    @GetMapping("/systems/{name}")
    public String system(@PathVariable String name, Model model) {
        SystemReadiness readiness = readinessService.assess(name);
        if (readiness == null) {
            model.addAttribute("error", "There is no system called " + name + ".");
            return "error";
        }

        List<BackupRecord> backups = repository.findBackups(name);
        List<RestoreTest> tests = repository.findRestoreTests(name);
        List<CapacitySample> samples = capacityService.samples(name);

        // The full history is long - a nightly full plus incrementals for a
        // month is fifty rows. The screen shows the most recent ones and
        // says how many are behind them.
        List<BackupRecord> recent = backups.size() > RECENT_BACKUPS
                ? backups.subList(0, RECENT_BACKUPS) : backups;

        model.addAttribute("readiness", readiness);
        model.addAttribute("backups", recent);
        model.addAttribute("backupTotal", backups.size());
        model.addAttribute("tests", tests);
        model.addAttribute("samples", samples);
        model.addAttribute("capacityChart",
                ReadinessChartRenderer.capacityChart(samples, readiness.getProfile(),
                        readiness.getForecast()));
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("gateway", syncService.gatewayDescription());
        return "system";
    }

    /**
     * Pulls the backup history of the system from the landscape.
     *
     * <p>The readiness score is recalculated straight afterwards, so the
     * effect of a sync is visible on the same screen.</p>
     */
    @PostMapping("/systems/{name}/sync")
    @RequireRole("OPS_ADMIN")
    public String sync(@PathVariable String name, Model model, HttpServletRequest request) {
        if (readinessService.assess(name) == null) {
            model.addAttribute("error", "There is no system called " + name + ".");
            return "error";
        }
        int imported = syncService.sync(name);
        auditService.record(CurrentUser.username(request.getSession(false)), "BACKUP_SYNC",
                name + " synchronised from " + syncService.gatewayDescription()
                        + ", " + imported + " new records");
        return "redirect:/systems/" + name + "?synced=" + imported;
    }

    /**
     * Records the result of a restore test.
     *
     * <p>This is the action that turns a hope into a recovery plan, so it is
     * written to the audit trail as well as to the restore test history.</p>
     */
    @PostMapping("/systems/{name}/restore-test")
    @RequireRole("OPS_ADMIN")
    public String recordRestoreTest(@PathVariable String name,
                                    @RequestParam String result,
                                    @RequestParam(defaultValue = "60") int durationMinutes,
                                    @RequestParam String performedBy,
                                    @RequestParam(required = false) String notes,
                                    @RequestParam(required = false) String testedAt,
                                    Model model,
                                    HttpServletRequest request) {
        if (readinessService.assess(name) == null) {
            model.addAttribute("error", "There is no system called " + name + ".");
            return "error";
        }

        LocalDateTime tested = testedAt == null || testedAt.trim().isEmpty()
                ? LocalDateTime.now()
                : LocalDate.parse(testedAt.trim()).atTime(22, 0);

        RestoreTest test = new RestoreTest();
        test.setTestId("RST" + name + System.currentTimeMillis() % 100000);
        test.setSystemName(name);
        test.setTestedAt(tested);
        test.setResult(result);
        test.setDurationMinutes(durationMinutes);
        test.setPerformedBy(performedBy);
        test.setNotes(notes == null || notes.trim().isEmpty()
                ? "Recorded from the readiness portal." : notes.trim());
        repository.insertRestoreTest(test);

        auditService.record(CurrentUser.username(request.getSession(false)),
                "RESTORE_TEST_RECORDED",
                name + " restore test recorded as " + result + ", " + durationMinutes
                        + " minutes, performed by " + performedBy);

        return "redirect:/systems/" + name + "?recorded=1";
    }
}
