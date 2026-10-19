# Open-source readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the fork for a public announcement: own applicationId, build-time cleartext host
list instead of a hardcoded IP, CI that is green on every PR, correct copyright lines, personal
operational data out of the tracked tree, and real documentation (README EN/RU, self-hosting,
1Money import, feature history, contributing).

**Architecture:** No app behaviour changes except the applicationId / deep-link scheme and the
network security config, which becomes a generated resource. Everything else is docs, CI and
headers. Kotlin packages and the `namespace` stay `ua.com.radiokot.money` so upstream merges stay
cheap.

**Tech Stack:** AGP 9.3 (Groovy `app/build.gradle`), GitHub Actions, GitHub-flavoured Markdown.

**Spec:** the owner's brief for this PR (recorded in "Rulings" below) plus
`docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md` for facts about A–F6.

## Global Constraints

- The repo is PUBLIC: nothing committed may contain the owner's IPs, hostnames, machine paths,
  usernames, private URLs (claude.ai artifacts, private Gitea), device model, card digits, real
  merchant/payee names from his notifications, his personal category/person names, keys or credentials.
- Never commit `local.properties`, `app/local.properties`, `docs/local/`.
- applicationId `io.github.nickkovdev.money`; debug keeps `applicationIdSuffix ".debug"`.
  `namespace "ua.com.radiokot.money"` and all Kotlin packages unchanged.
- versionCode 52, versionName "0.33.0".
- Name: "4Money" presented as "4Money — self-hosted fork", credit Oleg Koretsky, link
  https://github.com/Radiokot/4money.
- Header for files added by the fork: `Copyright 2026 Nikita Kovalovs`, same GPL text.
- Build/test command (Git Bash on the dev machine):
  `export JAVA_HOME="$(cygpath -w $(ls -d /e/dev/jdk-21*))" ANDROID_HOME='E:\dev\android-sdk'; ./gradlew.bat testDebugUnitTest lintDebug assembleDebug`
  Never print the contents of either `local.properties`.
- Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01PnZC8W3VU3YDKW6kKJsF7t
  ```

## Rulings

1. **Deep-link scheme.** `money21m` is upstream 4Money's scheme, so a phone with both apps (and the
   owner's phone during the migration) would show a chooser for `money21m://auth`. The fork signs in
   with email/password ("Just authenticate"), so nothing on the server depends on the scheme. Ruling:
   use the RFC 8252 reverse-domain private-use scheme equal to the application id —
   release `io.github.nickkovdev.money`, debug `io.github.nickkovdev.money.debug`. If magic-link
   auth is ever enabled, `<scheme>://auth` must be added to Supabase's redirect allow-list.
2. **Intent action strings** (`ua.com.radiokot.money.actions.ADD_*`, `...action.RECORD_PAYMENT`) are
   names, not package references, and are delivered to explicit components: unchanged.
   `shortcuts.xml` `targetPackage` (main and debug) must equal the new application ids;
   `targetClass` stays (Kotlin package).
3. **Cleartext.** `app/local.properties` key `cleartextHosts` (comma-separated hostnames/IPs,
   whitespace trimmed, empty entries dropped). Default empty = HTTPS only. Each entry must match
   `^[A-Za-z0-9.-]+$` (also `:` for IPv6 literals), otherwise the build fails naming the bad entry
   (prevents XML injection and typos). The XML is generated per variant through the AGP variant API
   `variant.sources.res.addGeneratedSourceDirectory`; the static file is deleted.
4. **Copyright on modified upstream files.** Keep Oleg's line; add a second line
   `   Copyright 2026 Nikita Kovalovs` only when `git diff --numstat upstream/main HEAD` shows
   ≥ 100 added lines for that file. Simple, mechanical, reproducible. Files whose header says
   `Copyright 2026 Oleg Koretsky` and were added by the fork are treated like other added files.
5. **SternBrocotTreeSearchTest.extensiveTest** is a bad test, not a bug: it performs 50 000
   unseeded random moves while its own comment says the double-based positions survive only
   ~1000–7000 moves; upstream copes by healing positions (`a3f3293 Heal account positions…`).
   Ruling: `@Ignore` with an explanatory comment; the deterministic SBT tests stay.
6. **fastlane.** Upstream has only `title.txt`, one changelog and screenshots. Keep screenshots and
   `title.txt`; add `short_description.txt`, `full_description.txt` (EN, the fork) and
   `changelogs/52.txt`.
