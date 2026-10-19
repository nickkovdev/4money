# Contributing

Thanks for your interest in the 4Money self-hosted fork. Build and project details are in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Issues and pull requests

- Open an issue first for anything larger than a small fix, so the direction can be agreed.
- Create a branch from `main` named `feature/<short-name>` (or `fix/<short-name>`) and open a pull
  request against `main`.
- CI (`./gradlew testDebugUnitTest lintDebug assembleDebug`) must be green.
- Keep a pull request focused on one change and describe how you verified it.

## Rules

- **Strings in English and Russian.** Add every new string to `values/strings.xml` and
  `values-ru/strings.xml`; lint fails on a missing translation.
- **Privacy.** Never put real data into issues, pull requests, tests, fixtures or screenshots: no
  card digits, real merchant or payee names, balances, account or person names, server addresses,
  keys, or `local.properties` contents. Use invented data.
- **Server changes.** A change to the database needs a new migration file in
  `supabase/migrations/` (never edit an applied one), the matching update of
  `deploy/powersync/sync-config.yaml` when synced tables change, and a note in the pull request
  about the install order: the migration and the sync rules go to the server before the app build
  that uses them is installed. See [docs/self-hosting.md](docs/self-hosting.md) for upgrading.
- **Namespace.** Keep `ua.com.radiokot.money` packages; this keeps upstream merges possible.
- **License headers.** New files get the GPL header, see
  [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#license-headers).

## License

By contributing you agree that your contribution is licensed under the GNU General Public License,
version 3 or (at your option) any later version, as stated in the file headers and in
[COPYING.txt](COPYING.txt).
