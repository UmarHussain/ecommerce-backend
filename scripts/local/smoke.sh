#!/usr/bin/env bash
# HTTP smoke test. Requires Keycloak (8180), the gateway (8090), and catalog-service.
# Portal backends are not part of the path.
set -euo pipefail
retry=(--fail --silent --show-error --retry 10 --retry-delay 3 --retry-connrefused)

echo "1/4 Keycloak realm discovery"
curl "${retry[@]}" http://localhost:8180/realms/ecommerce-local/.well-known/openid-configuration >/dev/null
echo "2/4 Gateway health"
curl "${retry[@]}" http://localhost:8090/actuator/health; echo
echo "3/4 Public catalog through gateway -> catalog service"
curl "${retry[@]}" http://localhost:8090/api/v1/store/catalog/products >/dev/null
echo "4/4 Unauthenticated admin endpoint must return 401"
code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/api/v1/admin/me)"
[[ "$code" == 401 ]] || { echo "Expected 401, received $code" >&2; exit 1; }

echo 'Smoke passed: public catalog readable, unauthenticated admin call rejected.'
echo 'Real OIDC login with issued tokens remains a Phase 1 verification gate.'