7. **Personal docs.** `docs/HANDOFF.md` is removed from the tree; its full text plus the private bits
   removed from other docs go to `docs/local/HANDOFF.local.md` (ignored). Generic replacements:
   `CONTRIBUTING.md` (short: PR flow, rules) and `docs/DEVELOPMENT.md` (build, tests, lint, structure,
   spec/plan process). History rewriting is out of scope (forbidden); old commits still contain the
   removed data — noted in the PR.
8. **CI dummy properties**: `supabaseUrl=https://example.invalid`, `supabaseAnonKey=ci-placeholder`,
   `supabaseSchema=money`, `powerSyncUrl=https://example.invalid`, `authTempCredential=ci-placeholder`,
   `cleartextHosts=` (empty).

## Review Focus

- Upgrading owner installs a build with a new id: it is a separate app; the PR must carry the
  migration note (re-login, notification access, widget, device-only prefs).
- `cleartextHosts` with spaces (`a, b`), trailing comma, or missing key must build; a malicious or
  mistyped value (`a"/><x`) must fail the build, not produce broken XML.
- Lint (`lintDebug`) must still pass with the generated resource (no "unused"/"missing" resource errors).
- Redaction must not break the design history: placeholders, not deletions, and every relative link
  that pointed to `docs/HANDOFF.md` must point to `docs/DEVELOPMENT.md` or `docs/self-hosting.md`.
- Docs must state only facts true in the code (migration names, local.properties keys, tables).

---

### Task 1: Own applicationId, deep-link scheme, version

**Files:**
- Modify: `app/build.gradle` (applicationId, versionCode/Name, `uri_scheme` resValues)
- Modify: `app/src/main/res/xml/shortcuts.xml`, `app/src/debug/res/xml/shortcuts.xml` (`targetPackage`)

- [ ] Step 1: `applicationId "io.github.nickkovdev.money"`, `versionCode 52`, `versionName "0.33.0"`;
  `resValue 'string', 'uri_scheme', 'io.github.nickkovdev.money'` (default) and
  `'io.github.nickkovdev.money.debug'` (debug).
- [ ] Step 2: shortcuts `targetPackage` → `io.github.nickkovdev.money` (main) /
  `io.github.nickkovdev.money.debug` (debug). Grep non-Kotlin files (`*.xml`, `*.gradle`, `*.pro`,
  `logback.xml`, `assets/`) for `ua.com.radiokot.money` used as an application id / authority and
  fix any other hit (report what was found).
- [ ] Step 3: Build: `./gradlew.bat assembleDebug`, then confirm
  `app/build/outputs/apk/debug/io.github.nickkovdev.money.debug-0.33.0-debug.apk` exists and the
  merged manifest (`app/build/intermediates/merged_manifests/debug/**/AndroidManifest.xml`) has
  `package="io.github.nickkovdev.money.debug"` and the `quickentry` taskAffinity with the new id.
- [ ] Step 4: Commit `Use own applicationId io.github.nickkovdev.money, bump to 0.33.0 (52)`.

### Task 2: Configurable cleartext hosts

**Files:**
- Delete: `app/src/main/res/xml/network_security_config.xml`
- Modify: `app/build.gradle` (task class + variant wiring)
- Modify: `.gitignore` not needed (output in `build/`)

**Interfaces:** Produces the `cleartextHosts` key used by Tasks 3, 6, 8.

- [ ] Step 1: In `app/build.gradle` define an abstract task `GenerateNetworkSecurityConfigTask`
  with `@Input abstract Property<String> getCleartextHosts()` and
  `@OutputDirectory abstract DirectoryProperty getOutputDir()`; the action parses per Ruling 3 and
  writes `<outputDir>/xml/network_security_config.xml`:
  - always `<base-config cleartextTrafficPermitted="false" />`;
  - if hosts non-empty: one `<domain-config cleartextTrafficPermitted="true">` with one
    `<domain includeSubdomains="false">HOST</domain>` per host, preceded by a comment saying the
    list comes from `cleartextHosts` in `app/local.properties`.
- [ ] Step 2: In `androidComponents.onVariants(selector().all())` register
  `generate<Variant>NetworkSecurityConfig` and call
  `variant.sources.res.addGeneratedSourceDirectory(task, { it.outputDir })`
  (value: `localProperties.getProperty('cleartextHosts', '')`).
