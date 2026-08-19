# Release Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions workflow that, on a `v*` tag push, builds the jar, ships it to the Azure VM over SCP with a secret `.pem` key, runs `sudo docker compose up -d --build`, and confirms the bot actually started.

**Architecture:** A single `release` job on `ubuntu-latest`. It builds with Maven (jar stays on the runner), then uses `appleboy/scp-action` and `appleboy/ssh-action` to clean old jars on the VM, copy the new jar into the VM's `~/discord-bot/` build context, rebuild/restart the stack, and verify. All Docker commands run with `sudo`. The Dockerfile, docker-compose.yaml and `.env` files already live on the VM and persist between releases — only the jar is shipped.

**Tech Stack:** GitHub Actions, `actions/checkout@v4`, `actions/setup-java@v4` (Java 21 Temurin), Maven, `appleboy/scp-action@v1`, `appleboy/ssh-action@v1`, Docker Compose on the VM.

**Git note:** Per the user's standing preference, the user performs all git operations themselves. Commit steps below are written for completeness but **must not be run automatically** — leave committing to the user.

**Reference spec:** `docs/superpowers/specs/2026-06-05-release-workflow-design.md`

---

## File Structure

- **Create:** `.github/workflows/release.yaml` — the entire release workflow (single job, build + deploy + verify).
- **Create:** `docs/RELEASING.md` — documents the required GitHub secrets and how to cut a release (no README exists yet).

No application code changes. The two files are independent; the workflow is the substance, the doc is a convenience.

---

## Task 1: Create the release workflow file

**Files:**
- Create: `.github/workflows/release.yaml`

This workflow is not unit-testable in the traditional sense. "The test" is (a) the file is valid YAML and (b) a real tag push performs a successful release (Task 3, manual). We validate YAML syntax with Python before committing.

- [ ] **Step 1: Write the full workflow file**

Create `.github/workflows/release.yaml` with exactly this content:

```yaml
name: release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - name: checkout
        uses: actions/checkout@v4

      - name: setup-java
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: build
        run: mvn clean package

      - name: clean-old-jars-on-vm
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VM_HOST }}
          username: ${{ secrets.VM_USER }}
          key: ${{ secrets.VM_SSH_KEY }}
          script: rm -f ~/discord-bot/*.jar

      - name: ship-jar
        uses: appleboy/scp-action@v1
        with:
          host: ${{ secrets.VM_HOST }}
          username: ${{ secrets.VM_USER }}
          key: ${{ secrets.VM_SSH_KEY }}
          source: "target/kill-team-discord-bot-*.jar"
          target: "/home/${{ secrets.VM_USER }}/discord-bot/"
          strip_components: 1

      - name: deploy
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VM_HOST }}
          username: ${{ secrets.VM_USER }}
          key: ${{ secrets.VM_SSH_KEY }}
          script: |
            cd ~/discord-bot || exit 1
            sudo docker compose up -d --build

      - name: verify
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VM_HOST }}
          username: ${{ secrets.VM_USER }}
          key: ${{ secrets.VM_SSH_KEY }}
          script: |
            cd ~/discord-bot || exit 1
            sudo docker compose ps --status running --services | grep -qx bot || {
              echo "bot container is not running"; sudo docker compose ps; exit 1; }
            for i in $(seq 1 30); do
              if sudo docker compose logs bot | grep -q "Started KillTeamDiscordBotApplication"; then
                echo "bot started successfully"; exit 0
              fi
              sleep 5
            done
            echo "bot did not start within timeout"
            sudo docker compose logs bot --tail 50
            exit 1
```

