#!/usr/bin/env bash
# Forced-command target for the GitHub Actions deploy key: that key can run ONLY this script,
# and only with one of the whitelisted targets below (passed as the SSH command).
set -euo pipefail
exec 9>/srv/nukkad/.deploy.lock
flock 9

target="${SSH_ORIGINAL_COMMAND:-${1:-}}"
cd /srv/nukkad

case "$target" in
  backend)
    git -C backend pull --ff-only
    docker compose up -d --build backend
    docker image prune -f >/dev/null
    ;;
  frontend)
    git -C frontend pull --ff-only
    rm -rf .frontend-build && mkdir .frontend-build
    docker buildx build --target export -o .frontend-build       --build-arg VITE_API_BASE_URL=https://api.buildadda.com/api       --build-arg VITE_GOOGLE_CLIENT_ID=213678785899-jlooi4qr57hh6ognkng7k9ov5s3e487k.apps.googleusercontent.com       frontend
    mkdir -p frontend-dist
    rsync -a --delete .frontend-build/ frontend-dist/
    rm -rf .frontend-build
    ;;
  *)
    echo "usage: backend|frontend" >&2
    exit 2
    ;;
esac
echo "deploy $target done"
