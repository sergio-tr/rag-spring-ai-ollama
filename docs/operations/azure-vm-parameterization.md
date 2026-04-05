# Azure VM — parameterization notes (no fixed IPs in runbooks)

**Purpose:** Deploy and operations documentation should remain valid when Azure **public IPs**, NICs, or scale-out patterns change. This page lists **parameterized** settings operators should use instead of hardcoding addresses in docs or scripts.

**Related:** [runbook-docker-vm.md](runbook-docker-vm.md), [deploy-workflow-audit.md](deploy-workflow-audit.md).

---

## Hostnames and endpoints

| Instead of | Use |
|------------|-----|
| Literal public IPv4 in README/runbooks | **DNS name** (Azure DNS, custom domain CNAME to FQDN, or Azure-provided FQDN if enabled) |
| Fixed private IP in team wiki | **Service name** / **Private DNS zone** / **subnet** documentation |

**GitHub Actions:** Store `VM_HOST` as the **stable DNS name** of the VM (or private ingress hostname if using a relay). Rotate Azure public IP without changing secret semantics — only DNS A/AAAA updates.

---

## Networking

- **NSG / firewall:** Document **ports** (80/443/22) and **purpose**, not “allow x.x.x.x”. Restrict SSH to known admin IPs via NSG or Azure Bastion where possible.
- **Outbound:** VM needs HTTPS to **ghcr.io** and **GitHub** for image pull and clone; allow by FQDN or tag-based rules as per your org standard.

---

## Secrets and config

| Concern | Practice |
|---------|----------|
| Connection strings | Environment variables on VM or **Azure Key Vault** references — not committed |
| `GHCR_TOKEN` | Short-lived PAT or org token in GitHub **Secrets**; rotate on schedule |
| TLS | Terminate at **Application Gateway**, **Nginx**, or **Caddy** with certs from Key Vault or Let’s Encrypt — document **hostname**, not IP |

---

## IaC alignment

If the team later adds Bicep/Terraform, **output** the deploy hostname and NSG names into the same variables used by `VM_HOST` and health-check URLs — single source of truth.

---

## What we avoid in canonical docs

- “SSH to `20.1.2.3`” as the **primary** instruction (use `VM_HOST` placeholder).
- Diagrams that only show numeric IPs without a legend that they are **examples**.
