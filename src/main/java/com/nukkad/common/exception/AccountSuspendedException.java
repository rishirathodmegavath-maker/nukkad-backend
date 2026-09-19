package com.nukkad.common.exception;

import org.springframework.http.HttpStatus;

public class AccountSuspendedException extends ApiException {
    public AccountSuspendedException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", message);
    }
}
