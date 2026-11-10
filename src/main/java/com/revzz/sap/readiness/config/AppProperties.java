package com.revzz.sap.readiness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application configuration.
 *
 * <p>The policy lives here, not in the code: how old a backup may get, how
 * old a restore test may be, and how close to full the database may get.
 * These are the numbers a customer agrees with its auditors, so they
 * belong in configuration.</p>
 */
@Component
@ConfigurationProperties(prefix = "readiness")
public class AppProperties {

    private String applicationName = "SAP Backup & Restore Readiness";
    private String sapSystem = "PRD";
    private boolean seedDemoUsers = true;
    private boolean seedDemoData = true;
    private String demoPassword = "demo1234";
    private Seed seed = new Seed();
    private Policy policy = new Policy();
    private Mail mail = new Mail();

    public static class Seed {
        private int capacityDays = 30;
        private long randomSeed = 20261110L;

        public int getCapacityDays() {
            return capacityDays;
        }

        public void setCapacityDays(int capacityDays) {
            this.capacityDays = capacityDays;
        }

        public long getRandomSeed() {
            return randomSeed;
        }

        public void setRandomSeed(long randomSeed) {
            this.randomSeed = randomSeed;
        }
    }

    public static class Policy {
        private int maxBackupAgeHours = 26;
        private int maxIncrementalAgeHours = 8;
        private int maxRestoreTestAgeDays = 30;
        private int maxFullBackupAgeDays = 7;
        private int capacityWarningDays = 30;

        public int getMaxBackupAgeHours() {
            return maxBackupAgeHours;
        }

        public void setMaxBackupAgeHours(int maxBackupAgeHours) {
            this.maxBackupAgeHours = maxBackupAgeHours;
        }

        public int getMaxIncrementalAgeHours() {
            return maxIncrementalAgeHours;
        }

        public void setMaxIncrementalAgeHours(int maxIncrementalAgeHours) {
            this.maxIncrementalAgeHours = maxIncrementalAgeHours;
        }

        public int getMaxRestoreTestAgeDays() {
            return maxRestoreTestAgeDays;
        }

        public void setMaxRestoreTestAgeDays(int maxRestoreTestAgeDays) {
            this.maxRestoreTestAgeDays = maxRestoreTestAgeDays;
        }

        public int getMaxFullBackupAgeDays() {
            return maxFullBackupAgeDays;
        }

        public void setMaxFullBackupAgeDays(int maxFullBackupAgeDays) {
            this.maxFullBackupAgeDays = maxFullBackupAgeDays;
        }

        public int getCapacityWarningDays() {
            return capacityWarningDays;
        }

        public void setCapacityWarningDays(int capacityWarningDays) {
            this.capacityWarningDays = capacityWarningDays;
        }
    }

    public static class Mail {
        private boolean enabled = false;
        private String from = "readiness@example.com";
        private String to = "basis-team@example.com";
        private String subjectPrefix = "[SAP Continuity]";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getSubjectPrefix() {
            return subjectPrefix;
        }

        public void setSubjectPrefix(String subjectPrefix) {
            this.subjectPrefix = subjectPrefix;
        }
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getSapSystem() {
        return sapSystem;
    }

    public void setSapSystem(String sapSystem) {
        this.sapSystem = sapSystem;
    }

    public boolean isSeedDemoUsers() {
        return seedDemoUsers;
    }

    public void setSeedDemoUsers(boolean seedDemoUsers) {
        this.seedDemoUsers = seedDemoUsers;
    }

    public boolean isSeedDemoData() {
        return seedDemoData;
    }

    public void setSeedDemoData(boolean seedDemoData) {
        this.seedDemoData = seedDemoData;
    }

    public String getDemoPassword() {
        return demoPassword;
    }

    public void setDemoPassword(String demoPassword) {
        this.demoPassword = demoPassword;
    }

    public Seed getSeed() {
        return seed;
    }

    public void setSeed(Seed seed) {
        this.seed = seed;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }
}
