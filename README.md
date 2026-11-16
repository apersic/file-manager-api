# File manager API

Spring Boot service for a browser file tree. It creates folders and files, lists a folder one page at a time, and reads a node with its ancestors. It does not search or delete yet. Product rules are in [specification.md](specification.md).

## Setup

Install Java 21 and Docker, then open a new terminal. `java -version` should print 21. If it does not, set `JAVA_HOME` to the JDK folder, the one that contains `bin`, and add `%JAVA_HOME%\bin` to `PATH`. Do not put a backslash at the end of `JAVA_HOME`. Eclipse Temurin on this machine is `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`. A terminal that was already open when Java was installed still has the old `PATH`. Close it and open another.

From this directory, start PostgreSQL:

```powershell
docker compose up -d
```

The database, user, and password are `file_manager`, `file_manager_user`, and `file_manager_password`, on port 5432. `application-local.yaml` already uses those values. You do not need a secrets file. Copy `.env.example` to `.env` only when you want different ones.

## Run

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local --spring.docker.compose.enabled=false"
```

`./gradlew` from PowerShell runs the same wrapper. If `JAVA_HOME` is still empty, the wrapper uses the newest Temurin 21 under `C:\Program Files\Eclipse Adoptium`.

Compose is started by hand because Spring Boot's compose integration has failed on the project this one is based on, with `No host port mapping found for container port 5432`. The Docker Compose dependency is still on the development classpath. The flag above keeps boot from starting a second Postgres.

The API listens on port 8080.

```powershell
curl http://localhost:8080/actuator/health
```

A running database returns `{"status":"UP"}`. `GET /api/v1/does-not-exist` returns a 404 JSON body. `GET /actuator/info` returns 403. Stop the app with Ctrl+C. Stop Postgres with `docker compose down`. The database volume is kept.

## Create and browse

The migration seeds a root folder named Home.

```powershell
curl http://localhost:8080/api/v1/nodes/root
curl -X POST http://localhost:8080/api/v1/nodes -H "Content-Type: application/json" -d "{\"parentId\":1,\"kind\":\"FOLDER\",\"name\":\"Designs\"}"
curl "http://localhost:8080/api/v1/nodes?parentId=1&limit=50"
```

`POST` returns 201 and a `Location` header. A second folder or file with the same name in that parent returns 409, and case does not make it a different name. `GET /api/v1/nodes/{id}` adds `ancestors`, root first, without the node itself. List pages are ordered by the folded name. `limit` defaults to 50 and cannot be higher than 100. `nextCursor` is null on the last page.

## Tests and format

`build` runs Spotless, the MockMvc tests, and the Testcontainers tests. Docker must be running. The end-to-end tests cover health, create, browse, duplicate names, and a parent that is a file.

```powershell
.\gradlew.bat clean build
.\gradlew.bat spotlessApply
.\gradlew.bat e2eTest
```

`e2eTest` is the Testcontainers set. `build` already includes it. Docker 29 rejects the API version Testcontainers 1.21 sends by default. The test sets docker-java to API 1.44 before it opens a container.

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

No search endpoint and no delete endpoint. Deleting a folder in the database would remove its children, but the API does not expose that yet.
