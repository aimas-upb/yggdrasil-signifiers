package org.hyperagents.yggdrasil.auth.model;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class ACL {
    // Vocabulary terms
    public static final String NS = "http://www.w3.org/ns/auth/acl#";
    private static final SimpleValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

    // Classes
    public static final IRI Access = createIRI("Access");
    public static final IRI Append = createIRI("Append");
    public static final IRI AuthenticatedAgent = createIRI("AuthenticatedAgent");
    public static final IRI Authorization = createIRI("Authorization");
    public static final IRI Control = createIRI("Control");
    public static final IRI Origin = createIRI("Origin");
    public static final IRI Read = createIRI("Read");
    public static final IRI Write = createIRI("Write");
    
    // Properties
    public static final IRI accessControl = createIRI("accessControl");
    public static final IRI accessTo = createIRI("accessTo");
    public static final IRI accessToClass = createIRI("accessToClass");
    public static final IRI agent = createIRI("agent");
    public static final IRI agentClass = createIRI("agentClass");
    public static final IRI agentGroup = createIRI("agentGroup");
    public static final IRI defaultProperty = createIRI("default");
    public static final IRI defaultForNew = createIRI("defaultForNew");
    public static final IRI delegates = createIRI("delegates");
    public static final IRI mode = createIRI("mode");
    public static final IRI origin = createIRI("origin");
    public static final IRI owner = createIRI("owner");

    private static IRI createIRI(String localName) {
        return VALUE_FACTORY.createIRI(NS, localName);
    }
}

