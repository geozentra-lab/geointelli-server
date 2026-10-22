# property-service

## Database configuration

`application.yml` defines the shared PostgreSQL connection using `DB_HOST`,
`DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSL_MODE` and
`DB_SSL_ROOT_CERT`. The local and Docker profiles only configure runtime-specific
settings. Start from `.env.example`; keep real credentials in your untracked
`.env` or your deployment's environment.

`APP_PORT` controls the application's port when running on your machine and
Docker's published host port (example: `APP_PORT=8081`). Inside Docker the app
continues to listen on port `8080`.

### Run on your machine

Load `.env` into your IDE or launch environment; Spring Boot does not load it
automatically. Start the app with:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

For local PostgreSQL, use `DB_HOST=localhost`, `DB_PORT=5432`,
`DB_NAME=geozentra`, `DB_SSL_MODE=prefer` and an empty `DB_SSL_ROOT_CERT`.
For RDS, set the database host to its DNS endpoint, supply the database name and
credentials, set `DB_SSL_MODE=verify-full`, and set `DB_SSL_ROOT_CERT` to an
existing AWS RDS CA bundle on your machine. Use forward slashes for Windows
paths. The database must already have the application's PostgreSQL/PostGIS schema.

### Run in Docker

Compose reads `.env` and passes its variables into the app. Its database host is
controlled separately by `DOCKER_DB_HOST` (default `postgres`) and
`DOCKER_DB_PORT` (default `5432`); host-side `DB_HOST=localhost` does not override
the container's database host.

```shell
docker compose up --build
```

To connect the app container to RDS, set `DOCKER_DB_HOST` to the RDS DNS endpoint,
set `DOCKER_DB_PORT` to the RDS port, and set `DB_NAME`, `DB_USER` and
`DB_PASSWORD` to the RDS database values. Set `RDS_CA_BUNDLE_PATH` to the existing
CA bundle on the Docker host, then run:

```shell
docker compose -f docker-compose.yml -f docker-compose.rds.yml up --build
```

The RDS override requires the endpoint and CA path, mounts the certificate
read-only at `/run/certs/rds-global-bundle.pem`, and enforces `verify-full`.
It does not create a missing certificate file. RDS must be reachable from the
container's network. Keycloak still uses the Compose PostgreSQL service.
The base Compose stack uses `postgres:15`; it does not install PostGIS or import
an application schema. Provision those separately before exercising spatial APIs.

### Tests

The `test` profile overrides the connection with in-memory H2 and keeps
PostgreSQL/PostGIS migrations out of the context smoke test. Run the full suite
with `mvn clean verify`; no local or RDS credentials are needed for these tests.
