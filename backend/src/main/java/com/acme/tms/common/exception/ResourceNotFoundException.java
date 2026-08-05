package com.acme.tms.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ResourceNotFoundException extends TmsException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(resource.toUpperCase() + "_NOT_FOUND", HttpStatus.NOT_FOUND, resource + " not found: " + id);
    }

    public ResourceNotFoundException(String code, String message) {
        super(code, HttpStatus.NOT_FOUND, message);
    }
}

