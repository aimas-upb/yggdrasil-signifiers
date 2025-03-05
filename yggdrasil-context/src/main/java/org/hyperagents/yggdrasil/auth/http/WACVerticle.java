package org.hyperagents.yggdrasil.auth.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

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
        this.contextMessageBox.init();

        this.wacMessageBox = new WACMessageBox(vertx.eventBus(), wacConfig);
        this.wacMessageBox.init();
        setupRequestHandling();

        startPromise.complete();
    }

    private void setupAuthorizationRegistry(Environment environment, HttpInterfaceConfig httpConfig) {
        // get the instance of the Authorization Registry
        AuthorizationRegistry authorizationRegistry = AuthorizationRegistry.getInstance();
        
        // Go through all the artifacts in the environment, looking at those that have an "access-policy-url" defined
        // and populate the Authorization Registry with the corresponding authorization policies
        // TODO: theoretically, here we can pre-compute the effective access policies for each artifact and store them in the Authorization Registry.
        // This implies walking up the workspace hierarchy to get the effective access policy PER REQUEST TYPE that would exist in the absence of any explicit 
        // access policy. This would be useful for performance reasons, as it would avoid having to compute the effective access policy for each request.
        environment.getWorkspaces().forEach(
            workspace -> workspace.getArtifacts().forEach(
                artifact -> {
                    if (artifact.getContextAccessPolicyURL().isPresent()) {
                        try {
                            // read the authorization policy from the URL into an jena RDF model
                            URL url = new URI(artifact.getContextAccessPolicyURL().get()).toURL();
                            try (InputStream inputStream = url.openStream()) {
                                Model contextAuthModel = Rio.parse(inputStream, "", RDFFormat.TURTLE);
                                
                                // populate the Authorization Registry with the authorization policy
                                List<ContextBasedAuthorization> authPolicies = ContextBasedAuthorization.fromModel(contextAuthModel);
                                authPolicies.forEach(authPolicy -> {
                                    authorizationRegistry.addContextAuthorisation(authPolicy.getResourceURI(), authPolicy);
                                });

                            } catch (Exception e) {
                                LOGGER.error("Failed to read RDF model from URL: " + url, e);
                            }
                        } catch (URISyntaxException | MalformedURLException ex) {
                            LOGGER.error("Invalid URI syntax for the artifact access policy URL: " + artifact.getContextAccessPolicyURL().get(), ex);
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
            message.fail(HttpStatus.SC_NOT_FOUND, new JsonObject().put("error", "Resource"+ resourceURI + " has no WAC policy").encode());
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
        AuthorizationAccessType accessType = AuthorizationAccessType.fromName(authReq.accessType()).get();
        
        LOGGER.info("Validating Authorization for agent " + agentURI + " to access resource " + accessedResourceUri + " in mode " + accessType);
        
        // Use the authorization registry to check if the authorization exists; if there isn't an authorization, return by default an OK response,
        // because it means that the resource is public
        AuthorizationRegistry authorizationRegistry = AuthorizationRegistry.getInstance();
        if (!authorizationRegistry.hasAccessAuthorization(agentURI, accessType)) {
            LOGGER.info("Authorization not found for agent " + agentURI + " to access resource " + accessedResourceUri + " in mode " + accessType + ". Resource is public.");   
            message.reply(true);
            return;
        }
        
        // forward a call to the Context Management Verticle to validate the authorization
        contextMessageBox.sendMessage(new ContextMessage.ValidateContextBasecAccess(agentURI, accessedResourceUri))
            .onSuccess(r -> {
                LOGGER.info("Authorization validated for agent " + agentURI + " to access resource " + accessedResourceUri + " in mode " + accessType);
                message.reply(true);
            })
            .onFailure(t -> {
                LOGGER.error("Error validating authorization for agent " + agentURI + " to access resource " + accessedResourceUri + " in mode " + accessType, t);
                message.fail(403, "Authorization validation failed");
            });
    }
}
