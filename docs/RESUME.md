# Resume content

Copy-paste blocks. Pick the length that fits the CV and delete the rest.

> **Do not paste the project title as a client engagement.** This is
> self-initiated portfolio work. Say *"a self-initiated project I built to
> practise..."* — an interviewer who asks "who was the client?" and gets a
> vague answer will stop asking the good questions.

---

## 1. The one-line version

> **SAP Backup & Restore Readiness** — continuity operations for SAP landscapes:
> backup freshness against the recovery point objective, restore test evidence,
> capacity forecasting and a per-system readiness score.

---

## 2. The three-bullet version

> **SAP Backup & Restore Readiness** — *Java 8 · Spring Boot 2.7 · JdbcTemplate · SAP HANA · Thymeleaf · ABAP RFC · SAP Java Connector*
>
> - Built a **continuity readiness portal for SAP landscapes** that measures backup freshness against the **recovery point objective**, scores **restore test evidence** as a first-class finding, forecasts **database capacity exhaustion**, and produces a per-system readiness score with a concrete recommended action behind every finding.
> - Designed the scoring so that **a broken backup chain is reported once** and so that the **severity is the worst finding rather than the score band** — a failed restore test stays critical even when the overall score is healthy.
> - Shipped the SAP-side artifacts (**RFC-enabled ABAP function module** reading `M_BACKUP_CATALOG_FILES`, **SAP Java Connector client** behind an optional Maven profile) on a **portable ANSI SQL** layer that runs unchanged on local H2 and SAP HANA, with **53 automated tests**.

---

## 3. The two-bullet version

> **SAP Backup & Restore Readiness** — continuity operations for SAP landscapes
> *Java 8 · Spring Boot 2.7 · JdbcTemplate · SAP HANA · ABAP RFC*
>
> - Built a **backup and restore readiness portal** that measures backup freshness against the **recovery point objective**, evaluates **restore test evidence**, forecasts **capacity exhaustion** and scores every system with a recommended action behind each finding.
> - Kept the SAP integration real but buildable anywhere: an **RFC-enabled ABAP function module** and a **SAP Java Connector client** behind an optional Maven profile, on **portable ANSI SQL** that runs unchanged on H2 and HANA, with **53 tests**.

---

## 4. The one-bullet version

> Built a **continuity readiness portal for SAP landscapes** (Java 8, Spring Boot, JdbcTemplate, SAP HANA, ABAP RFC) that measures backup freshness against the recovery point objective, scores restore test evidence, forecasts capacity exhaustion, and produces a per-system readiness score with an actionable finding behind it — with an RFC-enabled ABAP function module, a SAP Java Connector client and 53 automated tests.

---

## 5. Where it fits in the CV

This is the **sixth** BASIS portfolio project. The set covers six distinct
workstreams with no overlap, which is the point — it shows range, not six
versions of the same CRUD app.

| # | Project | Workstream |
|---|---|---|
| 1 | `sap-basis-ops-console` | Monitoring and governance analytics (Python CLI) |
| 2 | `sap-basis-operations-portal` | Operations: leave self-service and governance |
| 3 | `sap-custom-code-analyzer` | Repository analysis: where-used and custom code quality |
| 4 | `sap-interface-cockpit` | Integration: RFC, IDoc and BAPI operations |
| 5 | `sap-performance-analyzer` | Performance: ST12 and ST03 trace analysis |
| 6 | **`sap-backup-readiness`** | **Continuity and DR: backup, restore and capacity** |

Recommended CV section:

```markdown
### SAP Basis Analyst — Portfolio Projects

**SAP Backup & Restore Readiness** · Java 8 · Spring Boot · JdbcTemplate · SAP HANA · ABAP RFC
Built a continuity readiness portal for SAP landscapes that measures backup freshness
against the recovery point objective, scores restore test evidence, forecasts capacity
exhaustion, and produces a per-system readiness score with a concrete action behind every
finding. 53 automated tests. → github.com/Revanth2716/sap-backup-readiness

**SAP Performance & Workload Analyzer** · ...
**SAP Interface Cockpit** · ...
```

