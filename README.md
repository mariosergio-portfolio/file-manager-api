# file-manager-api: File Manager API

REST API service responsible for receiving file import/export requests, orchestrating asynchronous processing via RabbitMQ, and providing status polling and file download endpoints.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Import Flow](#import-flow)
- [Export Flow](#export-flow)
- [API Reference](#api-reference)
- [Domain Model](#domain-model)
- [Configuration](#configuration)
- [Storage Profiles](#storage-profiles)
- [Database](#database)
- [Running Locally](#running-locally)

---

---

## Overview

`file-manager-api` is a Spring Boot service that acts as the **entry point** for all file import and export operations.

1. The client submits a request and receives a **202 Accepted** with a tracking UUID.
2. The API persists a control record and an outbox entry, then publishes an event to RabbitMQ.
3. Downstream workers (`processor-service`, `mailer-service`, `job-outbox`) handle the heavy lifting.
4. The client polls a `/status` endpoint or waits for an email notification.

---

## Architecture

```
+----------------------+
|  Customer / Client   |
+----------+-----------+
           | HTTP  (< 2s response, up to 10k req/s)
           v
+----------------------+     +-------------------+
|   file-manager-api   |---->|   RabbitMQ        |
|  (this service)      |     |  import.exchange  |
|                      |     |  export.exchange  |
+--------+-------------+     +--------+----------+
         |                            |
         v                            v
+------------------+     +-------------------------+
|   PostgreSQL     |     |  processor-service      |
|  import_control  |     |  (Worker: import/export)|
|  export_control  |     +-------------------------+
|  outbox_import   |
|  outbox_export   |     +-------------------------+
+------------------+     |  mailer-service         |
                         |  (sends result emails)  |
+------------------+     +-------------------------+
|  File Storage    |
|  (S3 or local FS)|     +-------------------------+
|  max 5 MB/file   |     |  job-outbox             |
+------------------+     |  (scheduled relay job)  |
                         +-------------------------+
```

---

## Import Flow

The import flow allows clients to upload a CSV file for background processing into the database.

### Sequence

1. Client sends `POST /api/company/{companyId}/file-import` with a multipart file (max **5 MB**) and domain.
2. The API uploads the raw file to the storage backend and receives a file reference key.
3. An `IMPORT_CONTROL` record is persisted with state `PENDING`.
4. An `OUTBOX_IMPORT` row is inserted (transactional outbox pattern).
5. An `ImportEvent` is published to `import.exchange`.
6. The client receives `202 Accepted` with the `importId` — **within 2 seconds**.
7. `processor-service` consumes the event, processes the file, and updates state to `COMPLETED` or `ERROR`.
8. `mailer-service` consumes the result event and sends a summary email to the requester.

**States:** `PENDING` -> `PROCESSING` -> `COMPLETED` | `ERROR`

### Contingency / Outbox Relay

The `job-outbox` service runs on a schedule and queries the `OUTBOX_IMPORT` table for unprocessed records. If the message broker is unavailable it can either directly trigger `processor-service` (contingency mode) or re-publish the event to RabbitMQ.

---

## Export Flow

The export flow allows clients to request a file generation from stored data.

### Sequence

1. Client sends `POST /api/company/{companyId}/file-export` with format, columns, and domain.
2. The API generates an `exportId` (UUID) and persists an `EXPORT_CONTROL` record with state `PENDING`.
3. An `OUTBOX_EXPORT` row is inserted (transactional outbox pattern).
4. An `ExportEvent` is published to `export.exchange`.
5. The client receives `202 Accepted` with the `exportId` — **within 2 seconds**.
6. `processor-service` consumes the event, queries the data, renders the file, stores it, and updates state to `COMPLETED` or `ERROR`.
7. `mailer-service` consumes the result event and sends a notification email with the download URI.
8. The client polls `/status` until `COMPLETED`, then downloads via `/download`.

**Supported export formats:**

| Format | MIME Type | Extension |
|--------|-----------|-----------|
| `CSV`  | `text/csv` | `.csv` |
| `TXT`  | `text/plain` | `.txt` |
| `XLSX` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `.xlsx` |

**States:** `PENDING` -> `PROCESSING` -> `COMPLETED` | `ERROR`

The download endpoint returns `422 Unprocessable Entity` if the export is not yet `COMPLETED`.

### Contingency / Outbox Relay

The `job-outbox` service queries the `OUTBOX_EXPORT` table on a schedule. Pending records are either relayed back to RabbitMQ or forwarded directly to `processor-service` when operating in contingency mode.

---

## API Reference

### Import

#### Submit an import request

```
POST /api/company/{companyId}/file-import
Content-Type: multipart/form-data

Parameters:
  file    (required) - the CSV file to import. Max size: 5 MB.
  domain  (required) - CUSTOMER  (PRODUCT and EMPLOYEE not yet supported — see Known Limitations)
```

**Response `202 Accepted`:**
```json
{ "importId": "550e8400-e29b-41d4-a716-446655440000", "state": "PENDING" }
```

---

#### Poll import status

```
GET /api/company/{companyId}/file-import/{importId}/status
```

**Response `200 OK`:**
```json
{ "importId": "550e8400-e29b-41d4-a716-446655440000", "state": "PROCESSING" }
```

---

### Export

#### Submit an export request

```
POST /api/company/{companyId}/file-export
Content-Type: application/json

Body:
{
  "format":  "CSV" | "TXT" | "XLSX",
  "columns": ["id", "name", "email", "age", "country", "phone"],
  "domain":  "CUSTOMER"  (PRODUCT and EMPLOYEE not yet supported — see Known Limitations)
}
```

**Response `202 Accepted`:**
```json
{ "exportId": "550e8400-e29b-41d4-a716-446655440000", "state": "PENDING" }
```

---

#### Poll export status

```
GET /api/company/{companyId}/file-export/{exportId}/status
```

**Response `200 OK`:**
```json
{ "exportId": "550e8400-e29b-41d4-a716-446655440000", "state": "COMPLETED" }
```

---

#### Download exported file

```
GET /api/company/{companyId}/file-export/{exportId}/download
```

**Response `200 OK`:**
```
Content-Disposition: attachment; filename="export_<exportId>.<ext>"
Content-Type: text/csv | text/plain | application/vnd.openxmlformats...
<file bytes — streamed>
```

Returns `422` if export is not yet `COMPLETED`.

---

### Error responses

| Status | Condition |
|--------|-----------|
| `400` | Invalid request argument |
| `409` | Conflicting state |
| `413` | Upload file exceeds 5 MB limit |
| `422` | Export not ready for download |
| `500` | Unexpected server error |

All error responses follow:
```json
{ "status": 422, "message": "Export is not ready yet", "timestamp": "2026-09-07T10:00:00" }
```

---

## Domain Model

### Enums

| Type | Values | Notes |
|------|--------|-------|
| `FileDomain` | `CUSTOMER`, `PRODUCT`, `EMPLOYEE` | Only `CUSTOMER` is fully implemented. See [Known Limitations](#known-limitations--todo). |
| `ExportFormat` | `CSV`, `TXT`, `XLSX` | All supported. |
| `ImportState` | `PENDING`, `PROCESSING`, `COMPLETED`, `ERROR` | |
| `ExportState` | `PENDING`, `PROCESSING`, `COMPLETED`, `ERROR` | |

### Messaging events

**`ImportEvent`** - published to `import.exchange` on every new import:
```json
{
  "importId":      "uuid",
  "state":         "PENDING",
  "fileReference": "1-550e8400-e29b-41d4-a716-446655440000",
  "companyId":     1,
  "domain":        "CUSTOMER"
}
```

**`ExportEvent`** - published to `export.exchange` on every new export request:
```json
{
  "exportId":      "uuid",
  "state":         "PENDING",
  "companyId":     1,
  "domain":        "CUSTOMER",
  "format":        "CSV",
  "columns":       ["name", "email", "id"]
}
```

---

## Configuration

### `application.yml` key properties

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:planet}
    username: postgres
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
  servlet:
    multipart:
      max-file-size: 5MB       # NFR: max upload file size < 5 MB
      max-request-size: 5MB

rabbitmq:
  exchange:
    import: import.exchange
    export: export.exchange
  queue:
    import: import.queue
    export: export.queue
  routing-key:
    import: import.routing.key
    export: export.routing.key

server:
  port: 8080
```

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `local` | Active Spring profile (`local` or `dev`) |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `planet` | Database name |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `STORAGE_LOCAL_PATH` | `c:/myfolder` | Base path for local filesystem storage (`local` profile) |
| `AWS_S3_BUCKET_NAME` | _(required)_ | S3 bucket name (`dev` profile) |
| `AWS_S3_REGION` | _(required)_ | AWS region (`dev` profile) |

---

## Storage Profiles

The storage backend is selected via Spring profile:

| Profile | Bean | Description |
|---------|------|-------------|
| `local` (default) | `StorageFSystemService` | Reads/writes files from the local filesystem at `${STORAGE_LOCAL_PATH}`. Suitable for development without cloud credentials. |
| `dev` | `StorageS3Service` | Uses AWS S3 via `DefaultCredentialsProvider`. File keys follow the pattern `{companyId}-{uuid}`. |

Both implementations satisfy the `StorageService` interface (`upload` / `download`).

> **NFR note:** Files over 5 MB are rejected at the API layer. If the limit needs to be raised in the future, the recommended pattern is **S3 presigned URLs** — the client uploads directly to S3 and the API is only notified after the upload completes, keeping API response times well under 2 seconds regardless of file size.

---

## Database

Schema is managed by **Flyway** (`classpath:db/migration`). `hibernate.ddl-auto` is set to `validate`, so Flyway owns all schema changes.

### Tables

| Table | Purpose |
|-------|---------|
| `import_control` | One row per import job. Tracks `state`, `domain`, `file_reference`, `original_filename`, `company_id`. |
| `export_control` | One row per export job. Tracks `state`, `format`, `domain`, `columns`, `file_reference`, `company_id`. |
| `outbox_import` | Transactional outbox for import events. Consumed by `job-outbox` for relay/contingency. |
| `outbox_export` | Transactional outbox for export events. Consumed by `job-outbox` for relay/contingency. |
| `import_control_detail` | Per-row validation results for an import job (type and description of each error or warning). |

State columns enforce a `CHECK` constraint: `PENDING`, `PROCESSING`, `COMPLETED`, `ERROR`.

### Flyway migrations

| File | Description |
|------|-------------|
| `V1__init_schema.sql` | Creates all tables with constraints and indexes. |

---

## Running Locally

### Prerequisites

- Java 25
- Maven 3.9+
- PostgreSQL running on `localhost:5432`, database `planet`
- RabbitMQ running on `localhost:5672`

### Start the service

```bash
./mvnw spring-boot:run
```

The default profile is `local`, which uses the local filesystem for storage. No AWS credentials are required.

### Build

```bash
./mvnw clean package
```

### Swagger UI

Once running, the OpenAPI documentation is available at:

```
http://localhost:8080/swagger-ui.html
```
