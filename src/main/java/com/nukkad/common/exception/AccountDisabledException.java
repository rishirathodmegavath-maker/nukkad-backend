package com.nukkad.common.exception;

import org.springframework.http.HttpStatus;

public class AccountDisabledException extends ApiException {
    public AccountDisabledException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", message);
    }
}
