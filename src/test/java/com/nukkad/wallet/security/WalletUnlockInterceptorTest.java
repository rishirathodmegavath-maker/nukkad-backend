package com.nukkad.wallet.security;

import com.nukkad.common.exception.ApiException;
import com.nukkad.security.AuthenticatedUser;
import com.nukkad.wallet.controller.WalletController;
import com.nukkad.wallet.controller.WalletPinController;
import com.nukkad.wallet.service.WalletPinService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalletUnlockInterceptorTest {

    @RequiresWalletUnlock
    static class ProtectedController {
        public void handler() {
        }
    }

    static class PlainController {
        public void handler() {
        }
    }

    static class MethodLevelController {
        @RequiresWalletUnlock
        public void guarded() {
        }

        public void open() {
        }
    }

    @Mock private WalletPinService walletPinService;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private WalletUnlockInterceptor interceptor() {
        return new WalletUnlockInterceptor(walletPinService);
    }

    private static HandlerMethod handler(Object controller, String method) throws NoSuchMethodException {
        return new HandlerMethod(controller, controller.getClass().getMethod(method));
    }

    private static AuthenticatedUser signIn() {
        AuthenticatedUser user = new AuthenticatedUser("user-1", "a@b.co", Set.of("USER"), 2);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
        return user;
    }

    @Test
    void aProtectedHandlerIsCheckedAgainstTheTokenHeader() throws Exception {
        AuthenticatedUser user = signIn();
        request.addHeader(WalletUnlockInterceptor.HEADER, "the-token");

        assertThat(interceptor().preHandle(request, response, handler(new ProtectedController(), "handler"))).isTrue();

        verify(walletPinService).assertUnlocked(user, "the-token");
    }

    @Test
    void aProtectedHandlerWithNoHeaderIsStillChecked() throws Exception {
        AuthenticatedUser user = signIn();

        interceptor().preHandle(request, response, handler(new ProtectedController(), "handler"));

        verify(walletPinService).assertUnlocked(user, null);
    }

    @Test
    void theServiceRefusalPropagatesSoTheHandlerNeverRuns() throws Exception {
        AuthenticatedUser user = signIn();
        org.mockito.Mockito.doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "WALLET_LOCKED", "locked"))
                .when(walletPinService).assertUnlocked(user, null);

        assertThatThrownBy(() -> interceptor().preHandle(request, response, handler(new ProtectedController(), "handler")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aProtectedHandlerWithNoIdentifiedMemberIsRefused() {
        assertThatThrownBy(() -> interceptor().preHandle(request, response, handler(new ProtectedController(), "handler")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("WALLET_LOCKED"));
        verifyNoInteractions(walletPinService);
    }

    @Test
    void anUnannotatedHandlerIsLeftAlone() throws Exception {
        signIn();

        assertThat(interceptor().preHandle(request, response, handler(new PlainController(), "handler"))).isTrue();

        verifyNoInteractions(walletPinService);
    }

    @Test
    void aMethodLevelAnnotationProtectsOnlyThatMethod() throws Exception {
        AuthenticatedUser user = signIn();
        MethodLevelController controller = new MethodLevelController();

        interceptor().preHandle(request, response, handler(controller, "open"));
        verifyNoInteractions(walletPinService);

        interceptor().preHandle(request, response, handler(controller, "guarded"));
        verify(walletPinService).assertUnlocked(user, null);
    }

    @Test
    void nonHandlerMethodRequestsAreIgnored() {
        assertThat(interceptor().preHandle(request, response, new Object())).isTrue();
        verifyNoInteractions(walletPinService);
    }

    @Test
    void theWalletControllerIsProtectedAndThePinControllerIsNot() {
        // Guards against the annotation being dropped in a refactor: without it every wallet
        // endpoint silently becomes reachable with no PIN at all.
        assertThat(WalletController.class.isAnnotationPresent(RequiresWalletUnlock.class)).isTrue();
        // The PIN endpoints are how the wallet gets unlocked, so they must stay reachable while locked.
        assertThat(WalletPinController.class.isAnnotationPresent(RequiresWalletUnlock.class)).isFalse();
    }
}
