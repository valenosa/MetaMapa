# Running MetaMapa locally

Every service is a plain Spring Boot application. There is no Docker setup on `main`; the `deploy` branch has one Dockerfile per service, but no compose file.

## 1. Requirements

- **JDK 17 or newer.** Maven is optional: the repository includes the Maven wrapper (`backend/mvnw`, `frontend/mvnw`).
- **MySQL 8** on `localhost:3306`, with a user that can create databases. Each service creates its own schema on startup (`createDatabaseIfNotExist=true`).
- **MinIO** (or any S3-compatible storage) on `localhost:9000`, used by `dynamic-source` for event media. For example, run `minio server <data-dir> --console-address :9001`, then use the console to create a bucket named `metamapa-media` and an access key.
- **Internet access** for the GeoRef API (location normalization) and the course's natural-disasters API (proxy source).

## 2. Configuration

Each service has a `src/main/resources/application.properties.example`. Copy it to `application.properties` in the same folder; that file is git-ignored, so secrets never reach the repository. Then replace the placeholders:

| Service | Placeholders |
|---|---|
| `user-service` | `<DB_USERNAME>`, `<DB_PASSWORD>`, `<JWT_PRIVATE_KEY>`, `<JWT_PUBLIC_KEY>` |
| `api-gateway` | `<JWT_PUBLIC_KEY>` |
| `aggregation-service`, `static-source`, `statistics-service` | `<DB_USERNAME>`, `<DB_PASSWORD>` |
| `dynamic-source` | `<DB_USERNAME>`, `<DB_PASSWORD>`, `<MINIO_ACCESS_KEY>`, `<MINIO_SECRET_KEY>` |
| `proxy-source` | `<NATURAL_DISASTERS_API_EMAIL>`, `<NATURAL_DISASTERS_API_PASSWORD>` (credentials provided by the course) |
| `server-registry`, `frontend` | nothing to fill in |

The JWT keys can also be passed as environment variables (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`), which take precedence over the file.

### Generating the RS256 key pair

The services expect Base64-encoded DER keys without PEM headers: PKCS#8 for the private key and X.509 for the public key. With OpenSSL (also bundled with Git for Windows):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem

# One-line values for JWT_PRIVATE_KEY and JWT_PUBLIC_KEY
grep -v -- "-----" private.pem | tr -d '\r\n'; echo
grep -v -- "-----" public.pem  | tr -d '\r\n'; echo
```

Use the private key only in `user-service`, and the public key in both `user-service` and `api-gateway`. Do not commit the generated `.pem` files.

### Database schema mode

The example files use `spring.jpa.hibernate.ddl-auto=create-drop` (the team's development setting), so **tables are recreated on startup and dropped on shutdown**. `user-service` uses `update`, so accounts survive restarts. Change the others to `update` if you want to keep their data.

## 3. Start-up order

Start each service from its folder in a separate terminal (on Windows, use `..\mvnw.cmd` / `.\mvnw.cmd`):

```bash
cd backend/server-registry && ../mvnw spring-boot:run     # 1. Eureka, :8761
cd backend/user-service && ../mvnw spring-boot:run        # 2. Then the services, in any order
cd backend/static-source && ../mvnw spring-boot:run
cd backend/dynamic-source && ../mvnw spring-boot:run
cd backend/proxy-source && ../mvnw spring-boot:run
cd backend/aggregation-service && ../mvnw spring-boot:run
cd backend/statistics-service && ../mvnw spring-boot:run
cd backend/api-gateway && ../mvnw spring-boot:run         # 3. Gateway, :8086
cd frontend && ./mvnw spring-boot:run                     # 4. Web client, :8085
```

The Eureka dashboard at <http://localhost:8761> lists the registered services. All of them appear there except `proxy-source` and the web client, which do not register.

## 4. Connect the sources to the aggregator

The aggregator only receives data from sources it has subscribed to. Register each source service once, directly on the aggregator (this endpoint is not exposed through the gateway):

```bash
curl -X POST http://localhost:8080/source-clients -H "Content-Type: application/json" -d '{"url": "http://localhost:8083"}'  # static
curl -X POST http://localhost:8080/source-clients -H "Content-Type: application/json" -d '{"url": "http://localhost:8081"}'  # dynamic
curl -X POST http://localhost:8080/source-clients -H "Content-Type: application/json" -d '{"url": "http://localhost:8082"}'  # proxy
```

Each source then pushes its existing data and keeps notifying new events. The `Seed` Postman collection automates this step for the proxy and static sources and also creates sample collections ([testing.md](testing.md)). Subscriptions are kept in memory on the source side, so register a source again after restarting it.

## 5. Create an admin user

New accounts are always created as `CONTRIBUTOR`. Register a user from the web client (<http://localhost:8085/register>), then grant the admin role in MySQL:

```sql
USE metamapa_users;
INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE username = '<your-username>';
```

Log out and in again so the new access token includes the role. Admin pages (collections, sources, CSV import, moderation, statistics) then become available.

## 6. Load data

- **CSV:** as an admin, upload a dataset from the import page (or `POST /api/v1/sources` with a `csvfile` part through the gateway). Sample files are in `backend/static-source/src/main/resources/CSV/`. Uploaded files are stored under `~/metamapa/static-sources/csv`, and the import job runs every minute.
- **Citizen events:** submit them from the web client, then accept them as an admin. Only accepted events are pushed to the aggregator.
- **Proxy:** once subscribed, the proxy source imports from the course API on its schedule (hourly by default).

## Troubleshooting

- **A media upload is rejected:** its MIME type must be listed in `app.event.allowed-mime-types` (`dynamic-source`). The service refuses to start if an entry in that list is not a valid MIME type.
- **Statistics are empty:** `statistics-service` pulls from the aggregator on a schedule (every 2 minutes by default) and needs `aggregation-service.url` to point at the aggregator (`http://localhost:8080`).
- **401 or 403 from the gateway:** see the route rules in [authentication.md](authentication.md).
