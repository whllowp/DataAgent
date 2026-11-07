package com.dataagent.security;

public final class AuthContext {

    private static final ThreadLocal<Principal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static Principal current() {
        return CURRENT.get();
    }

    public static Principal currentOrSystem() {
        Principal principal = CURRENT.get();
        return principal == null ? Principal.system() : principal;
    }

    public static void set(Principal principal) {
        CURRENT.set(principal);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
