# Frontend workspace

Two independent React/TypeScript/Vite shells. Run `npm ci`, `npm test`, and `npm run build` here. Run `npm run dev --workspace @ecommerce/storefront-web` or `npm run dev --workspace @ecommerce/admin-web`.

Vite proxies `/api` to localhost:8090. Login and business screens are Phase 1 onward; current pages clearly report scaffold status. Use the two Keycloak SPA clients; never embed the confidential administration secret.
