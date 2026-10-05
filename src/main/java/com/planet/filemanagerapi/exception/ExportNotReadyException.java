package com.planet.filemanagerapi.exception;

import com.planet.filemanagerapi.domain.ExportState;

import java.util.UUID;

public class ExportNotReadyException extends RuntimeException {

    public ExportNotReadyException(UUID exportId, ExportState currentState) {
        super("Export is not ready for download. exportId=" + exportId + ", currentState=" + currentState);
    }
}
