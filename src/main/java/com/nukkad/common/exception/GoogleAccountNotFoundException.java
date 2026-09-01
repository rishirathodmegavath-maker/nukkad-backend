package com.nukkad.common.exception;

import org.springframework.http.HttpStatus;

public class GoogleAccountNotFoundException extends ApiException {
    public GoogleAccountNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "GOOGLE_NO_ACCOUNT", message);
    }
}
