package com.planet.filemanagerapi.domain;

import org.springframework.http.MediaType;

public enum ExportFormat {
    CSV("csv", MediaType.parseMediaType("text/csv")),
    TXT("txt", MediaType.TEXT_PLAIN),
    XLSX("xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final String extension;
    private final MediaType contentType;

    ExportFormat(String extension, MediaType contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public MediaType contentType() {
        return contentType;
    }
}
