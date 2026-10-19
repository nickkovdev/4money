# Self-hosting 4Money

This guide sets up the backend for the 4Money fork and builds an app that talks to it. It
describes the setup the fork is developed and used with: a self-hosted Supabase (Docker Compose)
plus a self-hosted PowerSync container next to it, reached from the phone over Tailscale. Other
setups (Supabase Cloud, a public HTTPS endpoint) are possible; the places that need changes are
pointed out, but they are not tested by the author.

Contents:

- [Overview](#overview)
- [Requirements](#requirements)
- [1. Database](#1-database)
- [2. PowerSync](#2-powersync)
- [3. App user](#3-app-user)
- [4. Build the app](#4-build-the-app)
- [5. Networking](#5-networking)
- [Operations](#operations)
- [Optional: currency prices (`update-pairs`)](#optional-currency-prices-update-pairs)
- [Optional: phrase auth](#optional-phrase-auth)
- [Troubleshooting](#troubleshooting)

## Overview

```
 Android phone (4Money)                         Server
 ┌───────────────────────┐   reads (sync)   ┌──────────────────────┐  logical    ┌──────────────────────────┐
 │ local SQLite          │ ◄─────────────── │ PowerSync service    │ replication │ Postgres (Supabase)      │
 │ (PowerSync client)    │                  │ :8080                │ ◄────────── │  schema `money`          │
 │                       │   writes (RPC)   └──────────────────────┘             │  publication `powersync` │
 │                       │ ───────────────► Supabase API (Kong) :8000 ─────────► │  DB `powersync_storage`  │
 └───────────────────────┘   sign-in (GoTrue), PostgREST RPC                     └──────────────────────────┘
```

- The app is local-first: every screen reads a local SQLite database that PowerSync keeps in sync.
- **Reads** flow Postgres → logical replication (publication `powersync`, role `powersync_role`)
  → PowerSync → phone. PowerSync keeps its bucket state in a separate Postgres database,
  `powersync_storage` (no MongoDB).
- **Writes** go from the phone's upload queue to Supabase PostgREST as RPC calls: `atomic_crud`
  for accounts, categories, rules, inbox items and notification templates; `transfer`,
  `transfer_edit` and `transfer_revert` for transfers (account balances are changed on the server).
- **Auth** is Supabase Auth (GoTrue). PowerSync accepts the same access tokens: it checks them
  with the Supabase JWT secret and uses the token's user id to select that user's data.
- Bank notifications are parsed on the phone. Notification texts never go to the server as such;
  only the resulting inbox items (including the raw text of the matched notification), rules and
  templates are synced to your server.

All 4Money tables and functions live in one Postgres schema, `money`, so the backend can share a
Supabase instance with other projects.

## Requirements

- **Supabase**, self-hosted with the official Docker Compose setup (tested), or Supabase Cloud
  (untested; see the notes in each section). Postgres must have `wal_level = logical`, which is
  the Supabase default. Check:

  ```sql
  show wal_level;  -- must be "logical"
  ```

- A database user able to create roles with `REPLICATION` and `BYPASSRLS`, a publication and a
  database. On self-hosted Supabase `postgres` is enough; if your image restricts it, use
  `supabase_admin`.
- **Docker** with Compose on a host where PowerSync can reach Postgres
  (image `journeyapps/powersync-service:1.26.1`, memory limit 768 MB in the shipped compose file).
- A way for the phone to reach ports `8000` (Supabase API) and `8080` (PowerSync): Tailscale or
  an HTTPS reverse proxy. See [5. Networking](#5-networking).
- An Android phone with Android 8.0 or newer (`minSdk 26`).
- JDK 21 and the Android SDK to build the app: see [DEVELOPMENT.md](DEVELOPMENT.md).

## 1. Database

### Apply the migrations

The migrations are plain SQL files in `supabase/migrations/`. Apply all of them, in this order,
each one exactly once:

| File | What it adds |
|------|--------------|
| `20261001000000_money_schema.sql` | Schema `money`; tables `currencies` (global, seeded with EUR, USD, UAH, RUB, GBP, PLN, BTC, ETH, USDT), `accounts`, `categories`, `transfers`, `sync_errors`, `daily_prices`; RLS policies; RPCs `atomic_crud`, `transfer`, `transfer_edit`, `transfer_revert`, `get_daily_prices_array`; role `powersync_role` (`replication bypassrls login`, no password) with `select` on the schema; publication `powersync` |
| `20261002000000_money_rules_inbox.sql` | Tables `payee_rules` and `inbox_items` (RLS, grants, added to the publication); `atomic_crud` now also accepts them |
| `20261003000000_money_inbox_direction.sql` | Column `inbox_items.direction` (`outgoing` / `incoming`) |
| `20261004000000_money_upstream_parity.sql` | Dead letter: failed uploads that would never succeed are recorded in `sync_errors` instead of being lost; account position healing; concurrency-safe transfers; indexes |
| `20261005000000_money_payee_rule_ranges.sql` | Amount ranges (`min_amount`, `max_amount`, `range_bounds`) and the `action` (`record` / `ask`) of payee rules |
| `20261006000000_money_notification_templates.sql` | Table `notification_templates` (RLS, grants, publication); `atomic_crud` now also accepts it |

On self-hosted Supabase the database container is `supabase-db`. From the directory with the
repository:

```bash
for f in supabase/migrations/*.sql; do
  echo "Applying $f"
  docker exec -i supabase-db psql -U postgres -v ON_ERROR_STOP=1 -1 < "$f" || break
done
```

`-1` runs each file in one transaction and `ON_ERROR_STOP=1` aborts it on the first error, so a
failed file leaves nothing half-applied. The files have no bookkeeping table: keep track of which
ones you applied (they are not written to be re-run).

Apply all files as the same role (e.g. `postgres`): the `alter default privileges` in the first
migration only covers tables created by the role that ran it, so a later migration run by another
role can leave `powersync_role` without `select` on the new tables.

With any other Postgres client, the same thing is:

```bash
psql "postgresql://postgres:<password>@<db-host>:5432/postgres" -v ON_ERROR_STOP=1 -1 -f <file>
```

### Why the `money` schema, and using another one

The schema exists so 4Money can live in a Supabase instance shared with other projects without
touching their `public` schema. The app does not hardcode it: it sends its RPC calls to the schema
set by the `supabaseSchema` key of `app/local.properties` (see
[4. Build the app](#4-build-the-app)). Note that when the key is absent, the build defaults to
`public`, not `money`.

The server side, however, does hardcode `money`: the migrations reference it throughout
(`money.<table>`, `create schema if not exists money`, `grant ... on schema money`,
`set search_path = money` in every function, `in schema money` grants), and so does
`deploy/powersync/sync-config.yaml` (`FROM money.<table>`). To use a different schema you must
edit the files **before** applying them:

1. Replace the schema name in all six migrations and in `sync-config.yaml`. With a dedicated
   name such as `finance`, a careful search and replace of the identifiers is enough, for example
   `sed -E 's/\bmoney\./finance./g; s/schema money\b/schema finance/g; s/search_path = money\b/search_path = finance/g'`,
   then check the result with `grep -n money` (the remaining hits should be comments and file names).
2. Set `supabaseSchema` to the new name.
3. Expose the new schema in PostgREST (next section).

Using `public` itself is **not recommended**, and never on a database shared with other projects:
several statements act on the whole schema, not only on 4Money objects. In `public` they would
`revoke all on all functions in schema public from public, anon` (breaking other projects'
functions), give `service_role` all privileges and `powersync_role` read access to every table
(`grant select on all tables in schema ...`, plus `alter default privileges`). If you still want
`public` on a database used only by 4Money, review these statements by hand after the replacement,
and drop `create schema if not exists` / `grant usage on schema` lines that become meaningless.

### Expose the schema in PostgREST

The app reaches the RPCs through PostgREST, which only serves schemas listed in its configuration.

Self-hosted Supabase: in the `.env` of your Supabase Docker project, add the schema to
`PGRST_DB_SCHEMAS`:

```bash
PGRST_DB_SCHEMAS=public,storage,graphql_public,money
```

Then recreate the `rest` container so it picks up the new environment (a plain restart does not
re-read `.env`):

```bash
docker compose up -d rest
```

Supabase Cloud: add the schema under the project's API settings ("Exposed schemas").

The migrations grant `usage` on the schema to `anon`, `authenticated` and `service_role`, but
`execute` on the RPCs only to `authenticated`, and all tables have row level security: exposing the
schema does not make data public.

### PowerSync roles and storage database

The first migration creates `powersync_role` without a password and the `powersync` publication.
Set the password yourself (it never goes into the repository):

```sql
alter role powersync_role with password '<replication-password>';
```

PowerSync also needs a database for its bucket storage. The migrations do not create it. Run these
statements as two separate commands, outside a transaction (`create database` cannot run inside one):

```sql
create role powersync_storage_user with login password '<storage-password>';
create database powersync_storage owner powersync_storage_user;
```

For example:

```bash
docker exec -it supabase-db psql -U postgres -c "create role powersync_storage_user with login password '<storage-password>'"
docker exec -it supabase-db psql -U postgres -c "create database powersync_storage owner powersync_storage_user"
```

PowerSync creates its own tables in that database on start. Its contents can be rebuilt at any
time (see [Backups](#backups)).

To check that the publication covers every synced table, use the query in
[Troubleshooting](#troubleshooting) ("Signed in, but nothing syncs").

## 2. PowerSync

`deploy/powersync/` holds the whole deployment:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Container `4money-powersync`, image `journeyapps/powersync-service:1.26.1`, port `8080` published on `${PS_BIND_IP:-127.0.0.1}`, attached to the external Docker network `supabase_default` |
| `service.yaml` | Replication source (`PS_SOURCE_URI`), Postgres bucket storage (`PS_STORAGE_URI`), client auth with the Supabase JWT secret (`PS_JWT_SECRET`), telemetry sharing off |
| `sync-config.yaml` | Sync rules: bucket `global` (all `currencies`) and bucket `user_data` (rows of the signed-in user from `accounts`, `categories`, `transfers`, `sync_errors`, `payee_rules`, `inbox_items`, `notification_templates`). Output names must match the client schema (`app/.../powersync/DbSchema.kt`) |

Copy the three files to a directory on the server, next to them create a `.env` file (never commit
it):

```bash
# Replication source: the Supabase database, as powersync_role.
PS_SOURCE_URI=postgresql://powersync_role:<replication-password>@supabase-db:5432/postgres
# Bucket storage: the powersync_storage database.
PS_STORAGE_URI=postgresql://powersync_storage_user:<storage-password>@supabase-db:5432/powersync_storage
# JWT_SECRET from the Supabase .env (the HS256 secret that signs access tokens).
PS_JWT_SECRET=<supabase-jwt-secret>
# Address to publish port 8080 on, e.g. the server's Tailscale IP.
# Leave unset to publish on 127.0.0.1 only (use this behind a reverse proxy on the same host).
PS_BIND_IP=100.x.y.z
```

Notes:

- `supabase-db` is the container name of the Supabase Postgres; it resolves because the
  PowerSync container joins the `supabase_default` network. That network name comes from the
  Compose project name of your Supabase stack; check it with `docker network ls` and change
  `docker-compose.yml` if yours differs.
- Connect directly to Postgres, not through the Supavisor/pgbouncer pooler: logical replication
  does not work through a transaction pooler.
- `service.yaml` sets `sslmode: disable` because the traffic stays inside the Docker network. If
  your database is elsewhere, enable TLS there.
- URL-encode special characters in the passwords inside the URIs.
- Supabase Cloud: there is no `supabase_default` network; remove the `networks` section, use the
  project's direct connection string as `PS_SOURCE_URI` (with TLS) and keep the storage database
  wherever you run PowerSync. `service.yaml` assumes the legacy HS256 JWT secret; a project that
  uses asymmetric JWT signing keys needs a different `client_auth` section (see the PowerSync
  documentation).

Start it:

```bash
docker compose up -d
docker logs -f 4money-powersync
```

The log should show the replication connection and an initial snapshot of the `money` tables
without errors. Then check that exactly one replication slot exists for it (see
[Replication slots](#replication-slots)).

After any change to `sync-config.yaml`, copy it to the server again and restart the container
(`docker restart 4money-powersync`). PowerSync then reprocesses the data with the new rules.

## 3. App user

The app's start screen has a **Just authenticate** button. It signs in to Supabase Auth by email
and password, using the `authTempCredential` value from `app/local.properties` as **both** the
email and the password (see `TempAuthScreenViewModel` and `SignInWithCredentialsUseCase`). So the
credential must be:

- a syntactically valid email address,
- at least as long as your Auth minimum password length (6 by default) and at most 72 characters
  (the bcrypt limit),
- hard to guess, because it is the only secret protecting your data.

Generate one, for example:

```bash
CRED="$(openssl rand -hex 24)@example.com"
```

Create a confirmed user with that value as email and password through the Auth admin API, using
the `SERVICE_ROLE_KEY` from your Supabase `.env` (on Cloud: the service role key of the project):

```bash
curl -sS -X POST "https://supabase.example.com/auth/v1/admin/users" \
  -H "apikey: $SERVICE_ROLE_KEY" \
  -H "Authorization: Bearer $SERVICE_ROLE_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$CRED\", \"password\": \"$CRED\", \"email_confirm\": true}"
```

(The Supabase Studio "Add user" dialog with "Auto confirm user" does the same.) No mail server is
needed, and public sign-ups can stay disabled (`DISABLE_SIGNUP=true` in the self-hosted `.env`):
the admin API ignores that setting.

Security consequence: the credential is compiled into the APK (`BuildConfig.AUTH_TEMP_CREDENTIAL`).
Anyone who gets your APK can extract it and sign in as you. Do not publish builds made with your
`app/local.properties`, and keep the server reachable only by you (see
[5. Networking](#5-networking)). The screen also offers **Use recovery phrase**, which needs the optional
edge functions described in [Optional: phrase auth](#optional-phrase-auth).

All user data references `auth.users(id)` with `on delete cascade`: deleting the Auth user deletes
all of that user's 4Money data.

## 4. Build the app

The build reads `app/local.properties` (the build fails if the file is missing). It is ignored by
git; never commit it. Keys:

| Key | Required | Example | Meaning |
|-----|----------|---------|---------|
| `supabaseUrl` | yes | `https://supabase.example.com` or `http://100.x.y.z:8000` | Supabase API URL (Kong; port `8000` on self-hosted) |
| `supabaseAnonKey` | yes | `eyJhbGciOi...` | `ANON_KEY` from the Supabase `.env` (Cloud: the anon/publishable key) |
| `supabaseSchema` | no, but set it | `money` | Schema the app sends PostgREST RPC calls to. Defaults to `public` when absent |
| `powerSyncUrl` | yes | `https://powersync.example.com` or `http://100.x.y.z:8080` | PowerSync service URL |
| `authTempCredential` | yes | `<48 hex chars>@example.com` | Email and password of the app user, see [3. App user](#3-app-user) |
| `cleartextHosts` | no | `100.x.y.z` or `100.x.y.z, myserver.tailnet-name.ts.net` | Hosts the app may reach over plain HTTP. Comma-separated host names or IP addresses; empty or absent means HTTPS only. See [5. Networking](#5-networking) |

A missing required key does not fail the build; the app gets the literal string `null` and
cannot connect. The root `local.properties` holds only `sdk.dir` (the Android SDK path), which
Android Studio writes for you.

Example `app/local.properties`:

```properties
supabaseUrl=http://100.x.y.z:8000
supabaseAnonKey=<anon key>
supabaseSchema=money
powerSyncUrl=http://100.x.y.z:8080
authTempCredential=<credential>
cleartextHosts=100.x.y.z
```

Build and install (from the repository root; on Windows use `gradlew.bat`):

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # app/build/outputs/apk/release/
adb install -r app/build/outputs/apk/release/<apk file>
```

The debug build has the application id `io.github.nickkovdev.money.debug` and the release build
`io.github.nickkovdev.money`; they install side by side.
Do not enable auto-booking (notification access and sources) in two installs signed in to the same
account: each would book the same payment, because notification de-duplication is local to the device. The release build is signed with your
local debug key unless a CI keystore is present. Details about the toolchain are in
[DEVELOPMENT.md](DEVELOPMENT.md).

Open the app, tap **Just authenticate**, and the first sync should bring the seeded currencies.

## 5. Networking

The phone must reach the Supabase API and PowerSync. Android blocks plain HTTP (cleartext) by
default, so pick one of two options.

### Option A: Tailscale (what the author uses)

Install Tailscale on the server and on the phone. Publish PowerSync on the server's Tailscale
address (`PS_BIND_IP=100.x.y.z`), make sure the Supabase API port `8000` is reachable on that
address too, and do not open either port to the internet.

Traffic inside a tailnet is encrypted by WireGuard, so plain HTTP is acceptable there. Allow it
for exactly that host with `cleartextHosts`:

```properties
supabaseUrl=http://100.x.y.z:8000
powerSyncUrl=http://100.x.y.z:8080
cleartextHosts=100.x.y.z
```

How `cleartextHosts` works: the value is split on commas, whitespace is trimmed and empty entries
are dropped. Each entry must consist only of letters, digits, `.` and `-` (and `:` for IPv6
literals); anything else fails the build with a message naming the entry. The build generates the
app's network security config from the list, permitting cleartext only to those exact hosts. With
an empty or missing value no cleartext host is allowed. The list is compiled in: changing it
requires a new build.

The phone loses access to the backend whenever Tailscale is off; the app keeps working offline
and syncs when the connection returns.

### Option B: HTTPS reverse proxy

Put both services behind a reverse proxy with valid certificates and leave `cleartextHosts` empty.
For example, with Caddy on the same host (PowerSync bound to `127.0.0.1`, the default):

```
supabase.example.com {
    reverse_proxy 127.0.0.1:8000
}

powersync.example.com {
    reverse_proxy 127.0.0.1:8080 {
        flush_interval -1
    }
}
```

The PowerSync endpoint streams sync data over long-lived connections: the proxy must not buffer
responses and must allow WebSocket upgrades, and its idle timeouts must not be too short.

```properties
supabaseUrl=https://supabase.example.com
powerSyncUrl=https://powersync.example.com
cleartextHosts=
```

This exposes your Supabase to the internet. Keep sign-ups disabled, keep the service role key and
JWT secret secret, and remember that the app credential is inside the APK (see
[3. App user](#3-app-user)).

## Operations

### Replication slots

PowerSync replicates through a logical replication slot. A slot that is no longer consumed (for
example after the PowerSync storage database was recreated, or a second PowerSync instance was
started and removed) makes Postgres keep WAL forever and eventually fills the disk. After every
PowerSync (re)deployment, check that there is exactly one active `powersync` slot:

```sql
select slot_name,
       active,
       pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) as retained_wal
from pg_replication_slots
where slot_name like 'powersync%';
```

Drop inactive leftovers (never the active one):

```sql
select pg_drop_replication_slot('<slot_name>');
```

### Upgrading

When you update to a newer version of the fork, use this order:

1. **Apply the new migrations** (the files you have not applied yet, in order).
2. **Deploy the new `sync-config.yaml`** and restart PowerSync; check the replication slots.
3. **Then install the new app build.**

Why this order:

- The app uploads changes through `atomic_crud`, which accepts only a fixed list of tables, and
  writes the columns it knows. If a new build writes to a table or column the server does not have
  yet, the call fails with a permission or "undefined column" error. Since
  `20261004000000_money_upstream_parity.sql` such errors are treated as permanent: the change is
  moved to `money.sync_errors` and reverted on the phone, not retried.
- `sync-config.yaml` selects the new columns and tables, so it fails if the migrations are not
  applied yet; and without it the new build would not receive the new data.
- Migrations are written to keep older builds working (new tables, new columns with defaults or
  nullable), so applying them before updating the phone is safe. Each migration's header
  comment says if it has extra requirements.

### Backups

Back up the 4Money data with `pg_dump`:

```bash
docker exec supabase-db pg_dump -U postgres -n money -Fc > money-$(date +%F).dump
```

The `money` tables reference `auth.users` by `user_id`, so a restore into a fresh instance needs
the same Auth users (same ids). Either back up the whole database as well (or at least
`auth.users`), or recreate the users with their original ids before restoring.

The `powersync_storage` database does not need a backup: it only holds PowerSync's derived bucket
state. To rebuild it, stop PowerSync, drop and recreate the database (see
[PowerSync roles and storage database](#powersync-roles-and-storage-database)), start PowerSync
again, then drop the old, now inactive replication slot. Phones resync automatically.

## Optional: currency prices (`update-pairs`)

Totals across accounts in different currencies need daily USD prices in `money.daily_prices`,
which the app downloads through the `get_daily_prices_array` RPC. Nothing fills that table by
default; without it, mixed-currency totals have no rates. If all your accounts use one currency,
you can skip this.

`supabase/functions/update-pairs` is a Supabase Edge Function (from upstream 4Money) that fetches
prices for the current day (or for the day given in the `time` query parameter, for backfilling)
and upserts them into `daily_prices`:

- crypto prices (BTC, ETH, XMR, CCD) from the public Binance and KuCoin APIs, USDT fixed at 1;
- fiat prices from the Wise rates API, which needs the `WISE_API_KEY` secret; without it, no
  fiat prices are written;
- it connects to Postgres with `SUPABASE_DB_URL` (provided by the Supabase functions runtime).

It is **not deployed** by the fork's author, and as shipped it inserts into the unqualified table
name `daily_prices`, which resolves to `public`. With the `money` schema, change
`INSERT INTO daily_prices` to `INSERT INTO money.daily_prices` in `index.ts` before deploying.

Deploy it with `supabase functions deploy update-pairs` (Cloud, uses `supabase/config.toml`,
`verify_jwt = true`), or on self-hosted Supabase copy the function directory into the
`volumes/functions/` directory of your Supabase Docker project and restart the `functions`
container. Then call it once a day, for example from cron on the server:

```bash
curl -sS -X POST "https://supabase.example.com/functions/v1/update-pairs" \
  -H "Authorization: Bearer $SERVICE_ROLE_KEY"
```

## Optional: phrase auth

Upstream 4Money signs in with a seed phrase (Ed25519 key) instead of a password. The **Use recovery phrase**
button on the start screen still offers that flow. It needs two edge functions from
`supabase/functions`, which the fork's author does **not** deploy:

- `signature-auth-challenge` returns a short-lived signed challenge;
- `signature-auth` verifies the signature, creates a confirmed user derived from the public key
  if it does not exist yet (`<uuid>@4mn.local`), and returns a session.

Both need the `SIGNATURE_AUTH_CHALLENGE_SECRET` secret; `signature-auth` also uses
`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`. Both run with `verify_jwt = false`.

Be aware that `signature-auth` creates a user for any new key: everyone who can reach your
Supabase URL can then create an account (row level security still keeps the data of each user
separate). With email/password auth this is not possible when sign-ups are disabled.

## Troubleshooting

**"Just authenticate" shows an error.** The toast reads "Sign in failed: <exception class
name>" (for example `UnknownServiceException`); the message and stack trace are in `adb logcat`.
- `UnknownServiceException` ("CLEARTEXT communication ... not permitted" in logcat): the host of an
  `http://` URL is not in `cleartextHosts`; add it and rebuild, or use HTTPS.
- An Auth error with "Invalid login credentials" in logcat: the user does not exist, is not
  confirmed, or its email/password differs from `authTempCredential`.
- Timeouts or connection errors: the phone cannot reach `supabaseUrl` (Tailscale off, port not
  published, wrong address).

**Signed in, but nothing syncs.**
- `docker logs 4money-powersync`: authentication errors mean `PS_JWT_SECRET` is not the secret
  that signs your Supabase access tokens; replication errors point to the role password,
  `wal_level`, or a missing publication (`select * from pg_publication_tables where pubname = 'powersync';`
  should list the eight `money` tables used in `sync-config.yaml`).
- Check that the phone reaches `powerSyncUrl`.

**Changes are reverted and Settings shows "There is a data upload error".** The server rejected an
upload permanently and recorded it in `money.sync_errors` (most often: a new app build on an
old database, see [Upgrading](#upgrading)). Inspect:

```sql
select id,
       user_id,
       details ->> 'sqlstate' as sqlstate,
       details ->> 'message'  as message,
       details -> 'payload'   as payload
from money.sync_errors
order by id desc
limit 20;
```

`payload` holds the rejected operations. Fix the cause (usually: apply the missing migration),
re-enter or replay the change if needed, then delete the rows; the notice disappears once the
user has no rows in `sync_errors`.

**RPC errors from PostgREST.**
- `PGRST106` ("The schema must be one of the following"): the schema is not in
  `PGRST_DB_SCHEMAS`, or `supabaseSchema` is wrong or missing (it defaults to `public`).
- `PGRST202` ("Could not find the function"): a migration is missing, or PostgREST still has an old
  schema cache. Reload it with `notify pgrst, 'reload schema';`.

**The database disk keeps growing.** Check for an inactive replication slot
([Replication slots](#replication-slots)).

**Totals in another currency show no amount.** `money.daily_prices` is empty; see
[Optional: currency prices](#optional-currency-prices-update-pairs).
