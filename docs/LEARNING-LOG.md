# Learning log

The decisions behind the code, and the mistakes behind some of them. Written
for the person reading the repository in six months — including me.

---

## 1. The order the work happened in

1. The schema, the domain classes and the repository — because the shape of
   the data decides the shape of everything else.
2. The rules, written as a plain method with no framework in sight.
3. The seed — and only then, the screens.

That order matters. The rules are the product; the screens are the report of
the rules. Writing the screens first produces a dashboard that shows what is
easy to show.

---

## 2. The decision that shaped the whole project

**Measure the RPO against the newest *recoverable* backup, not the newest
full backup.**

The first version checked the age of the last full backup against the RPO. It
is wrong whenever incrementals are running, because the incrementals are a
newer recovery point. A system whose incrementals run every two hours and
whose full backup is three days old has a two-hour recovery point, and a tool
that reports "three days against a 24-hour RPO" is lying by a factor of
thirty-six.

The fix was to separate three records that had been conflated into one:

| Record | What it means |
|---|---|
| The newest full backup **attempt** of any status | Did the job run, and did it succeed? |
| The newest **successful** backup of any type | What is the actual recovery point? |
| The newest **incremental** | Is the chain still keeping the window short? |

This is the single idea in the project that a real BASIS team would recognise
immediately — and it took a rewrite of the rules to get it right.

---

## 3. The score and the severity are two different things

The first version derived the severity from the score band. That is what a
score is for, so it seemed obvious.

It was wrong, and the seeded landscape exposed it. DEV's backups are perfect
and current; its last restore test *failed*. It loses 25 points and scores
75. On the band, 75 is "medium".

A system whose backups are perfect and whose restore path is broken is not
medium. It is the system that fails the day the building floods.

So:

```
severity = the worst severity among the findings
score    = 100 - the penalties      (for sorting only)
```

The score is now explicitly *only* a way to sort thirty systems so the worst
one is on top. It is never the answer on its own. The test
`severityIsTheWorstFindingNotTheScoreBand` pins this down, and its name says
why it exists.

---

## 4. Reporting a broken chain once

The rules were originally independent: each check added its own finding. A
system whose backups had stopped produced three findings — the failed backup,
the stale backup and the stale incremental — which is one problem written
three times.

The fix is a `chainBroken` flag. When the chain is broken, the most specific
cause is reported and the remaining checks are skipped. The operator needs one
action ("the backup job stopped"), not three.

This is a small change with a large effect on the report, and it is the kind
of thing that only becomes obvious once you look at real output. The first
version of the dashboard would have shown a wall of overlapping findings and
nobody would have read it.

---

## 5. Three bugs the tests caught

### 5.1 A backup scheduled in the future

The seeder anchored the "nightly" full backup to 22:00 of the **current
day**. Before 22:00 that backup was in the future — and a backup that has not
happened yet is indistinguishable from a stale one, because `ageHours` on a
future timestamp returns a negative number and every comparison silently
behaves as if the backup were current.

The fix is `nightAnchor()`: the most recent 22:00 that is **already in the
past**. It is five lines and it removes a whole class of confusing output.

> The general lesson: a time-relative seed needs an anchor that is guaranteed
> to be in the past, and the property has to be tested rather than assumed.

### 5.2 The incremental history was generated twice over

The original loop was:

```java
for (int day = 0; day < days; day++) {
    for (int offset = hoursAgo; offset < 24 * days; offset += everyHours) {
```

The inner loop did not depend on `day`, so the whole timeline was generated
once per day — twelve days of incrementals with identical timestamps under
different identifiers. The generator now walks one timeline backwards from a
single anchor.

### 5.3 The markdown endpoint returned 500 instead of 401

`@GetMapping(value = "/report.md", produces = "text/markdown;charset=UTF-8")`
forced the **401 body** through a markdown converter. The body is a
`LinkedHashMap`, so `HttpMessageNotWritableException` — and the anonymous
caller got a 500 instead of a 401.

The fix is to drop `produces` and set the content type on the response
instead. The converter then chooses by the body type: JSON for the error, the
declared content type for the Markdown.

> The general lesson: `produces` on a mapping constrains *every* return value
> of that method, including the error paths.

---

## 6. Things that were harder than they looked

### 6.1 The `LIMIT` question

`LIMIT 20` is the obvious way to write "the last twenty audit entries", and
it is **not valid on SAP HANA**. The first version of the audit query used it
and it worked perfectly on H2.

Every "top N" query in this project fetches the rows and cuts the list in
Java. It is slightly wasteful and it runs on both platforms. That trade is
worth making every time.

### 6.2 No `LIMIT`, and no `TOP` either

For the same reason there is no `TOP n`. The portable subset is
`ORDER BY` plus a cut in Java.

### 6.3 The backup IDs and the seed

The backup identifier is `"BKP" + system + "F" + %03d(day)`. For PRD the
failed full backup is `BKPPRDF009` and the incrementals are `BKPPRDI001`.
Reading a row of those out of a screenshot is genuinely difficult — the
first version of the report was checked against the API instead of against
the picture, which is the right way to check it.

---

## 7. Decisions that were easy

* **Plain Java domain classes.** No Lombok, no records (Java 8), no
  annotations. The domain is the part that has to be readable.
* **JdbcTemplate.** Decided in the first ten minutes, for portability.
* **Server-side SVG.** Decided when the first chart was drawn and the CDN
  dependency was written down. It was never tempting.
* **A linear capacity forecast.** Deliberately boring. The growth rate is the
  number the application team has to agree with, and a linear trend is a
  number they can argue about.
* **The seeded landscape as a story.** Five systems, one failure mode each.
  This is what makes the demo worth looking at, and it is also what makes the
  landscape tests possible.

---

## 8. Things I would do differently

* **Write the landscape test first.** I wrote the rules, then the seed, then
  the landscape test. The seed was supposed to demonstrate the rules, and it
  took three rounds of adjustment before it demonstrated all of them. Writing
  `ContinuityLandscapeTest` first would have made the intended story explicit
  before the data was written.
* **Make the thresholds one concept.** There are three of them — the RPO per
  system, the incremental policy, the full backup policy — and they are
  configured in two different places (the database and `application.yml`).
  That is correct, because they *are* different things, but it would be
  clearer with one named object holding the policy.
* **Link the restore test to the backup.** The limitation that hurts most is
  that the portal knows *when* recovery was last proven, but not *which*
  backup was proven. The schema has room for it — the foreign key is the
  obvious next change.
