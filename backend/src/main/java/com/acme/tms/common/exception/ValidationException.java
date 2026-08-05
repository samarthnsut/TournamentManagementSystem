package com.acme.tms.common.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends TmsException {

    public ValidationException(String code, String message) {
        super(code, HttpStatus.BAD_REQUEST, message);
    }
}