---

## 6. Keywords

For the systems that screen on keywords:

`SAP BASIS` · `SAP NetWeaver 7.5` · `Java 8` · `Spring Boot` · `JdbcTemplate` ·
`Thymeleaf` · `SAP HANA` · `ABAP` · `RFC` · `SAP Java Connector (JCo)` ·
`SM59` · `SE37` · `SE11` · `M_BACKUP_CATALOG_FILES` · `DBACOCKPIT` ·
`backup and restore management` · `RPO / RTO` · `disaster recovery` ·
`disaster recovery planning` · `service level agreement` ·
`capacity planning` · `database sizing` · `SAP HANA database` ·
`change and release management` · `IT service management`

---

## 7. How to talk about it in an interview

### The thirty-second version

> "I built a continuity readiness portal for SAP landscapes. The idea came from
> the question every customer asks and almost nobody answers: *is the backup
> actually current enough to meet the RPO we agreed, and has anybody ever
> restored one?* The tool measures both, forecasts when the database fills up,
> and scores every system. The interesting design decision was that the
> severity comes from the worst finding rather than from the score, because
> otherwise a failed restore test can hide behind a good average."

### The design decision worth defending

**Why is the severity not derived from the score?**

> "Because that is how problems hide. If the severity came from the score, a
> system whose backups are perfect but whose last restore test *failed* would
> be reported as 'medium' — the average is good because everything else is
> fine. In a real landscape that is exactly the system that is not ready. So
> the severity is the worst finding, always, and the score only exists to sort
> thirty systems so the worst one is on top."

### The bug worth telling

> "The first version seeded the 'nightly' backup at 22:00 of the current day.
> Before 22:00 that backup was in the future — and a backup that has not
> happened yet is indistinguishable from a stale one. A test caught it. The
> anchor is now the most recent 22:00 that is already in the past."

### The thing to be honest about

> "The ABAP and the JCo adapter are written against NetWeaver 7.5 semantics
> and are ready to compile, but I did not have a system available, so they have
> not been executed. The Java side is fully tested — 53 tests, and every
> screenshot in the repository was captured from the running application."

---

## 8. What to say if you are asked "what was the hardest part?"

Not the hardest *code*. The hardest **decision**.

> "The hard part was deciding what the score should measure. The first version
> measured the RPO against the last *full* backup, which is wrong: a system
> whose incrementals run every two hours and whose full backup is three days
> old has a two-hour recovery point, not a three-day one. The RPO has to be
> measured against the newest *recoverable* backup, and separately you need to
> know whether the last full backup attempt *failed* — because a failed job
> looks like a running job on the dashboard. Getting those two apart was the
> whole design."

---

## 9. Things not to claim

| Do not say | Say instead |
|---|---|
| "I built this for a customer" | "I built this as a self-initiated portfolio project" |
| "It is deployed in a production landscape" | "It runs locally against a seeded database; the live SAP path is written but not executed" |
| "It monitors the backups automatically" | "It reads the backup catalog over RFC and scores the result" |
| "The restore test is automated" | "The restore test history is a manual record — the portal is where that evidence is kept and scored" |
| "It supports 200 systems" | "It is modelled for a landscape of systems; the seeded demo has five" |

---

## 10. Interview questions this project invites

Have an answer ready for these — they are the good ones:

* "How do you decide that a backup is too old?"
* "What is the difference between a backup and a recovery plan?"
* "How would you measure the RPO in a real landscape?"
* "Why is your severity independent of your score?"
* "What happens when the backup tool is the one that tells you the backup
  failed?"
* "How do you prove a backup can be restored?"
* "Where do you draw the line between 'stale' and 'broken'?"
* "What would you change first if you had a real customer?"

The full answers are in [`INTERVIEW-QA.md`](INTERVIEW-QA.md).
