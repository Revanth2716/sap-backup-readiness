package com.revzz.sap.readiness.service;

import com.revzz.sap.readiness.domain.BackupRecord;
import com.revzz.sap.readiness.integration.BackupStatusGateway;
import com.revzz.sap.readiness.repo.ContinuityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pulls the backup history from the landscape into the portal.
 *
 * <p>The sync is additive: a backup that is already known is left alone, so
 * running it twice changes nothing. That matters because the whole point of
 * the sync is that it can be run from a scheduler without somebody having to
 * think about it first.</p>
 */
@Service
public class BackupSyncService {

    private final BackupStatusGateway gateway;
    private final ContinuityRepository repository;

    public BackupSyncService(BackupStatusGateway gateway, ContinuityRepository repository) {
        this.gateway = gateway;
        this.repository = repository;
    }

    /** Where the data currently comes from. */
    public String gatewayDescription() {
        return gateway.description();
    }

    /**
     * Imports what the gateway has and the portal does not.
     *
     * @return the number of new backup records
     */
    public int sync(String systemName) {
        List<BackupRecord> incoming = gateway.fetchRecentBackups(systemName, 50);
        List<BackupRecord> known = repository.findBackups(systemName);

        int imported = 0;
        for (BackupRecord record : incoming) {
            if (record.getBackupId() == null || contains(known, record.getBackupId())) {
                continue;
            }
            if (record.getSystemName() == null) {
                record.setSystemName(systemName);
            }
            if (record.getStartedAt() == null) {
                continue;
            }
            repository.insertBackup(record);
            known.add(record);
            imported++;
        }
        return imported;
    }

    private boolean contains(List<BackupRecord> known, String backupId) {
        for (BackupRecord record : known) {
            if (backupId.equals(record.getBackupId())) {
                return true;
            }
        }
        return false;
    }
}
