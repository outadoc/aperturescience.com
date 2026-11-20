# Minitel service on Cloudflare Workers

Serves the Aperture Science Minitel service from
[Cloudflare Workers](https://developers.cloudflare.com/workers/), using Kotlin/JS and
minipavi-kotlin's `core-cloudflare` module. It runs the same turn logic as the Ktor service in
`ui-minitel/`, from `ui-minitel-core/`.

## How it works

- `ui-minitel-core` holds the turn logic, compiled for both the JVM and JS.
- `src/jsMain/.../MinitelWorker.kt` wraps it in a `MinitelServiceHandler` and exports `fetch`.
- [`worker/index.mjs`](worker/index.mjs) re-exports that `fetch` as the default export, which is
  what Cloudflare Workers expects.
- The service answers `POST /` (the MiniPavi gateway).

## Development

You need JDK 21, Node.js 22, and a GitHub token with the `read:packages` scope to fetch
minipavi-kotlin from GitHub Packages (see the root `build.gradle.kts`).

```shell
npm ci
npm run dev     # builds the service and starts a local Worker on http://localhost:8787
```

## Deployment

```shell
npx wrangler login
npm run deploy  # builds the service and deploys it to your Cloudflare account
```

The `Deploy Cloudflare Worker` GitHub Actions workflow does the same on every push to `main`
(or on demand from the Actions tab). It needs:

1. An environment named `production` under **Settings → Environments**. You can restrict its
   deployment branches to `main` there, or require a manual approval.
2. The environment secrets `CLOUDFLARE_API_TOKEN` (an API token with the
   "Workers Scripts: Edit" permission) and `CLOUDFLARE_ACCOUNT_ID`.

Once deployed, point the MiniPavi service at the Worker's URL
(for example `https://aperturescience-minitel.<subdomain>.workers.dev/`).
