package com.example.liveklass.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 공통 에러
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized access."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Requested resource was not found."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error."),

    // COURSE 관련 에러
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition."),

    // ENROLLMENT 관련 에러
    DUPLICATE_ENROLLMENT(HttpStatus.CONFLICT, "Active enrollment already exists."),
    COURSE_NOT_OPEN(HttpStatus.CONFLICT, "Course is not open for enrollment."),
    COURSE_FULL(HttpStatus.CONFLICT, "Course capacity has been reached."),
    CANCELLATION_WINDOW_EXPIRED(HttpStatus.CONFLICT, "Cancellation window has expired."),
    LOCK_TIMEOUT(HttpStatus.CONFLICT, "Could not acquire lock; please retry.");

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