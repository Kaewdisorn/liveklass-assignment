package com.example.liveklass.common.error;

import java.time.OffsetDateTime;

public record ErrorResponse(
        OffsetDateTime timestamp,
        String code,
        String message,
        String path) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(
                OffsetDateTime.now(),
                errorCode.name(),
                message,
                path);
    }
}