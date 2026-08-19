## Context

`dionysus-service` runs as a single Docker container (`calvinference/dionysus`, `eclipse-temurin:21-jre` base, non-root `dionysus` user, `HEALTHCHECK` against `/health`, port 8080 by default via `HTTP_PORT`). It persists to a SQLite file at a path controlled by `DIONYSUS_DB_PATH` (default `./data/dionysus.db`, i.e. `/opt/docker/data/dionysus.db` given the image's working directory). This is a personal, single-user service — the same constraint that shaped `dionysus-planner`'s Helm chart (SQLite is single-writer) applies here identically, and the same structural answer applies: the chart must make multi-replica deployment structurally impossible, not just discouraged by convention.

## Goals / Non-Goals

**Goals:**
- Deploy `dionysus-service` to k3s as a single, persistent, health-probed workload.
- Keep the chart's `values.yaml` honest — no field that would let an operator accidentally misconfigure their way into two pods sharing one SQLite file.
- Reuse k3s's default `local-path` StorageClass so this works out of the box on a homelab node with no extra CSI setup.

**Non-Goals:**
- No auth/ingress-level access control is added by this chart — the service itself has no auth in this phase (by its own design), so this chart assumes it's deployed on a private network. Exposing it publicly is explicitly out of scope and the chart's default Ingress is disabled.
- No multi-node/HA story — SQLite forecloses this; a future Postgres migration (noted as an open question in `meal-planning-health`'s design.md) would be the prerequisite for revisiting it.
- No Secrets management beyond what k3s itself provides — this service needs no secrets today (no auth, no external API keys).

## Decisions

**1. `replicas: 1` hardcoded in `templates/deployment.yaml`, not a `values.yaml` field.**
Same precedent as `dionysus-planner`'s chart: if it's a values field, someone (future Calvin, or an over-eager autoscaler) will eventually set it to 2 and corrupt the SQLite file via concurrent writers. Making it structurally absent from `values.yaml` is a stronger guarantee than a code comment.

**2. `strategy: { type: Recreate }` on the Deployment.**
Kubernetes's default `RollingUpdate` strategy briefly runs the old and new pod simultaneously during a deploy — with a `ReadWriteOnce` PVC this would actually be blocked by the volume attach (the second pod can't mount a volume already attached to the first, on most CSI drivers including `local-path`), but that failure mode is a stuck rollout, not a clean deploy. `Recreate` tears down the old pod before starting the new one, trading a few seconds of downtime per deploy for a rollout that always succeeds cleanly.

**3. PVC using `local-path` (k3s's bundled default StorageClass), `ReadWriteOnce`, mounted at the image's `/opt/docker/data` working directory.**
Alternatives considered:
- *NFS/a shared-storage StorageClass* — would remove the node-pinning caveat below, but adds an operational dependency (an NFS server) this homelab deployment doesn't need for a single-writer SQLite file. Not worth it for this phase.
- *`emptyDir` (no persistence)* — rejected outright: this defeats the entire point of an eating log.
`local-path` provisions storage on whichever node the pod is first scheduled to, and a `ReadWriteOnce` volume can only be mounted by pods on that same node thereafter — this pins the workload to one node. Acceptable for a homelab single-node-class deployment (documented in `NOTES.txt`), same as `dionysus-planner`'s chart.

**4. Liveness and readiness probes both point at `GET /health`.**
The endpoint already distinguishes `200 UP` from `503 DOWN` (flipped by Coordinated Shutdown before the port unbinds) — no new server-side work needed. Using the same endpoint for both probes is fine here: liveness restarts a truly wedged process, readiness gates traffic during startup/shutdown, and `/health`'s binary up/down signal serves both correctly for a service this simple (no separate "alive but not ready" state exists).

**5. Service is `ClusterIP` by default; Ingress is a values-gated, disabled-by-default template.**
Matches `dionysus-planner`'s chart exactly. A homelab operator either port-forwards, adds their own Ingress, or flips `ingress.enabled: true` and supplies a host — the chart doesn't presume a particular ingress controller or TLS setup.

**6. `helm-lint` CI job, non-required.**
Mirrors `dionysus-planner`'s `ci-pr-gate` precedent: `helm lint` catches a broken chart on every PR without being a merge-blocking required check (chart changes are infrequent and low-risk compared to the app's own test suite).

## Risks / Trade-offs

- **[Risk] Node-pinning via `local-path` + `ReadWriteOnce`.** → **Mitigation:** acceptable for a homelab (documented in `NOTES.txt` and this design doc); revisit only if the cluster grows beyond a size where this matters.
- **[Risk] `Recreate` strategy means brief downtime on every deploy.** → **Mitigation:** acceptable trade-off for a personal single-user service; the alternative (`RollingUpdate` failing to attach the second pod's volume) is worse — a stuck rollout requiring manual intervention, not a clean short outage.
- **[Risk] No auth + a real Ingress could expose an unauthenticated eating/health-data API to the internet if misconfigured.** → **Mitigation:** Ingress is disabled by default; `NOTES.txt` states the no-auth caveat explicitly when the chart is installed.

## Migration Plan

Greenfield — no existing k3s deployment of this service to migrate from. Installation is `helm install dionysus-service charts/dionysus-service/`.

## Open Questions

- None blocking. Whether to eventually add TLS/cert-manager wiring to the optional Ingress template is left for whenever (if ever) this service is exposed beyond the homelab network — not needed now.
