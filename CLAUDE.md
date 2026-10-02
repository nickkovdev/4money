# Personal 4Money fork

Start with `docs/HANDOFF.md`: current state, build setup, server access and next steps.
The binding spec is `docs/superpowers/specs/2026-10-01-self-hosted-4money-design.md`.

- This repo is PUBLIC. Never commit secrets, `local.properties`, card digits or personal data.
- The backend is a shared production Supabase: 4Money uses only the `money` schema. Ask the owner
  before applying migrations or restarting server containers.
- The owner speaks Russian, is a backend/DevOps developer with little Android experience — explain
  Android tooling steps.
