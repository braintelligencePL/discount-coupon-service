# Coupon Discount Service

A REST service for managing discount coupons: 

- **create a coupon** 
- **redeem a coupon on behalf of a user**
- **look one up by code**.

I built this with Claude Code using a spec-first loop — `plan` → `implement` → `review` (skills live in `.claude/`) — making one small change at a time to keep the context focused, alongside ongoing discussion of design decisions.

A few changes are documented under `context/changes/<change-id>/` with their plan, implementation phases, and review; each phase was verified before moving on. Other changes were made outside that workflow — or by hand — and aren't reflected there.

> `.claude/` and `context/` aren't part of the running service. I kept them in the repo as a record of how it was built, because I think the workflow is worth showing.

#### Implemented features:

- **Correlation id** per request for log tracing
- **OpenAPI / Swagger** docs, and Actuator **health** (liveness/readiness) + **build-info** endpoints
- **Cache with a Caffeine for** geo-IP lookups (with optional override for local testing)
- **Layered architecture** (**api** → **application** → **domain**, plain Java; **infrastructure**
  implements the ports)
- **PostgreSQL persistence** with a Liquibase-managed schema
- **One redemption per user** (the optional requirement) — `(coupon_code, user_id)` uniqueness

#### Not implemented 

- **Authentication / authorization** — not required by the task; the redemption request carries
  an `userId` string that a real system would take from an authenticated principal
- **CI/CD pipeline** and **cloud deployment** — a production-style Docker image and compose file
  are provided, but no pipeline or hosting
- **Coupon validity window / expiry** — only a creation timestamp is stored
- **Offline geo-IP** — depends on the free `ip-api.com` (rate-limited); no bundled MaxMind DB
- **Shared geo-IP cache** — the cache is per-instance

## Quick start

**Prerequisites:** JDK 21 and Docker.

```bash
# 1. Start PostgreSQL
docker compose up db -d

# 2. Run the service in dev mode: 
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Once up:

| URL | |
| --- | --- |
| `http://localhost:8080/swagger-ui.html` | interactive API docs |
| `http://localhost:8080/v3/api-docs` | OpenAPI document |
| `http://localhost:8080/actuator/health` | health (with `liveness` / `readiness` groups) |
| `http://localhost:8080/actuator/info` | build info |

Stop with `docker compose down` (add `-v` to wipe the database).

**Clear the data if already run and wants to start from scratch** no app restart needed:

```bash
docker exec -it coupons-db psql -U coupons -d coupons \
  -c 'TRUNCATE coupon_redemption, coupon RESTART IDENTITY CASCADE;'
```

Or `docker compose down -v && docker compose up -d db`

to drop the volume entirely —
Liquibase rebuilds the schema on the next app start.

<br> 

### End-to-end walkthrough (manually via `curl`)

Assumes the service is running on `:8080` with the `dev` profile. 

Because `dev` sets `geoip.allow-ip-override=true`, a country-restricted coupon can be exercised locally by
naming a public IP via the `X-Client-IP` header (or `?ip=`); without it the caller is
`127.0.0.1`, which can't be geolocated → `422 COUNTRY_NOT_DETERMINED`.

**1. Create a coupon** — 2 uses, restricted to Poland → `201 Created`

```bash
curl -sS -i -X POST http://localhost:8080/api/v1/coupons \
  -H 'Content-Type: application/json' \
  -d '{"code":"SUMMER25","maxUses":2,"country":"PL"}'
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/coupons/summer25
Content-Type: application/json

{
  "code": "summer25",
  "createdAt": "2026-09-01T09:15:32.117Z",
  "maxUses": 2,
  "currentUses": 0,
  "remainingUses": 2,
  "country": "PL"
}
```

**2. Look it up** — case-insensitive → `200 OK`

```bash
curl -sS http://localhost:8080/api/v1/coupons/SUMMER25
```

```json
{
  "code": "summer25",
  "createdAt": "2026-09-01T09:15:32.117Z",
  "maxUses": 2,
  "currentUses": 0,
  "remainingUses": 2,
  "country": "PL"
}
```

**3. Redeem as `alice` from a Polish IP** → `200 OK`, `remainingUses` drops to 1

```bash
curl -sS -X POST http://localhost:8080/api/v1/coupons/summer25/redemptions \
  -H 'Content-Type: application/json' -H 'X-Client-IP: 194.204.159.1' \
  -d '{"userId":"alice"}'
```

```json
{
  "code": "summer25",
  "userId": "alice",
  "remainingUses": 1,
  "resolvedCountry": "PL",
  "redeemedAt": "2026-09-01T09:16:04.882Z"
}
```

**4. Same user again** → `409 Conflict`

