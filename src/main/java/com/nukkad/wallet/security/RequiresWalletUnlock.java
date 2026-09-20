package com.nukkad.wallet.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller (or handler method) that may only be used while the member's wallet is
 * unlocked with their PIN. Enforced by {@link WalletUnlockInterceptor} on the handler itself rather
 * than on URL patterns, so no alternate spelling of a path can slip past it, and every method added
 * to an annotated controller is protected by default.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresWalletUnlock {
}
