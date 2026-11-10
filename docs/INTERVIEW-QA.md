# Interview questions and answers

The questions this project invites, and the answers that are actually true.

---

## 1. The domain

### "What is the difference between a backup and a recovery plan?"

> "A backup is a copy. A recovery plan is a proven way back — and the proof is
> a restore. The plan says which backup, how long it takes to restore it, who
> does it, and where it goes. Almost every landscape has the copy and calls it
> the plan. That is why the portal treats a missing restore test as a finding
> in its own right, and a *failed* one as critical: a failed restore is not an
> absence of evidence, it is evidence that the path is broken."

### "What is an RPO and how do you measure it against real backups?"

> "The recovery point objective is the maximum amount of data the business is
> willing to lose, expressed as a time window — a 24-hour RPO means you can
> lose up to 24 hours of transactions.
>
> "Measuring it is the interesting part, and where most tools get it wrong.
> The naive approach is to look at the last full backup. That is wrong when
> incrementals are running, because the newest *recoverable* backup is what
> determines the real recovery point. A system whose incrementals run every two
> hours and whose full backup is three days old has a two-hour recovery point.
>
> "So the tool separates three records: the newest full backup *attempt* of
> any status — because a failed attempt is news in itself; the newest
> *successful* backup of any type — that is the actual recovery point; and the
> newest incremental on its own — because that is what keeps the window short.
> The RPO is then measured against the second one."

### "What is the difference between RPO and RTO?"

> "RPO is backwards in time — how much data you lose. RTO is forwards — how
> long you are down. A four-hour RTO with a 24-hour RPO is a system that
> recovers quickly but loses a day of work; the two are usually agreed
> separately because they are bought with different things.
>
> "The tool scores the RPO, because it can be measured from the backup
> catalog. The RTO cannot — you only learn it from an actual restore test,
> which is another reason the restore test evidence matters so much."

### "How do you prove that a backup can be restored?"

> "You restore it. Period. There is no shortcut and no tool can infer it from
> the backup metadata — the file being there and the file being restorable are
> different claims.
>
> "In practice the proof is a periodic restore into an isolated system, with
> the result recorded: when, how long it took, who did it, what happened. That
> record is what the portal scores. It is a manual record, and that is an
> honest limitation — the tool measures the evidence, it does not create it."

### "What is the difference between a full, an incremental and a differential backup?"

> "A full backup copies everything. An incremental copies only what changed
> since the last backup of any kind, and the restore chain is the full plus
> every incrementals in order. A differential copies everything that changed
> since the last **full** backup, so the restore is the full plus one
> differential — the restore is faster, but the backup grows over the week.
>
> "Why it matters here: the incremental chain is what keeps the RPO short, and
> it is also the fragile part. A single missing link breaks the restore, which
> is exactly the failure the DEV system in this project has — its backups are
> current and fine, and its restore test failed because the archive chain was
> incomplete."

### "What is the difference between a backup failing and a backup not running?"

> "A job that runs and fails is the dangerous one, because the monitoring says
> the job ran. And the failure often leaves a gap that is only visible later.
>
> "The tool treats them separately. The newest *attempt* of any status is
> compared with the newest *successful* backup — when they diverge, something
> is wrong that the job's own status never said. And when the chain is broken,
> the tool reports it once, with the most specific cause, rather than firing
> three overlapping findings at the same problem."

---

## 2. The design

### "Why is your severity not derived from your score?"

> "Because that is how problems hide. In the demo landscape, DEV loses 25
> points for a failed restore test and scores 75 — on a score band that is
> 'medium'. Its restore test *failed*. DEV is critical.
>
> "If the severity came from the score, a system with good backups and a
> broken restore path would be reported as medium, because the average is good
> since everything else is fine. That is precisely the system that is not
> ready. So the severity is the worst finding, always. The score exists only
> to sort thirty systems so the worst one is on top."

### "Why is a broken backup chain reported once?"

