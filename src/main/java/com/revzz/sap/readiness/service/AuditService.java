package com.revzz.sap.readiness.service;

import com.revzz.sap.readiness.domain.AuditEntry;
import com.revzz.sap.readiness.repo.PlatformRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The audit trail.
 *
 * <p>Recording a restore test and exporting a report are the two actions
 * worth remembering: the first is the evidence itself, the second is the
 * moment somebody was shown the findings and was asked to act on them.</p>
 */
@Service
public class AuditService {

    private final PlatformRepository platform;

    public AuditService(PlatformRepository platform) {
        this.platform = platform;
    }

    public void record(String username, String action, String detail) {
        AuditEntry entry = new AuditEntry();
        entry.setAuditId("AU" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        entry.setUsername(username);
        entry.setAction(action);
        entry.setDetail(detail);
        entry.setAt(LocalDateTime.now());
        platform.insertAudit(entry);
    }

    public List<AuditEntry> recent(int limit) {
        return platform.findRecentAudit(limit);
    }
}
