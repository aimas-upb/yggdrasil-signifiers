package org.hyperagents.yggdrasil.auth.model;

import java.util.Optional;

public enum AuthorizationAccessType {
    READ("Read"),
    WRITE("Write"),
    APPEND("Append"),
    CONTROL("Control");

    private final String name;
    private final String uri;

    AuthorizationAccessType(String name) {
        this.name = name;
        this.uri = ACL.NS + name;
    }

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    // method to get the AuthorizationAccessType from its URI, retuned as an optional
    public static Optional<AuthorizationAccessType> fromUri(String uri) {
        for (AuthorizationAccessType type : AuthorizationAccessType.values()) {
            if (type.getUri().equals(uri)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    // method to get the AuthorizationAccessType from its name, retuned as an optional
    public static Optional<AuthorizationAccessType> fromName(String name) {
        for (AuthorizationAccessType type : AuthorizationAccessType.values()) {
            if (type.getName().equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
    
}