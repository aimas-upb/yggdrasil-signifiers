package org.hyperagents.yggdrasil.eventbus.messages;

/**
 * An interface representing operations that can be performed by the Context Management Service.
 * 
 * <p>This interface is used to define different types of messages that can be sent to perform
 * operations on context information (static and profiled context graphs), ContextDomains or Context Streams managed by the Context Management Service. 
 * Each record implementing this interface represents a specific operation.
 */
public sealed interface ContextMessage {

    /**
     * A record representing a request to validate the current context of an accessRequester against the context-based conditions
     * that authorize the access to the accessedResource.
     * 
     * <p> The acceeRequesterURI is the URI of the entity that requests the access to the accessedResource.
     * <p> The accessedResourceURI is the URI of the entity that is requested to be accessed.
     * 
     * @param accessRequesterURI The URI of the entity that requests the access to the accessedResource.
     * @param accessedResourceURI The URI of the entity that is requested to be accessed.
     */
    record ValidateContextBasecAccess(String accessRequesterURI, String accessedResourceURI) implements ContextMessage {
    }

    /**
     * A record representing a request to get the contents of the static context graph managed by the Context Management Service.
     */
    record GetStaticContext() implements ContextMessage {
    }

    /**
     * A record representing a request to get the instances of a given type of profiled ContextAssertion, together with their annotations. 
     * 
     * <p> The contextAssertionType is the URI identifying the type of context assertion that is requested to be retrieved.
     * 
     * @param contextAssertionType The URI of the type of context assertion that is requested to be retrieved.
     */
    record GetProfiledContext(String contextAssertionType) implements ContextMessage {
    }   
}
