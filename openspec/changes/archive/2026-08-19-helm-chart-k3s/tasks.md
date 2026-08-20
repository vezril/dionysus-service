## 1. Chart scaffold

- [x] 1.1 `charts/dionysus-service/Chart.yaml` — name, description, version, appVersion
- [x] 1.2 `charts/dionysus-service/values.yaml` — image repository/tag/pullPolicy, service port, resources, ingress (disabled by default, host/annotations), pvc size/storageClassName; NO `replicaCount` field
- [x] 1.3 `charts/dionysus-service/templates/_helpers.tpl` — standard name/labels helpers

## 2. Workload templates

- [x] 2.1 `templates/deployment.yaml` — `replicas: 1` hardcoded, `strategy: { type: Recreate }`, container image from values, `HTTP_PORT`/`DIONYSUS_DB_PATH` env, volume mount at the data directory, liveness/readiness probes on `GET /health`
- [x] 2.2 `templates/pvc.yaml` — `ReadWriteOnce`, `local-path` StorageClass (default; overridable via values), size from values
- [x] 2.3 `templates/service.yaml` — `ClusterIP`, targets the container's HTTP port

## 3. Optional ingress and docs

- [x] 3.1 `templates/ingress.yaml` — gated behind `ingress.enabled` (default false); `fail` with a clear message if enabled without `ingress.host`
- [x] 3.2 `templates/NOTES.txt` — install instructions; explicit no-auth and node-pinning caveats

## 4. CI and verification

- [x] 4.1 Add a non-required `helm-lint` job to `.github/workflows/ci.yml` (mirrors `dionysus-planner`'s precedent): `helm lint charts/dionysus-service/`
- [x] 4.2 Run `helm lint charts/dionysus-service/` locally — must pass clean
- [x] 4.3 Run `helm template charts/dionysus-service/` and inspect the rendered manifests — confirm `replicas: 1`, `strategy.type: Recreate`, PVC/volume mount paths match `DIONYSUS_DB_PATH`'s default, probes point at `/health`, no Ingress resource renders with default values
- [x] 4.4 Render with `--set ingress.enabled=true` (no host) and confirm it fails with a clear error; render again with a host set and confirm the Ingress resource is correct
