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
    record ValidateContextBasedAccess(String accessRequesterURI, String accessedResourceURI) implements ContextMessage {
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

    /**
     * A record representing an update to a Context Stream managed by the Context Management Service.
     * <p> The streamURI is the URI of the Context Stream that is updated.
     * <p> The updateContent is a string containing the Turtle-syntax serialized content of the RDF Graph composing the update.
     * <p> The updateTimestamp is the timestamp of the update.
     * @param streamURI The URI of the Context Stream that is updated.
     * @param updateContent The Turtle-syntax serialized content of the RDF Graph composing the update.
     * @param updateTimestamp The timestamp of the update in milliseconds.
     */
    record ContextStreamUpdate(String streamURI, String updateContent, long updateTimestamp) implements ContextMessage {
    }

    /**
     * A record representing a request to verify a subscription to WebSub for updates from a Context Stream managed by the Context Management Service.
     * <p> The streamURI is the URI of the Context Stream for which the subscription is verified.
     * @param streamURI The URI of the Context Stream for which the subscription is verified.
     */
    record VerifyContextStreamSubscription(String streamURI) implements ContextMessage {
    }

    /**
     * A record representing a request to get the representation of a Context Stream managed by the Context Management Service.
     * <p> The streamURI is the URI of the Context Stream for which the representation is requested.
     * @param streamURI The URI of the Context Stream for which the representation is requested.
     */
    record GetContextStreamRepresentation(String streamURI) implements ContextMessage {
    }

    /**
     * A record representing a request to get the Context Domain representation of a given Context Domain URI.
     * <p> The contextDomainURI is the URI of the Context Domain for which the representation is requested.
     * @param contextDomainURI The URI of the Context Domain for which the representation is requested.
     */
    record ContextDomainRepresentation(String contextDomainURI) implements ContextMessage {
    }

    /**
     * A record representing a request to validate if the Context Management Service maintains instances 
     * of a given ContextAssertion type in both static and profiled context repositories.
     * 
     * <p> The contextAssertionType is the URI identifying the type of context assertion to check for.
     * 
     * @param contextAssertionType The URI of the type of context assertion to validate.
     */
    record ContainsAssertion(String contextAssertionType) implements ContextMessage {
    }

    /**
     * A record representing a request to add RDF data to the static context repository.
     * 
     * <p> The rdfContent contains the RDF data in Turtle format to be added to the static context graph.
     * This is typically used to add ContextAssertions and ContextEntities that represent static, 
     * unchanging contextual information.
     * 
     * @param rdfContent The RDF content in Turtle format to add to the static context repository.
     */
    record AddStaticContext(String rdfContent) implements ContextMessage {
    }

    /**
     * A record representing a request to add RDF data to the profiled context repository.
     * 
     * <p> The rdfContent contains the RDF data in Turtle format to be added to the profiled context graph.
     * This is typically used to add profiled ContextAssertions with their ContextAnnotations and 
     * ContextEntities that represent contextual information with temporal, spatial, or other 
     * qualifying characteristics.
     * 
     * @param rdfContent The RDF content in Turtle format to add to the profiled context repository.
     */
    record AddProfiledContext(String rdfContent) implements ContextMessage {
    }

    /**
     * A record representing a request to add and track a new ContextStream.
     * 
     * <p> The streamURI is the URI of the ContextStream to be added and tracked.
     * <p> The streamConfig contains configuration details for the stream including ontology URL and assertions.
     * This operation will register the stream with the Context Management Service and subscribe to its updates.
     * 
     * @param streamURI The URI of the ContextStream to add and track.
     * @param streamConfig The configuration object containing stream details (ontology URL, assertions, etc.).
     */
    record AddContextStream(String streamURI, String streamConfig) implements ContextMessage {
    }

    /**
     * A record representing a request to remove and stop tracking a ContextStream.
     * 
     * <p> The streamURI is the URI of the ContextStream to be removed and no longer tracked.
     * This operation will unsubscribe from the stream's updates and remove it from the managed streams.
     * 
     * @param streamURI The URI of the ContextStream to remove and stop tracking.
     */
    record RemoveContextStream(String streamURI) implements ContextMessage {
    }

    /**
     * A record representing a request to add a new ContextDomain.
     * 
     * <p> The contextDomainURI is the URI of the ContextDomain to be added.
     * <p> The contextDomainConfig contains configuration details for the ContextDomain.
     * 
     * @param contextDomainURI The URI of the ContextDomain to add.
     * @param contextDomainConfig The configuration details for the ContextDomain.
     */
    record AddContextDomain(String contextDomainURI, String contextDomainConfig) implements ContextMessage {
    }

    /**
     * A record representing a request to remove a ContextDomain.
     * 
     * <p> The contextDomainURI is the URI of the ContextDomain to be removed.
     * This operation will stop the domain's RSPQL engine and remove it from the managed domains.
     * 
     * @param contextDomainURI The URI of the ContextDomain to remove.
     */
    record RemoveContextDomain(String contextDomainURI) implements ContextMessage {
    }

    /**
     * A record representing a request to add a membership rule to an existing ContextDomain.
     * 
     * <p> The contextDomainURI is the URI of the ContextDomain to add a rule to.
     * <p> The membershipRule is a URL pointing to an RSPQL query that defines a membership rule.
     * 
     * @param contextDomainURI The URI of the ContextDomain to add a rule to.
     * @param membershipRule URL pointing to an RSPQL query that defines a membership rule.
     */
    record AddMembershipRule(String contextDomainURI, String membershipRule) implements ContextMessage {
    }

    /**
     * A record representing a request to remove a membership rule from an existing ContextDomain.
     * 
     * <p> The contextDomainURI is the URI of the ContextDomain to remove a rule from.
     * <p> The membershipRule is a URL pointing to an RSPQL query that should be removed.
     * 
     * @param contextDomainURI The URI of the ContextDomain to remove a rule from.
     * @param membershipRule URL pointing to an RSPQL query that should be removed.
     */
    record RemoveMembershipRule(String contextDomainURI, String membershipRule) implements ContextMessage {
    }
}
