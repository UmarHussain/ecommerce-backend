SHELL := bash
.DEFAULT_GOAL := help

MVN      := ./mvnw -B
COMPOSE  := bash scripts/local/compose.sh
SERVICE  ?= catalog-service
APP      ?= storefront-web

.PHONY: help bootstrap check \
        infra-up infra-down infra-logs infra-status apps-up realm-reconcile realm-migrate-portals \
        backend-compile backend-test backend-verify backend-clean \
        frontend-install frontend-test frontend-build frontend-clean \
        run-service run-frontend smoke security-check verify clean

help: ## Show this help
	@awk 'BEGIN {FS = ":.*## "; printf "Usage: make <target> [SERVICE=<module>] [APP=<frontend>]\n\n"} \
	      /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

## ---- Setup and static checks -------------------------------------------

bootstrap: ## Create .env (if missing) and render the Keycloak realm import; never overwrites
	bash scripts/local/bootstrap.sh

check: ## Static checks: shell syntax, Maven modules, realm template, rules, doc links
	bash scripts/local/check.sh

## ---- Infrastructure (PostgreSQL + Keycloak in Docker) ------------------

infra-up: ## Start PostgreSQL and Keycloak (reuses containers already started by manual-startup)
	@bash scripts/local/infra-status.sh
	$(COMPOSE) up -d postgres keycloak

infra-down: ## Stop this project's containers (incl. manual-startup ones); keeps database volumes
	$(COMPOSE) --profile apps --profile later --profile cache --profile events down

infra-logs: ## Follow logs of running containers (Ctrl+C to stop)
	$(COMPOSE) logs --tail=100 -f

infra-status: ## Show which project containers are running and which compose file started them
	@bash scripts/local/infra-status.sh

apps-up: ## Build images and start the backend services in containers (apps profile)
	$(COMPOSE) --profile apps up -d --build

realm-reconcile: ## Apply realm-template.json to the running Keycloak realm (additive, idempotent; never deletes or resets passwords)
	bash scripts/local/realm-reconcile.sh

realm-migrate-portals: ## Move admin.access onto api-gateway and remove obsolete portal clients; keeps users, passwords, and volumes
	bash scripts/local/migrate-remove-portal-backends.sh

## ---- Backend (Maven wrapper, Java 21) ----------------------------------

backend-compile: ## Compile main and test sources of all modules (mvnw test-compile)
	cd backend && $(MVN) test-compile

backend-test: ## Run unit/MVC tests only (mvnw test); skips *IT Testcontainers tests
	cd backend && $(MVN) test

backend-verify: ## Run unit tests plus *IT integration tests (mvnw verify); needs Docker
	cd backend && $(MVN) verify

backend-clean: ## Delete Maven target/ directories (mvnw clean)
	cd backend && $(MVN) -q clean

## ---- Frontend (npm workspaces: storefront-web, admin-web) ---------------

frontend-install: ## Install frontend dependencies from the lockfile (npm ci)
	cd frontend && npm ci

frontend-test: ## Run Vitest in both frontend workspaces (npm test)
	cd frontend && npm test

frontend-build: ## Type-check and build both frontends for production (npm run build)
	cd frontend && npm run build

frontend-clean: ## Delete frontend dist/ outputs; keeps node_modules
	cd frontend && npm run clean

## ---- Run one service / app in the current terminal ----------------------

run-service: ## Run one Java service from source, e.g. make run-service SERVICE=catalog-service
	bash scripts/local/run-service.sh $(SERVICE)

run-frontend: ## Run one Vite dev server, e.g. make run-frontend APP=admin-web
	cd frontend && npm run dev --workspace @ecommerce/$(APP)

## ---- Aggregates -----------------------------------------------------------

smoke: ## HTTP smoke test against running Keycloak and gateway (needs infra + services)
	bash scripts/local/smoke.sh

security-check: ## Phase 1 acceptance checks with real PKCE-issued tokens against the running stack (needs infra + all services)
	bash scripts/local/security-check.sh

verify: check backend-verify frontend-test frontend-build ## check + backend-verify + frontend test/build (run frontend-install first)

clean: backend-clean frontend-clean ## Remove build outputs only; keeps .env, .local/, and Docker volumes
