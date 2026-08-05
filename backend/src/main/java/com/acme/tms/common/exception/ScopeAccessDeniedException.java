package com.acme.tms.common.exception;

import org.springframework.http.HttpStatus;

public class ScopeAccessDeniedException extends TmsException {

    public ScopeAccessDeniedException(String code, String message) {
        super(code, HttpStatus.FORBIDDEN, message);
    }
}

