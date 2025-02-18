package org.hyperagents.yggdrasil.auth.model;

import java.util.Optional;

public enum AuthorizedEntityType {
    AGENT("agent"),
    AGENT_CLASS("agentClass"),
    AGENT_GROUP("agentGroup");

    private final String name;
    private final String property;
    
    AuthorizedEntityType(String name) {
        this.name = name;
        this.property = ACL.NS + name;
    }

    public String getName() {
        return name;
    }

    public String getProperty() {
        return property;
    }

    // method to get the AuthorizedEntityType from its property, retuned as an optional
    public static Optional<AuthorizedEntityType> fromProperty(String property) {
        for (AuthorizedEntityType type : AuthorizedEntityType.values()) {
            if (type.getProperty().equals(property)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
