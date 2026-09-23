# Code Coverage Guide (JaCoCo)

JaCoCo is wired into this project's `pom.xml`. You don't need to install or configure anything — just pull and run.

---

## 1. After `git pull` — how to run

Open a terminal in the project folder (PowerShell), then run these two commands **in order**:

```powershell
.\mvnw.cmd test
```

This runs all tests and records which lines of code they touch. **It's normal for this to end in `BUILD FAILURE`** if some tests are broken/failing — that doesn't stop coverage data from being collected. Ignore the failure and continue.

```powershell
.\mvnw.cmd jacoco:report
```

This builds the actual HTML report from the data collected above. This one **must** end in `BUILD SUCCESS`. If it doesn't, something went wrong in step 1 before any test even ran (e.g. compile error) — fix that first.

*(No `mvnw.cmd` in the folder? Use `mvn test` / `mvn jacoco:report` instead, with Maven installed on your machine.)*

---

## 2. How to see the report

Open this file in your browser:

```
target\site\jacoco\index.html
```

Fastest way from the terminal:

```powershell
start target\site\jacoco\index.html
```

---

## 3. How to read the report (drill-down, 4 levels)

**Level 1 — Project summary (the page that opens first)**
One row per package (`com.ridhitek.image.service`, `.controller`, `.entity`, etc.), with a red/green bar and a `%` for both "Missed Instructions" and "Missed Branches". This tells you which *area* of the app is weakest.

**Level 2 — Click a package → class list**
One row per class in that package, same red/green bars. Sort by "Missed Lines" (click the column header) to find the classes with the most untested code, not just the lowest percentage — a huge class at 5% matters more than a tiny class at 0%.

**Level 3 — Click a class → method list**
One row per method. Shows exactly which methods are fully tested, partially tested, or never touched at all.

**Level 4 — Click a method → actual source code**
This is where the colors that matter live:
- 🟢 **Green line background** = this line ran during a test.
- 🔴 **Pink/red line background** = this line never ran in any test.
- 🔶 **Red or yellow diamond** in the margin = a branch marker. It means the line ran, but only one side of an `if`/ternary/`switch` was ever tested — e.g. `x != null ? a : b` where `b` never happened in any test.

There is no plain "yellow line" — yellow only appears as a small diamond icon next to a branch, meaning "partially covered," not as a full line background.

---

## 4. What counts as normal / medium / critical

Rough guide, applied per class or package:

| Coverage | Status | What it means |
|---|---|---|
| 0–30% | 🔴 **Critical** | Effectively untested. Any change here is a real risk — bugs won't be caught. |
| 30–60% | 🟠 **Medium** | Some paths tested, but major gaps remain, usually the error/edge cases. |
| 60–80% | 🟡 **Acceptable** | Core logic is tested. Good enough for most classes; push higher for critical business logic (photo uploads, verification overrides, storage integrations). |
| 80–100% | 🟢 **Good** | Well tested. Don't chase 100% on getters/setters/DTOs — low value for the effort. |

Don't panic-target "80% everywhere overnight." Prioritize by **missed line count**, not just by low percentage — a large service at 3% is a bigger problem than a small utility at 0%.

---

## 5. How to actually fix / improve coverage

1. Open the report, drill down (Level 1 → 2 → 3 → 4) to a specific red method.
2. Read the method: what does it do, what inputs would make it run?
3. Write a test for the plain/normal case first — call the method, assert the expected result.
4. Only after that, add tests for edge cases that matter: `null` input, empty list, an exception being thrown, a branch that goes the "other" way (fixes the yellow/red diamond).
5. Put the test in the matching path: a class at `src/main/java/com/ridhitek/image/service/Foo.java` gets its test at `src/test/java/com/ridhitek/image/service/FooTest.java`.
6. Re-run both commands from Section 1. Refresh the report — lines you covered turn green, and the class/package percentage goes up.
7. Repeat, one class at a time, starting from the biggest "Missed Lines" numbers.

---

## 6. If `.\mvnw.cmd test` fails with "Failed to load ApplicationContext"

This means a `@SpringBootTest` class is trying to start the *real* app (real database, real Redis, real external services) and one of those isn't available on your machine. This is a pre-existing issue, not something caused by pulling this guide or running coverage — it just means those specific test classes contribute 0% coverage until it's fixed separately. Everything else in the report is still accurate.

---

## 7. A note on `GcpFileStorageService`

This class resolves real GCP credentials in a field initializer the moment it's constructed. Tests for it must never call `new GcpFileStorageService()` directly — see `GcpFileStorageServiceTest` for the `Mockito.mock(..., CALLS_REAL_METHODS)` pattern used to instantiate it safely without touching real GCP.
