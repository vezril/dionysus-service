## ADDED Requirements

### Requirement: The chart deploys exactly one replica, structurally
The chart SHALL deploy the Deployment with `replicas: 1` hardcoded in the template. `values.yaml` MUST NOT expose a `replicaCount` (or equivalent) field that could set it to any other value.

#### Scenario: replicas cannot be overridden via values
- **WHEN** an operator inspects `values.yaml` or passes `--set` overrides at install time
- **THEN** there is no field that changes the Deployment's replica count away from 1

### Requirement: The Deployment uses the Recreate strategy
The chart SHALL set the Deployment's update strategy to `Recreate`, not the Kubernetes default `RollingUpdate`.

#### Scenario: strategy is Recreate
- **WHEN** the rendered Deployment manifest is inspected
- **THEN** `spec.strategy.type` is `Recreate`

### Requirement: SQLite data persists across pod restarts via a PersistentVolumeClaim
The chart SHALL provision a PersistentVolumeClaim (`ReadWriteOnce`, using the `local-path` StorageClass) and mount it at the application's data directory, matching the image's `DIONYSUS_DB_PATH` default.

#### Scenario: data survives a pod restart
- **WHEN** the pod is deleted and Kubernetes recreates it
- **THEN** the new pod mounts the same PVC and the SQLite file (and its data) is unchanged

### Requirement: liveness and readiness probes use the existing health endpoint
The chart SHALL configure both liveness and readiness probes against `GET /health` on the container's HTTP port.

#### Scenario: readiness gates traffic during startup
- **WHEN** the pod has started but the HTTP server has not yet bound
- **THEN** the pod is not marked Ready and receives no Service traffic

#### Scenario: a wedged process is restarted
- **WHEN** the liveness probe against `/health` fails repeatedly past its configured threshold
- **THEN** Kubernetes restarts the container

### Requirement: a ClusterIP Service fronts the application port
The chart SHALL create a `ClusterIP` Service targeting the container's HTTP port (8080 by default).

#### Scenario: Service resolves to the pod
- **WHEN** another workload in the cluster resolves the Service's DNS name
- **THEN** it reaches the `dionysus-service` pod on the configured port

### Requirement: Ingress is optional and disabled by default
The chart SHALL include an Ingress template gated behind `ingress.enabled` (default `false`). When disabled, no Ingress resource is created.

#### Scenario: default install creates no Ingress
- **WHEN** the chart is installed with default values
- **THEN** no Ingress resource exists in the cluster

#### Scenario: enabling ingress requires a host
- **WHEN** an operator sets `ingress.enabled: true` without providing `ingress.host`
- **THEN** the chart SHALL fail to render with a clear error, rather than creating a hostless Ingress

### Requirement: NOTES.txt discloses the no-auth and node-pinning caveats
The chart's post-install `NOTES.txt` SHALL state that the service has no authentication (must not be exposed beyond a private network) and that the PVC's `local-path` storage pins the workload to whichever node it is first scheduled on.

#### Scenario: install output includes both caveats
- **WHEN** `helm install` completes
- **THEN** the printed NOTES mention both the no-auth requirement and the node-pinning behavior
