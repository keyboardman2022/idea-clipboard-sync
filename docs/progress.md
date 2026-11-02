# SDD ledger — plan: docs/superpowers/plans/2026-10-23-idea-clipboard-directory-sync.md

- User approved the spec and plan; execution method: inline, 2026-10-23.
- Ruling: Parent workspace is not a Git repository. Use the new standalone `idea-clipboard-sync/` directory for isolation; do not initialize the business workspace or create a worktree.
- Pre-flight Task 1 → Tasks 2/4: Snapshot/FileEntry/Limits shared, no conflict.
- Pre-flight Tasks 2 → 3/5: compressed bytes and chunk schema shared, no conflict.
- Pre-flight Task 4 → 5: Preview/ApplyResult and document guard must precede any writes, no conflict.
- Ruling: Test/build downloads may be unavailable. Keep the Gradle build; add a JDK-only local build/test path using installed SDK and cached test libraries if necessary. Actual lowest-version verification remains mandatory to report separately.
- Environment: usable Java 21 at C:\Users\topto\.jdks\graalvm-jdk-21.0.8; installed IDEA 253.31033.145; default Java 8 broken. First Gradle HTTP probe failed TLS authentication; investigate without changing global settings.
- Ruling: local javac --release 21 fails while closing ct.sym with AccessDeniedException in this sandbox. Local fallback compiles with the actual Java 21 compiler and -source 21 -target 21. Gradle retains --release 21 for normal environments.
- Task 1: scanner RED observed missing production types; local JUnit 4 passed, 1 link-privilege assumption aborted.
- Tasks 2/3: protocol/receive RED observed missing codec/store types; local suite 12 passed, 1 link-privilege assumption aborted.
- Task 4: RED observed missing DirectorySync/BackupStore/PathPolicy; first full run revealed malformed case-conflict test fixture (path used as content). Corrected fixture; no production change was needed for that failure.
- Gradle primary build: plugin portal resolution failed in 53 seconds; Python HTTPS confirms the 2.19.0 marker exists (HTTP 200). Keep JDK-only path available; this is network access in Gradle, not a nonexistent plugin version.
- Task 4: complete local suite 23 passed, 1 link-privilege assumption aborted. Added late document guard, mutation failure, and partial cancellation cases.
- Task 5: built ZIP with installed 253 SDK and actual official 251.26094.121 API jars. Core filesystem mutation callbacks integrate IDEA write actions and document refresh.
- Whole-project read-only review: no Critical, 2 Important (partial cancellation report, existing executable permissions); both accepted for repair. Minor skipped-item details accepted. Corrupt-backup listing isolation deferred and documented; oversize scans recover through exclusion settings/subdirectory selection.
- Verifier RED: plugin metadata rejected middle-dot name and non-Latin description start. Corrected only metadata (English plugin name/description prefix, Chinese operational UI unchanged), then rerun required.
- Review fix RED: CompletionRelay tests fail because relay absent; POSIX regression test is platform-conditional and cannot execute on this Windows filesystem. Preserve existing destination POSIX mode on replace/restore; new file modes use target-system defaults.
- Task 5: complete after review repair. CompletionRelay handles Task.onCancel and only delivers partial ApplyResult; commit/restore use short non-cancelable IDEA write actions. Local suite 26 passed, 2 environment assumptions aborted, 0 failed.
- Task 6: final local compile against 251.26094.121; compile also passed against 253.31033.145. Final ZIP SHA-256 50AF9D1604D93FD531781F64678670B3BD60226F1D37DA6584C3DC1D4AE96945.
- Task 6: official Plugin Verifier 1.411 final combined run reports Compatible for IC-251.26094.121 and IU-253.31033.145; one deprecated chooser API usage each, no compatibility problems. Minimum SDK is official platform-library subset with module descriptors; absent unrelated bundled components are warnings, not claimed as full UI testing.
- Task 6: standard Gradle configuration now BUILD SUCCESSFUL using process-local HTTPS proxy. Corrected verification-IDE DSL `ide` to official `create` after observed script error. Full Gradle build not rerun; separate verified build path delivered.
- Task 6: single-text and nine-part Windows bootstrap installers executed in Windows PowerShell 5.1; reverse-order and duplicate parts reconstruct identical SHA-256. No live VM clipboard or actual IDEA UI smoke test performed; recorded in verification.md.
- Ruling: no Git repository/branch exists, so no merge, PR, archive, or branch-integration menu applies. Preserve source and installable artifacts in the standalone project directory for delivery.

## 0.2.0 — automatic RDP clipboard transfer