```bash
curl -sS -X POST http://localhost:8080/api/v1/coupons/summer25/redemptions \
  -H 'Content-Type: application/json' -H 'X-Client-IP: 194.204.159.1' \
  -d '{"userId":"alice"}'
```

```json
{
  "type": "about:blank",
  "title": "Coupon already redeemed by this user",
  "status": 409,
  "detail": "User 'alice' has already redeemed coupon 1",
  "instance": "/api/v1/coupons/summer25/redemptions",
  "code": "ALREADY_REDEEMED"
}
```

**5. `bob` from a US IP** → `403 Forbidden` (coupon is PL-only)

```bash
curl -sS -X POST http://localhost:8080/api/v1/coupons/summer25/redemptions \
  -H 'Content-Type: application/json' -H 'X-Client-IP: 8.8.8.8' \
  -d '{"userId":"bob"}'
```

```json
{
  "type": "about:blank",
  "title": "Coupon not available from your country",
  "status": 403,
  "detail": "Coupon 'summer25' is not available from country US",
  "instance": "/api/v1/coupons/summer25/redemptions",
  "code": "COUNTRY_NOT_ALLOWED"
}
```

**6. `bob` from a Polish IP** → `200 OK`, `remainingUses` drops to 0

```bash
curl -sS -X POST http://localhost:8080/api/v1/coupons/summer25/redemptions \
  -H 'Content-Type: application/json' -H 'X-Client-IP: 194.204.159.1' \
  -d '{"userId":"bob"}'
```

```json
{
  "code": "summer25",
  "userId": "bob",
  "remainingUses": 0,
  "resolvedCountry": "PL",
  "redeemedAt": "2026-09-01T09:17:20.545Z"
}
```

**7. `carol`, coupon now exhausted** → `409 Conflict`

```bash
curl -sS -X POST http://localhost:8080/api/v1/coupons/summer25/redemptions \
  -H 'Content-Type: application/json' -H 'X-Client-IP: 194.204.159.1' \
  -d '{"userId":"carol"}'
```

```json
{
  "type": "about:blank",
  "title": "Coupon usage limit reached",
  "status": 409,
  "detail": "Coupon 'summer25' has reached its usage limit",
  "instance": "/api/v1/coupons/summer25/redemptions",
  "code": "USAGE_LIMIT_REACHED"
}
```

**8. No IP override** → `422 Unprocessable Entity` (fail closed)

```bash
curl -sS -X POST http://localhost:8080/api/v1/coupons/summer25/redemptions \
  -H 'Content-Type: application/json' -d '{"userId":"dave"}'
```

```json
{
  "type": "about:blank",
  "title": "Could not determine the caller's country",
  "status": 422,
  "detail": "Could not determine the caller's country; coupon 'summer25' is country-restricted",
  "instance": "/api/v1/coupons/summer25/redemptions",
  "code": "COUNTRY_NOT_DETERMINED"
}
```

<br>

### Request correlation id

`CorrelationIdFilter` tags each request with an id — from the `X-Correlation-Id` header, or a
generated UUID — and echoes it on the response. Every log line for that request is prefixed
with it in brackets. It makes easier to trace a request through the logs:

```
18:36:47.524 INFO  [83225672-b8af-4574-93a0-db211cd5d18e] c.e.c.application.CouponService - redemption requested code=summer25 user=bob
18:36:47.778 INFO  [83225672-b8af-4574-93a0-db211cd5d18e] c.e.c.application.CouponService - redemption outcome=CountryNotAllowedException code=summer25 user=bob
18:36:51.290 INFO  [21ac47b9-5d24-4195-84a1-c86091066f49] c.e.c.application.CouponService - redemption requested code=summer25 user=bob
18:36:51.309 INFO  [21ac47b9-5d24-4195-84a1-c86091066f49] c.e.c.application.CouponService - redemption outcome=SUCCESS code=summer25 user=bob country=PL remainingUses=0
18:36:53.183 INFO  [6a02734b-4bca-42e9-9f7f-521790cbea94] c.e.c.application.CouponService - redemption requested code=summer25 user=carol
18:36:53.197 INFO  [6a02734b-4bca-42e9-9f7f-521790cbea94] c.e.c.application.CouponService - redemption outcome=UsageLimitReachedException code=summer25 user=carol
```


## Extension points (out of scope)

- **Coupon validity window** — only a creation timestamp is stored; `valid_from` / `valid_to`
  / expiry would be an additive migration
- **Offline geo-IP** — swap the `GeoIpResolver` implementation for a bundled MaxMind GeoLite2
  database to remove the runtime network dependency and rate limit.
- **Shared cache** — move the geo-IP cache to Redis if instance count makes per-instance
  caching wasteful.
- Authentication/authorization 
