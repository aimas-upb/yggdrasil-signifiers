package org.hyperagents.yggdrasil.auth.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.auth.AuthorizationRegistry;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.context.http.Utils;
import org.hyperagents.yggdrasil.eventbus.messageboxes.Messagebox;
import org.hyperagents.yggdrasil.eventbus.messageboxes.RdfStoreMessagebox;
import org.hyperagents.yggdrasil.eventbus.messages.RdfStoreMessage;
import org.hyperagents.yggdrasil.utils.EnvironmentConfig;
import org.hyperagents.yggdrasil.utils.HttpInterfaceConfig;
import org.hyperagents.yggdrasil.utils.RepresentationFactory;
import org.hyperagents.yggdrasil.utils.WebSubConfig;
import org.hyperagents.yggdrasil.utils.impl.RepresentationFactoryFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.ext.web.RoutingContext;

public class WACHandler {
  private static final String AGENT_WEBID_HEADER = "X-Agent-WebID";
  private static final String AGENT_LOCALNAME_HEADER = "X-Agent-LocalName";
  private static final String SLUG_HEADER = "Slug";
  private static final String TURTLE_CONTENT_TYPE = "text/turtle";

  private static final Logger LOGGER = LogManager.getLogger(WACHandler.class);
  
  private final Vertx vertx;
  private final HttpInterfaceConfig httpConfig;
  private final EnvironmentConfig envConfig;
  private final WebSubConfig notificationConfig;

  private final Messagebox<RdfStoreMessage> rdfStoreMessagebox;
  private final RepresentationFactory representationFactory;

  private final boolean environment;
  
  public WACHandler(
    final Vertx vertx,
    final HttpInterfaceConfig httpConfig,
    final EnvironmentConfig envConfig,
    final WebSubConfig notificationConfig) {
      this.vertx = vertx;
      this.httpConfig = httpConfig;
      this.envConfig = envConfig;
      this.notificationConfig = notificationConfig;

      this.rdfStoreMessagebox = new RdfStoreMessagebox(vertx.eventBus());
      
      // Should be able to use this boolean value to decide if we use cartago messages or not
      // that way the router does not need to check for routes itself
      this.environment = envConfig.isEnabled();
      this.representationFactory =
          RepresentationFactoryFactory.getRepresentationFactory(envConfig.getOntology(),
              notificationConfig,
              httpConfig);
  }

  /**
   * This method is invoked by the Yggdrasil HTTP server to handle a request to retrieve the WAC representation of an entity.
   * @param routingContext
   */
  public void handleWACRepresentation(RoutingContext routingContext) {
    LOGGER.info("Handling WAC Representation retrieval action...");
    
    // obtain the entity IRI by concatenating the base URI with the request path up to the second to last path segment, which will contain the entity ID
    String entityPath = routingContext.request().path();
    String entityIRI = httpConfig.getBaseUri() + entityPath.substring(0, entityPath.lastIndexOf("/"));
    
    AuthorizationRegistry authRegistry = AuthorizationRegistry.getInstance();

    // get the WAC document URI from the authorization registry for the entity
    Optional<String> wacDocumentURI = authRegistry.getAuthorisationDocumentURI(entityIRI);

    // if there is no WAC document URI for the entity, return a 404 Not Found response
    if (!wacDocumentURI.isPresent()) {
      LOGGER.info("No WAC document URI found for entity with URI: " + entityIRI);
      routingContext.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end();
    }
    else {
      // otherwise, send a request to the RdfStoreVerticle event bus to retrieve the WAC document
      LOGGER.info("Sending request to retrieve WAC document with URI: " + wacDocumentURI.get() + " ...");
      this.rdfStoreMessagebox
        .sendMessage(new RdfStoreMessage.GetEntity(wacDocumentURI.get()))
        .onComplete(
          this.handleWACDocumentReply(wacDocumentURI, routingContext, HttpStatus.SC_OK));
    }

    // String entityRepresentation = context.getBodyAsString();
    // String envName = context.pathParam("envid");
    // String wkspName = context.pathParam("wkspid");
    // String artifactName = context.pathParam("artid");
  }

