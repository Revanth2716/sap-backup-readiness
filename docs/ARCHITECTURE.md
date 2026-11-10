# Architecture

Why the SAP Backup & Restore Readiness portal is built the way it is.

---

## 1. The shape of the problem

A BASIS team is accountable for three separate things that are usually
monitored by three separate systems that do not talk to each other:

| Question | Where the answer usually lives | Where it usually fails |
|---|---|---|
| Are the backups current? | The backup tool's own log | The job fails silently, and the log says "OK" because the *job* ran |
| Has recovery ever been proven? | A spreadsheet, or nowhere | Nobody has restored anything since the last infrastructure change |
| Is there room? | The DBA cockpit | Nobody looks until the database is full, which is at the worst possible moment |

The value of this application is not that it reports each of these — it is
that it puts them **on one screen, for one system, and turns them into an
ordered list of things to do**.

That is why the unit of the application is the *system*, not the backup. A
backup without a system behind it has no RPO, no RTO and no owner.

---

## 2. The layers

```
web/          Controllers, session auth, role gating, REST API
service/      The rules: readiness, capacity, sync, audit
repo/         JdbcTemplate over portable ANSI SQL
domain/       Plain Java objects, no framework annotations
support/      Server-side SVG rendering
integration/  The boundary to a live SAP system
seed/         The demo landscape and the demo accounts
```

The dependency rule is one-directional and never bent: `web → service → repo →
domain`. The domain objects know nothing about Spring, the repository knows
nothing about HTTP, and the services know nothing about Thymeleaf.

### Why `JdbcTemplate` and not JPA

Three reasons, in order of weight:

1. **Portability.** The SQL has to run unchanged on H2 (the demo) and on SAP
   HANA (production). Hand-written ANSI SQL is the only way to guarantee
   that. An ORM would generate dialect-specific SQL and hide the difference
   until it broke.
2. **No `LIMIT`.** `LIMIT` is not valid on SAP HANA. Every "top N" query in
   this project fetches the rows and cuts the list **in Java**
   (`PlatformRepository.findRecentAudit`). That is a deliberate constraint,
   not an oversight.
3. **The schema is small.** Five tables. An ORM would add more configuration
   than the schema itself.

### Why no `LIMIT`, in one sentence

The first version of the audit trail used `LIMIT 20`, which works perfectly on
H2 and fails on HANA — and a demo that only ever runs on H2 is a demo that
looks finished and is not.

---

## 3. The domain model

```
SystemProfile          one system: SID, client, owner, RPO, RTO, max size
   │
   ├── BackupRecord    one backup job: type, start, end, size, status, media
   │
   ├── RestoreTest     one restore test: when, result, duration, who, notes
   │
   ├── CapacitySample  one day's database size
   │
   └── SystemReadiness the assessment: score, severity, findings, forecast
            │
            ├── ReadinessFinding  severity + category + message + action
            └── CapacityForecast  current, maximum, growth, days to full
```

Everything is a plain Java class with getters and setters. No Lombok, no
records (the target is Java 8), no annotations on the domain.

---

## 4. The readiness rules

This is the heart of the application. It runs in `ReadinessService.assess()`
and answers three questions per system.

### 4.1 Are the backups current?

Three separate records are derived from the backup history, because they mean
different things:

| Record | Definition | Why it is separate |
|---|---|---|
| `lastFullAttempt` | The newest **full** backup of any status | A failed attempt is news in itself |
| `lastGoodBackup` | The newest backup of **any** type with status `OK` | This is the actual recovery point |
| `lastIncremental` | The newest **incremental** of any status | The incremental chain is what makes the RPO short |

The rules, in the order they are evaluated:

| # | Condition | Finding | Severity | Penalty |
|---|---|---|---|---|
| 1 | The newest full attempt failed | `BACKUP_FAILED` | CRITICAL | 45 |
| 2 | There is no successful backup at all | `NO_BACKUP` | CRITICAL | 45 |
| 3 | The newest **recoverable** backup is older than the RPO | `BACKUP_STALE` | HIGH | 30 |
| 4 | No incremental is recorded | `NO_INCREMENTAL` | MEDIUM | 15 |
| 5 | The newest incremental is older than the policy | `INCREMENTAL_STALE` | HIGH | 15 |
| 6 | No successful full backup is recorded | `NO_FULL_BACKUP` | CRITICAL | 45 |
| 7 | The newest successful full backup is older than the policy | `FULL_BACKUP_STALE` | MEDIUM | 15 |

**Rule 3 is the important one.** The RPO is measured against the newest
*recoverable* backup, not against the newest full backup. A system whose
incrementals run every two hours and whose full backup is three days old has
a two-hour recovery point — it does not have a three-day one. Measuring the RPO
against the full backup would report a system as out of compliance when it is
in fact fine.

**Rules 4–7 only run when the chain is not already broken.** This is the
"report a broken chain once" rule: if the backups have stopped, that is one
finding with one action, not three overlapping ones. The engine sets a
`chainBroken` flag and skips the rest.

### 4.2 Has recovery been proven?

| Condition | Finding | Severity | Penalty |
|---|---|---|---|
| No restore test has ever been recorded | `NO_RESTORE_TEST` | HIGH | 30 |
| The last restore test **failed** | `RESTORE_TEST_FAILED` | CRITICAL | 25 |
| The last successful restore test is older than the policy | `RESTORE_TEST_STALE` | HIGH | 30 |

A failed test outranks an old one. A failed restore is not an absence of
evidence — it is evidence that the backup path is broken.

These rules run **independently** of the backup rules. DEV is the case that
proves it: its backups are perfect and its restore test failed. The backup
rules say nothing; the restore rule says *critical*.

### 4.3 Is there room?

| Condition | Finding | Severity | Penalty |
|---|---|---|---|
| The database reaches its maximum within the warning window | `CAPACITY_RISK` | MEDIUM | 20 |

The forecast is a **linear trend** over the observed window:

```
growthPerDay = (lastSize - firstSize) / daysBetweenFirstAndLast
headroom     = maxDbGb - lastSize
daysToFull   = headroom / growthPerDay
```

A linear trend is not clever, and that is the point. "Are we really growing
4 GB a day?" is a question an application team can argue about. A model that
fits a seasonal curve to thirty data points is a question nobody can argue
about, and an argument about the growth rate is the argument that actually
matters.

---

## 5. The score, and why the severity is not the score

```
score = max(0, 100 - sum of the penalties)
severity = the worst severity among the findings
```

These are two different things, and conflating them is the most common
mistake in this kind of tool.

DEV, in this project, loses 25 points for a failed restore test and scores
75. On a score band of "60–80 = medium", DEV is medium. Its restore test
*failed*. DEV is **critical**.

If the severity were derived from the score, a failed restore could hide
behind a good average — which is exactly how it hides in a real landscape,
where the average is good because everything else is fine.

`ReadinessServiceTest.severityIsTheWorstFindingNotTheScoreBand` pins this
down, and the test name says why it exists.

The score exists for one purpose: sorting thirty systems so that the worst one
is on top. It is never the answer on its own.

---

## 6. The gateway boundary

```java
public interface BackupStatusGateway {
    String description();
    List<BackupRecord> fetchRecentBackups(String systemName, int limit);
}
```

Two implementations:

| Implementation | Where | When |
|---|---|---|
| `LocalBackupStatusGateway` | `src/main/java` | The default — reads the local database |
| `RfcBackupStatusGateway` | `src/extra/java` | `readiness.gateway: jco` — calls `Z_BACKUP_STATUS` over RFC |

The JCo adapter lives in `src/extra/java` and is added to the build by the
`with-jco` profile through `build-helper-maven-plugin`. The SAP Java Connector
is published on SAP's own Maven repository, not Maven Central, so a plain
`mvn package` cannot resolve it. Keeping the adapter out of the default source
path means **the default build compiles and tests anywhere**, with no SAP
repository access and no licence.

`BackupSyncService` makes the sync **additive**: a backup that is already
known is left alone. It is meant to be run from a scheduler, and a scheduler
does not think first.

