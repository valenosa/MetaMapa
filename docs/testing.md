# Testing

The services were tested at the API level with **Postman**. The collections live in [`postman/`](../postman/) and can be run interactively, in batch with Postman's Collection Runner, or from the command line with Newman.

## Collections

| File | Purpose | Requests | Automated assertions (`pm.test`) |
|---|---|---|---|
| [`MetaMapa.postman_collection.json`](../postman/MetaMapa.postman_collection.json) | Every service, organized by service and feature | 61 | 6 |
| [`Seed-Proxy-Static.postman_collection.json`](../postman/Seed-Proxy-Static.postman_collection.json) | Data seeding: subscribe the aggregator to the proxy and static sources, import a sample CSV, create collections | 6 | 0 |
| [`Dynamic-Edit-Scenario.postman_collection.json`](../postman/Dynamic-Edit-Scenario.postman_collection.json) | End-to-end moderation scenario: create events, accept or reject submissions, request and approve edits | 11 | 0 |
| **Total** | | **78** | **6** |

The main collection is organized like the system:

| Folder | Requests | What it covers |
|---|---|---|
| Aggregation service | 22 | Collections (create, update, delete, consensus, add and remove sources), deletion requests (including a spam example), source subscriptions, events, categories, provinces |
| Sources | 17 | Dynamic source (events, submissions, edit requests), static source (create and import), proxy source (sources and events) |
| Statistics service | 4 | Top categories, top provinces by category and by collection, busiest hours |
| API Gateway | 11 | Register, login and refresh through the gateway; route-security checks; events through the gateway |
| Auth | 3 | The same auth flow directly against `user-service` |
| Multimedia (currently not working) | 4 | Kept for reference (see below) |

## How the collections are built

- **Environment variables for every base URL.** One variable per service (`aggregation-service-baseUrl`, `dynamic-source-baseUrl`, …) plus `APIGateway-baseUrl`. The same requests can target a local or a deployed instance by switching the environment. Template: [`MetaMapa.local.postman_environment.example.json`](../postman/MetaMapa.local.postman_environment.example.json).
- **Chained authentication.** The register, login and refresh requests check that the response contains `accessToken` and `refreshToken` (these are the 6 assertions) and store them in `AccessToken` and `RefreshToken`. Protected requests then send `Authorization: Bearer {{AccessToken}}`, and the refresh request sends `{{RefreshToken}}`.
- **Direct and gateway calls.** Requests sent straight to a service's port test its logic in isolation. Requests that need a user simulate the gateway by sending the `X-User-Id` header themselves. Requests sent through `APIGateway-baseUrl` test the real path, including the security rules.
- **Route-security checks.** The *API Gateway → Tests* folder calls the gateway's six self-test routes, one per rule of the [route syntax](authentication.md#route-rules-a-small-declarative-syntax): public, any authenticated user, admin only, admin **and** contributor, admin **or** contributor, and optional authentication. Each one forwards to an endpoint that echoes the request headers. The expected result of each case is documented next to its route in [`routes.yml`](../backend/api-gateway/src/main/resources/routes.yml): `401` without a token or with an expired one, `403` with the wrong role, and `200` with the `X-User-Id` header injected otherwise. These checks are run by hand.
- **Batch flows.** The seeding and moderation-scenario collections are ordered so they can be run top to bottom with the Collection Runner on a fresh database. Some requests use fixed ids (for example, submission `1`).

## What is not automated

Being transparent about coverage:

- Only the 6 assertions above are automated. The remaining requests, including the route-security checks, are verified by hand against the expected status code and body.
- The **Multimedia** folder was already marked as not working when the collection was exported. Its image route (`/events/:eventId/images/:imageName`) is not one of the gateway routes; media is served from `/api/v1/multimedia/**`.
- There are no unit tests in the repository today. JUnit tests were written for the first version of the static source; they became obsolete when the second delivery changed the requirements and were later removed.
- No CI pipeline was set up during the course. Newman (below) is the natural way to add one.

## Last run

On **2026-11-24** the `API Gateway` folder of the main collection was run with Newman against a local stack: registry, user, aggregation, static and dynamic services, gateway and web client, each on an empty test database.

| Executed | Failed |
|---|---|
| 11 requests | 0 |
| 6 assertions | 0 |

The route-security checks, run with a freshly registered `CONTRIBUTOR` token, returned the expected codes:

| Route | Status |
|---|---|
| `/test/authenticated` | 200 |
| `/test/admin-only` | 403 |
| `/test/admin-and-contributor` | 403 |
| `/test/admin-or-contributor` | 200 |
| `/test/soft-auth` (no token) | 200 |

The folder's `Create Event` request returned `415`: it sends raw JSON, while the dynamic source now expects `multipart/form-data` (an `event` part plus optional `multimedia` files). That request needs updating.

## Running the collections

1. Start the system ([running-locally.md](running-locally.md)).
2. Import the collection and the environment template into Postman, select the environment and replace the `<TEST_USERNAME>`, `<TEST_PASSWORD>` and `<TEST_USER_UUID>` placeholders.

**Collection Runner (Postman):** open the collection (or one folder) → *Run* → keep the default order → *Run*. Use it for the seeding and moderation-scenario collections, which depend on running in order.

**Newman (command line, CI-friendly):**

```bash
npx newman run postman/MetaMapa.postman_collection.json \
  -e postman/MetaMapa.local.postman_environment.example.json \
  --folder "API Gateway"          # optional: run only one folder

npx newman run postman/Seed-Proxy-Static.postman_collection.json \
  -e postman/MetaMapa.local.postman_environment.example.json
```

Newman exits with a non-zero code when an assertion fails, so the same command can run as a step in a CI pipeline once the services are started there.
