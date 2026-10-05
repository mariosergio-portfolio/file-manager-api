DROP TABLE IF EXISTS import_control_detail;
DROP TABLE IF EXISTS import_control;
DROP TABLE IF EXISTS export_control;
DROP TABLE IF EXISTS outbox_import;
DROP TABLE IF EXISTS outbox_export;

CREATE TABLE import_control (
    import_control_id BIGSERIAL PRIMARY KEY,
    import_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_reference VARCHAR(255) NOT NULL,
    state VARCHAR(20) NOT NULL,
    company_id BIGINT NOT NULL,
    domain VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);

CREATE TABLE import_control_detail (
    import_control_detail_id BIGSERIAL PRIMARY KEY,
    import_id UUID NOT NULL,
    type VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    description VARCHAR(255)
);

-- file_reference left nullable: ExportControl is persisted without it in ExportService.requestExport
CREATE TABLE export_control (
    export_control_id BIGSERIAL PRIMARY KEY,
    export_id UUID NOT NULL,
    file_reference VARCHAR(255),
    state VARCHAR(20) NOT NULL,
    company_id BIGINT NOT NULL,
    domain VARCHAR(20) NOT NULL,
    format VARCHAR(10) NOT NULL,
    columns VARCHAR(1000) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);

CREATE TABLE outbox_import (
    outbox_import_id BIGSERIAL PRIMARY KEY,
    import_id UUID NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

CREATE TABLE outbox_export (
    outbox_export_id BIGSERIAL PRIMARY KEY,
    export_id UUID NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);