---

## 7. The charts

`ReadinessChartRenderer` produces SVG markup as a string. No library, no CDN,
no client-side JavaScript.

Two reasons:

1. **Corporate networks block outbound internet.** A chart library loaded from
   a CDN renders as an empty box in exactly the environment where this tool
   has to work.
2. **An auditor has to be able to save the page.** A PNG of a chart is
   evidence; a chart that needs JavaScript to appear is not.

The SVG is rendered into the page with `th:utext`, so every label that comes
from data is XML-escaped by `ReadinessChartRenderer.esc()` first.

---

## 8. Security

| Concern | Decision |
|---|---|
| Password storage | BCrypt, hashed at runtime — no reusable hash is committed to the repository |
| Sessions | Server-side `HttpSession`, 30-minute timeout, `HttpOnly` and `SameSite=Lax` cookies |
| Authorisation | Three roles (`VIEWER`, `ANALYST`, `OPS_ADMIN`), enforced by one interceptor and one `@RequireRole` annotation |
| The API | Authenticated like the screens; only `/api/v1/health` is open, because a load balancer has to be able to reach it |
| Write operations | Recording a restore test and synchronising are `OPS_ADMIN` only, and both are written to the audit trail |

Spring Security is used for the BCrypt encoder only — the filter chain is
deliberately **not** used. A self-contained interceptor keeps the application
deployable as a plain WAR on the NetWeaver Java stack, without pulling a
security framework's session strategy into it.

Every finding message and every recommendation is written by the service layer,
never by the view. The templates cannot invent a recommendation.

---

## 9. The audit trail

Recorded actions:

| Action | Why it matters |
|---|---|
| `LOGIN` / `LOGOUT` | Who was in the system |
| `LOGIN_FAILED` | Repeated failures against an account |
| `RESTORE_TEST_RECORDED` | **The evidence itself** — this is the write that changes the score |
| `BACKUP_SYNC` | When the landscape was last read |
| `REPORT_VIEWED` / `REPORT_EXPORTED` | The moment somebody was shown the findings and asked to act on them |

The trail exists because a continuity review asks "when did you last look?"
and "who recorded that restore test?" — and an answer that is not written down
is not an answer.

---

## 10. The demo landscape

The seeded data tells a story. This is not decoration — it is the only way a
portfolio project proves that the rules work.

| System | Score | What it demonstrates |
|---|---|---|
| PRD | 25 | A failed full backup with nothing running since — `BACKUP_FAILED` and `BACKUP_STALE`, and the "report once" rule |
| DEV | 75 | The severity-is-not-the-score-band rule: perfect backups, a failed restore, and a *critical* verdict |
| BW | 50 | A stale restore test (75 days) **and** a capacity risk (21 days to full) |
| QA | 70 | The most dangerous state of all: everything current, recovery never tested |
| SOL | 100 | The control — the rules produce no findings when there is nothing wrong |

The backups are anchored to the most recent 22:00 that is **already in the
past**. Without that, a "nightly" backup scheduled for 22:00 on the current day
sits in the future before 22:00 — and a backup that has not happened yet is
indistinguishable from a stale one. This was a real bug, caught by a test.

---

## 11. Testing strategy

| Layer | What is tested | How |
|---|---|---|
| Rules | Each readiness rule on its own | `ReadinessServiceTest` — a hand-built repository per scenario, so a failure names the rule |
| Data | The story the seed has to tell | `ContinuityLandscapeTest` — the whole Spring context, the real seed |
| Behaviour | The sync is additive | `BackupSyncServiceTest` — a mocked gateway |
| Contract | The API shape and the authentication | `ApiControllerTest` — MockMvc |
| Rendering | Every screen renders | `PageRenderTest` — MockMvc against every URL |

`PageRenderTest` is not optional. A template can compile, the service layer can
be fully green, and one screen can still return HTTP 500 because a single
expression in the markup does not resolve. This has caught real bugs in this
project family more than once. It also catches the opposite failure — a
template file that silently does not exist at all.
