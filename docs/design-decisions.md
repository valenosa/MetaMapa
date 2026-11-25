# Design decisions

Short architecture decision records (ADRs) for the main decisions. They are based on the team's design document (including the drafts written for each service), the course statement and the code. Where a draft and the code disagree, this document follows the code.

---

## ADR-01: One service per source type, plus an aggregator

**Context.** The course asks for a system that institutions can install and run in a decentralized way. It must combine static datasets, citizen contributions and external APIs.

**Decision.** Each source type is its own service (`static-source`, `dynamic-source`, `proxy-source`), and an `aggregation-service` combines them. Every service follows the same layering (controllers → services → repositories) and owns its own database schema.

**Alternatives.** A single application with modules was simpler to deploy, but an institution could not run only the parts it needs, and a failure in one source would affect the rest.

**Consequences.** Services can be developed, deployed and scaled independently, and an instance can run several sources of the same type. The price is network communication and the need for a shared contract between sources and aggregator.

---

## ADR-02: Sources push data to the aggregator

**Context.** In the second delivery the aggregator pulled events from each source (`GET /events`). The aggregator had to poll, sources were queried even when nothing had changed, and adding a source meant changing the aggregator.

**Decision.** A publisher/subscriber model. The aggregator subscribes once (`POST /subscribers` with a callback URL). Each source then pushes its existing sources and events, and afterwards notifies new events (`POST /events`) and updated events (`PUT /events`). Each source implements this with an Observer (`Publisher` / `ISubscriber`).

**Alternatives.** Periodic polling was easier to implement but inefficient and slow to reflect changes. A message broker (Kafka, RabbitMQ) was out of scope for the course.

**Consequences.** Near-real-time updates without polling, and new sources can be added without changing the aggregator. Several aggregators can subscribe to the same source. Subscriptions are not persisted on the source side, so an aggregator must subscribe again after a source restarts.

---

## ADR-03: API Gateway and service registry

**Context.** Before this change, the aggregator also acted as an entry point and forwarded requests to other services. Tokens had to be validated both there and in the target service.

**Decision.** Add an API Gateway (Spring Cloud Gateway) as the single entry point, and Eureka for service discovery (`lb://SERVICE-NAME`). The gateway validates tokens and roles centrally, removes the `/api/v1` prefix and passes the user's identity as `X-User-Id` / `X-User-Name` headers. Only the gateway sets those headers: any copies sent by the client are dropped.

**Alternatives.** Keeping the aggregator as the router, or validating tokens in every service. Both spread security logic and coupled unrelated responsibilities.

**Consequences.** One place to read and change access rules ([`routes.yml`](../backend/api-gateway/src/main/resources/routes.yml)). Services stay simple and never parse tokens. Service addresses are discovered rather than hard-coded. The gateway is now a component every request depends on.

---

## ADR-04: RS256 tokens issued by a dedicated user service

**Context.** Tokens are created in one place (`user-service`) and verified in another (the gateway).

