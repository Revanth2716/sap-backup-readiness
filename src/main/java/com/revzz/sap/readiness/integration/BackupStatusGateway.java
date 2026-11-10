package com.revzz.sap.readiness.integration;

import com.revzz.sap.readiness.domain.BackupRecord;

import java.util.List;

/**
 * The boundary between the readiness application and a real SAP landscape.
 *
 * <p>Everything above this interface works on {@link BackupRecord} objects
 * and knows nothing about RFC, JCo or ABAP. Everything below it knows how
 * to ask a real system for its backup history.</p>
 *
 * <p>Two implementations exist:</p>
 * <ul>
 *   <li>{@code LocalBackupStatusGateway} - the default, reads the local
 *       database. It is what the demo runs on.</li>
 *   <li>{@code RfcBackupStatusGateway} - the live path, calls the
 *       RFC-enabled ABAP function module {@code Z_BACKUP_STATUS} through the
 *       SAP Java Connector. It is only compiled when the {@code with-jco}
 *       Maven profile is active, because the SAP Java Connector is not
 *       published on Maven Central.</li>
 * </ul>
 */
public interface BackupStatusGateway {

    /** A short description of where the data comes from, shown in the UI. */
    String description();

    /**
     * The most recent backup jobs of one system.
     *
     * @param systemName the system to read
     * @param limit      the maximum number of records to return
     */
    List<BackupRecord> fetchRecentBackups(String systemName, int limit);
}
