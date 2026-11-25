# Testing

The services are tested at the API level with **Postman**. The collections live in [`postman/`](../postman/) and can be run interactively, in batch with Postman's Collection Runner, or from the command line with Newman.

## Collections

| File | Purpose | Requests | Automated assertions (`pm.test`) |
|---|---|---|---|
| [`MetaMapa.postman_collection.json`](../postman/MetaMapa.postman_collection.json) | Every service, organized by service and feature | 69 | 36 |
| [`Dynamic-Edit-Scenario.postman_collection.json`](../postman/Dynamic-Edit-Scenario.postman_collection.json) | End-to-end moderation scenario: create events, accept or reject submissions, request and approve edits | 11 | 17 |
| [`Seed-Proxy-Static.postman_collection.json`](../postman/Seed-Proxy-Static.postman_collection.json) | Data seeding: subscribe the aggregator to the proxy and static sources, import a sample CSV, create collections | 6 | 0 |
| **Total** | | **86** | **53** |

The main collection is organized like the system:

| Folder | Requests | Assertions | What it covers |
|---|---|---|---|
| API Gateway | 19 | 36 | Authentication and route security through the gateway (see below), plus an anonymous event submission and the public events feed |
| Aggregation service | 22 | 0 | Collections (create, update, delete, consensus, add and remove sources), deletion requests (including a spam example), source subscriptions, events, categories, provinces |
| Sources | 17 | 0 | Dynamic source (events, submissions, edit requests), static source (create and import), proxy source (sources and events) |
| Statistics service | 4 | 0 | Top categories, top provinces by category and by collection, busiest hours |
| Auth | 3 | 0 | The same auth flow directly against `user-service` |
| Multimedia (currently not working) | 4 | 0 | Kept for reference (see below) |

## What the assertions check

**API Gateway → Auth** (6 requests, 15 assertions). Registers a new user and checks that it gets the `CONTRIBUTOR` role, then the error cases: an existing username returns `409`, a wrong password returns `401` without tokens, and an access token sent to the refresh endpoint returns `401`. Login and refresh must return both tokens. The tokens and the user's id are stored in `AccessToken`, `RefreshToken` and `testUserId` for the next requests.

**API Gateway → Tests** (11 requests, 17 assertions). One request per rule of the [route syntax](authentication.md#route-rules-a-small-declarative-syntax), each sent to a gateway self-test route that echoes the headers that reached the service:

| Request | Expected |
|---|---|
| Public route, no token | `200`, no identity headers |
| Authenticated route, valid token | `200`, `X-User-Id` and `X-User-Name` match the logged-in user |
| Authenticated route, no token | `401` |
| Authenticated route, malformed token | `401` |
| Authenticated route, refresh token instead of an access token | `401` |
| Admin only, `CONTRIBUTOR` token | `403` |
| Admin **and** contributor, `CONTRIBUTOR` token | `403` |
| Admin **or** contributor, `CONTRIBUTOR` token | `200`, identity headers from the token |
| Optional authentication, no token | `200`, no identity headers |
| Optional authentication, no token, forged `X-User-Id` / `X-User-Name` | `200`, the forged headers are dropped |
| Optional authentication, valid token plus forged headers | `200`, the identity comes from the token |

The two requests with forged headers are a regression test for the [identity-header fix](authentication.md#identity-headers): against the gateway without the fix, the forged-headers check is the only assertion that fails.

**Dynamic edit scenario** (11 requests, 17 assertions). Every step must return `200`; created events must come back with an id, and each evaluated submission must end up `ACCEPTED` or `REJECTED` as requested.

## How the collections are built

- **Environment variables for every base URL.** One variable per service (`aggregation-service-baseUrl`, `dynamic-source-baseUrl`, …) plus `APIGateway-baseUrl`. The same requests can target a local or a deployed instance by switching the environment. Template: [`MetaMapa.local.postman_environment.example.json`](../postman/MetaMapa.local.postman_environment.example.json).
- **Chained authentication.** Register, login and refresh store the returned tokens, and protected requests send `Authorization: Bearer {{AccessToken}}`. The tokens are only stored when the call succeeds, so a failed login does not overwrite a working token.
- **Direct and gateway calls.** Requests sent straight to a service's port test its logic in isolation. Requests that need a user simulate the gateway by sending the `X-User-Id` header themselves. Requests sent through `APIGateway-baseUrl` test the real path, including the security rules.
- **Batch flows.** The seeding and moderation-scenario collections are ordered so they can be run top to bottom on a fresh database. Some requests use fixed ids (for example, submission `1`).

## What is not automated

Being transparent about coverage:

- The assertions cover the *API Gateway* folder and the moderation scenario. The other folders of the main collection and the seeding collection have no assertions; they are checked by hand against the expected status code and body.
- The **Multimedia** folder was already marked as not working when the collection was exported. Its image route (`/events/:eventId/images/:imageName`) is not one of the gateway routes; media is served from `/api/v1/multimedia/**`.
- There are no unit tests in the repository today. JUnit tests were written for the first version of the static source; they became obsolete when the second delivery changed the requirements and were later removed.
- There is no CI pipeline. Newman (below) is the natural way to add one, but the pipeline would also need MySQL and the services running.

## Last run

On **2026-11-24**, with Newman 6.2.3 against a local stack (registry, user, aggregation and dynamic services and the gateway, each on an empty test database):

| Run | Requests | Assertions | Failed |
|---|---|---|---|
| Main collection, `API Gateway` folder | 19 | 36 | 0 |
| Dynamic edit scenario (fresh database) | 11 | 17 | 0 |
| `API Gateway` folder against the gateway **without** the identity-header fix | 19 | 36 | 1 (the forged-headers check) |

## Running the collections

1. Start the system ([running-locally.md](running-locally.md)).
2. Import the collections and the environment template into Postman, select the environment and replace the `<TEST_USERNAME>` and `<TEST_PASSWORD>` placeholders. Use a username that does not exist yet: the *API Gateway* folder registers it. `testUserId` is filled in by the register and login requests.

**Collection Runner (Postman):** open the collection (or one folder) → *Run* → keep the default order → *Run*. The *API Gateway* folder, the seeding collection and the moderation scenario depend on running in order.

**Newman (command line, CI-friendly):**

```bash
# Gateway authentication and route security (needs registry, user service and gateway;
# the last two requests also need the dynamic source and the aggregator)
npx newman run postman/MetaMapa.postman_collection.json \
  -e postman/MetaMapa.local.postman_environment.example.json \
  --folder "API Gateway"

# Moderation scenario (needs the dynamic source on a fresh database)
npx newman run postman/Dynamic-Edit-Scenario.postman_collection.json \
  -e postman/MetaMapa.local.postman_environment.example.json
```

Newman exits with a non-zero code when an assertion fails, so the same commands can run as a step in a CI pipeline once the services are started there.