**Decision.** Sign JWTs with **RS256** (asymmetric) instead of HS256 (shared secret). Only `user-service` holds the private key; the gateway gets the public key. Keys come from environment variables (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`), with a fallback in the properties file for development. User entities stay lean, and other services reference users by a public UUID instead of internal ids or usernames.

**Alternatives.** HS256 would have meant distributing the same secret to every verifier; any of them could then mint tokens.

**Consequences.** A leaked public key cannot be used to forge tokens. Deployments configure keys without code changes. Details in [authentication.md](authentication.md).

---

## ADR-05: Sessions in the web tier, tokens in the back end

**Context.** The web client is server-side rendered (Thymeleaf), so the browser talks only to the web server.

**Decision.** The browser keeps a session cookie with the web server. The web server stores the access and refresh tokens in the HTTP session and sends them to the gateway. A single API client (`WebApiCallerService`) refreshes expired tokens and retries the call.

**Alternatives.** Sending the JWT to the browser (cookie or local storage). This exposes tokens to the client and gains nothing for a server-rendered application.

**Consequences.** Tokens never reach the browser, the back end stays stateless and can scale horizontally, and token renewal is invisible to the user.

---

## ADR-06: Coarse roles now, RBAC later

**Context.** The domain has two kinds of users: contributors and administrators.

**Decision.** Two non-overlapping roles (`ADMIN`, `CONTRIBUTOR`) carried in the access token and checked by the gateway. Access tokens are short-lived (5 minutes in the example configuration), so role changes take effect quickly.

**Alternatives.** Role-based access control with fine-grained permissions. It follows the principle of least privilege and is more flexible, but it takes longer to set up while the features are still changing.

**Consequences.** Delivered quickly and is fully functional. Roles have to be repeated in the gateway and the web client, and they are coarse. The documented next step is RBAC: roles become sets of permissions managed in `user-service`.

---

## ADR-07: Location normalization in parallel batches against GeoRef

**Context.** Sources report events with coordinates only. To filter and compute statistics by province, every event needs a normalized location (province, department, municipality), and the GeoRef API accepts at most 1,000 points per request.

**Decision.** When events reach the aggregator, `NormalizationService` groups them by coordinate. `LocationAPI`, an adapter for GeoRef, splits the coordinates into batches (`batch.size`, 1,000) and sends them in parallel on a fixed pool of `thread.count` threads, with a tuned connection pool and timeouts. Provinces, departments and municipalities are deduplicated in memory and saved only if they do not exist yet.

**Alternatives.** The team's draft planned a separate normalization microservice, notified asynchronously through a Strategy so it could later move to a queue. Normalization currently runs synchronously inside the aggregator.

**Consequences.** Bulk imports are normalized in a few parallel requests instead of one per event, and the adapter lets the geocoding provider change without touching business logic. If GeoRef is unavailable, ingesting that batch fails; the code marks this as a pending improvement.

---

## ADR-08: Media in S3-compatible object storage

**Context.** Contributors attach images and videos. The first idea was to store files on the server's file system under UUID names.

**Decision.** Store media in **object storage through the S3 API** (AWS SDK v2). MinIO is used in development, and any S3-compatible provider can be used in production by changing the configuration. Files are stored under `events/<uuid>`, while the original name and MIME type go to the SQL database. Uploads are limited by a MIME-type whitelist (validated at startup) and a maximum number of files per event. If saving the event fails, the uploaded files are deleted.

**Alternatives.** The file system. It is hard to grow or scale horizontally, it behaves differently in development and in production, and container volumes would be needed to keep the files. For the whitelist, the team chose strings in configuration over a Java enum: types can be added without recompiling, at the cost of typos, which are caught at startup.

**Consequences.** The application does not depend on a storage provider. Storage scales independently. Files are streamed through the gateway for now; the planned improvement is pre-signed URLs, so the storage serves the files directly.

---

## ADR-09: Media URLs built in the back end by a Strategy

**Context.** Clients need a URL for each media file, and that URL depends on how files are served (through the gateway today, perhaps pre-signed storage URLs tomorrow).

**Decision.** URLs are generated when output DTOs are built, by an injected `UrlGenerationStrategy` (currently `ApiGatewayUrlStrategy`: gateway URL + `/api/v1/multimedia/` + object key).

**Alternatives.** Building URLs in the web client hard-codes gateway paths there, and every consumer would have to repeat that logic.

**Consequences.** Switching to pre-signed URLs or a CDN means adding a strategy, not changing existing code (open/closed principle).

---

## ADR-10: Reusable, typed collection criteria

**Context.** Collections include events that match a criterion: a combination of conditions on title, category and date range. Criteria and conditions repeat across collections.

**Decision.** A `CollectionCriteria` entity groups `Condition`s and is referenced from `Collection` (`@ManyToOne`). `Condition` is an abstract class with one subclass per condition type, each stored with properly typed columns. Equal conditions and criteria are reused instead of duplicated. They are saved through repositories rather than cascades, so deduplication can be validated.

**Alternatives.** A single table where every condition value is a string, parsed according to its type. It was rejected because it prevents filtering in the database, loses type safety and makes extensions harder.

**Consequences.** Queries can filter directly in SQL, data keeps its types, and a new condition type is a new subclass.

---

## ADR-11: Statistics in a separate service fed by incremental pulls

**Context.** The course asks for a statistics service that connects to the aggregator, recomputes statistics periodically and shows them in the UI.

**Decision.** `statistics-service` has its own schema. On a schedule it pulls only what changed since its last run (`fromUpdatedDate`) from the aggregator's REST API, then computes top categories, top provinces by category and by collection, and the busiest hours by category.

**Alternatives.** The team's draft considered computing statistics inside the aggregator, which would query its database directly with no duplicated model. The code implements the separate service.

**Consequences.** Heavy computations do not load the aggregator, and the service can evolve on its own. In exchange, part of the model is duplicated and statistics are only as fresh as the last scheduled pull.
