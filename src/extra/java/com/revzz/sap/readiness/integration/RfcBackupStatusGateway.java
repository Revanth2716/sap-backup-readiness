package com.revzz.sap.readiness.integration;

import com.revzz.sap.readiness.domain.BackupRecord;
import com.sap.jco.JCoDestination;
import com.sap.jco.JCoException;
import com.sap.jco.JCoFunction;
import com.sap.jco.JCoTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Live SAP gateway - RFC through the SAP Java Connector.
 *
 * <p><b>Only compiled when the {@code with-jco} Maven profile is active</b>,
 * because the SAP Java Connector is published on SAP's own Maven repository
 * rather than on Maven Central. The default build therefore compiles and
 * tests anywhere.</p>
 *
 * <pre>
 *   mvn -Pwith-jco clean package
 * </pre>
 *
 * <p>The remote function module {@code Z_BACKUP_STATUS} must be
 * <b>Remote Enabled</b> in transaction SE37, otherwise SAP will refuse the
 * call with {@code RFC function module not found} - which is the single most
 * common reason an RFC adapter that "should work" does not.</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "readiness.gateway", havingValue = "jco")
public class RfcBackupStatusGateway implements BackupStatusGateway {

    private static final Logger LOG = LoggerFactory.getLogger(RfcBackupStatusGateway.class);

    /** RFC-enabled ABAP function module that reads the backup catalog. */
    private static final String FUNCTION_BACKUP_STATUS = "Z_BACKUP_STATUS";

    private final String destinationName;

    public RfcBackupStatusGateway() {
        this("SAPPRD");
    }

    public RfcBackupStatusGateway(String destinationName) {
        this.destinationName = destinationName;
    }

    @Override
    public String description() {
        return "SAP system over RFC (" + destinationName + ")";
    }

    @Override
    public List<BackupRecord> fetchRecentBackups(String systemName, int limit) {
        List<BackupRecord> backups = new ArrayList<BackupRecord>();

        try {
            JCoDestination destination = buildDestination();
            JCoFunction function = JCoFunction.getInstance(destination, FUNCTION_BACKUP_STATUS);
            function.getParameter("IV_SYSTEM").setString(systemName);
            function.getParameter("IV_LIMIT").setInt(limit);
            function.execute(destination);

            JCoTable table = function.getTable("ET_BACKUPS");
            if (table == null) {
                return backups;
            }
            table.first();
            while (!table.isEof()) {
                BackupRecord record = new BackupRecord();
                record.setBackupId(table.getField("BACKUP_ID").getString());
                record.setSystemName(table.getField("SYS_NAME").getString());
                record.setBackupType(table.getField("BACKUP_TYPE").getString());
                record.setStartedAt(toLocalDateTime(table.getField("STARTED_AT").getString()));
                record.setFinishedAt(toLocalDateTime(table.getField("FINISHED_AT").getString()));
                record.setDurationMinutes(table.getField("DURATION_MIN").getInt());
                record.setSizeGb(table.getField("SIZE_GB").getInt());
                record.setStatus(table.getField("STATUS").getString());
                record.setMedia(table.getField("MEDIA").getString());
                backups.add(record);
                table.next();
            }
            LOG.info("Fetched {} backup records over RFC for {}", backups.size(), systemName);
        } catch (JCoException exception) {
            LOG.error("Could not read the backup catalog of {} over RFC: {}",
                    systemName, exception.getMessage());
        }
        return backups;
    }

    private java.time.LocalDateTime toLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private JCoDestination buildDestination() throws JCoException {
        Properties properties = new Properties();
        try {
            java.io.InputStream stream = new java.io.FileInputStream("jco.properties");
            try {
                properties.load(stream);
            } finally {
                stream.close();
            }
        } catch (java.io.IOException exception) {
            throw new JCoException("jco.properties not found - copy jco.properties.example");
        }

        JCoDestination destination = JCoDestination.createDestination(destinationName);
        destination.setApplicationServer(properties.getProperty("sap.applicationServer"));
        destination.setSystemNumber(properties.getProperty("sap.systemNumber"));
        destination.setClient(properties.getProperty("sap.client"));
        destination.setUser(properties.getProperty("sap.user"));
        destination.setPassword(properties.getProperty("sap.password"));
        destination.setPoolSize(2);
        return destination;
    }
}