  private Handler<AsyncResult<Message<String>>> handleWACDocumentReply(
      final Optional<String> wacDocumentURI,
      final RoutingContext routingContext,
      final int successCode
  ) {
      return reply -> {
        if (reply.succeeded()) {
          // if the reply is successful, return a 200 OK response with the WAC document
          LOGGER.info("WAC document with URI: " + wacDocumentURI.get() + " successfully retrieved.");
          final var httpResponse = routingContext.response();
          httpResponse.setStatusCode(successCode);
          httpResponse.putHeader(HttpHeaders.CONTENT_TYPE, TURTLE_CONTENT_TYPE);
          
          // set the headers for the WAC document URI
          final var headers = new HashMap<>(this.getWACDocumentHeaders(wacDocumentURI.get()));
          headers.putAll(Utils.getCorsHeaders());

          headers.forEach((headerName, headerValue) -> {
            if (headerName.equalsIgnoreCase("Link")) {
              httpResponse.putHeader(headerName, headerValue);
            } else {
              httpResponse.putHeader(headerName, String.join(",", headerValue));
            }
          });

          Optional.ofNullable(reply.result().body())
            .filter(r -> !r.isEmpty())
            .ifPresentOrElse(httpResponse::end, httpResponse::end);
        }
        else {
          // if reply is a 404 Not Found, return a 404 Not Found response
          // check if the reply is a ReplyException
          final var exception = ((ReplyException) reply.cause());
          LOGGER.error(exception);

          if (exception.failureCode() == HttpStatus.SC_NOT_FOUND) {
              LOGGER.info("WAC document with URI: " + wacDocumentURI.get() + " not found. Sending 404 Not Found response.");
              routingContext.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end();
          }
          else {
            // otherwise return a 500 Internal Server Error response
            LOGGER.info("Error retrieving WAC document with URI: " + wacDocumentURI.get() + ". Reason: " + reply.cause().getMessage());
            routingContext.response().setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).end();
          }
        }
      };
  }

  private Map<String, List<String>> getWACDocumentHeaders(String wacDocumentURI) {
    return Map.of(
        "Link",
        List.of(
            "<" + wacDocumentURI + ">; rel=\"self\"; type=\"text/turtle\""
        )
    );
  }

  /**
   * This method is invoked by the Yggdrasil HTTP server to filter access to the requested artifact resource
   * @param context the routing context
   */
  public void filterAccess(RoutingContext context) {
    // We need to check whether the entity is protected by a shared context web access control list
    // If so, we need to validate the access request by sending a request to the WAC Handler event bus with a request to validate the access
    // HttpInterfaceConfig httpConfig = new HttpInterfaceConfig(Vertx.currentContext().config());
    
    // obtain the entity IRI by concatenating the base URI with the request path up to the second to last path segment, which will contain the artifact id
    String requestPath = context.request().path();
    String artifactIRI = httpConfig.getBaseUri() + requestPath.substring(0, requestPath.lastIndexOf("/"));
    
    // obtain the agent's web id from the request header
    String agentWebId = context.request().getHeader("X-Agent-WebID");

    LOGGER.info("Handling Authorization validation for resource with URI: " + artifactIRI 
      + " invoked by agent with WebID: " + agentWebId);
    
    AuthorizationRegistry authRegistry = AuthorizationRegistry.getInstance();

    // For now we only handle the case where the agent invokes the artifact action using a POST method, so we need to 
    // check whether the artifact is write protected by a Shared Context Access Authorization
    if (authRegistry.isWriteProtected(artifactIRI)) {
      // In this case we need to validate the access request, by sending a request to the WAC Handler event bus
      // with a request to validate the access. We do this because the check will involve a federated SPAQRL query and
      // we want to avoid blocking the main event loop
      DeliveryOptions options = new DeliveryOptions();
      options.addHeader(WACVerticle.WAC_METHOD, WACVerticle.VALIDATE_AUTHORIZATION);
      options.addHeader(WACVerticle.ACCESSED_RESOURCE_URI, artifactIRI);
      options.addHeader(WACVerticle.ACCESS_TYPE, AuthorizationAccessType.WRITE.toString());
      options.addHeader(WACVerticle.AGENT_WEBID, agentWebId);

      LOGGER.info("Sending request to validate access to resource with URI: " + artifactIRI + " ...");
      LOGGER.info("Delivery options: " + options.toString());

      vertx.eventBus().request(WACVerticle.BUS_ADDRESS, null, options, reply -> {
        if (reply.succeeded()) {
          // if the access is granted, we let the request go through
          LOGGER.info("Access to resource with URI: " + artifactIRI + " granted.");
          context.next();
        }
        else {
          // otherwise we return an error
          LOGGER.info("Access to resource with URI: " + artifactIRI + " denied.");
          context.response().setStatusCode(HttpStatus.SC_UNAUTHORIZED).end();
        }
      });
    }
    else {
      // otherwise we just let the request go through
      LOGGER.info("Resource with URI: " + artifactIRI + " is not write protected. Letting request go through.");
      context.next();
    }
  }
}
