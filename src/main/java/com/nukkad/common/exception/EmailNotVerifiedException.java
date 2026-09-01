package com.nukkad.common.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends ApiException {
    public EmailNotVerifiedException(String message) {
        super(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", message);
    }
}