> "Because three overlapping findings for one broken backup job make the
> report longer, not clearer. The engine sets a flag when the chain is broken
> and suppresses the incremental and full-backup checks — the operator needs
> one action, 'the backup job stopped', not three.
>
> "The rule is tested directly: `brokenChainIsReportedOnce`."

### "How do you decide what is stale and what is broken?"

> "Three different thresholds, and they answer three different questions.
>
> "- **Against the RPO** — is the recovery point still inside what the
>   business agreed to lose? That is the contract, and it is per system.
> "- **Against the incremental policy** — is the incremental chain still fresh
>   enough to be worth anything? That is a landscape policy, not a customer
>   agreement, so it is configuration.
> "- **Against the full backup policy** — has it been so long since a full
>   backup that a restore would have to replay a long chain of incrementals?
>   That is an operational rule, and it is a *performance* problem, not a
>   correctness one, so it is a medium finding.
>
> "Mixing them up is how you get either a dashboard that cries wolf or one
> that misses the point."

### "Why a linear capacity forecast? Why not something clever?"

> "Because the growth rate is the thing the application team has to agree
> with, and a linear trend is a number they can argue about. 'Are we really
> growing 6 GB a day?' is a question worth having. A seasonal model fitted to
> thirty data points is a number nobody can argue about, and nobody argues
> with — which means nobody acts on it.
>
> "I would add the log volume next, because in a lot of landscapes the log is
> what fills up before the data volume does."

### "Why do you draw the charts on the server?"

> "Two reasons. Corporate networks block outbound internet, and a chart
> library from a CDN renders as an empty box in exactly the environment where
> this has to work. And an auditor has to be able to save the page and still
> see the evidence — a chart that needs JavaScript to appear is not evidence.
>
> "The SVG is plain markup, no library, no client-side JavaScript. It is less
> impressive to write than a chart library, and it works everywhere."

### "Why JdbcTemplate and not JPA?"

> "Portability, first. The SQL has to run unchanged on H2 for the demo and on
> SAP HANA for production, and hand-written ANSI SQL is the only way to
> guarantee that. An ORM generates dialect-specific SQL and hides the
> difference until it breaks.
>
> "Second: `LIMIT` is not valid on SAP HANA. Every 'top N' query here
> fetches the rows and cuts the list in Java. That was a deliberate constraint
> from the start — a demo that only ever runs on H2 looks finished and is
> not."

### "Why a session and an interceptor rather than Spring Security?"

> "Only the BCrypt encoder is taken from Spring Security. The filter chain
> would pull in a session strategy that is hard to reconcile with the
> NetWeaver Java stack, and the whole point of writing this as a plain WAR is
> that it deploys there without fighting the container. Three roles, one
> interceptor and one annotation is the entire authorisation model, and it is
> readable in a minute."

---

## 3. The implementation

### "Walk me through the request path for a system detail screen."

> "The interceptor checks the session and the `@RequireRole` annotation, and
> redirects or returns 403. The controller asks `ReadinessService` for the
> assessment of that one system. The service reads the backups, the restore
> tests and the capacity samples from the repository, applies the rules, and
> returns a `SystemReadiness` with the score, the severity and the findings.
> The controller puts that in the model, and the view renders it — including
> the capacity chart, which the controller asks the SVG renderer to produce as
> a string. There is no client-side JavaScript anywhere in the path."

### "How do you test a scoring rule?"

> "One scenario per test, with the repository stubbed so that the test
> contains exactly the situation it is about — a failed backup and nothing
> else, a breached RPO and nothing else. When a test fails it names the rule
> that broke, rather than saying 'the score changed'.
>
> "On top of that there is a test against the whole seeded landscape, because
> the seed has to tell a specific story: PRD critical because the chain
> stopped, DEV critical because the restore failed, QA high because recovery
> was never tested, SOL clean."

### "What is the `PageRenderTest` for, and why is it in every project?"

> "A template can compile, the service layer can be fully green, and one
> screen can still return HTTP 500, because a single expression in the markup
> does not resolve. Nothing else in the suite would catch that — the service
> tests never touch the templates.
>
> "It has caught real bugs in this project family more than once, including
> one where a template file silently did not exist at all."

