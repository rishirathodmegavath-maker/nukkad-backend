#!/usr/bin/env bash
# Daily backup of the production database and uploaded media. Installed on the VPS as a systemd
# timer (see deploy/README.md) -- there was previously no backup of either at all.
#
# Keeps 7 days locally under /srv/nukkad/backups. This protects against accidental deletion, a bad
# migration, or app-level data corruption, but it is NOT an off-server backup: a full VPS loss
# (disk failure, account loss) still loses everything, including the backups themselves. Shipping
# these off-box (e.g. to Cloudflare R2, which the media directory is already meant to migrate to)
# is a deliberate follow-up that needs a destination the operator chooses, not done here.
set -euo pipefail
cd /srv/nukkad

stamp=$(date +%Y%m%d-%H%M%S)
mkdir -p backups/mysql backups/media

# Reads the DB name/root password from the mysql container's own environment (set by
# docker-compose.prod.yml from .env) rather than re-sourcing .env on the host.
docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip > "backups/mysql/nukkad-$stamp.sql.gz"

tar -C media -czf "backups/media/media-$stamp.tar.gz" .

find backups/mysql -name '*.sql.gz' -mtime +7 -delete
find backups/media -name '*.tar.gz' -mtime +7 -delete

echo "backup $stamp done: db=$(du -h "backups/mysql/nukkad-$stamp.sql.gz" | cut -f1) media=$(du -h "backups/media/media-$stamp.tar.gz" | cut -f1)"
