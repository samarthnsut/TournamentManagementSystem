package com.acme.tms.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends TmsException {

    public ConflictException(String code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }
}

