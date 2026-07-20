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
