# Deploying the Cafe Bar Noir demo (isolated stack, same VPS)

Companion to `DEPLOY.md`, not a replacement for it. This deploys a **second,
fully isolated** Docker Compose stack — its own containers, its own Postgres
database/volume, its own secrets, `AI_PROVIDER=mock` — alongside the existing
production stack on the same VPS, without touching production's containers,
volumes, database, or `.env`.

Every command below is written for **you to run manually**. Nothing in this
file was executed as part of preparing it — no SSH access exists from the
environment that wrote it.

**Read the whole file before running anything.** Steps are labelled:

- 🟢 **SAFE BEFORE CADDY/DNS** — touches only the new demo directory/stack;
  cannot affect production or be seen from the internet yet.
- 🟡 **TOUCHES EXISTING PRODUCTION INFRASTRUCTURE** — the few steps that add
  (never modify or remove) something on the production side: one Docker
  network attachment, one Caddy site block, one DNS record. Each is additive
  and independently reversible (see "Rollback" at the end).

---

## Architecture

```
EXISTING VPS
│
├── PRODUCTION (~/ratel)            AI_PROVIDER=openai   — untouched
│   ├── ratel-postgres  (internal only)
│   ├── ratel-backend   (internal only)
│   ├── ratel-frontend  (internal only)
│   └── ratel-caddy     (:80/:443 — the ONLY container with host ports)
│
├── SERENITY                                              — untouched
│
└── CAFE BAR NOIR DEMO (~/ratel-demo)   AI_PROVIDER=mock   — new, isolated
    ├── demo postgres  (internal only, own volume)
    ├── demo backend   (internal only, no public route at all)
    └── demo frontend  (joins the shared "ratel-edge" network so the
                         EXISTING production Caddy can reach it — no new
                         Caddy container, since production's already owns
                         80/443)
```

Key fact from `frontend/middleware.ts`: every browser call to `/api/*` is
proxied **server-side** by Next.js itself (that's what attaches the auth
header from the httpOnly cookie) — the browser never talks to the backend
directly. So the demo backend needs **no public route of any kind**; only the
demo frontend needs to be reachable through Caddy.

---

## STEP 1 — Prepare directory 🟢

```bash
mkdir -p ~/ratel-demo
cd ~/ratel-demo
```

A directory **separate from `~/ratel`** is what makes every container name,
network, and volume below automatically distinct from production — Compose's
project name defaults to the directory name, and nothing in either compose
file overrides that with a hardcoded name.

## STEP 2 — Get the code 🟢

The Cafe Bar Noir work lives on the `phase-5c-checkpoint` branch locally, on
top of commit `a30fd4b` — **not yet pushed to GitHub**. Push it yourself when
ready (not done automatically, per your instructions):

```bash
# From your own machine, in the repo:
git push origin phase-5c-checkpoint
```

Then, on the VPS:

```bash
cd ~/ratel-demo
git clone -b phase-5c-checkpoint https://github.com/Dennisasante/Ratel-Business-Management-System-RBMS-.git .
git checkout a30fd4bb9fe5477d6c275da8903c5fd30dc64b01   # pin to the exact validated commit
```

(If you'd rather not push a branch named after an internal phase checkpoint,
push it to any branch name you like and substitute it above — the commit
hash pin is what actually matters for reproducibility.)

## STEP 3 — Create demo `.env` 🟢

```bash
cp .env.demo.example .env
nano .env
```

Fill in every value — see `.env.demo.example` in the repo for the full
annotated template. Nothing in it may match production's own `.env` (see
Phase 5's table below). Leave `OPENAI_API_KEY` blank — `AI_PROVIDER=mock`
never reads it.

