# AGENTS.md

## Cursor Cloud specific instructions

### Repository status

This repository is in the **architecture/documentation phase** — there is no application source code yet. The `src/`, `pom.xml`, `build.gradle`, and `package.json` files do not exist. The `.cursor/rules/architecture-only-mode.mdc` rule explicitly enforces docs-only mode.

### What can be run

The only runnable service is **WireMock** (mocks Wildberries marketplace API):

```bash
# Start WireMock (requires Docker)
sudo docker compose up -d   # from /workspace/docker/
```

WireMock listens on `http://localhost:9090` and serves 11 mock WB API endpoints (tariffs, stocks, warehouses, returns, pricing, promos, advertising). Verify with:

```bash
curl http://localhost:9090/__admin/mappings   # list all registered stubs
curl http://localhost:9090/api/v1/tariffs/commission   # sample GET endpoint
```

### No lint / test / build

There are no lint, test, or build commands because there is no source code. When code is introduced (planned stack: Java 17 + Spring Boot 3 + Maven), this section should be updated.

### Docker in cloud VM

Docker requires `sudo` unless the user is in the `docker` group. The daemon uses `fuse-overlayfs` storage driver and `iptables-legacy` for compatibility with the Firecracker VM kernel.
