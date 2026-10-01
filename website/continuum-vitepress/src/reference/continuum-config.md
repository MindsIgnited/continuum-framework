# Continuum Configuration

## Network Transport

- `continuum.preferNativeTransport` (default `true`) — run Vert.x on the platform's native transport (epoll on Linux) instead of Java NIO. Continuum ships the epoll libraries for Linux x86_64 and aarch64, and Netty loads the one matching the CPU it runs on, so the same image works on both. Elsewhere, such as on macOS, Vert.x falls back to NIO.

The native transport is what applies TCP options such as `TCP_USER_TIMEOUT` and keepalive tuning; NIO silently ignores them. Clients that rely on them to notice a peer that vanished without closing its connections, such as Structures' Elasticsearch client, only get that on the native transport. At startup Continuum logs which transport Vert.x is using, with a warning on Linux if it fell back to NIO. Set it to `false` only to rule the native transport out while troubleshooting.

```yaml
continuum:
  preferNativeTransport: true
```

## Clustering Options

Continuum clustering is powered by Apache Ignite and is controlled through the `continuum` namespace in your Spring Boot configuration (`application.yml`, `application.properties`, or environment variables). By default clustering is enabled and uses the local discovery mode, which lets you run a single-node Continuum instance without any extra configuration.

- `continuum.disableClustering` — global switch. Set to `true` to run Continuum in pure single-node mode (ignores all Ignite and Vert.x clustering setup).
- `continuum.cluster.*` — detailed Ignite discovery configuration. These properties are bound to `DefaultIgniteClusterProperties` and apply whenever clustering is enabled.

```yaml
# application.yml
continuum:
  disableClustering: false        # Disable all clustering when true
  cluster:
    discoveryType: LOCAL          # LOCAL | SHAREDFS | KUBERNETES
    joinTimeoutMs: 0              # Wait time for cluster join (0 = no timeout)
    discoveryPort: 47500          # Ignite TCP discovery port
    communicationPort: 47501      # Ignite communication port
```

### Discovery Types

| Value | Use Case | Extra Properties |
| ----- | -------- | ---------------- |
| `LOCAL` | Development or single-node deployments. Ignite runs in local-only mode. | None |
| `SHAREDFS` | Static infrastructure such as VMs, bare metal, or Docker Compose/Swarm where instances share a filesystem. | `continuum.cluster.sharedFsPath` |
| `KUBERNETES` | Kubernetes or OpenShift deployments. Nodes discover each other via the Kubernetes API. | `continuum.cluster.kubernetesNamespace`, `continuum.cluster.kubernetesServiceName`, optional Kubernetes API overrides |

### Local Mode (`LOCAL`)

No additional configuration is required beyond the defaults. Useful for local development and testing. Clustering is effectively dormant, but it remains easy to switch to another discovery type later.

```yaml
continuum:
  cluster:
    discoveryType: LOCAL
```

### Shared Filesystem Discovery (`SHAREDFS`)

Enable this when your nodes can read and write to the same filesystem path (for example NFS, shared volume, or Docker bind mount). Ignite drops small marker files that advertise node addresses.

```yaml
continuum:
  cluster:
    discoveryType: SHAREDFS
    sharedFsPath: /var/lib/continuum/ignite-ipfinder
    discoveryPort: 47500
    communicationPort: 47501
```

- `sharedFsPath` must point to a location that every cluster node can access.
- Adjust the ports if your infrastructure already uses the defaults.

### Kubernetes Discovery (`KUBERNETES`)

Use this mode when running Continuum in Kubernetes. Ignite asks the Kubernetes API for pod IPs behind a headless service and joins those nodes automatically.

```yaml
continuum:
  cluster:
    discoveryType: KUBERNETES
    kubernetesNamespace: continuum
    kubernetesServiceName: continuum-ignite
    kubernetesIncludeNotReadyAddresses: false
    discoveryPort: 47500
    communicationPort: 47501
```

- `kubernetesNamespace` and `kubernetesServiceName` must match the namespace and headless service that expose your Continuum pods.
- Optional overrides:
  - `kubernetesMasterUrl` — point Ignite to an external API server (default: in-cluster).
  - `kubernetesAccountToken` — supply a service account token manually (default: use mounted token).
  - `kubernetesIncludeNotReadyAddresses` — set to `true` if you need to include pods that are not yet marked ready.

### Join Timeout and Ports

- `joinTimeoutMs` The join timeout defines how much time the node waits to join a cluster. If a non-shared IP finder is used and the node fails to connect to any address from the IP finder, the node keeps trying to join within this timeout. If all addresses are unresponsive, an exception is thrown and the node terminates. 0 means waiting indefinitely.
- `discoveryPort` (TCP discovery) and `communicationPort` (data plane) must be reachable between all nodes. Keep them consistent across every deployment unit.

### Event Bus Cluster Settings

Continuum’s Vert.x event bus clustering piggybacks on many of the same discovery settings. When clustering is active you can fine-tune the event bus endpoints:

```yaml
continuum:
  eventBusClusterPort: 0          # 0 lets Vert.x choose an open port
  eventBusClusterPublicPort: -1   # -1 reuses eventBusClusterPort
  eventBusClusterHost: 0.0.0.0    # bind address, defaults to Ignite host
  eventBusClusterPublicHost: null # external hostname if different from bind host
```

These properties help when Continuum nodes sit behind load balancers or NAT where the public address differs from the local bind address.

### Verifying the Effective Configuration

At startup Continuum logs the resolved `DefaultIgniteClusterProperties` values. You can also run the Spring Boot actuator `env` endpoint (if enabled) to confirm that the properties were bound as expected.
