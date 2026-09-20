# Production deployment (Hostinger VPS)

Reference copies of the files that live in `/srv/nukkad/` on the VPS. The server copies are the ones that
actually run — edit them there, then mirror the change here. **No secrets are stored in this repo**; the real
values are in `/srv/nukkad/.env` on the server (see `.env.example` for the variable names).

| File | On the server | Purpose |
|---|---|---|
| `docker-compose.prod.yml` | `/srv/nukkad/docker-compose.yml` | mysql, minio (+ bucket init), backend, caddy |
| `Caddyfile` | `/srv/nukkad/Caddyfile` | HTTPS and routing for app./api./media.buildadda.com |
| `deploy.sh` | `/srv/nukkad/deploy.sh` | whitelisted deploy entry point: `backend` or `frontend` |
| `backup.sh` | `/srv/nukkad/backup.sh` | daily MySQL + media backup, run by a systemd timer |

## Pipeline

Push to `main` in either repo -> GitHub Actions (`.github/workflows/deploy.yml`) -> SSH to the VPS with a
key that is restricted (`command=` in `authorized_keys`) to `deploy.sh` -> pull, rebuild, restart.

Each repo needs one Actions secret: `VPS_SSH_KEY` (the private half of the CI deploy key).

## Media

Uploads live in `/srv/nukkad/media` (MinIO data directory, S3-compatible). To move to Cloudflare R2 later,
point `S3_ENDPOINT_OVERRIDE`, `S3_PUBLIC_BASE_URL`, and the access keys at R2 and copy the bucket with
`mc mirror` — no application code changes.

## Backups

`backup.sh` dumps MySQL (`mysqldump --single-transaction`, gzipped) and tars the MinIO media directory,
into `/srv/nukkad/backups/{mysql,media}/`, keeping 7 days. It is local-disk-only — see the script's own
comment for why an off-server copy is a deliberate follow-up, not included here. Installed as a systemd
timer on the VPS (`/etc/systemd/system/nukkad-backup.{service,timer}`, `OnCalendar=daily`) rather than a
crontab entry, so `systemctl status nukkad-backup.timer` / `journalctl -u nukkad-backup` show its history.
