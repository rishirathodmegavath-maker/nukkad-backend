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
    # docker compose up reports success once the container STARTS, not once it's actually
    # serving -- a backend that starts then crash-loops (e.g. a bad migration) would otherwise
    # leave prod down with a green Action. Poll through caddy (alpine, has wget) since backend's
    # port isn't published to the host.
    healthy=""
    for _ in $(seq 1 30); do
      if docker compose exec -T caddy wget -qO- http://backend:8082/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        healthy=1
        break
      fi
      sleep 2
    done
    if [ -z "$healthy" ]; then
      echo "backend deploy: container is up but /actuator/health never reported UP within 60s" >&2
      docker compose logs --tail 50 backend >&2
      exit 1
    fi
    ;;
  frontend)
    git -C frontend pull --ff-only
    rm -rf .frontend-build && mkdir .frontend-build
    docker buildx build --target export -o .frontend-build       --build-arg VITE_API_BASE_URL=https://api.buildadda.com/api       --build-arg VITE_GOOGLE_CLIENT_ID=213678785899-jlooi4qr57hh6ognkng7k9ov5s3e487k.apps.googleusercontent.com       frontend
    # Atomic cut-over: the previous version rsync'd straight into the live, Caddy-served
    # directory, so a request landing mid-sync could see a new index.html referencing a JS chunk
    # that --delete had already removed. Caddy's bind mount is fixed to frontend-dist itself (not
    # to whatever it currently contains), so the swap has to happen INSIDE that directory, as an
    # atomic symlink rename, rather than by replacing frontend-dist itself.
    mkdir -p frontend-dist/releases
    release="frontend-dist/releases/$(date +%s)"
    rm -rf "$release"
    mv .frontend-build "$release"
    ln -sfn "releases/$(basename "$release")" frontend-dist/current.tmp
    mv -T frontend-dist/current.tmp frontend-dist/current
    # Keep the last 3 releases (current + 2 for manual rollback), prune the rest.
    ls -1dt frontend-dist/releases/*/ 2>/dev/null | tail -n +4 | xargs -r rm -rf
    ;;
  *)
    echo "usage: backend|frontend" >&2
    exit 2
    ;;
esac
echo "deploy $target done"
