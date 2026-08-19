## Why

Calvin wants to run `dionysus-service` on his homelab k3s cluster. There's no Kubernetes packaging for this service yet — `dionysus-planner`'s old Helm chart doesn't apply (different app, different image, different persistence model). A Helm chart is needed to deploy the now-published `calvinference/dionysus` image with its SQLite data persisted across restarts.

## What Changes

- New Helm chart at `charts/dionysus-service/` packaging the Pekko HTTP server as a Kubernetes Deployment.
- `replicas` is hardcoded to `1`, not a values field — SQLite is single-writer, so this is a structural constraint, not a tunable (same precedent as `dionysus-planner`'s chart).
- `strategy: Recreate` (not `RollingUpdate`) — two pods must never run against the same SQLite file simultaneously, even for a moment during a rolling deploy.
- A PersistentVolumeClaim for the SQLite data directory (`/opt/docker/data`, matching `DIONYSUS_DB_PATH`'s default), using `local-path` (k3s's default StorageClass) — same node-pinning caveat as before: a `ReadWriteOnce` volume ties the pod to whichever node it's first scheduled on.
- Liveness/readiness probes against the existing `GET /health` endpoint (already returns 503 while draining, via Coordinated Shutdown — no new server-side work needed).
- A Service (`ClusterIP` by default) fronting port 8080.
- An optional, gated Ingress (disabled by default, matching `dionysus-planner`'s pattern — enabling it and its host/TLS config is left to the operator).
- No auth is added here — deploying this chart is only appropriate on a private/internal network, consistent with the service's phase-1 no-auth design decision. The chart does not change that; it's a deployment concern, not an application one.

## Capabilities

### New Capabilities
- `helm-deployment`: packaging `dionysus-service` for k3s — single-replica Deployment, persistent SQLite volume, health-probed, optional Ingress.

### Modified Capabilities
(none)

## Impact

- New: `charts/dionysus-service/` (Chart.yaml, values.yaml, templates/).
- No application code changes — this is deployment packaging only, built against the already-published `calvinference/dionysus` image (Docker Hub, wired this session).
- CI: a non-required `helm-lint` job added to `ci.yml` (same precedent as `dionysus-planner`) so a broken chart is caught on PRs without blocking merges on it.
