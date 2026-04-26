package com.example.liveklass.common.error;

import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ErrorResponse> handleBusinessException(
                        BusinessException exception,
                        HttpServletRequest request) {
                ErrorCode errorCode = exception.getErrorCode();
                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(ErrorResponse.of(errorCode, exception.getMessage(),
                                                request.getRequestURI()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationException(
                        MethodArgumentNotValidException exception,
                        HttpServletRequest request) {
                String message = exception.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(FieldError::getDefaultMessage)
                                .collect(Collectors.joining(", "));

                return ResponseEntity
                                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                                .body(ErrorResponse.of(ErrorCode.BAD_REQUEST, message,
                                                request.getRequestURI()));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
                        DataIntegrityViolationException exception,
                        HttpServletRequest request) {
                String msg = exception.getMostSpecificCause().getMessage();

                if (msg != null && msg.contains("uq_enrollment_active_per_student_course")) {
                        return ResponseEntity
                                        .status(ErrorCode.DUPLICATE_ENROLLMENT.getHttpStatus())
                                        .body(ErrorResponse.of(
                                                        ErrorCode.DUPLICATE_ENROLLMENT,
                                                        ErrorCode.DUPLICATE_ENROLLMENT.getDefaultMessage(),
                                                        request.getRequestURI()));
                }

                return ResponseEntity
                                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                                .body(ErrorResponse.of(
                                                ErrorCode.BAD_REQUEST,
                                                "Database constraint violation.",
                                                request.getRequestURI()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleUnexpectedException(
                        Exception exception,
                        HttpServletRequest request) {
                return ResponseEntity
                                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                                .body(ErrorResponse.of(
                                                ErrorCode.INTERNAL_SERVER_ERROR,
                                                "Unexpected server error.",
                                                request.getRequestURI()));
        }
}