## STEP 4 — Generate demo-only secrets 🟢

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 48   # ENCRYPTION_KEY  — run again, a DIFFERENT value
openssl rand -base64 24   # DB_PASSWORD
openssl rand -base64 18   # SUPER_ADMIN_PASSWORD
```

Paste each into `.env`. Never reuse a value already in production's `.env`.

## STEP 5 — Start the demo database 🟢

```bash
docker compose -p ratel-demo -f docker-compose.demo.yml up -d postgres
docker compose -p ratel-demo -f docker-compose.demo.yml ps
```

Wait for `postgres` to show `healthy` before continuing.

## STEP 6 — Build/start the demo backend 🟢

```bash
docker compose -p ratel-demo -f docker-compose.demo.yml up -d --build backend
docker compose -p ratel-demo -f docker-compose.demo.yml logs -f backend
```

Watch for `Started RbmsApplication` (Flyway migrations run automatically, the
same as any other fresh database — against the demo's own Postgres only).
Ctrl-C out of `logs -f` once you see it; the container keeps running.

## STEP 7 — Build/start the demo frontend 🟢

```bash
docker compose -p ratel-demo -f docker-compose.demo.yml up -d --build frontend
```

## STEP 8 — Verify health 🟢

```bash
docker compose -p ratel-demo -f docker-compose.demo.yml ps
docker stats --no-stream ratel-demo-postgres-1 ratel-demo-backend-1 ratel-demo-frontend-1
```

All three should show `Up`/`healthy`. Confirm memory is tracking near the
limits set in `docker-compose.demo.yml` (512m/1g/384m), not against them.

At this point the demo stack is fully running but **invisible from the
internet** — nothing has published a host port or told Caddy about it yet.

## STEP 9 — Seed Cafe Bar Noir 🟢

Log in as the demo's own Super Admin (the one from `.env`, not production's)
and call the seed endpoint once. The backend publishes no host port in
`docker-compose.demo.yml` (by design — only the frontend needs a route,
see the Architecture section above), so run this
**from inside the backend container** rather than from the VPS shell
directly — `docker compose exec` reaches it over the container's own
loopback regardless:

```bash
docker compose -p ratel-demo -f docker-compose.demo.yml exec backend sh -c '
  TOKEN=$(curl -s -X POST http://localhost:8090/api/platform/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"<SUPER_ADMIN_EMAIL>\",\"password\":\"<SUPER_ADMIN_PASSWORD>\"}" \
    | grep -o "\"token\":\"[^\"]*" | cut -d\" -f4) &&
  curl -s -X POST http://localhost:8090/api/platform/demo/seed-cafe-bar-noir \
    -H "Authorization: Bearer $TOKEN"
'
```

Expect a JSON response with `"created":true` (or `false` with the same
`businessId` if it already existed — reconciliation, not duplication, per
`CafeBarNoirDemoSeedService`'s own idempotent-by-slug design).

## STEP 10 — Disable the seed flag 🟢

```bash
# In ~/ratel-demo/.env, change:
#   DEMO_SEED_ENABLED=true
# to:
#   DEMO_SEED_ENABLED=false
docker compose -p ratel-demo -f docker-compose.demo.yml up -d backend
```

Recreates only the demo backend (picks up the new env var) — demo Postgres
and its data are untouched.

## STEP 11 — Configure Caddy 🟡 TOUCHES EXISTING PRODUCTION INFRASTRUCTURE

This is the one place the demo genuinely needs help from something
production owns (port 80/443). Two additive steps, both **outside** any
git-tracked file, so a future `git pull` in `~/ratel` can never silently
undo or reapply them:

**11a. Create the shared network once** (idempotent — safe if it already exists):

```bash
docker network create ratel-edge || true
```

**11b. Attach production's Caddy to it**, via a local override file in
`~/ratel` (production's own directory) — never edit `docker-compose.prod.yml`
itself:

```bash
cat > ~/ratel/docker-compose.prod.override.yml <<'EOF'
services:
  caddy:
    networks:
      - ratel-edge
networks:
  ratel-edge:
    external: true
EOF
```

```bash
cd ~/ratel
docker compose -f docker-compose.prod.yml -f docker-compose.prod.override.yml up -d caddy
```

This recreates **only** the `caddy` container (to attach the new network) —
`depends_on` order means Compose won't touch backend/frontend/postgres.
Confirm with `docker compose -f docker-compose.prod.yml -f docker-compose.prod.override.yml ps` that all four production containers are still `Up`.

From now on, always deploy production with **both** `-f` flags so this
override keeps applying:

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.prod.override.yml up -d --build
```

**11c. Add the demo site block** to production's live `Caddyfile` directly on
the VPS (again, not via `git pull` — hand-edit `~/ratel/Caddyfile`):

On a shared Docker network, bare service names (`frontend`) only resolve
*within* the same Compose project — across projects (production's Caddy vs.
the demo's frontend), use the demo frontend's actual **container name**,
which `docker compose -p ratel-demo -f docker-compose.demo.yml ps` will show
as `ratel-demo-frontend-1` (Compose's default `<project>-<service>-<n>`
pattern; confirm the exact name rather than assuming):

```caddyfile
demo.example.com {
	reverse_proxy ratel-demo-frontend-1:3000
}
```

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.prod.override.yml exec caddy caddy reload --config /etc/caddy/Caddyfile
```

## STEP 12 — Configure DNS 🟡 TOUCHES EXISTING PRODUCTION INFRASTRUCTURE (additive only)

One new A record, same zone as production's own:

| Type | Name | Value |
|---|---|---|
| A | `demo` (or whatever you put in `.env`'s `DOMAIN`) | `<VPS IP>` — same IP production already uses |

Does not touch or replace any existing record.

## STEP 13 — Verify HTTPS 🟢

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.prod.override.yml logs caddy | grep -i demo
```

Wait for Caddy to report a certificate obtained for the demo domain (same
automatic Let's Encrypt flow `DEPLOY.md` already describes — no extra
config needed beyond the site block above).

## STEP 14 — Enable AI for Cafe Bar Noir 🟢

Through the demo's own Super Admin UI (`https://demo.example.com/platform/login`,
demo credentials from `.env`) → Businesses → Cafe Bar Noir → Enabled modules
→ Edit → check **AI** → Save. Same normal mechanism used throughout this
whole project — no bypass, no special-casing.

## STEP 15 — Remote browser smoke test 🟢

See the full checklist in "Phase 12" below — run it against
`https://demo.example.com`, not localhost.

## STEP 16 — Verify booking and database state 🟢

```bash
docker compose -p ratel-demo -f docker-compose.demo.yml exec postgres \
  psql -U <DB_USERNAME> -d cafe_bar_noir_demo -c \
  "select id, booking_number, payment_status, customer_whatsapp from bookings order by created_at desc limit 5;"
```

---

## Rollback (removes ONLY the demo stack)

```bash
cd ~/ratel-demo
docker compose -p ratel-demo -f docker-compose.demo.yml down       # containers + demo network only
docker compose -p ratel-demo -f docker-compose.demo.yml down -v    # + demo volumes (pg_data, uploads) — irreversible for demo data, fine to lose
```

**Why the `-p ratel-demo` flag makes this safe:** Compose scopes `down`/`down -v`
to containers, networks, and volumes it created **under that exact project
name**. Production was created under project name `ratel` (from its own
directory). There is no world in which `-p ratel-demo down -v`, run from
`~/ratel-demo`, touches anything named `ratel-*`. The danger scenario is
running a *bare* `docker compose down -v` **from inside `~/ratel`** — always
pass `-p`/`-f` explicitly, or better, always `cd` into the correct directory
first and double-check `pwd` before any `down`.

**Verify production survived:**

```bash
docker compose -f ~/ratel/docker-compose.prod.yml -f ~/ratel/docker-compose.prod.override.yml ps
```

All four (`postgres`, `backend`, `frontend`, `caddy`) should still show `Up`.

**Reversing Steps 11/12** (only if you want the demo domain gone entirely,
not just the containers):

- Remove the `demo.example.com { ... }` block from `~/ratel/Caddyfile`, reload Caddy.
- Delete the DNS A record.
- The `ratel-edge` network and `docker-compose.prod.override.yml` are harmless to leave in place for a future demo; remove with `docker network rm ratel-edge` only after confirming nothing else references it.

---

## Phase 6 — Resource limits (already in `docker-compose.demo.yml`)

| Service | Memory limit | CPU limit | Why this number |
|---|---|---|---|
| demo backend | `1g` (JVM capped to `-Xmx512m -XX:MaxMetaspaceSize=128m` via `JAVA_TOOL_OPTIONS`) | `0.75` | Heap cap is the real ceiling; container limit is the backstop if off-heap/native memory grows beyond it |
| demo frontend | `384m` | `0.5` | Next.js standalone server, light |
| demo postgres | `512m` | `0.5` | Single-tenant demo data; default `shared_buffers` on alpine is already conservative |

These use the plain Compose `mem_limit`/`cpus` keys (enforced directly via
the Docker Engine), **not** `deploy.resources.limits` — that block is
silently ignored outside Swarm mode, and this project runs plain
`docker compose up`, not `docker stack deploy`. Using the wrong key would
mean "limits" that don't actually limit anything.

## Phase 5 — Environment configuration (production vs. demo)

| Variable | Production | Demo | Shared? |
|---|---|---|---|
| `DOMAIN`/`FRONTEND_URL` | existing prod domain | new subdomain | No |
| `BACKEND_URL` | public `API_DOMAIN` (needed for Paystack/WooCommerce webhooks) | internal-only; no public route (demo has neither integration) | No |
| `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` | prod values | new, independent | **No** |
| `JWT_SECRET` | prod secret | new, independent | **No** — a shared secret would make tokens valid across both |
| `ENCRYPTION_KEY` | prod key | new, independent | **No** |
| `AI_PROVIDER` | `openai` | `mock` | No — the entire point |
| `OPENAI_API_KEY` | prod key | blank | No — never read by mock |
| `DEMO_SEED_ENABLED` | `false` | `true`, then `false` after Step 10 | No |
| `SUPER_ADMIN_EMAIL`/`PASSWORD` | prod admin | new demo-only admin | **No** |
| `PAYSTACK_*`/`SMTP_*`/`VAPID_*` | prod values | blank (quietly disabled) | No |

## Phase 8 — Seed mechanism, reviewed again

- Gated by `DEMO_SEED_ENABLED` **and** Super-Admin-only auth — two
  independent gates.
- Idempotent by business slug (`cafe-bar-noir-demo`) — a second call
  reconciles/returns the existing tenant, never duplicates.
- Every write scoped to the one business it creates — cannot touch another
  tenant, verified by this project's own automated tenant-isolation-adjacent
  tests (`CafeBarNoirDemoSeedServiceTest`).
- The seed runs against **this stack's own Postgres only** — there is no
  code path in `CafeBarNoirDemoSeedService` that could reach production's
  database even if misconfigured, because the demo backend's own
  `SPRING_DATASOURCE_URL` points at `demo-postgres` and nothing else.

## Phase 13 — OpenAI confirmation

`MockAiProvider` is annotated
`@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)`
and contains no reference to `OPENAI_API_KEY` or any HTTP client to
`api.openai.com` anywhere in its source. With `AI_PROVIDER=mock` in the demo
`.env`, `OpenAiProvider` is never instantiated at all — not "instantiated but
unused," genuinely never constructed by Spring. No OpenAI credential is ever
read, needed, or prompted for by anything in this deployment.

## Phase 12 — Smoke-test checklist (run against the live demo URL)

1. Open `https://demo.example.com` — loads
2. Log in as Cafe Bar Noir demo owner → AI Concierge tab loads
3. Header shows "Cafe Bar Noir" — confirms correct tenant, no Serenity content anywhere
4. Quick actions: View the Menu / Explore Dinner Packages / Make a Booking / Plan an Event / Talk to Someone
5. "Ask about the beach" does **not** appear anywhere
6. "What's on the menu?" → real menu response
7. "What dinner packages do you have?" → all 4 packages, real prices, as cards
8. Select Seafood Experience
9. State a party size (e.g. 8 guests)
10. Pricing shown is per-guest × guests = total, with 70%/30% deposit/balance
11. Request a valid substitution → applied, price updates correctly
12. Request an invalid substitution → rejected, real alternatives offered instead
13. Order summary shows real selections + real relevant policy excerpt
14. Confirming requires an explicit affirmative reply (not a checkbox bypass)
15. Booking is created only after that confirmation — check the dashboard Bookings list
16. Booking shows **Unpaid** — never "paid"/"payment successful"
17. Confirmation is clearly labelled DEMO / payment not processed
18. New conversation → "What sandwiches do you have?" → real sandwich menu
19. Ask something genuinely unknown (e.g. live music schedule) → honest "I don't know" + staff-escalation offer, never invented
20. Internal tool activity is hidden by default; the small dev-only toggle reveals it when explicitly clicked
21. Production's own URL, logged in as a real existing tenant, still works normally and shows none of the above
