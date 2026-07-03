# Agent Environment Guide

## Credential Policy

Do not store plaintext SSH passwords, cloud login tokens, private keys, UKey PINs, database passwords, or MQTT secrets in this repository.

When SSH access is needed, use this precedence:

1. Existing `~/.ssh/config` host aliases and SSH agent keys.
2. Windows Credential Manager or an approved local secret store.
3. A one-time credential provided by the user for the current session.

If no credential is available locally, ask the user for the credential at runtime. Do not add it to this file, command history, docs, memory, or logs.

## Known SSH Targets

| Alias | Host | Port | User | Role | Credential Reference |
| --- | --- | ---: | --- | --- | --- |
| `monitor-cloud-ecs` | `120.26.33.225` | `22` | `root` | Aliyun ECS public test platform; EMQX and platform services were used here during MQTT cloud联调. Private IP observed as `172.31.81.103`. | Use local SSH key or ask user for one-time credential. |
| `monitor-site-25` | `192.168.1.25` | `22` | `root` | Company LAN platform/gateway host. Observed deployment root: `/opt/monitor-platform/deploy`; also used for publish-gateway tests. | Use local SSH key or ask user for one-time credential. |
| `terminal-gateway-26` | `192.168.1.26` | `22` | `root` | Company LAN terminal/decrypt gateway host; service probe observed on `8093`. | Use local SSH key or ask user for one-time credential. |
| `base-services-31` | `192.168.1.31` | `22` | `root` | Company LAN base-services host. Used for MySQL/Redis/MinIO/Nacos-related runtime dependencies and disk cleanup work. | Use local SSH key or ask user for one-time credential. |

## Non-SSH Environment Endpoints

| Endpoint | Purpose | Notes |
| --- | --- | --- |
| `192.168.1.31:3306` | Runtime MySQL target in current LAN deployment. | Treat credentials as secrets; do not store in repo. |
| `192.168.1.31:8848` | Nacos service discovery/config. | Used by monitor-platform services. |
| `192.168.1.31:9000` | MinIO endpoint. | Used by upgrade package storage flows. |
| `192.168.1.251` | SVN server address seen in workspace history. | Not an SSH target unless separately confirmed. |
| `192.168.1.253` | SMB/NAS host seen in workspace history. | Use Windows Credential Manager; do not store share password. |

## Suggested SSH Config

Use local SSH aliases instead of embedding secrets in scripts:

```sshconfig
Host monitor-cloud-ecs
  HostName 120.26.33.225
  User root
  Port 22
  Password 
  IdentityFile ~/.ssh/<approved-key>

Host monitor-site-25
  HostName 192.168.1.25
  User root
  Port 22
  Password 123456
  IdentityFile ~/.ssh/<approved-key>

Host terminal-gateway-26
  HostName 192.168.1.26
  User root
  Port 22
  Password 123456
  IdentityFile ~/.ssh/<approved-key>

Host base-services-31
  HostName 192.168.1.31
  User root
  Port 22
  Password 123456
  IdentityFile ~/.ssh/<approved-key>
```

## Operational Notes for Agents

- Before restarting remote services, inspect the live host and back up changed config files.
- For `192.168.1.25`, validate Nginx changes with `docker exec monitor-nginx nginx -t` before reload/restart.
- For MQTT cloud tests, distinguish the public ECS `120.26.33.225` from the LAN gateway `192.168.1.25`.
- Temporary IP cutover tests have used alternate LAN IPs before; confirm the current active IP before SSH or deployment.
- Some historical project docs contain example or legacy plaintext credentials. Treat them as unsafe examples; do not copy them into new files or responses.
