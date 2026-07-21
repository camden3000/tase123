package com.example.crypt;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Registers the Bouncy Castle JCA/JCE provider once for the JVM.
 */
public final class BouncyCastleSupport {
    public static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;

    private BouncyCastleSupport() {
    }

    public static synchronized void ensureProvider() {
        if (Security.getProvider(PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static String providerInfo() {
        ensureProvider();
        var provider = Security.getProvider(PROVIDER_NAME);
        return provider.getName() + " " + provider.getVersionStr();
    }
}
