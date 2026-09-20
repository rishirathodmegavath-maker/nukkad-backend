package com.nukkad.wallet.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WalletWebConfig implements WebMvcConfigurer {

    private final WalletUnlockInterceptor walletUnlockInterceptor;

    public WalletWebConfig(WalletUnlockInterceptor walletUnlockInterceptor) {
        this.walletUnlockInterceptor = walletUnlockInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Every path: the interceptor only acts on handlers carrying @RequiresWalletUnlock.
        registry.addInterceptor(walletUnlockInterceptor);
    }
}
