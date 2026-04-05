# Monorepo shortcuts (POSIX make). Intended for Linux or WSL2; use docker/scripts/*.sh or tests/**/*.sh if make is unavailable.
.PHONY: help backend-test webapp-test webapp-e2e webapp-e2e-fullstack gatling-matrix classifier-test system-checks test lint openapi-docs

help:
	@echo "Targets: backend-test, webapp-test, webapp-e2e, webapp-e2e-fullstack, gatling-matrix, classifier-test, system-checks, test, lint, openapi-docs"

backend-test:
	cd rag-service && ./mvnw verify

webapp-test:
	cd webapp && npm test && npm run lint && npm run typecheck

webapp-e2e:
	cd webapp && npm run build && npm run test:e2e

# Requires Spring on :9000 with profile e2e + Postgres (Flyway seed). Build with NEXT_PUBLIC_API_BASE_URL pointing at API.
webapp-e2e-fullstack:
	cd webapp && NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:9000 npm run build && E2E_ALLOW_INSECURE_COOKIES=true npm run test:e2e:fullstack

# Sequential Gatling smoke (Git Bash / WSL). Requires Spring at GATLING_BASE_URL.
gatling-matrix:
	chmod +x tests/gatling/run-gatling-scenario-matrix.sh && ./tests/gatling/run-gatling-scenario-matrix.sh

classifier-test:
	cd classifier-service && pytest tests/

# Playwright API smoke against a running Spring instance (default http://127.0.0.1:9000). Canonical operator HTTP checks.
system-checks:
	cd webapp && API_BASE_URL=http://127.0.0.1:9000 npm run test:api

test: backend-test webapp-test classifier-test

lint:
	cd rag-service && ./mvnw -q -DskipTests compile
	cd webapp && npm run lint

# Open Swagger UI when the backend is running (default http://localhost:9000)
openapi-docs:
	@echo "Open http://localhost:9000/swagger-ui/index.html (or /swagger-ui.html)"
