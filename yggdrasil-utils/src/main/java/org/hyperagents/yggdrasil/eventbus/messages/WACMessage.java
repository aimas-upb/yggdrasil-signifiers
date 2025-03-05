package org.hyperagents.yggdrasil.eventbus.messages;

/**
 * An interface representing operations that can be performed by the Web Access Control Service.
 * 
 * <p>This interface is used to define different types of messages that can be sent to perform
 * operations concerning authorized access to resources (mainly artifacts and workspaces) in an Hypermedia MAS deployment.
 * The authorizations are of two types: explicitily granting access, or implicitily granting access based on context-dependent conditions which are 
 * verified with help from the Context Management Service. 
 * 
 * Existing (default configuration added) authorizations can currently be validated or retrieved.
 * TODO: Add support for adding and removing explicit or context-based authorizations.
 */
public sealed interface WACMessage {

    /**
     * A record representing a request to get the WAC resource associated with a given resource URI.
     * @param accessedResourceURI The URI of the resource for which the WAC representation is requested.
     */
    record GetWACResource(String accessedResourceURI) implements WACMessage {
    }

    /**
     * A record representing a request to validate an authorization for a given agent to access a given resource.
     * @param accessedResourceURI The URI of the resource to which the authorization is added.
     * @param agentURI The URI of the agent to which the authorization is added.
     * @param accessType The type of access that is granted to the agent.
     */
    record AuthorizeAccess(String accessedResourceURI, String agentURI, String accessType) implements WACMessage {
    }

}
