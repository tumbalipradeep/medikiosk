# MediKiosk

**Patient Case-Taking Platform** — SIH26047

A modular-monolith web application for taking patient cases at a medical kiosk.

## Technology Stack

- Java 25
- Spring Boot 4.1.1
- Maven
- PostgreSQL 17+
- Thymeleaf
- Flyway
- Spring Security
- Bootstrap
- Vanilla JavaScript
- Modular monolith architecture

## Checkpoint Status

**Checkpoint 1 — Foundation (complete).** This repository establishes the application shell:
startup, home page, health endpoint, PostgreSQL + Flyway wiring, Bootstrap integration, and
the modular package skeleton. No feature modules (auth, clinical, AI, etc.) are implemented yet.

## Prerequisites

- JDK 25
- Maven 3.9+
- PostgreSQL 17+ running locally

## Local Setup

### 1. Create the PostgreSQL database and user

```bash
sudo -u postgres psql -c "CREATE USER medikiosk WITH PASSWORD 'medikiosk';"
sudo -u postgres psql -c "CREATE DATABASE medikiosk OWNER medikiosk;"
```

### 2. Configure environment variables (optional; defaults shown)

```bash
export DB_URL="jdbc:postgresql://localhost:5432/medikiosk"
export DB_USERNAME="medikiosk"
export DB_PASSWORD="medikiosk"
export SERVER_PORT="8080"
```

Defaults are already set in `application.yml`, so a stock setup needs no exports.

### 3. Run tests

```bash
mvn clean test
```

### 4. Start the application

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

### Local profile (no PostgreSQL required)

For a quick start without a local PostgreSQL instance (uses an in-memory PostgreSQL-compatible
H2 database), run:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Verify

- Home page: http://localhost:8080 — shows **MediKiosk / Patient Case-Taking Platform / Foundation Ready / Application Status: UP**
- Health endpoint: http://localhost:8080/actuator/health

## Project Structure

```
src/main/java/in/devmedi/kiosk/
  MediKioskApplication.java   # Entry point
  config/                     # Security and general configuration
  core/                       # Shared base domain
  health/                     # Health indicator
  home/                       # Home controller
  module/                     # Feature modules (placeholders for later checkpoints)
    auth/                     # Authentication (later)
    patient/                  # Patient registration (later)
    clinical/                 # Clinical case taking (later)
    ai/ ocr/ voice/ ayush/ redflags/ appointment/ physician/ fhir/ abdm/ his/ document/
src/main/resources/
  application.yml             # PostgreSQL / Flyway configuration
  templates/home.html         # Home page
  static/css/app.css          # Application CSS
  static/js/app.js            # Application JavaScript
  db/migration/               # Flyway migrations
```

## Notes

- `spring.jpa.hibernate.ddl-auto=validate` + Flyway together manage the schema; no auto DDL.
- The `local` and `test` profiles use an in-memory PostgreSQL-compatible H2 database for
  development and testing; PostgreSQL remains the production datasource.
