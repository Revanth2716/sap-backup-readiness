# Honest status

What in this repository is **verified working**, what is **written but not yet
executed**, and what is **modelled rather than measured**. Read this before
describing the project in an interview.

---

## 1. Verified working today

| Item | Evidence |
|---|---|
| The application builds | `mvn clean package` → `BUILD SUCCESS`, executable JAR produced |
| The whole test suite passes | `mvn test` → **53 tests, 0 failures, 0 errors** |
| The application starts and serves | `java -jar target/sap-backup-readiness.jar` → HTTP 200 on `/api/v1/health` |
| Every screen renders | Six screens captured from the running application into `docs/screenshots/` |
| The readiness rules are right | `ReadinessServiceTest` — each rule on its own: a failed backup, a breached RPO, a failed restore test, a stale one, a missing one, a capacity risk |
| The severity rule holds | `severityIsTheWorstFindingNotTheScoreBand` — a failed restore test stays critical at a score of 75 |
| The "report once" rule holds | `brokenChainIsReportedOnce` — one broken chain, one finding for that cause |
| The seeded landscape tells the story | `ContinuityLandscapeTest` — PRD 25 critical, DEV 75 critical, BW 50 high, QA 70 high, SOL 100 clean |
| The capacity forecast works | The forecast of BW reaches its 1000 GB limit in 21 days at 6 GB a day, and the finding is raised |
| The sync is additive | `BackupSyncServiceTest` — running it twice imports nothing the second time |
| The API works | `ApiControllerTest` — health, readiness, one system, findings, capacity, the markdown report |
| The API is authenticated | Every endpoint except `/api/v1/health` returns 401 for an anonymous caller |
| Role gating works | `PageRenderTest` — a viewer is blocked from the report, an analyst cannot record a restore test |
| The write path works | Recording a restore test inserts the record, recalculates the score and writes to the audit trail |
| The log is clean | No exception or error in the application log during a full pass |

**Reproduce all of it:**

```bash
mvn clean package
java -jar target/sap-backup-readiness.jar
```

---

## 2. Written but not yet executed on a live SAP system

| Item | Status | Why |
|---|---|---|
| `abap/Z_BACKUP_STATUS.abap` | **Not yet compiled on NetWeaver** | No SAP system was available in the build environment. The code follows NetWeaver 7.5 / ABAP 7.40+ syntax and the Remote-Enabled flag is documented in the header, but it has not been through the ABAP syntax check. |
| `abap/Z_BACKUP_TYPES.abap` | **Not yet created in SE11** | This is the DDIC structure the function module imports. |
| `src/extra/java/.../RfcBackupStatusGateway.java` | **Not yet compiled** | The SAP Java Connector (`com.sap.jco:sapjco3`) is published on SAP's own Maven repository, not Maven Central, and that host was unreachable from the build environment. The `with-jco` profile declares the exact coordinates needed. |
| The HANA JDBC path | **Configured, not exercised** | The `hana` profile and the datasource configuration are in place; no HANA instance was available. |
| The WAR deployment on NetWeaver 7.5 | **Designed, not executed** | `application.yml` and `DEPLOYMENT.md` describe it; the project is built as a JAR, and the `pom.xml` change to `war` is documented but not committed. |
| `M_BACKUP_CATALOG_FILES` | **Not read from a live database** | This is the HANA system view the function module reads. On Oracle or Sybase the function module has to be adapted. |

**What this means in an interview:** say *"the SAP-side artifacts are written
against NetWeaver 7.5 semantics and are ready to compile, but I could not run
them because I did not have a system available."*

**The plan to close the gap** is in [`DEPLOYMENT.md`](DEPLOYMENT.md): create
the DDIC structure in `SE11`, paste the function module into `SE37` with
**Remote-Enabled** ticked, create the RFC destination in `SM59`, and build the
JCo adapter against SAP's Maven repository.

---

## 3. Modelled rather than measured

| Area | What is modelled | What production needs |
|---|---|---|
| The backup catalog | A seeded history of full and incremental backups, with one deliberate failure | The real catalog from `M_BACKUP_CATALOG_FILES`, or the backup tool's own log |
| The restore test history | A seeded set of restore tests, including one that failed | A real, maintained record — the evidence is manual everywhere, including in production |
| The capacity history | A seeded daily size with a constant daily growth | The real daily size, collected from the DBA cockpit |
| The landscape | Five systems | Every system in the landscape, DEV through PRD |
| The objectives (RPO, RTO, maximum size) | Seeded values | Agreed with the customer and with the auditor, per system |
| The findings | Derived from the rules | Reviewed and refined by the operating team |

---

## 4. Known limitations — the ones an interviewer would find

1. **The restore test history is manual.** The portal scores the evidence, it
   does not create it. A test that was done and never recorded still shows as
   "never tested" — correctly, because that is the state of the evidence.
2. **A restore test is not linked to the backup it restored.** The portal knows
   *when* recovery was last proven, not *which* backup was proven. Linking the
   two is the first thing to build with a real customer.
3. **The capacity forecast is linear.** It extrapolates the average daily
   growth of the observed window. It does not model seasonality, and it does
   not include the log volume — which in many landscapes is what fills up
   first.
4. **The schema is rebuilt on every start.** `schema.sql` drops and recreates
   every table and the seeders reload the landscape. That is predictable for a
   demo and wrong for production — migrations replace it, and
   `spring.sql.init.mode` becomes `never`.
5. **The sync needs a session.** There is no machine-to-machine endpoint, so
   an unattended scheduler has to carry a session cookie. In production this
   becomes a service account on a separate port.
6. **The seeded data is dated relative to the moment of seeding.** A system
   that was healthy when the seed ran drifts — a backup that was inside the
   RPO crosses the line during the night. That is correct behaviour, but it
   means the demo landscape changes as the days pass.
7. **One landscape, five systems.** There is no comparison against a previous
   period, so "this system used to be better" is not a question the tool can
   answer yet.

---

## 5. What the project is not

* It is **not** connected to a live SAP system out of the box — it runs
  against the seeded dataset, and the RFC path needs a system that was not
  available.
* It is **not** a backup tool. It never creates a backup and never schedules
  one. It reads what happened and judges it.
* It is **not** a restore tool. It does not restore anything — it records and
  scores the evidence that somebody else did.
* It is **not** an alerting engine. It produces findings; who escalates them
  is a process decision, and a tool that pages at 03:00 for a stale restore
  test teaches people to ignore it.
* It is **not** a replacement for the DBA cockpit. It is the cross-system
  view — the RPO, the restore evidence and the capacity in one place, per
  system, with an action attached.

---

## 6. Why the gaps were left open rather than faked

A portfolio project that pretends to have run against a production system is
easy to expose in an interview: the interviewer asks which release, which
client, which SID, and the story collapses. The gaps above are specific,
explained, and each one comes with the concrete step that closes it.

The same rule applies to the data. The seeded landscape is explicitly
synthetic, and the way it is seeded — one deliberate failure mode per system —
makes it a **test fixture**, not a pretence of a customer. A portfolio project
that fabricates customer data teaches an interviewer to check everything else
in the repository.