- [ ] Step 3: Verify with the worktree's `app/local.properties` containing a test value
  (controller adds `cleartextHosts=` lines; never commit): build `assembleDebug`, inspect the generated
  file under `app/build/generated/` — contains the configured host; with the key removed it has only
  the base-config; with `cleartextHosts=bad"host` the build fails with a message naming the entry.
  Run `lintDebug` — passes.
- [ ] Step 4: Commit `Generate the network security config from cleartextHosts`.

### Task 3: CI and the flaky upstream test

**Files:**
- Delete: `.github/workflows/dogfood_release.yml`
- Create: `.github/workflows/ci.yml`
- Modify: `app/src/test/java/ua/com/radiokot/money/util/SternBrocotTreeSearchTest.kt`

- [ ] Step 1: `@Ignore("…")` on `extensiveTest` plus a comment block per Ruling 5 (an issue-style
  explanation: what fails, why it is the test's assumption, how production copes, what a real fix
  would be — rational/BigInteger positions or a seeded bounded run).
- [ ] Step 2: `ci.yml`: name `CI`; on `pull_request` and `push` to `main`; `ubuntu-latest`;
  `actions/checkout@v4`, `actions/setup-java@v4` (temurin 21), `gradle/actions/setup-gradle@v4`;
  step writing `app/local.properties` with Ruling 8 values; run
  `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`; upload lint HTML report and test
  reports as artifacts on failure (`if: failure()`); `permissions: contents: read`;
  `concurrency` cancelling superseded PR runs.
- [ ] Step 3: Run the full build command locally: `BUILD SUCCESSFUL`, 0 failed tests.
- [ ] Step 4: Commit `Replace the dogfood release workflow with CI; ignore the SBT stress test`.

### Task 4: Copyright headers

**Files:** every file from `git diff --name-only --diff-filter=A upstream/main HEAD` carrying
`Copyright 20(25|26) Oleg Koretsky`; modified files per Ruling 4.

- [ ] Step 1: Write a one-off script in the scratchpad (not committed) that (a) replaces the line in
  added files with `Copyright 2026 Nikita Kovalovs`, (b) for modified files with ≥100 added lines
  inserts `   Copyright 2026 Nikita Kovalovs` (matching indentation of the existing header) on the line
  after Oleg's. Keep line endings as they are.
- [ ] Step 2: Review: `git diff --stat`, spot-check 5 files of each kind; `git grep -c "Nikita Kovalovs"`
  counts match the script's report; no non-header line changed (`git diff -U0 | grep '^[-+]' | grep -v
  Copyright` is only file markers).
- [ ] Step 3: Build command passes. Commit `Credit the fork's author in file headers`, the commit
  body states the criterion and counts.

### Task 5: Personal handoff out of the tree, DEVELOPMENT and CONTRIBUTING

**Files:**
- Modify: `.gitignore` (`/docs/local/`)
- Create (ignored): `docs/local/HANDOFF.local.md`
- Delete: `docs/HANDOFF.md`
- Create: `docs/DEVELOPMENT.md`, `CONTRIBUTING.md`
- Modify: `CLAUDE.md`

- [ ] Step 1: `docs/local/HANDOFF.local.md` = the current `docs/HANDOFF.md` verbatim, plus an
  appendix "Removed from public docs on 2026-10-19" holding every private item Task 6 redacts
  (Task 6 appends to it).
- [ ] Step 2: `docs/DEVELOPMENT.md`: prerequisites (JDK 21, Android SDK, licenses), both
  `local.properties` files (keys with placeholders, link to `docs/self-hosting.md` for values,
  `cleartextHosts`), build/test/lint commands (Windows and Unix), installing with adb, debug vs release
  ids, project structure (feature packages under `app/src/main/java/ua/com/radiokot/money/`:
  `accounts`, `categories`, `transfers`, `inbox`, `overview`, `widget`, `uikit`, …; `supabase/`,
  `deploy/powersync/`, `tools/`), how features are specified and planned (`docs/superpowers/specs`,
  `docs/superpowers/plans`, rulings sections, `docs/redesign`), strings in en+ru (lint
  `MissingTranslation`), GPL header for new files, icons via `tools/icons`, upstream merge note
  (namespace unchanged on purpose), the ignored SBT test.
