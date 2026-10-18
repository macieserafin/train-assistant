#!/usr/bin/env bash
set -euo pipefail

release_sha=${1:?Usage: deploy.sh COMMIT_SHA}
[[ "$release_sha" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid release SHA' >&2; exit 1; }
app_root="$HOME/train-assistant"
release_dir="$app_root/releases/$release_sha"
export TRAIN_ENV_FILE="$app_root/.env"
test -s "$TRAIN_ENV_FILE"
test -f "$release_dir/image.tar.gz"
exec 9>"$app_root/deploy.lock"
flock -w 300 9
previous=$(readlink -f "$app_root/current" || true)

compose() {
  local directory=$1 sha=$2
  shift 2
  sudo -n env TRAIN_ENV_FILE="$TRAIN_ENV_FILE" RELEASE_SHA="$sha" \
    docker compose -p train-assistant -f "$directory/compose.prod.yml" "$@"
}

rollback() {
  if [[ -n "$previous" && "$previous" != "$release_dir" && -f "$previous/compose.prod.yml" ]]; then
    echo "Restoring previous release: ${previous##*/}" >&2
    compose "$previous" "${previous##*/}" up -d --wait --wait-timeout 180
  else
    echo 'No previous release available; inspect the failed container.' >&2
  fi
}

gzip -dc "$release_dir/image.tar.gz" | sudo -n docker load
if ! compose "$release_dir" "$release_sha" up -d --wait --wait-timeout 180; then
  rollback
  exit 1
fi
if ! curl -fsS --retry 3 --retry-delay 2 --max-time 10 http://127.0.0.1:18080/healthz > /dev/null; then
  rollback
  exit 1
fi
ln -sfn "$release_dir" "$app_root/current.next"
mv -Tf "$app_root/current.next" "$app_root/current"
echo "Deployed $release_sha"
