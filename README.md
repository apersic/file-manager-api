# File manager API

Spring Boot service for a browser file tree. The process starts, talks to PostgreSQL, and returns the error and health contracts. It does not create, list, search, or delete files yet. Product rules are in [specification.md](specification.md).

## Prerequisites

- Java 21
- Docker, for PostgreSQL and for the end-to-end test

## Debug

From this directory:

```powershell
docker compose up -d
.\gradlew.bat bootRun --args="--spring.profiles.active=local --spring.docker.compose.enabled=false"
```

`application-local.yaml` already points at `localhost:5432` with the user and database from `compose.yaml`. You do not need a secrets file. Copy `.env.example` to `.env` only when you want different values.

Compose is started by hand because Spring Boot's compose integration has failed on the project this one is based on, with `No host port mapping found for container port 5432`. The Docker Compose dependency is still on the development classpath. The flag above keeps boot from starting a second Postgres.

Health check:

```powershell
curl http://localhost:8080/actuator/health
```

A running database returns `{"status":"UP"}`. `GET /api/v1/does-not-exist` returns a 404 JSON body. `GET /actuator/info` returns 403.

## Tests and format

`build` runs Spotless, the MockMvc tests, and the Testcontainers health test. Docker must be running.

```powershell
.\gradlew.bat clean build
.\gradlew.bat spotlessApply
.\gradlew.bat e2eTest
```

`e2eTest` is only the health test. `build` already includes it. Docker 29 rejects the API version Testcontainers 1.21 sends by default. The test sets docker-java to API 1.44 before it opens a container.

## Git hook

The pre-commit hook formats Java with Spotless, compiles, and rejects the commit if formatting changed files.

```powershell
& "C:\Program Files\Git\bin\bash.exe" scripts/setup-hooks.sh
```

`bash` on this machine is the WSL stub and does not run the script. Git Bash does.

Details are in [scripts/README.md](scripts/README.md).

## GitHub Actions

`.github/workflows/gradle-build.yaml` runs `./gradlew clean build` on push and pull request to `main`, then uploads the JaCoCo HTML report.

## Local image

```powershell
.\gradlew.bat bootJar
docker build -t file-manager-api .
```

The image does not start Compose. Postgres has to be running already. Set `DATASOURCE_URL`, `DATASOURCE_USERNAME`, and `DATASOURCE_PASSWORD`.

## Not in this build

No folder or file endpoints. The migration directory is empty on purpose.
