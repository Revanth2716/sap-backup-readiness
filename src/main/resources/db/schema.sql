-- ---------------------------------------------------------------------------
-- SAP Backup & Restore Readiness - database schema
--
-- The continuity picture a BASIS team is accountable for:
--
--   SYS_CONFIG      the landscape - which system, which client, which RPO and
--                   RTO was agreed, and how big the database may get
--   SYS_BACKUP      every backup job: when it ran, how long it took, how
--                   big it was, and whether it succeeded
--   SYS_RESTORE_TEST  the evidence that a restore actually works
--   SYS_CAPACITY    daily database size, used to forecast when it fills up
--
-- In a live landscape the backup rows come from the DBA cockpit and the
-- backup tooling's own log; the restore tests and the capacity history are
-- maintained by the operating team.
--
-- All statements are portable ANSI SQL: valid on H2 (local) and SAP HANA.
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS SYS_CAPACITY;
DROP TABLE IF EXISTS SYS_RESTORE_TEST;
DROP TABLE IF EXISTS SYS_BACKUP;
DROP TABLE IF EXISTS SYS_CONFIG;
DROP TABLE IF EXISTS PORTAL_AUDIT;
DROP TABLE IF EXISTS PORTAL_USER;
DROP TABLE IF EXISTS SAPUSER;

CREATE TABLE SAPUSER (
    USERID   VARCHAR(20) NOT NULL,
    FULLNAME VARCHAR(60) NOT NULL,
    UFLAG    VARCHAR(1)  NOT NULL,
    DEPT     VARCHAR(40) NOT NULL,
    CONSTRAINT PK_RDY_SAPUSER PRIMARY KEY (USERID)
);

-- The landscape: one row per system, with the agreed objectives.
CREATE TABLE SYS_CONFIG (
    SYS_NAME    VARCHAR(20) NOT NULL,
    SID         VARCHAR(3)  NOT NULL,
    CLIENT      VARCHAR(3)  NOT NULL,
    DESCRIPTION VARCHAR(80) NOT NULL,
    OWNER       VARCHAR(20) NOT NULL,
    CRITICAL    VARCHAR(1)  NOT NULL,   -- Y / N
    RPO_HOURS   INTEGER     NOT NULL,   -- recovery point objective
    RTO_HOURS   INTEGER     NOT NULL,   -- recovery time objective
    MAX_DB_GB   INTEGER     NOT NULL,   -- the size the database may reach
    CONSTRAINT PK_RDY_SYS_CONFIG PRIMARY KEY (SYS_NAME)
);

-- One row per backup job.
CREATE TABLE SYS_BACKUP (
    BACKUP_ID    VARCHAR(24) NOT NULL,
    SYS_NAME     VARCHAR(20) NOT NULL,
    BACKUP_TYPE  VARCHAR(12) NOT NULL,   -- FULL / INCREMENTAL / DIFFERENTIAL
    STARTED_AT   TIMESTAMP   NOT NULL,
    FINISHED_AT  TIMESTAMP   NULL,
    DURATION_MIN INTEGER     NOT NULL,
    SIZE_GB      INTEGER     NOT NULL,
    STATUS       VARCHAR(8)  NOT NULL,   -- OK / FAILED / RUNNING
    MEDIA        VARCHAR(40) NOT NULL,
    CONSTRAINT PK_RDY_SYS_BACKUP PRIMARY KEY (BACKUP_ID),
    CONSTRAINT FK_RDY_SYS_BACKUP FOREIGN KEY (SYS_NAME) REFERENCES SYS_CONFIG (SYS_NAME)
);

-- The evidence that a restore works.
CREATE TABLE SYS_RESTORE_TEST (
    TEST_ID      VARCHAR(24) NOT NULL,
    SYS_NAME     VARCHAR(20) NOT NULL,
    TESTED_AT    TIMESTAMP   NOT NULL,
    RESULT       VARCHAR(8)  NOT NULL,   -- OK / FAILED
    DURATION_MIN INTEGER     NOT NULL,
    PERFORMED_BY VARCHAR(20) NOT NULL,
    NOTES        VARCHAR(200) NOT NULL,
    CONSTRAINT PK_RDY_SYS_RESTORE PRIMARY KEY (TEST_ID),
    CONSTRAINT FK_RDY_SYS_RESTORE FOREIGN KEY (SYS_NAME) REFERENCES SYS_CONFIG (SYS_NAME)
);

-- Daily database size, used to forecast when the system fills up.
CREATE TABLE SYS_CAPACITY (
    SYS_NAME  VARCHAR(20) NOT NULL,
    SAMPLE_AT DATE       NOT NULL,
    DB_SIZE_GB INTEGER    NOT NULL,
    LOG_SIZE_GB INTEGER   NOT NULL,
    CONSTRAINT PK_RDY_SYS_CAPACITY PRIMARY KEY (SYS_NAME, SAMPLE_AT),
    CONSTRAINT FK_RDY_SYS_CAPACITY FOREIGN KEY (SYS_NAME) REFERENCES SYS_CONFIG (SYS_NAME)
);

-- Portal accounts and the audit trail.
CREATE TABLE PORTAL_USER (
    USERNAME      VARCHAR(40) NOT NULL,
    PASSWORD_HASH VARCHAR(120) NOT NULL,
    DISPLAY_NAME  VARCHAR(60) NOT NULL,
    PORTAL_ROLE   VARCHAR(20) NOT NULL,
    ENABLED       VARCHAR(1)  NOT NULL,
    CONSTRAINT PK_RDY_PORTAL_USER PRIMARY KEY (USERNAME)
);

CREATE TABLE PORTAL_AUDIT (
    AUD_ID     VARCHAR(20) NOT NULL,
    USERNAME   VARCHAR(40) NOT NULL,
    ACTION     VARCHAR(40) NOT NULL,
    DETAIL_TXT VARCHAR(400) NOT NULL,
    AT_TS      TIMESTAMP   NOT NULL,
    CONSTRAINT PK_RDY_PORTAL_AUDIT PRIMARY KEY (AUD_ID)
);
