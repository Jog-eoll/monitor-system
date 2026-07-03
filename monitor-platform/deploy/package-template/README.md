# Stage 1 Deployment Input Template

This directory defines the standard input package for later deployment stages.
It does not replace the current `deploy/install.sh` entry yet.

## Directory Layout

```text
package-template/
  deploy.env.example
  devices.csv.example
  certs/
    platform/
    publish-gateway/
    terminal-gateway/
  images/
  sdk/
    lib/
  single-node/
    docker-compose.yml
  scripts/
    validate-stage1-input.sh
    install-single-node.sh
```

## Usage

1. Copy this directory to a real install package directory.
2. Rename `deploy.env.example` to `deploy.env`.
3. Rename `devices.csv.example` to `devices.csv`.
4. Put gateway certificates under `certs/`.
5. Put Docker image archives under `images/` if offline deployment is required.
6. Put VAuth SDK dynamic libraries under `sdk/lib/`.
7. Run:

```bash
bash scripts/validate-stage1-input.sh .
```

`certFile` values in `devices.csv` are relative to `CERT_DIR`. The example CSV
uses sample certificate names; replace them with real certificate files before
running validation on a real package.

## Validation Scope

- Required environment values in `deploy.env`.
- Password and forbidden default account checks.
- Host port format and common host port checks.
- `devices.csv` header, column count, `deviceType`, and `role`.
- Gateway certificate file, UKey SN, and auth ID presence.
- `one_to_many` topology:
  - exactly one `monitor_platform_monolith`;
  - exactly one `publish_gateway`;
  - at least one `terminal_encrypt_gateway`;
  - lightweight platform and publish-side gateway on the same host;
  - each terminal-side gateway references an existing `publish_gateway`.

## Naming Rules

Use the existing platform/device codes. Do not introduce a second enum set.

| Object | Code |
| --- | --- |
| Full control platform | `monitor_platform` |
| Lightweight control platform | `monitor_platform_monolith` |
| Publish-side encryption gateway | `publish_gateway` |
| Terminal-side gateway | `terminal_encrypt_gateway` |
| Publish server | `publish_server` |
| Info board / screen | `info_board` |
| Content server | `content_server` |

Business roles such as encryption and decryption are expressed by `role`, not
by creating new `deviceType` values.

## Stage 2 Single-Node MVP

The single-node MVP starts MySQL, Redis, MinIO, and
`monitor-platform-monolith` on one Linux host. It does not deploy terminal or
publish gateway containers yet.

Dry-run:

```bash
bash scripts/install-single-node.sh . --dry-run
```

Deploy:

```bash
bash scripts/install-single-node.sh .
```

The script refuses to deploy with `.example` input files. For a real deployment,
copy `deploy.env.example` to `deploy.env`, copy `devices.csv.example` to
`devices.csv`, replace site values, place real certificates and SDK libraries,
and then run the deploy command.
