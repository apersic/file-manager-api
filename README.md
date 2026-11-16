# File manager API

Spring Boot service for a browser file tree. It creates folders and files, lists a folder one page at a time, reads a node with its ancestors, searches by exact name or prefix, and deletes a file or a folder. Product rules are in [specification.md](specification.md).

## Setup

Install Java 21 and Docker, then open a new terminal. `java -version` should print 21. If it does not, set `JAVA_HOME` to the JDK folder, the one that contains `bin`, and add `%JAVA_HOME%\bin` to `PATH`. Do not put a backslash at the end of `JAVA_HOME`. Eclipse Temurin on this machine is `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`. A terminal that was already open when Java was installed still has the old `PATH`. Close it and open another.

From this directory, start PostgreSQL:

```powershell
docker compose up -d
```

The database, user, and password are `file_manager`, `file_manager_user`, and `file_manager_password`, on port 5432. `application-local.yaml` already uses those values. You do not need a secrets file. Copy `.env.example` to `.env` only when you want different ones.

## Debug

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local --spring.docker.compose.enabled=false"
```

`./gradlew` from PowerShell runs the same wrapper. If `JAVA_HOME` is still empty, the wrapper uses the newest Temurin 21 under `C:\Program Files\Eclipse Adoptium`.

The `local` profile turns SQL logging on and points at the Compose database. Compose is started by hand because Spring Boot's compose integration has failed on the project this one is based on, with `No host port mapping found for container port 5432`. The Docker Compose dependency is still on the development classpath. The flag above keeps boot from starting a second Postgres.

To attach a debugger, add `--debug-jvm`. The process listens on port 5005 and waits until the debugger connects.

```powershell
.\gradlew.bat bootRun --debug-jvm --args="--spring.profiles.active=local --spring.docker.compose.enabled=false"
```

The API listens on port 8080.

```powershell
curl http://localhost:8080/actuator/health
```

A running database returns `{"status":"UP"}`. `GET /api/v1/does-not-exist` returns a 404 JSON body. `GET /actuator/info` returns 403. Stop the app with Ctrl+C. Stop Postgres with `docker compose down`. The database volume is kept.

## Jar

The jar does not start Compose. Postgres has to be running already, on this machine or another one. `bootRun` is the debug path. This path uses `application.yaml` only, so SQL logging stays off unless you set `SHOW_SQL`.

```powershell
.\gradlew.bat bootJar
$env:DATASOURCE_URL = "jdbc:postgresql://localhost:5432/file_manager"
$env:DATASOURCE_USERNAME = "file_manager_user"
$env:DATASOURCE_PASSWORD = "file_manager_password"
java -jar build\libs\file-manager-api-0.0.1-SNAPSHOT.jar
```

Change `DATASOURCE_URL` when Postgres is not on localhost. Then run [scripts/demo.ps1](scripts/demo.ps1). It checks health, creates a folder and a file, lists, searches, and deletes.

## Create and browse

The migration seeds a root folder named Home.

```powershell
curl http://localhost:8080/api/v1/nodes/root
curl -X POST http://localhost:8080/api/v1/nodes -H "Content-Type: application/json" -d "{\"parentId\":1,\"kind\":\"FOLDER\",\"name\":\"Designs\"}"
curl "http://localhost:8080/api/v1/nodes?parentId=1&limit=50"
```

`POST` returns 201 and a `Location` header. A second folder or file with the same name in that parent returns 409, and case does not make it a different name. `GET /api/v1/nodes/{id}` adds `ancestors`, root first, without the node itself. List pages are ordered by the folded name. `limit` defaults to 50 and cannot be higher than 100. `nextCursor` is null on the last page.

## Search

```powershell
curl "http://localhost:8080/api/v1/nodes/search?q=des&match=PREFIX&limit=10"
curl "http://localhost:8080/api/v1/nodes/search?q=Designs&match=EXACT&parentId=1"
```

Leave out `parentId` to search the whole tree. Set it to search only the direct children of that folder. `match` is `EXACT` or `PREFIX`. A prefix is the start of the folded name. `%` and `_` are ordinary characters. Results include files and folders, and each row has `kind`. An unknown folder is 404. A file id used as `parentId` is 409. No matches is an empty page, not an error.

## Query plans

These plans were measured on PostgreSQL 17.4 with 322 rows: Home, 80 folders, and 241 files, after `ANALYZE`, limit 10. The figures are `EXPLAIN ANALYZE` execution time for the SQL the repository sends, with the bound values visible to the planner.

| Query | Plan | Time |
| --- | --- | --- |
| Exact name inside one folder | Index scan on `uq_node_sibling_name` | 0.058 ms |
| Prefix inside one folder | Index scan on `uq_node_sibling_name`, then an incremental sort | 0.052 ms |
| Exact name across the tree | Index scan on `node_pkey` with a name filter, stopped after 10 rows | 0.037 ms |
| Prefix across the tree | Index scan on `ix_node_name_key` | 0.015 ms |

Exact search across the tree can use the primary key because every match shares one folded name, so id order is the requested order. A pooled connection that has prepared the same statement several times keeps a generic plan. On this table that generic plan used `ix_node_name_key` for a folder exact search (0.131 ms) and a sequential scan for a tree-wide exact search (0.086 ms). The plan test turns sequential and bitmap scans off and fails unless a folder search uses `uq_node_sibling_name`. A tree-wide prefix uses `ix_node_name_key` in this measurement. On this 322-row table the tree-wide exact query still used `node_pkey` with those scans off, because ten matching rows are at the start of the primary key.

## Delete

```powershell
curl -X DELETE http://localhost:8080/api/v1/nodes/4
```

A successful delete returns 204 and an empty body. Deleting a folder removes that folder and everything inside it in one database transaction. A missing id returns 404, and deleting the same id again returns 404. Deleting Home returns 409. A direct SQL delete of Home is rejected as well.

A large folder stays one transaction. That delete can hold locks for seconds. There is no background job.

## Pool

The jar uses a Hikari pool named `file-manager`. The defaults are 10 connections, 2 idle, a 10 second wait for a connection, 10 minutes idle, and a 30 minute max lifetime. `minimum-idle` has to stay at or below `maximum-pool-size`. Override them with `HIKARI_MAXIMUM_POOL_SIZE`, `HIKARI_MINIMUM_IDLE`, `HIKARI_CONNECTION_TIMEOUT`, `HIKARI_IDLE_TIMEOUT`, and `HIKARI_MAX_LIFETIME`. The timeout values are milliseconds. `.env.example` lists the same defaults.

## Tests and format

`check` runs Spotless, the MockMvc tests, and the Testcontainers tests. Docker must be running. The end-to-end tests cover health, create, browse, search, delete, duplicate names, and a parent that is a file. `build` includes `check`. JaCoCo writes an HTML report to `build/jacocoHtml` when `jacocoTestReport` runs. That report stays informational. `check` does not fail on a coverage number.

```powershell
.\gradlew.bat check
.\gradlew.bat clean build
.\gradlew.bat spotlessApply
.\gradlew.bat e2eTest
```

`e2eTest` is the Testcontainers set. Docker 29 rejects the API version Testcontainers 1.21 sends by default. The test sets docker-java to API 1.44 before it opens a container.

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
docker run --rm -p 8080:8080 -e DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/file_manager -e DATASOURCE_USERNAME=file_manager_user -e DATASOURCE_PASSWORD=file_manager_password file-manager-api
```

The image does not start Compose and does not contain the Compose file. Postgres has to be running already. `host.docker.internal` reaches a database published on the host. Set `DATASOURCE_URL`, `DATASOURCE_USERNAME`, and `DATASOURCE_PASSWORD`.