Notes for the implementer (do not put these in the file):
- `strip_components: 1` removes the leading `target/` from the source path so the jar lands directly in `~/discord-bot/` (the user's home-relative `discord-bot/` directory), not `~/discord-bot/target/`.
- `clean-old-jars-on-vm` runs **before** `ship-jar` so the single-stage Dockerfile's `COPY ./*.jar` never sees more than one jar.
- `deploy` and `verify` are separate SSH steps for clearer per-step logs in the Actions UI.
- The verify loop polls for up to ~150s (30 × 5s) for Spring's `Started KillTeamDiscordBotApplication` log line.

- [ ] **Step 2: Validate the YAML parses**

Run (PowerShell):
```powershell
python -c "import yaml; yaml.safe_load(open('.github/workflows/release.yaml')); print('YAML OK')"
```
Expected output: `YAML OK` (no traceback).

- [ ] **Step 3: Sanity-check the structure**

Run (PowerShell):
```powershell
python -c "import yaml; d=yaml.safe_load(open('.github/workflows/release.yaml')); steps=d['jobs']['release']['steps']; names=[s.get('name') for s in steps]; print(names); assert names==['checkout','setup-java','build','clean-old-jars-on-vm','ship-jar','deploy','verify'], 'unexpected steps'; print('STRUCTURE OK')"
```
Expected output: the list of step names followed by `STRUCTURE OK`.

- [ ] **Step 4: Commit (performed by the user — do not run git automatically)**

```bash
git add .github/workflows/release.yaml
git commit -m "feat: add release workflow for tag-triggered VM deploy"
```

---

## Task 2: Document the release process and required secrets

**Files:**
- Create: `docs/RELEASING.md`

- [ ] **Step 1: Write the release documentation**

Create `docs/RELEASING.md` with exactly this content:

```markdown
# Releasing

Releases are performed automatically by the `.github/workflows/release.yaml`
GitHub Actions workflow when a version tag is pushed.

## Required GitHub repository secrets

Create these under **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Contents |
|--------|----------|
| `VM_SSH_KEY` | The full contents of the `.pem` private key used to SSH into the VM (including the `-----BEGIN ... KEY-----` / `-----END ... KEY-----` lines). |
| `VM_HOST` | Public IP / hostname of the Azure VM. |
| `VM_USER` | SSH login user on the VM. |

These values are **never** committed to the repo.

## VM prerequisites (one-time)

The workflow ships **only the jar**. The VM's `~/discord-bot/` directory must
already contain a working `docker-compose.yaml`, the single-stage `Dockerfile`
(which does `COPY ./*.jar`), and the `.env` / `.env-local` files. Docker and the
Compose plugin must be installed, and the SSH user must have **passwordless
`sudo`** for Docker.

## Cutting a release

1. Bump `<version>` in `pom.xml` to the release version (e.g. `0.3.0`) and commit.
2. Tag and push:
   ```bash
   git tag v0.3.0
   git push origin v0.3.0
   ```
3. Watch the **release** workflow in the Actions tab. It will:
   - build the jar (`mvn clean package`, runs the full test suite),
   - remove old jars on the VM,
   - copy the new jar to `~/discord-bot/`,
   - run `sudo docker compose up -d --build`,
   - verify the `bot` container is running and the app logged
     `Started KillTeamDiscordBotApplication`.
4. A green run means the release is live. A failed `verify` step dumps the last
   50 lines of the bot's logs to help diagnose a failed startup.
```

- [ ] **Step 2: Verify the doc parses as markdown / has no leftover placeholders**

Run (PowerShell):
```powershell
python -c "t=open('docs/RELEASING.md',encoding='utf-8').read(); assert 'TODO' not in t and 'TBD' not in t; assert 'VM_SSH_KEY' in t and 'docker compose up -d --build' in t; print('DOC OK')"
```
Expected output: `DOC OK`.

- [ ] **Step 3: Commit (performed by the user — do not run git automatically)**

```bash
git add docs/RELEASING.md
git commit -m "docs: document release process and required secrets"
```

---

## Task 3: End-to-end verification (manual, real release)

This is the only true integration test. It requires the three secrets to be set
and the VM to be in its expected state. Do this once after the workflow is
committed.

**Files:** none (manual).

- [ ] **Step 1: Confirm the secrets exist**

In the GitHub repo: **Settings → Secrets and variables → Actions**. Confirm
`VM_SSH_KEY`, `VM_HOST`, and `VM_USER` are present.

- [ ] **Step 2: Push a tag (performed by the user — do not run git automatically)**

```bash
git tag v0.2.1
git push origin v0.2.1
```
(Use a version that matches the current `pom.xml` `<version>`, or bump the pom
first as described in `docs/RELEASING.md`.)

- [ ] **Step 3: Watch the workflow**

Open the **Actions** tab → the **release** run triggered by the tag.
Expected: all steps green — `build`, `clean-old-jars-on-vm`, `ship-jar`,
`deploy`, and `verify`. The `verify` step log should end with
`bot started successfully`.

- [ ] **Step 4: Confirm on the VM (optional spot check)**

SSH into the VM and run:
```bash
cd ~/discord-bot && sudo docker compose ps
```
Expected: `postgres` healthy and `bot` running, and exactly one
`kill-team-discord-bot-*.jar` present in `~/discord-bot/`.

---

## Self-Review Notes

- **Spec coverage:** trigger (`v*` tag) ✓; single job, no artifact round-trip ✓; build with Java 21 + `mvn clean package` ✓; clean old jars ✓; scp jar to `~/discord-bot/` ✓; `sudo docker compose up -d --build` ✓; verify container running + `Started KillTeamDiscordBotApplication` log ✓; secrets `VM_SSH_KEY`/`VM_HOST`/`VM_USER` with no literal values ✓; every docker command `sudo`-prefixed ✓.
- **Placeholders:** none — the full workflow and doc contents are inline.
- **Consistency:** secret names, step names, and the log string `Started KillTeamDiscordBotApplication` match across the workflow, the doc, and the verification steps.
```

