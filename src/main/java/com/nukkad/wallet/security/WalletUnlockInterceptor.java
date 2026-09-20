package com.nukkad.wallet.security;

import com.nukkad.common.exception.ApiException;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.wallet.service.WalletPinService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Refuses any {@link RequiresWalletUnlock} handler unless the request carries a valid wallet unlock token. */
@Component
public class WalletUnlockInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Wallet-Token";

    private final WalletPinService walletPinService;

    public WalletUnlockInterceptor(WalletPinService walletPinService) {
        this.walletPinService = walletPinService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !requiresUnlock(method)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            // Fail closed: a protected handler with no identified member is never served.
            throw new ApiException(HttpStatus.FORBIDDEN, "WALLET_LOCKED", "Enter your wallet PIN to continue.");
        }
        walletPinService.assertUnlocked(user, request.getHeader(HEADER));
        return true;
    }

    private static boolean requiresUnlock(HandlerMethod method) {
        return method.hasMethodAnnotation(RequiresWalletUnlock.class)
                || method.getBeanType().isAnnotationPresent(RequiresWalletUnlock.class);
    }
}