- [ ] Step 3: `CONTRIBUTING.md`: issues/PRs, branch naming `feature/…`, CI must be green, privacy
  rules for test data and screenshots, server-side changes need a migration file, license of
  contributions (GPL-3.0-or-later as the headers say); links to DEVELOPMENT.
- [ ] Step 4: `CLAUDE.md`: "Start with `docs/DEVELOPMENT.md`"; keep the public-repo rule, the
  migration/container rule and the owner-language note; mention that private notes may exist in
  `docs/local/` (ignored).
- [ ] Step 5: Fix every link to `docs/HANDOFF.md` / `../HANDOFF.md` in the tree.
- [ ] Step 6: Commit `Move the personal handoff out of the tree; add DEVELOPMENT and CONTRIBUTING`.

### Task 6: Redact personal data in docs/superpowers and docs/redesign

**Files:** `docs/superpowers/**`, `docs/redesign/**`, `docs/local/HANDOFF.local.md` (append).

- [ ] Step 1: Scan for IPs, hostnames/SSH aliases, machine paths and usernames, private URLs,
  device model, card digits, real merchant/payee names, personal category or person names, the
  owner's bank account identifiers, real amounts tied to merchants. Neutral placeholders:
  `<server-tailscale-ip>`, `<phone-ip>`, `<ssh-host>`, `<private mockup link>`, `ACME STORE`,
  `COFFEE SHOP RIGA`, `Groceries`, `0000`, etc. Generic chains used purely as examples of bank text
  shape (e.g. a fast-food chain) → generic placeholder too.
- [ ] Step 2: Append each removed item (file, original, placeholder) to the appendix of
  `docs/local/HANDOFF.local.md`.
- [ ] Step 3: Re-run the scan: zero hits. Commit `Redact personal data from the design docs`.

### Task 7: README.md and README.ru.md

**Files:** `README.md` (rewrite), `README.ru.md` (create).

- [ ] Step 1: Sections per the brief: language switch line on top; pitch; credit; Why this fork;
  comparison table fork vs 1Money vs upstream (factual; 1Money-only: recurring/scheduled, budgets,
  search, projected balance, iOS → "not yet"); Mermaid architecture; quick start → `docs/self-hosting.md`;
  building → `docs/DEVELOPMENT.md`; privacy & security model; status; screenshots TODO; license and
  credits (GPL-3.0 `COPYING.txt`, Tabler MIT, Onest OFL, upstream). Keep the upstream "Points of
  interest" as a shorter "For developers" section (it is useful and credits upstream design).
- [ ] Step 2: Russian version written natively, same structure and facts.
- [ ] Step 3: Commit `Rewrite the README for the fork (EN + RU)`.

### Task 8: docs/self-hosting.md

- [ ] Step 1: Write per the brief: requirements, migrations in order (all six files), `money` vs
  `public` (`supabaseSchema`, what to change in migrations), `PGRST_DB_SCHEMAS`, PowerSync (compose,
  env vars from `deploy/powersync/docker-compose.yml`, `service.yaml`, `sync-config.yaml`, replication
  role + publication from the schema migration, storage DB, JWT secret), app user + "Just authenticate"
  (`authTempCredential`), phrase-auth functions as an alternative, every `app/local.properties` key,
  networking (Tailscale + `cleartextHosts`, or HTTPS reverse proxy), replication slots check,
  upgrading order, backups, the optional `update-pairs` function.
- [ ] Step 2: Commit `Add the self-hosting guide`.

### Task 9: 1Money import doc, feature history, fastlane

**Files:** `docs/importing-from-1money.md`, `docs/features.md`, fastlane files per Ruling 6.

- [ ] Step 1: Import doc from `tools/onemoney-import/onemoney_to_4money.py` (arguments, what is
  imported/skipped, deterministic IDs, balances caveat, how to apply the SQL, timezone).
- [ ] Step 2: `docs/features.md`: 0.33.0 (52) on top (this PR), then A … F6 in order with dates
  2026-10-01 … 2026-10-06 and links to spec sections / plans.
- [ ] Step 3: fastlane short/full descriptions + `changelogs/52.txt`.
- [ ] Step 4: Commit `Add 1Money import guide, feature history and store texts`.

### Final: whole-branch review (opus), full build, push, PR.
