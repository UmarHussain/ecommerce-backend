#!/usr/bin/env bash
# Phase 4 Redis and resilience demonstration.
# Starts nothing except this project's Redis when it is down, and restores Redis if the outage step stops it.
# Does not delete volumes, start Kafka, or print secrets.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

fail() { echo "FAIL  $*" >&2; exit 1; }
ok() { echo "ok    $*"; }
COMPOSE=(bash scripts/local/compose.sh --profile cache)

restore_redis() {
  "${COMPOSE[@]}" start redis >/dev/null
}

echo "1. Redis for this project"
"${COMPOSE[@]}" up -d redis
container="$("${COMPOSE[@]}" ps -q redis)"
[[ -n "$container" ]] || fail "redis container was not started"
ok "redis container"

echo "2. Public catalog cache key and TTL"
curl -fsS "http://localhost:8090/api/v1/store/catalog/categories" -o /tmp/elp-cache-body >/dev/null || fail "public categories failed"
curl -fsS "http://localhost:8090/api/v1/store/catalog/categories" -o /tmp/elp-cache-body >/dev/null || fail "public categories repeat failed"
key="$(docker exec "$container" redis-cli --scan --pattern 'catalog:v1:*' | head -n 1)"
[[ -n "$key" ]] || fail "no catalog:v1 key was stored"
ttl="$(docker exec "$container" redis-cli TTL "$key")"
[[ "$ttl" -gt 0 && "$ttl" -le 65 ]] || fail "TTL for $key was $ttl"
ok "cached $key ttl=${ttl}s"

echo "3. Catalog stays readable while Redis is stopped, then Redis is restored"
trap restore_redis EXIT
"${COMPOSE[@]}" stop redis >/dev/null
code="$(curl -sS -o /tmp/elp-cache-body -w '%{http_code}' http://localhost:8090/api/v1/store/catalog/categories)"
[[ "$code" == 200 ]] || fail "catalog during redis outage returned $code"
restore_redis
trap - EXIT
ok "redis outage fallback"

echo "4. Local resilience diagnostics"
for port in 8095 8096; do
  body="$(curl -fsS "http://localhost:${port}/actuator/circuitbreakers")" || fail "circuit breakers on $port"
  echo "$body" | jq -e '.circuitBreakers | length > 0' >/dev/null || fail "no circuit breaker instance on $port"
  body="$(curl -fsS "http://localhost:${port}/actuator/retries")" || fail "retries on $port"
  echo "$body" | jq -e '.retries | length > 0' >/dev/null || fail "no retry instance on $port"
done
ok "resilience diagnostics"
echo "cache-check passed"
