package org.hyperagents.yggdrasil.auth.model;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class CASHMERE {
    // Namespaces
    public static final String CASHMERE_NS = "https://aimas.cs.pub.ro/ont/cashmere#";
    public static final String ACL_NS = "http://www.w3.org/ns/auth/acl#";
    public static final String CONSERT_CORE_NS = "http://pervasive.semanticweb.org/ont/2017/07/consert/core#";

    // Classes
    public static final IRI Access = iri(ACL_NS, "Access");
    public static final IRI Append = iri(ACL_NS, "Append");
    public static final IRI AuthenticatedAgent = iri(ACL_NS, "AuthenticatedAgent");
    public static final IRI Authorization = iri(ACL_NS, "Authorization");
    public static final IRI Control = iri(ACL_NS, "Control");
    public static final IRI Origin = iri(ACL_NS, "Origin");
    public static final IRI Read = iri(ACL_NS, "Read");
    public static final IRI Write = iri(ACL_NS, "Write");
    public static final IRI ContextAuthorizedResource = iri(CASHMERE_NS, "ContextAuthorizedResource");
    public static final IRI ContextBasedAccessAuthorization = iri(CASHMERE_NS, "ContextBasedAccessAuthorization");
    public static final IRI ContextBasedAccessCondition = iri(CASHMERE_NS, "ContextBasedAccessCondition");
    public static final IRI ContextBasedAuthorization = iri(CASHMERE_NS, "ContextBasedAuthorization");
    public static final IRI ContextBasedControlAuthorization = iri(CASHMERE_NS, "ContextBasedControlAuthorization");
    public static final IRI ContextDomain = iri(CASHMERE_NS, "ContextDomain");
    public static final IRI ContextDomainCondition = iri(CASHMERE_NS, "ContextDomainCondition");
    public static final IRI ContextDomainGroup = iri(CASHMERE_NS, "ContextDomainGroup");
    public static final IRI ContextManagementService = iri(CASHMERE_NS, "ContextManagementService");
    public static final IRI ContextStream = iri(CASHMERE_NS, "ContextStream");
    public static final IRI ProfiledContextCondition = iri(CASHMERE_NS, "ProfiledContextCondition");
    public static final IRI StaticContextCondition = iri(CASHMERE_NS, "StaticContextCondition");

    // Object Properties
    public static final IRI accessTo = iri(ACL_NS, "accessTo");
    public static final IRI accessToClass = iri(ACL_NS, "accessToClass");
    public static final IRI agent = iri(ACL_NS, "agent");
    public static final IRI agentClass = iri(ACL_NS, "agentClass");
    public static final IRI agentGroup = iri(ACL_NS, "agentGroup");
    public static final IRI delegates = iri(ACL_NS, "delegates");
    public static final IRI definesGroup = iri(CASHMERE_NS, "definesGroup");
    public static final IRI groupFor = iri(CASHMERE_NS, "groupFor");
    public static final IRI hasAccessAuthorization = iri(CASHMERE_NS, "hasAccessAuthorization");
    public static final IRI hasAccessCondition = iri(CASHMERE_NS, "hasAccessCondition");
    public static final IRI hasContextDimension = iri(CASHMERE_NS, "hasContextDimension");
    public static final IRI hasControlAuthorization = iri(CASHMERE_NS, "hasControlAuthorization");
    public static final IRI managesDomain = iri(CASHMERE_NS, "managesDomain");
    public static final IRI managesStream = iri(CASHMERE_NS, "managesStream");
    public static final IRI memberIn = iri(CASHMERE_NS, "memberIn");
    public static final IRI streams = iri(CASHMERE_NS, "streams");

    // Individual
    public static final IRI accessRequester = iri(CASHMERE_NS, "accessRequester");

    // Annotation Properties
    public static final IRI describes = iri("http://purl.org/dc/elements/1.1/describes");
    public static final IRI title = iri("http://purl.org/dc/elements/1.1/title");
    
    // Helper method to create IRI from direct string
    private static IRI iri(String iri) {
        return SimpleValueFactory.getInstance().createIRI(iri);
    }

    // Helper method to create IRI
    private static IRI iri(String namespace, String localName) {
        return SimpleValueFactory.getInstance().createIRI(namespace, localName);
    }

}

