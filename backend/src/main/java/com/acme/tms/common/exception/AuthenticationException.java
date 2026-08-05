package com.acme.tms.common.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationException extends TmsException {

    public AuthenticationException(String code, String message) {
        super(code, HttpStatus.UNAUTHORIZED, message);
    }
}

