# Release Workflow Design

**Date:** 2026-06-05
**Status:** Approved (design), pending implementation plan

## Goal

A GitHub Actions workflow that performs a full release: build the application
jar, ship it to the Azure VM over SCP using a secret `.pem` key, then SSH in and
run `docker compose up -d --build`. The release is considered done only when the
containers spin up and the bot actually starts.

## Context

- Repo already has `.github/workflows/build-test.yaml` running `mvn clean package`
  on every push.
- The `local/` directory (holding the `.pem`, `.env`, `Dockerfile`,
  `docker-compose.yaml`) is **gitignored** — these files are not in the repo and
  must already live on the VM.
- The VM's compose uses a **single-stage `Dockerfile`** that does
  `COPY ./*.jar /app/app.jar` — it expects a pre-built jar in the build context
  (the VM user's `~/discord-bot/`). The scp-the-jar flow is therefore
  valid.
- The app is a **headless JDA bot** — there is **no `spring-boot-starter-web`**,
  so Spring Actuator has no HTTP server and `/actuator/health` is not reachable.
  Health must be confirmed another way (logs).
- Reference commands live in `local/howtoconnect.md`.

## Decisions

| Topic | Decision |
|-------|----------|
| Trigger | Push of a git tag matching `v*` (e.g. `v0.3.0`) |
| VM files | Dockerfile / compose / .env persist on the VM; workflow ships **only the jar** |
| VM Dockerfile | Single-stage, `COPY ./*.jar` — needs a pre-built jar |
| Success check | Containers Up **and** bot log shows `Started KillTeamDiscordBotApplication` |
| SSH/SCP tooling | `appleboy/ssh-action` + `appleboy/scp-action` |
| Structure | **Single job** (`release`): build then deploy in sequence — jar stays on the runner, no artifact round-trip |
| Docker invocation | **Every** docker command prefixed with `sudo` |

## Workflow Design

**File:** `.github/workflows/release.yaml` (separate from `build-test.yaml`)

**Trigger:**
```yaml
on:
  push:
    tags:
      - 'v*'
```

### Single job — `release`

The whole release runs in one job so the freshly built jar stays on the runner's
filesystem — no `upload-artifact` / `download-artifact` round-trip is needed.

**Build steps**
1. `actions/checkout@v4`.
2. `actions/setup-java@v4` — Java 21, Temurin.
3. `mvn clean package` — runs the full test suite (39 tests); failing tests abort
   the release.

**Deploy steps**
4. **Clean old jars on VM** (`appleboy/ssh-action`):
   `rm -f ~/discord-bot/*.jar` — prevents `COPY ./*.jar` from failing when more
   than one jar is present.
5. **Ship the jar** (`appleboy/scp-action`) → the VM's `~/discord-bot/` build
   context, reading the jar straight from `target/`.
6. **Deploy** (`appleboy/ssh-action`):
   `cd ~/discord-bot && sudo docker compose up -d --build`.
7. **Verify** (`appleboy/ssh-action`):
   - Wait a short grace period.
   - `sudo docker compose ps` — confirm postgres is healthy and the bot container
     has not exited.
   - Poll `sudo docker compose logs bot` for `Started KillTeamDiscordBotApplication`
     up to a timeout.
   - On timeout: dump the last ~50 log lines (`sudo docker compose logs bot --tail 50`)
     and fail the job with a non-zero exit.

### Verification script (remote, illustrative)
```bash
cd ~/discord-bot
# bot container must be running
sudo docker compose ps --status running --services | grep -qx bot || {
  echo "bot container is not running"; sudo docker compose ps; exit 1; }
# app must report it started
for i in $(seq 1 30); do
  if sudo docker compose logs bot | grep -q "Started KillTeamDiscordBotApplication"; then
    echo "bot started successfully"; exit 0
  fi
  sleep 5
done
echo "bot did not start within timeout"; sudo docker compose logs bot --tail 50; exit 1
```

## Secrets (created in GitHub repo settings)

All connection details are stored as GitHub repository secrets and are **not**
versioned in this spec or anywhere in the repo.

| Secret | Description |
|--------|-------------|
| `VM_SSH_KEY` | Contents of the `.pem` private key used to authenticate to the VM |
| `VM_HOST` | Public IP / hostname of the Azure VM |
| `VM_USER` | SSH login user on the VM |

## Assumptions

- The VM's `~/discord-bot/` already contains the working `docker-compose.yaml`,
  the single-stage `Dockerfile`, and the `.env` / `.env-local` files. These
  persist between releases; the workflow does not ship them.
- Passwordless `sudo` is configured for docker on the VM (howtoconnect uses
  `sudo docker ...`).
- The pom `<version>` is bumped to match the tag before tagging (e.g. tag
  `v0.3.0` ↔ pom `0.3.0`). The workflow ships whatever jar the build produces
  regardless of filename, so this is for clarity, not correctness.

## Out of Scope

- Shipping `Dockerfile` / `docker-compose.yaml` / `.env` from the repo or secrets
  (they persist on the VM).
- Adding `spring-boot-starter-web` to expose an HTTP health endpoint.
- Database backup/migration steps as part of the release.
- Rollback automation.
```