### "How does the sync know what is new?"

> "It does not, and it does not need to. The sync is additive: the gateway
> returns the recent backups, the service compares the identifiers against
> what is already in the portal, and inserts only the ones it has not seen.
> Running it twice changes nothing, which matters because it is meant to run
> from a scheduler — and a scheduler does not think first."

---

## 4. The SAP side

### "How does the live path work?"

> "Two artifacts. The ABAP function module `Z_BACKUP_STATUS` reads the HANA
> system view `M_BACKUP_CATALOG_FILES`, which the database maintains for every
> backup it has taken — the file name, the type, the start and end time, the
> size and the state. The JCo adapter calls it over RFC through a destination
> maintained in `SM59`.
>
> "The important design point is the interface. `BackupStatusGateway` is the
> boundary; the local implementation reads the database, the RFC
> implementation calls the function module. Nothing above the interface knows
> that RFC exists."

### "What goes wrong first with RFC?"

> "The **Remote Enabled** flag. If the function module is created in `SE37`
> without it, the call fails with 'function module not found / not remote
> enabled'. And the error appears as an `SM58` entry on the **calling**
> system, not as a dump on the callee — so the system you are looking at is
> the one that looks fine. That asymmetry is what makes it confusing the
> first time."

### "Why is the JCo adapter not in the main source path?"

> "Because the SAP Java Connector is published on SAP's own Maven
> repository, not Maven Central. If it were in the main build, `mvn package`
> would fail for anyone without access to SAP's repository — including every
> recruiter and every person who clones the repository to look at it. It lives
> in `src/extra/java` and is added by the `with-jco` profile, so the default
> build compiles and tests anywhere, with no licence and no SAP access."

### "What does the function module deliberately not do?"

> "Report whether a restore has ever been tested. The database has no idea —
> that evidence is a manual record. It lives in the portal, and it is scored
> there. That gap is the reason the project exists at all."

---

## 5. The honest questions

### "Has this run against a real SAP system?"

> "No. The Java side runs and is tested — 53 tests, and every screenshot in
> the repository was captured from the running application. The ABAP, the DDIC
> types and the JCo adapter are written against NetWeaver 7.5 semantics and
> are ready to compile, but there was no NetWeaver system and no access to
> SAP's Maven repository in the build environment, so they have not been
> executed. The repository says so in `HONEST-STATUS.md`, and I would rather
> say it here than have it come out in an interview."

### "Where does the data come from then?"

> "It is a seeded synthetic landscape, and the seed is deliberately a *story*
> rather than noise. Five systems, each with one specific failure mode: a
> backup chain that stopped, a failed restore test, a stale test combined with
> a capacity problem, a system that was never tested at all, and a healthy
> control. The dates are computed relative to the current time, so the RPO
> breaches are real breaches at the moment the application runs."

### "What would you change first with a real customer?"

> "The restore test history. In this project it is a manual record, which is
> honest but weak — the tool measures the evidence and does not create it.
> The first real integration would be to link a restore test to the specific
> backup it restored, so that 'this restore proves *that* backup' becomes a
> fact rather than an assumption.
>
> "Second, the capacity forecast would include the log volume, because in a
> lot of landscapes the log fills up before the data does."

### "What would you not build?"

> "I would not build an alerting engine on top of this. The tool produces a
> finding with an action; who escalates it and when is a process question, and
> a tool that decides to page somebody at 03:00 for a stale restore test
> teaches people to ignore it. The email digest on a schedule is enough."

---

## 6. Questions to ask them back

* "How do you currently know whether a restore has ever been tested?"
* "When a backup fails, how long before somebody notices?"
* "How is your RPO agreed, and with whom?"
* "What happens to your backup window when the log volume doubles?"
* "Who owns the continuity of the landscape — the BASIS team, the DBA team, or
  the application team?"
* "How long did the last full restore actually take, versus the RTO?"