- User reported 300+ segments over Windows mstsc and approved the bounded automatic-transfer design; existing inline execution preference retained. No new spec/plan approval round needed.
- TDD RED: missing AutoTransfer types; GREEN: >300 actual segments with virtual-time retry/loss, wrong ACK, corruption, pause/resume and cache validation. Existing filesystem/backup suite retained.
- Protocol: separate AUTO/1 control frames wrap unchanged DIRSYNC/1 data. HELLO/READY run handshake precedes DATA; ACK identifies run, transfer, segment, payload and bundle hashes. COMPLETE requires full persisted snapshot decode/validation. Cached completed snapshots can finish early.
- Receipt performance: cache already validated part headers within one store instance, continue enforcing byte quotas, and reread/validate every persisted part at final assembly. Duplicate parts are checked against disk.
- IDE integration: daemon worker polls native clipboard off EDT, checks for intervening changes before writing, publishes immutable progress, queues pause/resume, and releases clipboard ownership when stopped/project disposed. All preview-changing callbacks have epoch guards.
- Independent review: no Critical; 3 Important accepted. Stale-DATA restart fixed with HELLO/READY and dedicated RED→GREEN tests; disk-held endpoint monitors no longer acquired by EDT controls; mandatory final preview is deferred until the controller is idle, with cancellation when view/epoch is obsolete. Deferred-completion tests observed RED for absent helper then GREEN.
- Additional RED: delayed READY incorrectly paused an active sender. Current-run READY/HELLO are now treated as stale control frames; regression test added.
- Final core suite: 43 discovered, 41 successful, 2 environment assumptions aborted, 0 failed (build/auto-final-test.log). Source compiled against 251; final 253 and binary verification results follow in verification.md.
- Final 0.2.0 ZIP 94,158 bytes, SHA-256 06624C3423ACAF42FF0AF3FD84541FCB81C2EE6CF084051A799F71C41B863F99. Additional 253 compile passed. Official verifier reports Compatible for both specified versions; one pre-existing deprecated chooser usage each.
- Windows PowerShell 5.1 single-text and 11-part installer smoke tests passed, including reverse order and duplicate part. Actual mstsc clipboard bridge remains untested and is stated in README and verification.md. Previous 0.1.0 ZIP and install texts preserved.

## 0.3.0 — folder selection and direct synchronization (2026-11-02)

- User confirms 0.2 meets transport requirements; explicitly requests directory hierarchy/folder checkbox selection and removal of backups. Request is fully specified; proceeded inline on the existing project, with no new transport design or approval round.
- DirectorySelection and NoBackupApply tests observed RED for absent directory-selection model and direct-apply API. After implementation and removal of backup-only cases: 50 tests discovered, 48 successful, 2 platform assumptions aborted, zero failures.
- Added shared FileTree to send and receive. Supports directory/leaf selection, partial ancestor state/counts, all/none and expand/collapse, keyboard Space, disabled unchanged/blocked receiver files, stable relative paths and frozen selection while generation/application runs.
- Removed BackupStore, RestoreBackupAction, backup models/IDs, service methods, restore menu, creation calls, and backup promises in current UI/README. No old backup data deleted. Direct writes retain validation, concurrency/document guards, staging/permission preservation and partial cancellation results.
- UX search had no verified desktop-tree-specific result after the narrowed retry; used native Swing tree conventions plus existing IntelliJ ThreeStateCheckBox rather than persisting unrelated web/mobile recommendations.
- Headless actual-component smoke test passed keyboard/click/lock/eligibility behavior. Visual inspection exposed blank rows: reused renderer panels had no child layout. Added a failing geometry assertion, laid out children during renderer painting, reran smoke successfully and inspected the resulting preview.
- Independent whole-change review found no Critical/Important issues. Source compiled against both specified SDKs. Final Plugin Verifier: Compatible on 251 and 253, with no deprecated API usage findings after restore chooser removal.
- Final ZIP SHA-256 82F0F65FCFDF07AF930DC1943F0834835E0A2B017649386901087FCCF9847B86, 96,638 bytes. Single and 11-part Windows bootstrap smoke tests passed. Build and verification records in verification.md; live full-IDE UI/real RDP not newly exercised.

## 0.3.1 — receiver hides identical files (2026-11-02)

- User explicitly scopes the change to the receiver. Added a tested display filter; sender selection and transport remain unchanged.
- RED observed missing changedFiles(Preview); GREEN: mixed add/modify/blocked entries retained, identical leaves and empty descendant folders omitted, all-identical preview empty, and later local changes become visible on refresh. Original snapshot/comparison lists remain intact.
- Final core suite: 53 discovered, 51 successful, 2 environment assumptions aborted, zero failures. Actual FileTree fixture now uses the production filter and asserts hidden identical leaves and directories.
- All-identical preview disables Apply and reports that received files match (without claiming target-only files are absent). Existing no-backup and automatic transfer behavior retained.
- Final lowest-SDK build SHA-256 26803B1D606BDE547174D9B68D5EF80ABCE536A669F3BF880D7173A0356582E7, 97,019 bytes. Both specified SDK/API checks passed; final Plugin Verifier reports Compatible for both target versions. Windows installer smoke passed. Existing public GitHub publishing preference retained for patch delivery.
