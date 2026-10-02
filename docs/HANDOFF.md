# Handoff — personal 4Money fork (state as of 2026-10-02)

For the next agent/session continuing this work on another machine. Read this first, then
`docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md` (the binding spec) and, if needed,
the two plans in `docs/superpowers/plans/`.

**This repo is PUBLIC** (fork of `Radiokot/4money`). Never commit keys, credentials, card digits,
personal category names or other personal data. `app/local.properties`, `local.properties`,
`1Money_BACKUP_*.bin` and `tools/icons/local/` are ignored — keep it that way.

## Remotes

- `origin` — `github.com/nickkovdev/4money` (this fork, push here)
- `upstream` — `github.com/Radiokot/4money` (original author; pull updates from here)
- the owner also has a private Gitea mirror (`nas`), optional

## What exists

| | What | State |
|---|---|---|
| A | Backend: `money` schema in a shared self-hosted Supabase + self-hosted PowerSync | live |
| B | 1Money history import (SQLite backup → SQL) | done, data is on the server |
| C/D | SEB Latvia bank notification listener → payee rules → auto expense / income or Inbox | merged, **verified on device** 2026-10-02 (card, account and incoming payments; rules learned; no `sync_errors`) |
| E | 1Money-style UI: dark theme, Tabler icons, Categories ring, Overview tab, period swipe, animations, swipe-to-edit/delete, redesigned transfer sheet | merged, in use; being replaced by F |
| F | Full redesign (themes Midnight/Paper/Ember/Aurora, new components), Inbox swipe cards, payee rules by amount, "ask in the notification" | branch `feature/redesign`, **not verified on device, needs a migration first**: see `docs/redesign/PROGRESS.md` |

Key places:
- Server schema/RPCs: `supabase/migrations/` (applied in order: `20261001000000_money_schema.sql`,
  `20261002000000_money_rules_inbox.sql`, `20261003000000_money_inbox_direction.sql`).
- PowerSync deployment: `deploy/powersync/` (compose, `service.yaml`, `sync-config.yaml`).
- 1Money importer: `tools/onemoney-import/onemoney_to_4money.py` (table semantics documented inside).
- Icons: `tools/icons/import_tabler.py` (Tabler Icons, MIT, pinned version) → `*_itemicon` drawables.
- Notifications: `app/src/main/java/ua/com/radiokot/money/inbox/` (listener, SEB parser, rules, Inbox).
- Theme: `MoneyTheme` (search for it), choice in Settings → Appearance.

## Infrastructure

- Server: the owner's Azure VM, reachable **only over Tailscale** at `100.87.103.65`
  (the phone must have Tailscale on). SSH alias on the owner's machines: `azure-vm`.
  - Supabase (shared with other projects!): compose in `~/supabase-project/`, Kong `:8000`,
    Postgres container `supabase-db`. 4Money lives ONLY in schema `money` — never touch `public`
    or other schemas. `money` is in `PGRST_DB_SCHEMAS` in `~/supabase-project/.env`.
  - PowerSync: container `4money-powersync`, files in `~/4money-powersync/` (+ `.env` with secrets),
    API `100.87.103.65:8080`, bucket storage in DB `powersync_storage`.
  - After changing `sync-config.yaml`: copy it to `~/4money-powersync/` and `docker restart 4money-powersync`;
    then check `pg_replication_slots` has exactly one active `powersync_*` slot (an orphaned slot fills the disk).
