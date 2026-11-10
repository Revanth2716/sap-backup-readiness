# Troubleshooting

The problems that actually happen, and what they mean.

---

## 1. The application

### `Port 8084 was already in use`

Another process holds the port.

```bash
# Windows
netstat -ano | findstr :8084
tasklist /FI "PID eq 12345"

# Linux
lsof -i :8084
```

Or start on a different port:

```bash
java -jar target/sap-backup-readiness.jar --server.port=9090
```

> If you started the application from the IDE and it is still running, the
> old process still holds the **JAR file open**. `mvn clean` then fails with
> *"the process cannot access the file because it is being used by another
> process"*. Stop the application before cleaning.

### `Table "SYS_CONFIG" not found`

The schema was not created.

```bash
# The schema runs at start-up from classpath:db/schema.sql
java -jar target/sap-backup-readiness.jar
```

Check the log for `Copying 8 resources` and the H2 console output. If the
schema ran and the tables are still missing, check that
`spring.sql.init.mode` is `always`.

### The application starts but every page is empty

The seeding did not run.

```yaml
readiness:
  seed-demo-data: true
```

The seeder is **idempotent**: it does nothing when `SYS_CONFIG` already has
rows. If you want the demo landscape back, delete the database file:

```bash
rm -rf data/          # Linux / macOS
# Windows: delete the data folder
```

### The landscape shows the wrong dates

The seed is anchored to the **current time**, and the backups are anchored to
the most recent 22:00 that is already in the past. If the application has not
been started for a while, the oldest data is old — that is correct behaviour.
Restart it to reload the landscape.

---

## 2. A finding looks wrong

### "The newest recoverable backup is 216 h old" but the backups are running

Check whether the backups actually **succeeded**. The engine separates the
newest *attempt* from the newest *successful* backup, and a job that ran and
failed is not a recovery point.

```bash
curl -s -b cookies.txt http://localhost:8084/api/v1/systems/PRD | python -m json.tool
```

Look at `lastFullBackup.status` and `lastGoodBackup.backupId`.

### "Recovery has never been tested" for a system that was tested last year

The restore test history is a **manual record**. It is only what somebody
entered. If a test was done and never recorded, the portal is right and the
process is wrong.

### The score changed without anything changing

The score depends on **the current time**, not on the data. A backup that was
30 minutes inside the RPO yesterday is 31 minutes inside it today, and a
system that was 22 hours old crosses the 24-hour line at some point in the
night. That is the RPO working as agreed.

### Two systems have the same score but different statuses

The severity is the **worst finding**, not the score band. Two systems can
score the same and be in different bands. That is deliberate — see
[`ARCHITECTURE.md`](ARCHITECTURE.md#5-the-score-and-why-the-severity-is-not-the-score).

### The capacity forecast says "not growing"

The system is not growing, or the history is too short. The forecast needs at
least **two** samples; below that it returns `null` and no finding is raised.
Check the number of samples on the capacity chart.

---

## 3. RFC and the live SAP system

### `RFC function module not found` / `not remote enabled`

**The single most common RFC problem.** The function module was created in
`SE37` but **Remote Enabled** was not ticked.

1. `SE37` → open `Z_BACKUP_STATUS` → *Attributes* → tick **Remote Enabled**
2. Save and activate

> The error surfaces as an `SM58` entry on the **calling** system, not as a
> dump on the callee. That asymmetry is what makes it confusing: the callee
> looks fine.

### The error only appears in `SM58`

`SM58` on the **calling** system shows the RFC errors. Open it after a
failed call — the long text names the reason.

### `jco.properties not found`

The gateway reads `jco.properties` from the **working directory** of the
process.

```bash
cp jco.properties.example jco.properties
# and make sure you start the application from the directory that has it
java -jar target/sap-backup-readiness.jar
```

### `Communication link failure` / `RFC destination not found`

The destination name in the code must match the name in `SM59` **exactly**,
including the case. The default is `SAPPRD`:

```java
public RfcBackupStatusGateway() {
    this("SAPPRD");
}
```

### `Logon failed` / `Authentication failed`

The user in `jco.properties` cannot log on to the application server. Check
the password, the client (`sap.client`) and that the user is not locked.

### The call works but returns nothing

The function module found no rows in `M_BACKUP_CATALOG_FILES` for the system
name. The `system_name` column holds the **SAP HANA system name**, which is
not always the same string as the SID. Check:

```sql
SELECT DISTINCT system_name FROM m_backup_catalog_files;
```

and pass the value from that query.

### `M_BACKUP_CATALOG_FILES` does not exist

It is a **SAP HANA** system view. On Oracle or Sybase the function module has
to be adapted to read that platform's backup log instead — the interface in
`BackupStatusGateway` does not change, only the ABAP behind it.

---

## 4. The build

### `Could not resolve dependencies for project ... com.sap.jco:sapjco3`

The SAP Java Connector is not on Maven Central. Either add SAP's repository to
`~/.m2/settings.xml` (see [`DEPLOYMENT.md`](DEPLOYMENT.md#51-build-with-the-sap-java-connector))
or build **without** the profile:

```bash
mvn clean package          # the default build, no SAP repository needed
```

### `maven-compiler-plugin` fails on a Java 8 feature

The project is compiled with `source/target = 1.8` on purpose, so it stays
deployable on the NetWeaver 7.5 Java stack. Do not raise it.

### The build fails with "the process cannot access the file because it is being used"

A running application holds the JAR open. Stop it before `mvn clean`.

---

## 5. Tests

### `PageRenderTest` fails with 500

A template expression does not resolve. The response body contains the
`TemplateProcessingException`; the first line of the stack trace names the
template and the line.

The three most common causes in this project:

| Symptom | Cause |
|---|---|
| `EL1008E: Property or field 'xxx' cannot be found` | The model attribute is not in the controller — usually a new value that was added to the template but not to the model |
| `EL1007E: Property or field 'xxx' cannot be found on null` | A `null` object — usually a forecast for a system with fewer than two capacity samples |
| `EL1028E: Expected to find attribute 'xxx'` | A `th:each` over a value that is not in the model |

### The landscape test fails with "expected 25 but was 0"

The seeder did not run, or the schema was rebuilt under it. The seeders are
idempotent and run in order: `DemoUserSeeder` (`@Order(1)`) then
`ContinuitySeeder` (`@Order(2)`).

### A test fails only when the whole suite runs

Almost always the H2 database being shared between test classes. The test
profile uses `jdbc:h2:mem:readinesstest;DB_CLOSE_DELAY=-1`, so the database
survives between classes and the schema is only created once. A test that
assumes a fresh database will fail depending on the order.

---

## 6. The report

### The report does not mention a system

The report is rendered from the current readiness assessment. If a system was
added after the last start, restart the application so the seeder picks it up.

### The downloaded file has the wrong name

The download sets `Content-Disposition` to
`continuity-readiness-report.md`. A browser that ignores the header saves it
as `report.md` — the content is still Markdown.

---

## 7. Where to look first

| Symptom | Look at |
|---|---|
| A finding is wrong | `GET /api/v1/systems/{name}` — it returns the score, the findings and the underlying records |
| The data is stale | `GET /api/v1/health` — it returns the system count and the average score |
| Something was changed | The **Recent activity** table on the dashboard and on the report screen |
| The application misbehaved | The log — every sign in, recorded restore test, sync and report export is written to the audit trail, and the application log carries the stack trace |
