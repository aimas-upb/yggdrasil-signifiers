package org.hyperagents.yggdrasil.auth.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.hyperagents.yggdrasil.auth.AuthorizationRegistry;
import org.hyperagents.yggdrasil.auth.model.ACL;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;
import org.hyperagents.yggdrasil.eventbus.messageboxes.ContextMessageBox;
import org.hyperagents.yggdrasil.eventbus.messageboxes.WACMessageBox;
import org.hyperagents.yggdrasil.eventbus.messages.ContextMessage;
import org.hyperagents.yggdrasil.eventbus.messages.WACMessage;
import org.hyperagents.yggdrasil.model.interfaces.Environment;
import org.hyperagents.yggdrasil.utils.ContextManagementConfig;
import org.hyperagents.yggdrasil.utils.HttpInterfaceConfig;
import org.hyperagents.yggdrasil.utils.RdfModelUtils;
import org.hyperagents.yggdrasil.utils.WACConfig;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class WACVerticle extends AbstractVerticle {
    public static final String BUS_ADDRESS = "org.hyperagents.yggdrasil.eventbus.wac";
    
    // WAC methods
    public static final String GET_WAC_RESOURCE = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".getWacResource";
    public static final String ADD_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".addAuthorization";
    public static final String REMOVE_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".removeAuthorization";
    public static final String VALIDATE_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".validateAuthorization";

    // keys for the headers of the event bus messages
    public static final String WAC_METHOD = "org.hyperagents.yggdrasil.eventbus.headers.wacMethod";
    public static final String ACCESSED_RESOURCE_URI = "org.hyperagents.yggdrasil.eventbus.headers.accessedResourceUri";
    public static final String ACCESS_TYPE = "org.hyperagents.yggdrasil.eventbus.headers.accessType";
    public static final String AGENT_WEBID = "org.hyperagents.yggdrasil.eventbus.headers.agentWebId";
    public static final String AGENT_NAME = "org.hyperagents.yggdrasil.eventbus.headers.agentName";
    
    // Logger
    private static final Logger LOGGER = LogManager.getLogger(WACVerticle.class);
    
    private ContextMessageBox contextMessageBox;
    private WACMessageBox wacMessageBox;

    @Override
    public void start(final Promise<Void> startPromise) {
        // retrieve the configuration object for the wac verticle
        final var wacConfig = this.vertx.sharedData()
            .<String, WACConfig>getLocalMap("wac")
            .get("default");

        final var contextManagementConfig = this.vertx.sharedData()
            .<String, ContextManagementConfig>getLocalMap("context-management-config")
            .get("default");
        final var environment = this.vertx.sharedData()
            .<String, Environment>getLocalMap("environment")
            .get("default");
        final var httpConfig = this.vertx.sharedData()
            .<String, HttpInterfaceConfig>getLocalMap("http-config")
            .get("default");
        
        // populate the Authorization Registry with initial artifacts for in the environment for which an authorization policy is defined
        setupAuthorizationRegistry(environment, httpConfig);

        // setup message handling 
        this.contextMessageBox = new ContextMessageBox(vertx.eventBus(), contextManagementConfig);

        this.wacMessageBox = new WACMessageBox(vertx.eventBus(), wacConfig);
        this.wacMessageBox.init();
        setupRequestHandling();

        startPromise.complete();
    }

    private void setupAuthorizationRegistry(Environment environment, HttpInterfaceConfig httpConfig) {
        // get the instance of the Authorization Registry
        AuthorizationRegistry authorizationRegistry = AuthorizationRegistry.getInstance();
        
        final var wacConfig = this.vertx.sharedData()
            .<String, WACConfig>getLocalMap("wac")
            .get("default");
            
        if (wacConfig != null && wacConfig.getWorkspacePolicies() != null) {
            LOGGER.info("Loading workspace policies...");
            LOGGER.info("Number of workspace policies: " + wacConfig.getWorkspacePolicies().size());
            
            wacConfig.getWorkspacePolicies().forEach(policy -> {
                try {
                    String workspaceUri = policy.getWorkspaceUri();
                    String policyUrl = policy.getPolicyUrl();
                    
                    LOGGER.info("Loading workspace policy for " + workspaceUri + " from " + policyUrl);
                    
                    URL url = URI.create(policyUrl).toURL();
                    try (InputStream inputStream = url.openStream()) {
                        Model contextAuthModel = Rio.parse(inputStream, "", RDFFormat.TURTLE);
                        LOGGER.info("Parsed model with " + contextAuthModel.size() + " statements");
                        
                        List<ContextBasedAuthorization> authPolicies = ContextBasedAuthorization.fromModel(contextAuthModel);
                        LOGGER.info("Extracted " + authPolicies.size() + " authorization policies");
                        
                        authPolicies.forEach(authPolicy -> {
                            LOGGER.info("Adding authorization: " + authPolicy.getResourceURI() + " -> " + authPolicy.getAccessTypes());
                            authorizationRegistry.addContextAuthorisation(authPolicy.getResourceURI(), authPolicy);
                        });
                        
                        LOGGER.info("Successfully loaded workspace policy for " + workspaceUri);
                        
                    } catch (IOException e) {
                        LOGGER.error("Failed to load workspace policy from: " + policyUrl, e);
                    }
                } catch (MalformedURLException e) {
                    LOGGER.error("Invalid policy URL: " + policy.getPolicyUrl(), e);
                } catch (Exception e) {
                    LOGGER.error("Error processing workspace policy", e);
                }
            });
        } else {
            LOGGER.warn("No WAC config or workspace policies found!");
        }
        
        // Go through all the artifacts in the environment, looking at those that have an "access-policy-url" defined
        // and populate the Authorization Registry with the corresponding authorization policies
        // TODO: theoretically, here we can pre-compute the effective access policies for each artifact and store them in the Authorization Registry.
        // This implies walking up the workspace hierarchy to get the effective access policy PER REQUEST TYPE that would exist in the absence of any explicit 
        // access policy. This would be useful for performance reasons, as it would avoid having to compute the effective access policy for each request.
        environment.getWorkspaces().forEach(
            workspace -> workspace.getArtifacts().forEach(
                artifact -> {
                    if (artifact.getRepresentation().isPresent()) {
                        try {
                            // read the authorization specification from the RDF representation file path, if it exists
                            var representationPath = artifact.getRepresentation().get();
                            URL url = representationPath.toUri().toURL();
                            try (InputStream inputStream = url.openStream()) {
                                Model contextAuthModel = Rio.parse(inputStream, "", RDFFormat.TURTLE);
                                
                                // populate the Authorization Registry with the authorization policy
                                List<ContextBasedAuthorization> authPolicies = ContextBasedAuthorization.fromModel(contextAuthModel);
                                authPolicies.forEach(authPolicy -> {
                                    authorizationRegistry.addContextAuthorisation(authPolicy.getResourceURI(), authPolicy);
                                });

                            } catch (IOException e) {
                                LOGGER.error("Failed to read RDF model from URL: " + url, e);
                            }
                        } catch (MalformedURLException ex) {
                            LOGGER.error("Invalid URI syntax for artifact representation path: " + artifact.getRepresentation().get(), ex);
                        }
                    }
                }
            )
        );
    }

    private void setupRequestHandling() {
        // handle WAC requests
        wacMessageBox.receiveMessages(
            wacMessage -> {
                LOGGER.info("Received WAC message: " + wacMessage.body());
                
                switch (wacMessage.body()) {
                    case WACMessage.AuthorizeAccess authRequest -> {
                        LOGGER.info("Received WAC Authorization request...");
                        // handle the authorization request
                        validateAuthorization(authRequest, wacMessage);
                    }
                    case WACMessage.GetWACResource getWACResourceReq -> {
                        LOGGER.info("Received WAC Get Resource request...");
                        // handle the get resource request
                        getWACRepresentation(getWACResourceReq, wacMessage);
                    }
                    default -> {
                        LOGGER.info("Received WAC message not supported yet");
                        throw new UnsupportedOperationException("Not implemented yet");
                    }
                }
                
            });
    }
    
    private void getWACRepresentation(WACMessage.GetWACResource getWACResourceReq, Message<WACMessage> message) {
        String resourceURI = getWACResourceReq.accessedResourceURI();
        LOGGER.info("Getting WAC representation for resource " + resourceURI);
        
        // obtain the representation from the Authorization Registry
        AuthorizationRegistry authorizationRegistry = AuthorizationRegistry.getInstance();

        // check if the resource is public; if so return a 404 response with a JSON payload of the error
        if (!authorizationRegistry.hasAccessAuthorization(resourceURI)) {
            LOGGER.info("Resource " + resourceURI + " is public. Returning 404 response.");
            
            // create a JSON payload for the not found message
            message.fail(HttpStatus.SC_NOT_FOUND, new JsonObject().put("error", "Resource "+ resourceURI + " has no WAC policy").encode());
        }
        else {
            List<ContextBasedAuthorization> auths = authorizationRegistry.getContextAuthorisations(resourceURI);

            // create a global org.eclipse.rdf4j.Model for the authorization policies
            ModelBuilder builder = new ModelBuilder();
            builder.setNamespace("acl", ACL.NS);
            builder.setNamespace("rdf", RDF.NAMESPACE);
            builder.setNamespace("cashmere", CASHMERE.CASHMERE_NS);
            Model authModel = builder.build();

            auths.forEach(auth -> {
                auth.toModel().forEach((authIRI, authModelPart) -> {
                    authModel.addAll(authModelPart);
                });
            });

            try {
                // obtain a string representation of the RDF content
                var serializedAuthModel = RdfModelUtils.modelToString(authModel, RDFFormat.TURTLE, null);
                message.reply(serializedAuthModel);
            } catch (IllegalArgumentException | IOException e) {
                LOGGER.error("Error converting the authorization model to a string", e);
                message.fail(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error converting the authorization model to a string");
            }
        }

    }

    private void validateAuthorization(WACMessage.AuthorizeAccess authReq, Message<WACMessage> message) {
        String agentURI = authReq.agentURI();
        String accessedResourceUri = authReq.accessedResourceURI();
        
        // Safely handle the access type conversion
        Optional<AuthorizationAccessType> accessTypeOpt = AuthorizationAccessType.fromName(authReq.accessType());
        if (accessTypeOpt.isEmpty()) {
            LOGGER.error("Unknown access type: " + authReq.accessType());
            message.fail(HttpStatus.SC_BAD_REQUEST, "Unknown access type: " + authReq.accessType());
            return;
        }
        AuthorizationAccessType accessType = accessTypeOpt.get();
        
        LOGGER.info("=== WAC AUTHORIZATION REQUEST ===");
        LOGGER.info("Agent URI: " + agentURI);
        LOGGER.info("Accessed Resource URI: " + accessedResourceUri);
        LOGGER.info("Access Type: " + accessType);
        LOGGER.info("=================================");
        
        AuthorizationRegistry authorizationRegistry = AuthorizationRegistry.getInstance();
        
        // Step 1: Check for direct authorization on the resource
        boolean hasDirectAuth = authorizationRegistry.hasAccessAuthorization(accessedResourceUri, accessType);
        LOGGER.info("Direct authorization check for " + accessedResourceUri + " (type: " + accessType + "): " + hasDirectAuth);
        
        if (hasDirectAuth) {
            LOGGER.info("DIRECT authorization found for resource " + accessedResourceUri);
            
            contextMessageBox.sendMessage(new ContextMessage.ValidateContextBasedAccess(agentURI, accessedResourceUri))
                .onSuccess(r -> {
                    LOGGER.info("Direct authorization validation SUCCEEDED for " + agentURI);
                    message.reply(true);
                })
                .onFailure(t -> {
                    LOGGER.error("Direct authorization validation FAILED for " + agentURI, t);
                    message.fail(403, "Authorization validation failed");
                });
            return;
        }
        
        LOGGER.info("No direct authorization found. Starting WAC hierarchy traversal...");
        
        // Step 2: WAC Hierarchy - Check for default policies in parent containers
        String effectiveACLResource = findEffectiveACLResource(accessedResourceUri, authorizationRegistry, accessType);
        
        LOGGER.info("WAC hierarchy result: effectiveACLResource = " + effectiveACLResource);
        
        if (effectiveACLResource != null) {
            LOGGER.info("FOUND effective ACL resource: " + effectiveACLResource + " for " + accessedResourceUri);
            
            contextMessageBox.sendMessage(new ContextMessage.ValidateContextBasedAccess(agentURI, effectiveACLResource))
                .onSuccess(r -> {
                    LOGGER.info("Workspace/container authorization GRANTED for " + accessedResourceUri + " via " + effectiveACLResource);
                    message.reply(true);
                })
                .onFailure(t -> {
                    LOGGER.error("Workspace/container authorization DENIED for " + accessedResourceUri + " via " + effectiveACLResource, t);
                    message.fail(403, "Container access denied");
                });
            return;
        }
        
        // Step 3: No authorization found anywhere in hierarchy - default deny (WAC security policy)
        LOGGER.info("NO authorization found in hierarchy for " + accessedResourceUri + ". DENYING access (WAC default policy).");
        LOGGER.info("WAC security policy: Access denied when no explicit authorization is found");
        message.fail(403, "Access denied: No authorization found in containment hierarchy");
    }

    /**
     * WAC Effective ACL Resource Determination Protocol
     * Traverses the containment hierarchy to find effective authorization
     */
    private String findEffectiveACLResource(String resourceURI, AuthorizationRegistry authorizationRegistry, AuthorizationAccessType requestedAccessType) {
        LOGGER.info("findEffectiveACLResource called with: " + resourceURI + " (access type: " + requestedAccessType + ")");
        
        // For artifacts, check parent workspace
        if (resourceURI.contains("/artifacts/")) {
            // Extract workspace URI from artifact URI
            String workspaceURI = resourceURI.substring(0, resourceURI.indexOf("/artifacts/")) + "#workspace";
            
            LOGGER.info("Extracted workspace URI: " + workspaceURI);
            LOGGER.info("Checking parent workspace for access type: " + requestedAccessType);
            
            // Check if the workspace has authorization for the specific access type being requested
            boolean hasRequestedAuth = authorizationRegistry.hasAccessAuthorization(workspaceURI, requestedAccessType);
            
            LOGGER.info("Workspace authorization for " + requestedAccessType + ": " + hasRequestedAuth);
            
            if (hasRequestedAuth) {
                LOGGER.info("Found workspace authorization for " + requestedAccessType + " - returning: " + workspaceURI);
                return workspaceURI;
            } else {
                LOGGER.info("No workspace authorization found for " + requestedAccessType + " on: " + workspaceURI);
            }
            
            // TODO: Add support for subworkspace hierarchy traversal here
            // For now, we only check direct parent workspace
            
        } else {
            LOGGER.info("Resource is not an artifact (no /artifacts/ in URI): " + resourceURI);
        }
        
        LOGGER.info("No effective ACL resource found, returning null");
        return null; // No effective ACL resource found
    }
}
