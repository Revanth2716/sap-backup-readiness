package com.revzz.sap.readiness.service;

import com.revzz.sap.readiness.domain.BackupRecord;
import com.revzz.sap.readiness.integration.BackupStatusGateway;
import com.revzz.sap.readiness.repo.ContinuityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sync from the landscape into the portal.
 *
 * <p>The rule that matters: a sync is additive. Running it twice must not
 * change anything, because it is meant to be run from a scheduler without
 * anybody thinking about it first.</p>
 */
class BackupSyncServiceTest {

    @Test
    @DisplayName("a backup that is already known is not imported twice")
    void knownBackupsAreNotImportedTwice() {
        ContinuityRepository repository = mock(ContinuityRepository.class);
        when(repository.findBackups(anyString())).thenReturn(new ArrayList<BackupRecord>());

        BackupRecord incoming = backup("BKP-001");
        BackupStatusGateway gateway = mock(BackupStatusGateway.class);
        when(gateway.fetchRecentBackups(eq("PRD"), eq(50)))
                .thenReturn(new ArrayList<BackupRecord>(java.util.Arrays.asList(incoming)));

        BackupSyncService service = new BackupSyncService(gateway, repository);

        assertEquals(1, service.sync("PRD"));
        verify(repository, times(1)).insertBackup(incoming);

        // The second run finds the record already there.
        when(repository.findBackups(anyString()))
                .thenReturn(new ArrayList<BackupRecord>(java.util.Arrays.asList(incoming)));
        assertEquals(0, service.sync("PRD"));
        verify(repository, times(1)).insertBackup(incoming);
    }

    @Test
    @DisplayName("a backup without a start time is skipped")
    void backupsWithoutAStartTimeAreSkipped() {
        ContinuityRepository repository = mock(ContinuityRepository.class);
        when(repository.findBackups(anyString())).thenReturn(new ArrayList<BackupRecord>());

        BackupRecord broken = backup("BKP-002");
        broken.setStartedAt(null);

        BackupStatusGateway gateway = mock(BackupStatusGateway.class);
        when(gateway.fetchRecentBackups(eq("PRD"), eq(50)))
                .thenReturn(new ArrayList<BackupRecord>(java.util.Arrays.asList(broken)));

        BackupSyncService service = new BackupSyncService(gateway, repository);

        assertEquals(0, service.sync("PRD"));
        verify(repository, never()).insertBackup(org.mockito.ArgumentMatchers.any(BackupRecord.class));
    }

    @Test
    @DisplayName("a record without an id is skipped")
    void backupsWithoutAnIdAreSkipped() {
        ContinuityRepository repository = mock(ContinuityRepository.class);
        when(repository.findBackups(anyString())).thenReturn(new ArrayList<BackupRecord>());

        BackupRecord broken = backup(null);
        BackupStatusGateway gateway = mock(BackupStatusGateway.class);
        when(gateway.fetchRecentBackups(eq("PRD"), eq(50)))
                .thenReturn(new ArrayList<BackupRecord>(java.util.Arrays.asList(broken)));

        BackupSyncService service = new BackupSyncService(gateway, repository);

        assertEquals(0, service.sync("PRD"));
        verify(repository, never()).insertBackup(org.mockito.ArgumentMatchers.any(BackupRecord.class));
    }

    @Test
    @DisplayName("the gateway describes where the data comes from")
    void gatewayDescribesItself() {
        ContinuityRepository repository = mock(ContinuityRepository.class);
        BackupStatusGateway gateway = mock(BackupStatusGateway.class);
        when(gateway.description()).thenReturn("SAP system over RFC (SAPPRD)");

        assertEquals("SAP system over RFC (SAPPRD)",
                new BackupSyncService(gateway, repository).gatewayDescription());
    }

    private BackupRecord backup(String id) {
        BackupRecord record = new BackupRecord();
        record.setBackupId(id);
        record.setSystemName("PRD");
        record.setBackupType(BackupRecord.TYPE_FULL);
        record.setStartedAt(LocalDateTime.now());
        record.setDurationMinutes(45);
        record.setSizeGb(900);
        record.setStatus(BackupRecord.STATUS_OK);
        record.setMedia("TAPE");
        return record;
    }
}
