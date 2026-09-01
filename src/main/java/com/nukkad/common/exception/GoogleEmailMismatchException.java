package com.nukkad.common.exception;

import org.springframework.http.HttpStatus;

public class GoogleEmailMismatchException extends ApiException {
    public GoogleEmailMismatchException(String message) {
        super(HttpStatus.BAD_REQUEST, "GOOGLE_EMAIL_MISMATCH", message);
    }
}
