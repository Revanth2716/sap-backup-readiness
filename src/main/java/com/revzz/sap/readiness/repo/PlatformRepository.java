package com.revzz.sap.readiness.repo;

import com.revzz.sap.readiness.domain.AppUser;
import com.revzz.sap.readiness.domain.AuditEntry;
import com.revzz.sap.readiness.domain.PortalRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Portal accounts and the audit trail.
 *
 * <p>The audit trail is written when something is recorded - a restore test
 * result, a report export - because those are the actions a continuity review
 * asks about afterwards.</p>
 */
@Repository
public class PlatformRepository {

    private final JdbcTemplate jdbc;

    public PlatformRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AppUser findAccount(String username) {
        List<AppUser> rows = jdbc.query("SELECT USERNAME, PASSWORD_HASH, DISPLAY_NAME, PORTAL_ROLE, "
                        + "ENABLED FROM PORTAL_USER WHERE USERNAME = ?",
                new RowMapper<AppUser>() {
                    @Override
                    public AppUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                        AppUser user = new AppUser();
                        user.setUsername(rs.getString("USERNAME"));
                        user.setPasswordHash(rs.getString("PASSWORD_HASH"));
                        user.setDisplayName(rs.getString("DISPLAY_NAME"));
                        user.setRole(PortalRole.valueOf(rs.getString("PORTAL_ROLE")));
                        user.setEnabled("Y".equalsIgnoreCase(rs.getString("ENABLED")));
                        return user;
                    }
                }, username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertAccount(AppUser user) {
        jdbc.update("INSERT INTO PORTAL_USER (USERNAME, PASSWORD_HASH, DISPLAY_NAME, PORTAL_ROLE, "
                        + "ENABLED) VALUES (?,?,?,?,?)",
                user.getUsername(), user.getPasswordHash(), user.getDisplayName(),
                user.getRole().name(), user.isEnabled() ? "Y" : "N");
    }

    public int countAccounts() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM PORTAL_USER", Integer.class);
        return count == null ? 0 : count;
    }

    public void insertAudit(AuditEntry entry) {
        jdbc.update("INSERT INTO PORTAL_AUDIT (AUD_ID, USERNAME, ACTION, DETAIL_TXT, AT_TS) "
                        + "VALUES (?,?,?,?,?)",
                entry.getAuditId(), entry.getUsername(), entry.getAction(), entry.getDetail(),
                Timestamp.valueOf(entry.getAt()));
    }

    /**
     * The most recent audit entries, newest first.
     *
     * <p>The limit is applied in Java on purpose: {@code LIMIT} is not
     * portable to SAP HANA, and this project runs against both.</p>
     */
    public List<AuditEntry> findRecentAudit(int limit) {
        List<AuditEntry> all = jdbc.query("SELECT AUD_ID, USERNAME, ACTION, DETAIL_TXT, AT_TS "
                        + "FROM PORTAL_AUDIT ORDER BY AT_TS DESC",
                new RowMapper<AuditEntry>() {
                    @Override
                    public AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
                        AuditEntry entry = new AuditEntry();
                        entry.setAuditId(rs.getString("AUD_ID"));
                        entry.setUsername(rs.getString("USERNAME"));
                        entry.setAction(rs.getString("ACTION"));
                        entry.setDetail(rs.getString("DETAIL_TXT"));
                        Timestamp at = rs.getTimestamp("AT_TS");
                        entry.setAt(at == null ? null : at.toLocalDateTime());
                        return entry;
                    }
                });
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(0, Math.max(0, limit));
    }
}
