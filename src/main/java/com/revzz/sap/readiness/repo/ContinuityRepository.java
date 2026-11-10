package com.revzz.sap.readiness.repo;

import com.revzz.sap.readiness.domain.BackupRecord;
import com.revzz.sap.readiness.domain.CapacitySample;
import com.revzz.sap.readiness.domain.RestoreTest;
import com.revzz.sap.readiness.domain.SystemProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The continuity data: the landscape, the backups, the restore tests and
 * the capacity history.
 *
 * <p>All statements are portable ANSI SQL, so the same repository runs
 * against the local H2 database and against a live SAP HANA instance.</p>
 */
@Repository
public class ContinuityRepository {

    private final JdbcTemplate jdbc;

    public ContinuityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<SystemProfile> SYSTEM_MAPPER = new RowMapper<SystemProfile>() {
        @Override
        public SystemProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
            SystemProfile profile = new SystemProfile();
            profile.setName(rs.getString("SYS_NAME"));
            profile.setSid(rs.getString("SID"));
            profile.setClient(rs.getString("CLIENT"));
            profile.setDescription(rs.getString("DESCRIPTION"));
            profile.setOwner(rs.getString("OWNER"));
            profile.setCritical("Y".equalsIgnoreCase(rs.getString("CRITICAL")));
            profile.setRpoHours(rs.getInt("RPO_HOURS"));
            profile.setRtoHours(rs.getInt("RTO_HOURS"));
            profile.setMaxDbGb(rs.getInt("MAX_DB_GB"));
            return profile;
        }
    };

    private static final RowMapper<BackupRecord> BACKUP_MAPPER = new RowMapper<BackupRecord>() {
        @Override
        public BackupRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            BackupRecord record = new BackupRecord();
            record.setBackupId(rs.getString("BACKUP_ID"));
            record.setSystemName(rs.getString("SYS_NAME"));
            record.setBackupType(rs.getString("BACKUP_TYPE"));
            Timestamp started = rs.getTimestamp("STARTED_AT");
            record.setStartedAt(started == null ? null : started.toLocalDateTime());
            Timestamp finished = rs.getTimestamp("FINISHED_AT");
            record.setFinishedAt(finished == null ? null : finished.toLocalDateTime());
            record.setDurationMinutes(rs.getInt("DURATION_MIN"));
            record.setSizeGb(rs.getInt("SIZE_GB"));
            record.setStatus(rs.getString("STATUS"));
            record.setMedia(rs.getString("MEDIA"));
            return record;
        }
    };

    private static final RowMapper<RestoreTest> TEST_MAPPER = new RowMapper<RestoreTest>() {
        @Override
        public RestoreTest mapRow(ResultSet rs, int rowNum) throws SQLException {
            RestoreTest test = new RestoreTest();
            test.setTestId(rs.getString("TEST_ID"));
            test.setSystemName(rs.getString("SYS_NAME"));
            Timestamp tested = rs.getTimestamp("TESTED_AT");
            test.setTestedAt(tested == null ? null : tested.toLocalDateTime());
            test.setResult(rs.getString("RESULT"));
            test.setDurationMinutes(rs.getInt("DURATION_MIN"));
            test.setPerformedBy(rs.getString("PERFORMED_BY"));
            test.setNotes(rs.getString("NOTES"));
            return test;
        }
    };

    private static final RowMapper<CapacitySample> CAPACITY_MAPPER =
            new RowMapper<CapacitySample>() {
                @Override
                public CapacitySample mapRow(ResultSet rs, int rowNum) throws SQLException {
                    CapacitySample sample = new CapacitySample();
                    sample.setSystemName(rs.getString("SYS_NAME"));
                    sample.setSampleDate(rs.getDate("SAMPLE_AT").toLocalDate());
                    sample.setDbSizeGb(rs.getInt("DB_SIZE_GB"));
                    sample.setLogSizeGb(rs.getInt("LOG_SIZE_GB"));
                    return sample;
                }
            };

    // -- the landscape --------------------------------------------------------

    public List<SystemProfile> findSystems() {
        return jdbc.query("SELECT SYS_NAME, SID, CLIENT, DESCRIPTION, OWNER, CRITICAL, RPO_HOURS, "
                + "RTO_HOURS, MAX_DB_GB FROM SYS_CONFIG ORDER BY CRITICAL DESC, SYS_NAME",
                SYSTEM_MAPPER);
    }

    public SystemProfile findSystem(String name) {
        List<SystemProfile> rows = jdbc.query("SELECT SYS_NAME, SID, CLIENT, DESCRIPTION, OWNER, "
                        + "CRITICAL, RPO_HOURS, RTO_HOURS, MAX_DB_GB FROM SYS_CONFIG "
                        + "WHERE SYS_NAME = ?",
                SYSTEM_MAPPER, name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** How many systems are configured - used to keep the seeder idempotent. */
    public int countSystems() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM SYS_CONFIG", Integer.class);
        return count == null ? 0 : count;
    }

    public void insertSystem(SystemProfile profile) {        jdbc.update("INSERT INTO SYS_CONFIG (SYS_NAME, SID, CLIENT, DESCRIPTION, OWNER, CRITICAL, "
                        + "RPO_HOURS, RTO_HOURS, MAX_DB_GB) VALUES (?,?,?,?,?,?,?,?,?)",
                profile.getName(), profile.getSid(), profile.getClient(), profile.getDescription(),
                profile.getOwner(), profile.isCritical() ? "Y" : "N", profile.getRpoHours(),
                profile.getRtoHours(), profile.getMaxDbGb());
    }

    // -- backups --------------------------------------------------------------

    public List<BackupRecord> findBackups(String systemName) {
        return jdbc.query("SELECT BACKUP_ID, SYS_NAME, BACKUP_TYPE, STARTED_AT, FINISHED_AT, "
                        + "DURATION_MIN, SIZE_GB, STATUS, MEDIA FROM SYS_BACKUP "
                        + "WHERE SYS_NAME = ? ORDER BY STARTED_AT DESC",
                BACKUP_MAPPER, systemName);
    }

    public List<BackupRecord> findAllBackups() {
        return jdbc.query("SELECT BACKUP_ID, SYS_NAME, BACKUP_TYPE, STARTED_AT, FINISHED_AT, "
                + "DURATION_MIN, SIZE_GB, STATUS, MEDIA FROM SYS_BACKUP ORDER BY STARTED_AT DESC",
                BACKUP_MAPPER);
    }

    public void insertBackup(BackupRecord record) {
        jdbc.update("INSERT INTO SYS_BACKUP (BACKUP_ID, SYS_NAME, BACKUP_TYPE, STARTED_AT, "
                        + "FINISHED_AT, DURATION_MIN, SIZE_GB, STATUS, MEDIA) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                record.getBackupId(), record.getSystemName(), record.getBackupType(),
                Timestamp.valueOf(record.getStartedAt()),
                record.getFinishedAt() == null ? null : Timestamp.valueOf(record.getFinishedAt()),
                record.getDurationMinutes(), record.getSizeGb(), record.getStatus(),
                record.getMedia());
    }

    // -- restore tests --------------------------------------------------------

    public List<RestoreTest> findRestoreTests(String systemName) {
        return jdbc.query("SELECT TEST_ID, SYS_NAME, TESTED_AT, RESULT, DURATION_MIN, "
                        + "PERFORMED_BY, NOTES FROM SYS_RESTORE_TEST WHERE SYS_NAME = ? "
                        + "ORDER BY TESTED_AT DESC",
                TEST_MAPPER, systemName);
    }

    public List<RestoreTest> findAllRestoreTests() {
        return jdbc.query("SELECT TEST_ID, SYS_NAME, TESTED_AT, RESULT, DURATION_MIN, "
                + "PERFORMED_BY, NOTES FROM SYS_RESTORE_TEST ORDER BY TESTED_AT DESC", TEST_MAPPER);
    }

    public void insertRestoreTest(RestoreTest test) {
        jdbc.update("INSERT INTO SYS_RESTORE_TEST (TEST_ID, SYS_NAME, TESTED_AT, RESULT, "
                        + "DURATION_MIN, PERFORMED_BY, NOTES) VALUES (?,?,?,?,?,?,?)",
                test.getTestId(), test.getSystemName(), Timestamp.valueOf(test.getTestedAt()),
                test.getResult(), test.getDurationMinutes(), test.getPerformedBy(), test.getNotes());
    }

    // -- capacity -------------------------------------------------------------

    public List<CapacitySample> findCapacity(String systemName) {
        return jdbc.query("SELECT SYS_NAME, SAMPLE_AT, DB_SIZE_GB, LOG_SIZE_GB FROM SYS_CAPACITY "
                + "WHERE SYS_NAME = ? ORDER BY SAMPLE_AT", CAPACITY_MAPPER, systemName);
    }

    public void insertCapacity(CapacitySample sample) {
        jdbc.update("INSERT INTO SYS_CAPACITY (SYS_NAME, SAMPLE_AT, DB_SIZE_GB, LOG_SIZE_GB) "
                        + "VALUES (?,?,?,?)",
                sample.getSystemName(), Date.valueOf(sample.getSampleDate()),
                sample.getDbSizeGb(), sample.getLogSizeGb());
    }
}
