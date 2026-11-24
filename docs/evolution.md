# Evolution across the six deliveries

The project was built in six deliveries over the 2025 academic year. For each one, this page summarizes what the course statement asked for and what the repository shows was built. Dates are approximate and come from the git history (first commit April 2025, last commit February 2026). Screenshots of the diagrams submitted for deliveries 1, 2, 3 and 6 were used as additional evidence.

## Delivery 1: Architecture and object modeling (I)

**Asked:** a static source that reads events from CSV files without loading them into memory, collections with configurable membership criteria, browsing and filtering events, anonymous and identified contributors, and deletion requests with a justification of at least 500 characters. Deliverables: domain model, architecture diagrams, use cases.

**Built:** the initial domain model and use-case diagram, a CSV-based static source, collections with membership criteria, and deletion requests that enforce the 500-character minimum.

## Delivery 2: Architecture and object modeling (II)

**Asked:** a dynamic source for citizen contributions (anonymous or registered, editable for one week, reviewed by admins), proxy sources for the course's external API and for other MetaMapa instances, an aggregation service, an hourly collection refresh, and automatic rejection of spam deletion requests. Git Flow became mandatory.

**Built:** the code was split into separate services for the static, dynamic and proxy sources and the aggregator (May 2025). The aggregator pulled events from each source (`GET /events`), and source services could be registered in it at runtime. A spam detector was added that combines word repetition with similarity to earlier requests for the same event.

## Delivery 3: Integration, object modeling (III) and UX/UI

**Asked:** a REST API (administrative operations, plus a public API for other instances), consensus algorithms (multiple mentions, simple majority, absolute) run at low-load hours, curated and unrestricted navigation modes, and UI sketches.

**Built:** the REST API for collections, events, sources and deletion requests, class diagrams per service, and the three consensus algorithms behind a nightly scheduler. The team designed a webhook flow in which the aggregator registers with each source. It became the **push-based integration** (August 2025), replacing the polling of delivery 2.

## Delivery 4: Persistence and data analysis

**Asked:** persistence with an ORM and a separate schema per service, normalization of key values, a statistics service, support for images and videos, CSV export of statistics, and (bonus) free-text search.

**Built:** JPA/Hibernate with MySQL, one schema per service (August 2025). **Location normalization** through the GeoRef API (September–October 2025). The **statistics service** (October 2025). Entity-relationship diagrams per service.

**Not in this repository:** CSV export of statistics and free-text search. Image and video support arrived later (January–February 2026).

## Delivery 5: Web MVC architecture and user interface

**Asked:** a decoupled, server-side rendered web client using a template engine: landing page, anonymous browsing, interactive map, registration and login, filters and navigation modes, event submission and editing, and an admin panel (collections, sources, consensus, moderation, CSV import). Bonus: single sign-on with social login.

**Built:** the Spring MVC + Thymeleaf web client (from September 2025): landing, explore page (feed, map and filters), collections, map, "my events", and admin pages for collections, sources, CSV import, pending submissions, deletion requests, edit requests and statistics. The web client consumes the back end exclusively through its REST APIs.

**Not in this repository:** single sign-on / social login.

## Delivery 6: Deployment, observability and security

**Asked:** a public cloud deployment, observability (metrics, traces, logs), health monitoring with auto-restart, rate limiting, IP allow and deny lists, gRPC or GraphQL. Bonus: adapt the services to a microservice architecture with a service registry and an API Gateway.

**Built:**

- **API Gateway and Eureka service registry** (October 2025), so every request now enters through one point.
- **Authentication:** a dedicated user service issuing RS256 JWTs, verification and role rules in the gateway, and sessions in the web client (December 2025).
- **Media storage** in S3-compatible object storage, with MinIO in development (January–February 2026).
- A `deploy` branch with a Dockerfile per service.

**Not in this repository:** observability, rate limiting, IP allow/deny lists and gRPC/GraphQL.

## Timeline from git

| Period | Milestone |
|---|---|
| April – May 2025 | First commits; static source; dynamic, proxy and aggregation services |
| August 2025 | Consensus algorithms; push-based notifications; JPA persistence |
| September – October 2025 | Location normalization; web client; statistics service; API Gateway + Eureka |
| December 2025 | User service, RS256 tokens, gateway authentication filter |
| January – February 2026 | Object storage (MinIO), media in the UI, pagination and final fixes |
