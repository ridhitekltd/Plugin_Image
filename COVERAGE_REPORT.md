# Code Coverage Report — ridhitek-image-plugin

Generated: 2026-09-22
Tool: [JaCoCo](https://www.jacoco.org/) 0.8.12 (see [JACOCO_COVERAGE_GUIDE.md](JACOCO_COVERAGE_GUIDE.md) for setup/usage)

## Summary

| Metric | Before | After |
|---|---|---|
| Instruction coverage | 0% (no tests existed) | **98.0%** (1,822 / 1,859) |
| Branch coverage | 0% | **94.3%** (230 / 244) |
| Unit tests | 0 | 88 |

Same starting point as `ridhitek-uan-plugin`: no test source, no test dependency in `pom.xml`, no Maven wrapper. All three were added the same way — `spring-boot-starter-test`, the `jacoco-maven-plugin` (`prepare-agent` + `report` bound to the `test` phase), and `mvnw`/`mvnw.cmd` — followed by a full test suite covering every class.

## Per-class breakdown (after)

| Class | Instructions covered | Missed | Branches covered | Missed |
|---|---|---|---|---|
| `service.ImageVerificationService` | 1,282 | 4 | 172 | 12 |
| `service.LocalFileStorageService` | 207 | 25 | 36 | 2 |
| `service.GcpFileStorageService` | 224 | 8 | 20 | 0 |
| `controller.ImageVerificationController` | 94 | 0 | 2 | 0 |
| `entity.VerificationResult` | 11 | 0 | 0 | 0 |
| `entity.VerificationOverride` | 4 | 0 | 0 | 0 |

## What was tested

- **`ImageVerificationServiceTest`** (57 tests) — the largest class by far. Covers candidate listing/status lookup, tenant-ID resolution (including the reflective lookup of the backend's `TenantContextHolder`, see below), bulk and single photo uploads through both the local-disk fallback and a mocked `StorageService` (GCS) path, the L1/L2/L3 "replace existing file" clearing logic (including a leftover subdirectory that must survive the clear), the `updateEntityFromDto` field-by-field merge (every optional field, both present and absent), and the full `overrideVerificationStatus` flow — every `stage` branch including the `L1_VS_L2_AND_L2_VS_L3` alias, tenant fallback precedence (present / blank-not-null / fully unresolved), and the fraud-detection downstream-DB-update path (including its `NumberFormatException` catch when `candidateId` isn't numeric).
- **`LocalFileStorageServiceTest`** (12 tests) — real filesystem I/O against a JUnit `@TempDir`: candidate-folder prefixing (in both `uploadFile` and `listFiles`, which each re-implement the check), stage-directory clearing individually for `l1`/`l2`/`l3` (including a leftover subdirectory and the "directory doesn't exist yet" case), image-extension filtering (`.jpg`/`.jpeg`/`.png`/`.webp`), and the "path exists but isn't a directory" edge case.
- **`GcpFileStorageServiceTest`** (7 tests) — see note below on how the GCS client was mocked. Covers blob upload, stage-based blob deletion-before-replace individually for `l1`/`l2`/`l3`, image-only listing across all four extensions (filtering out folder placeholders and non-image blobs), and the exception-to-empty-list fallback.
- **`ImageVerificationControllerTest`** (9 tests) — response delegation for every endpoint, plus the `Principal` → auditor-id resolution (including the `null` → `"ADMIN"` default).
- **`VerificationResultTest` / `VerificationOverrideTest`** (3 tests) — `@PrePersist`/`@PreUpdate` lifecycle callbacks.

## Notable implementation details

**Mocking the GCS client without hitting real GCP.** `GcpFileStorageService` initializes its `Storage` client eagerly in a field initializer (`StorageOptions.getDefaultInstance().getService()`), which resolves real Application Default Credentials the moment the object is constructed — unsafe and slow to do in a unit test. The fix: `Mockito.mock(GcpFileStorageService.class, CALLS_REAL_METHODS)` creates the instance via Objenesis, which bypasses the constructor (and therefore the field initializer) entirely, then `ReflectionTestUtils.setField` injects a mocked `Storage` afterward. Real method bodies still execute — only the GCS client itself is faked.

**Testing a reflective integration point.** `ImageVerificationService.resolveTenantId()` looks up `com.ridhitek.backend.config.TenantContextHolder` and `com.uniquepeople.config.TenantContextHolder` via `Class.forName` at runtime — neither is a compile-time dependency of this plugin, since it's meant to plug into either backend. Two throwaway stand-in classes were added under `src/test/java` (same package/method signature, test-scope only, never packaged) so the reflection success path is actually exercised instead of always falling through to the `ClassNotFoundException` branch. Both expose a mutable static `tenantId` field (defaulting to a resolvable value) so individual tests can flip it to simulate a total resolution failure — needed because `resolveTenantId()` is called from four different call sites (`updateVerificationStatus`, `uploadPhotos`, `uploadPhoto`, `overrideVerificationStatus`), each with its own "still unresolved" branch that only a deliberately-failing lookup can reach.

## What's intentionally not covered

A handful of branches are either genuinely unreachable or not worth the added test complexity:

- **`uploadPhoto`'s `subFolder.contains("candidate")` branch is dead code.** The string `"candidate"` always contains the substring `"id"` (can**did**ate), so the preceding `if (subFolder.contains("id"))` check always wins first — the `contains("candidate")` half of the next `else if` can never evaluate to `true` for any real input. This was true before these tests existed; it's a pre-existing quirk in `ImageVerificationService`, not a testing gap.
- **A few `File`/`java.io.File.listFiles()` null-return branches** (as opposed to an empty array) in `ImageVerificationService.listPhotosAsUrls` and `LocalFileStorageService.listFiles`/`uploadFile` — the JDK only returns `null` here on an I/O error, which isn't practical to force against a real temp directory without mocking `java.io.File` itself.
- **One duplicated reflection branch** in the second `TenantContextHolder` lookup and one `updateEntityFromDto` field (`verificationStatus == null`) — low-value combinations of already well-covered logic.

## Reproducing this report

```powershell
.\mvnw.cmd test
.\mvnw.cmd jacoco:report
start target\site\jacoco\index.html
```
