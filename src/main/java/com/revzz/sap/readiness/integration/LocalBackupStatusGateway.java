package com.revzz.sap.readiness.integration;

import com.revzz.sap.readiness.domain.BackupRecord;
import com.revzz.sap.readiness.repo.ContinuityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The default gateway: reads the backup history from the local database.
 *
 * <p>This is what the application runs with when there is no SAP system to
 * talk to. It is also the implementation the unit tests exercise, which is
 * why the rest of the code never depends on RFC being available.</p>
 */
@Component
@ConditionalOnProperty(name = "readiness.gateway", havingValue = "local", matchIfMissing = true)
public class LocalBackupStatusGateway implements BackupStatusGateway {

    private final ContinuityRepository repository;

    public LocalBackupStatusGateway(ContinuityRepository repository) {
        this.repository = repository;
    }

    @Override
    public String description() {
        return "Local database";
    }

    @Override
    public List<BackupRecord> fetchRecentBackups(String systemName, int limit) {
        List<BackupRecord> backups = repository.findBackups(systemName);
        if (backups.size() <= limit) {
            return backups;
        }
        return backups.subList(0, Math.max(0, limit));
    }
}
