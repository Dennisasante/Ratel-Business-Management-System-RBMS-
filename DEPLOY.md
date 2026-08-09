# Deploying RBMS

Single VPS, Docker Compose, four containers: Postgres, the Spring Boot
backend, the Next.js frontend, and Caddy in front of both handling HTTPS.
Caddy issues and renews Let's Encrypt certificates automatically — no
certbot, no manual renewal cron.

This assumes Hostinger since that's what this project has been planned
around, but nothing here is Hostinger-specific beyond step 1 — any VPS with
a public IP and root SSH access works the same way.

## 1. Provision the VPS

Recommended plan: **KVM 2** (2 vCPU / 8 GB RAM / 100 GB NVMe) or
better. Postgres, a JVM, and a Node server on one box comfortably fit in
8 GB with headroom; the cheaper KVM 1 (4 GB RAM) will *work* for an early
pilot with a handful of businesses but leaves little margin.

When creating the VPS:
- **OS image:** Ubuntu 22.04 LTS (or 24.04 — either is fine; these steps
  assume Ubuntu/Debian's `apt` package manager).
- **Auth:** add your SSH public key during setup rather than a password —
  simpler and safer than password auth over SSH.

Note the server's public IPv4 address once it's up — you'll need it for DNS.

## 2. Point DNS at it

In your domain's DNS settings (at whichever registrar holds it), add two
**A records** pointing at the VPS's IP:

| Type | Name | Value |
|------|------|-------|
| A | `@` (or your bare domain) | `<VPS IP>` |
| A | `api` | `<VPS IP>` |

This gives you `yourdomain.com` (the app) and `api.yourdomain.com` (the
backend — needed as a direct public endpoint for WooCommerce/Paystack
webhooks, which call it directly rather than through the frontend).

DNS propagation can take anywhere from a few minutes to a few hours.
Caddy won't be able to get a certificate until it's resolved — no rush
between this step and the next few, but the first `docker compose up` needs
it to have already propagated.

## 3. Install Docker on the VPS

SSH in (`ssh root@<VPS IP>`), then:

```bash
curl -fsSL https://get.docker.com | sh
```

That's Docker's own official install script — installs Docker Engine and
the `docker compose` plugin together. Verify with `docker compose version`.

## 4. Clone the repo and configure secrets

```bash
git clone https://github.com/Dennisasante/Ratel-Business-Management-System-RBMS-.git ratel
cd ratel
cp .env.production.example .env
nano .env   # or vim/whatever — fill in every value
```

Generate the two random secrets it asks for:

```bash
openssl rand -base64 48   # run twice — once for JWT_SECRET, once for ENCRYPTION_KEY
```

See `.env.production.example` for what every other value means and where it
comes from (SMTP provider, Paystack dashboard, Google Cloud Console for
sign-in — all optional except DB/JWT/ENCRYPTION_KEY, the app runs fine with
the rest blank, just with those features quietly disabled).

## 5. First deploy

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

First run takes a few minutes (Maven + npm builds happen inside the
containers). Watch it come up:

```bash
docker compose -f docker-compose.prod.yml logs -f
```

Flyway migrations run automatically on backend startup — same as local dev,
just against this Postgres instead. Once you see the backend log
`Started RbmsApplication` and Caddy log that it's obtained certificates for
both domains, visit `https://yourdomain.com`.

If a domain doesn't get a cert (check `docker compose logs caddy`), it's
almost always DNS not having propagated yet — wait and Caddy will retry on
its own, no restart needed.

## 6. Log in as Super Admin

If you set `SUPER_ADMIN_EMAIL`/`SUPER_ADMIN_PASSWORD` in `.env`, that
account was created on this first startup. Log in at
`https://yourdomain.com/platform/login`. Those two env vars are safe to
leave in `.env` afterward — the backend only acts on them when no Super
Admin exists yet, so they're a no-op on every later restart.

## Redeploying after a code change

```bash
cd ratel
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

Rebuilds only the images whose source actually changed (Docker's layer
cache), then recreates just those containers — Postgres data and uploaded
logos survive in their named volumes untouched.

There's no CI-triggered auto-deploy yet (the GitHub Actions workflow only
builds/tests) — this is a manual `git pull` + rebuild on the server for now.
Worth automating with a deploy key + webhook later if pushes become frequent
enough that this gets tedious.

## Backups

Two things hold real data outside the containers themselves, both in named
Docker volumes: `ratel_pg_data` (the database) and `ratel_uploads` (business
logos). Back up the Postgres one at minimum:

```bash
docker compose -f docker-compose.prod.yml exec postgres \
  pg_dump -U <DB_USERNAME> ratel_db > backup-$(date +%F).sql
```

Worth putting on a daily cron once there's real business data at stake.

## A note on scale

This single-VPS setup is exactly what `backend/README.md`'s "Business logo
storage" section warns about: uploaded logos live on local disk, not object
storage. Fine here since everything's one server — if this ever moves to
multi-instance or ephemeral hosting, that needs to become S3-compatible
storage first (Cloudflare R2 or DigitalOcean Spaces both work as drop-in
replacements). Not worth building before it's actually needed.
