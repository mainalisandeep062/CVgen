# CVgen Backend

REST API for CVgen: accounts and OAuth2 login, CV editing, PDF export, CV import, templates, billing (eSewa, Khalti), notifications and an admin area.

Built with Spring Boot 3.4, Java 17, PostgreSQL and Liquibase.

## Run with Docker (recommended)

Only Docker is needed. No JDK or Maven on the host.

1. Create your env file and fill in the values:

   ```bash
   cp .env.example .env
   ```

2. Start the database and the backend:

   ```bash
   docker compose up -d --build
   ```

3. Open the API docs at http://localhost:8080/swagger-ui/index.html

Useful commands:

```bash
docker compose logs -f backend   # follow backend logs
docker compose down              # stop (data is kept)
docker compose down -v           # stop and wipe the database and uploaded files
```

### Ports and overrides

| Variable       | Default | Purpose                         |
|----------------|---------|---------------------------------|
| `APP_PORT`     | `8080`  | Host port for the backend       |
| `DB_HOST_PORT` | `5434`  | Host port for Postgres          |
| `DB_NAME`      | `cvgen` | Database name                   |

Example: `APP_PORT=8090 docker compose up -d`

Compose points the backend at its own `db` container, so `DATABASE_URL` in `.env` is ignored there. `DATABASE_USERNAME` and `DATABASE_PASSWORD` are used for both services.

## Run locally without Docker

Needs JDK 17 and a running PostgreSQL. Set `DATABASE_URL` in `.env` to your database, then:

```bash
./mvnw spring-boot:run
```

The app reads `.env` from the working directory and refuses to start without it.

## Tests

Integration tests use Testcontainers, so Docker must be running.

```bash
./mvnw test
```

## Configuration

Every setting lives in `.env`. See [.env.example](.env.example) for the full list with comments. The required ones:

- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `JWT_SECRET` (at least 64 characters)
- OAuth2 client IDs and secrets for Google, GitHub and LinkedIn
- `MAIL_*` for OTP emails
- `CORS_ALLOWED_ORIGINS` for the frontend origin

A payment gateway with a blank secret key is switched off. `ADMIN_BOOTSTRAP_EMAILS` promotes the listed verified accounts to admin.

## API overview

| Path                   | Area                                |
|------------------------|-------------------------------------|
| `/api/auth`            | Sign up, login, OTP, tokens         |
| `/api/users`           | Current user, profile picture       |
| `/api/cvs`             | CV CRUD, export, import, analysis   |
| `/api/templates`       | CV templates and unlocks            |
| `/api/billing`         | Credit packs, checkout, orders      |
| `/api/notifications`   | User notifications                  |
| `/api/admin/*`         | Users, templates, billing, analytics, audit logs, notifications |

Full details in Swagger UI.

## Project layout

```
src/main/java/io/github/mainalisandeep/cvgen/
  config/       Spring and app configuration
  controller/   REST endpoints
  service/      Business logic
  repository/   JPA repositories
  entity/       JPA entities
  dto/ records/ mapper/   Request/response types and mapping
  security/     JWT, OAuth2, security filters
src/main/resources/
  application.yaml
  changelog/    Liquibase migrations
  templates/    CV and email templates
  fonts/        Fonts embedded in exported PDFs
```
