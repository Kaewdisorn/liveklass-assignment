package com.example.liveklass.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request."),
    COURSE_NOT_OPEN(HttpStatus.CONFLICT, "Course is not open for enrollment."),
    COURSE_FULL(HttpStatus.CONFLICT, "Course capacity has been reached."),
    DUPLICATE_ENROLLMENT(HttpStatus.CONFLICT, "Active enrollment already exists."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Requested resource was not found.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}