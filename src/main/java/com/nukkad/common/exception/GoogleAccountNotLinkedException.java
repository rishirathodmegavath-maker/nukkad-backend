package com.nukkad.common.exception;

import org.springframework.http.HttpStatus;

public class GoogleAccountNotLinkedException extends ApiException {
    public GoogleAccountNotLinkedException(String message) {
        super(HttpStatus.NOT_FOUND, "GOOGLE_ACCOUNT_NOT_LINKED", message);
    }
}
