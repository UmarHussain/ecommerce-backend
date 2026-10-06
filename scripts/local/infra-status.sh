#!/usr/bin/env bash
# Report which containers of this Compose project are already running and which
# compose file started them. Informational only; always exits 0.
#
# Both infrastructure/local/compose.yaml (main, used by make/Cursor) and
# infrastructure/local/manual-startup/compose.yaml (IDE workflow) use the same
# project name and service names, so the containers listed here are shared.
set -euo pipefail
project="${COMPOSE_PROJECT_NAME:-ecommerce-local-platform}"

command -v docker >/dev/null 2>&1 || { echo "infra-status: docker not on PATH."; exit 0; }

mapfile -t rows < <(docker ps --filter "label=com.docker.compose.project=$project" \
  --format '{{.Label "com.docker.compose.service"}}'$'\t''{{.Status}}'$'\t''{{.Label "com.docker.compose.project.config_files"}}')

if ((${#rows[@]} == 0)); then
  echo "infra-status: no running containers for Compose project '$project'."
  exit 0
fi

echo "infra-status: running containers for Compose project '$project' (shared by make and manual-startup):"
printf '  %-22s %-32s %s\n' SERVICE STATUS STARTED_FROM
for row in "${rows[@]}"; do
  IFS=$'\t' read -r service status files <<<"$row"
  printf '  %-22s %-32s %s\n' "$service" "$status" "${files##*/}"
done
