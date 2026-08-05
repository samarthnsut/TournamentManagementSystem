package com.acme.tms.common.api;

import com.acme.tms.common.exception.TmsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TmsException.class)
    ResponseEntity<ProblemDetail> handleTmsException(TmsException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setType(problemType(exception.getCode()));
        problem.setTitle(exception.getStatus().getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", exception.getCode());
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body failed validation.");
        problem.setType(problemType("VALIDATION_FAILED"));
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("errors", fieldErrors(exception));
        return ResponseEntity.badRequest().body(problem);
    }

    private URI problemType(String code) {
        return URI.create("https://docs.acme-tms.com/problems/" + code.toLowerCase().replace('_', '-'));
    }

    private List<Map<String, String>> fieldErrors(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
            .map(this::fieldError)
            .toList();
    }

    private Map<String, String> fieldError(FieldError fieldError) {
        return Map.of(
            "field", fieldError.getField(),
            "message", fieldError.getDefaultMessage() == null ? "is invalid" : fieldError.getDefaultMessage()
        );
    }
}