- Applying a migration (production, shared DB — get the owner's OK first):
  `scp <file> azure-vm:/tmp/m.sql && ssh azure-vm 'docker cp /tmp/m.sql supabase-db:/tmp/m.sql && docker exec supabase-db psql -U postgres -v ON_ERROR_STOP=1 -1 -f /tmp/m.sql'`
- The app user is a single Supabase email user; its credential is in `~/.4money_cred` on the VM.

## Build setup on a new machine

1. JDK 21 (Gradle 9.6 needs ≥17) and Android SDK command-line tools. On the original PC they were
   portable in `E:\dev\jdk-21*` and `E:\dev\android-sdk` (no installer). Android Studio works too.
   Accept licenses once: `sdkmanager --licenses`. AGP downloads platform 37 / build-tools itself.
2. `local.properties` in the repo root: `sdk.dir=<path to Android SDK>` (on Windows use forward
   slashes or escape: `sdk.dir=E\:/dev/android-sdk`).
3. `app/local.properties` — keys the build requires (`app/build.gradle` fails without the file):
   ```
   supabaseUrl=http://100.87.103.65:8000
   supabaseAnonKey=<ANON_KEY from ~/supabase-project/.env on the VM>
   supabaseSchema=money
   powerSyncUrl=http://100.87.103.65:8080
   authTempCredential=<contents of ~/.4money_cred on the VM>
   ```
   Generate it without printing secrets:
   ```bash
   ANON=$(ssh azure-vm 'grep ^ANON_KEY= ~/supabase-project/.env | cut -d= -f2-')
   CRED=$(ssh azure-vm 'cat ~/.4money_cred')
   printf "supabaseUrl=http://100.87.103.65:8000\nsupabaseAnonKey=%s\nsupabaseSchema=money\npowerSyncUrl=http://100.87.103.65:8080\nauthTempCredential=%s\n" "$ANON" "$CRED" > app/local.properties
   ```
   If there's no SSH to the VM from the laptop, copy `app/local.properties` from the owner's PC.
   **Signing:** debug builds are signed with the machine's `~/.android/debug.keystore`. The phone
   currently has a build signed by the PC's key — a build from another machine can't be installed
   over it (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Either copy `debug.keystore` from the PC
   (`C:\Users\PC\.android\debug.keystore`) to the laptop, or uninstall the app first — data is
   server-side, so a reinstall just re-syncs (local unsynced edits would be lost).
4. Build / test / install (Windows Git Bash shown; on macOS/Linux use `./gradlew`):
   ```bash
   export JAVA_HOME=<jdk21> ANDROID_HOME=<sdk>
   ./gradlew.bat testDebugUnitTest   # expected: only SternBrocotTreeSearchTest > extensiveTest fails (pre-existing upstream)
   ./gradlew.bat assembleDebug
   adb install -r app/build/outputs/apk/debug/ua.com.radiokot.money.debug-*-debug.apk
   ```
   Debug application id: `ua.com.radiokot.money.debug`. Launch: `adb shell monkey -p ua.com.radiokot.money.debug -c android.intent.category.LAUNCHER 1`.
   Sign in with "Just authenticate" (uses `authTempCredential`).

Second machine (owner's laptop, set up 2026-10-02): JDK = Zulu 22 (`C:/Program Files/Zulu/zulu-22`,
works with Gradle 9.6), SDK in `C:/Users/KOVNIK/dev/android-sdk`, the PC's `debug.keystore` copied, so
builds from both machines install over each other. Local-only files there (never commit): `schema.sql`
(the original author's schema, excluded via `.git/info/exclude`) and `docs/letter-to-oleg.md` (a draft
letter to him, same). Copy them over manually if the PC needs them.

Device notes: Samsung S25+ (Android, no root). Be careful with UI automation over adb — `KEYCODE_BACK`
can leave the app and screenshots then capture the owner's home screen; prefer asking the owner to tap.
Reading a specific notification: `adb shell dumpsys notification --noredact` and grep for the package,
never dump everything into a chat.

## Next steps (in order)

0. **Redesign (current work).** Continue from `docs/redesign/PROGRESS.md` (state, install order,
   next steps, open decisions) and `docs/redesign/BRIEF.md` (requirements). Mockups (private, owner's
   claude.ai): https://claude.ai/artifact/KEA2TwZvtvC6U6qHwGgCzo. Before installing the branch head the
   migration `20261004000000_money_payee_rule_ranges.sql` must be applied (owner's OK), see PROGRESS.md.

1. **On-device verification** (C/D done; E is superseded by F, so check the remaining items on F):
   - Theme light/dark/system incl. dialogs and date picker; avatars; bottom bar; profile icon → settings.
   - Categories ring (shares, centre totals, tap toggles); Overview (bars, toggle, "More…").
   - Swipes: horizontal on empty space = month; vertical fling must scroll; row left-swipe = edit/delete,
     delete undo within 4 s; deleting then switching tab still commits.
   - Transfer sheet: two halves, subcategory chips, keypad "=" then ✓, date key, edit/income/account→account.
   - Notifications: Settings → Bank notifications → grant access (Android 13+: App info → ⋮ →
     "Allow restricted settings"). A SEB card payment → pending Inbox item within a second (no network),
     card row shown; categorize with "Remember" → rule; same merchant again → auto expense with payee memo,
     Undo works; foreign currency → always pending; Google Wallet ignored; after sync rows appear in
     `money.inbox_items` / `money.payee_rules` and no new `money.sync_errors`.
   - App in background → PowerSync stream disconnects after ~30 s (battery).
2. **Upstream schema.** The original author sent the owner his full Supabase schema (incl. transfer edit
   functions and triggers that fix account positions on reordering). Ours was reconstructed from the
   client. Diff his against `supabase/migrations/20261001000000_money_schema.sql`, write a new migration
   with what's missing (position triggers at least), adapted to the `money` schema. Don't commit his file
   verbatim unless he agrees.
3. More SEB templates: parsed are the card payment ("Jauna rezervācija"), the outgoing account payment
   ("Jauns darījums" / "Jūs samaksājāt … <payee> par …") and the incoming one ("<payer> samaksāja …",
   recorded as an income). Still missing: refund and other variants; unmatched SEB texts land in the
   Inbox raw. The listener also processes notifications still shown when it connects.
4. Known gaps: `update-pairs` edge function + cron for currency prices not deployed (all accounts are EUR,
   so only foreign totals are affected); phrase (Ed25519) auth edge functions not deployed (not needed for
   one user); Kong `:8000` is open to the internet via the Azure NSG (owner's TODO, data is protected by RLS).
5. Out of scope so far: recurring/scheduled transfers, projected balance, search, drag account→category.

## Decisions worth knowing

- Rules/inbox live inside the app (local-first, no network per notification) — battery is a hard requirement.
- Only `se.seb.latvia` is a source; Google Wallet posts duplicates and is ignored.
- Dedup = hash(package, title, text) for recognized payments: two identical payments in the same minute count once.
- Foreign-currency payments never auto-create an expense.
- No 1Money assets copied (proprietary); icons are Tabler (MIT).
- The full list of implementation rulings is in the branch commit history and the plans.
