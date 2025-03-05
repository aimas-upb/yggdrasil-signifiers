package org.hyperagents.yggdrasil.auth.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.context.http.Utils;
import org.hyperagents.yggdrasil.eventbus.messageboxes.Messagebox;
import org.hyperagents.yggdrasil.eventbus.messageboxes.WACMessageBox;
import org.hyperagents.yggdrasil.eventbus.messages.WACMessage;
import org.hyperagents.yggdrasil.utils.HttpInterfaceConfig;
import org.hyperagents.yggdrasil.utils.WACConfig;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.ext.web.RoutingContext;

public class WACHandler {
  private static final String TURTLE_CONTENT_TYPE = "text/turtle";
  private static final String ARTIFACT_FRAGMENT = "#artifact";

  private static final Logger LOGGER = LogManager.getLogger(WACHandler.class);
  
  private final HttpInterfaceConfig httpConfig;
  private final WACConfig wacConfig;

  private final Messagebox<WACMessage> wacMessagebox;

  public WACHandler(
    final Vertx vertx,
    final HttpInterfaceConfig httpConfig,
    final WACConfig wacConfig) {
      this.httpConfig = httpConfig;
      this.wacConfig = wacConfig; 

      this.wacMessagebox = new WACMessageBox(vertx.eventBus(), wacConfig);
  }

  /**
   * This method is invoked by the Yggdrasil HTTP server to handle a request to retrieve the WAC representation of an entity.
   * @param routingContext
   */
  public void handleWACRepresentation(RoutingContext routingContext) {
    LOGGER.info("Handling WAC Representation retrieval action...");
    
    // first, consider if the wacConfig is enabled
    if (!wacConfig.isEnabled()) {
      LOGGER.info("WAC is disabled. Skipping WAC document retrieval. Sending 404 Not Found response.");
      routingContext.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end();
      return;
    }

    // obtain the entity IRI by concatenating the base URI with the request path up to the second to last path segment, 
    // which will contain the entityID, then append the ARTIFACT_FRAGMENT
    // TODO: refactor this to consider that we can ask the WAC for any resource, not just artifacts
    String entityPath = routingContext.request().path();
    String entityIRI = httpConfig.getBaseUri() + entityPath.substring(0, entityPath.lastIndexOf("/")) + ARTIFACT_FRAGMENT;
    
    // otherwise, send a request to the RdfStoreVerticle event bus to retrieve the WAC document
    LOGGER.info("Sending request to retrieve WAC document for entity URI: " + entityIRI + " ...");
    this.wacMessagebox
      .sendMessage(new WACMessage.GetWACResource(entityIRI))
      .onComplete(
        this.handleWACDocumentReply(entityIRI, routingContext, HttpStatus.SC_OK));
  }

  private Handler<AsyncResult<Message<String>>> handleWACDocumentReply(
      final String entityIRI,
      final RoutingContext routingContext,
      final int successCode
  ) {
      return reply -> {
        if (reply.succeeded()) {
          // if the reply is successful, return a 200 OK response with the WAC document
          LOGGER.info("WAC document for IRI: " + entityIRI + " successfully retrieved.");
          
          final var httpResponse = routingContext.response();
          httpResponse.setStatusCode(successCode);
          httpResponse.putHeader(HttpHeaders.CONTENT_TYPE, TURTLE_CONTENT_TYPE);
          
          // set the headers for the WAC document URI
          final var headers = new HashMap<>(this.getWACDocumentHeaders(routingContext.request().absoluteURI()));
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
              LOGGER.info("WAC document for IRI: " + entityIRI + " not found. Sending 404 Not Found response.");
              routingContext.response().setStatusCode(HttpStatus.SC_NOT_FOUND).end();
          }
          else {
            // otherwise return a 500 Internal Server Error response
            LOGGER.info("Error retrieving WAC document for IRI: " + entityIRI + ". Reason: " + reply.cause().getMessage());
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
   * @param routingContext the routing context
   */
  public void filterAccess(RoutingContext routingContext) {
    // TODO: refactor this to consider that we can ask to filter access to any resource, not just artifacts
    
    // first, consider if the wacConfig is enabled
    if (!wacConfig.isEnabled()) {
      LOGGER.info("WAC is disabled. Skipping Authorization validation.");
      routingContext.next();
      return;
    }

    // obtain the entity IRI by concatenating the base URI with the request path up to the second to last path segment, which will contain the artifact id
    String requestPath = routingContext.request().path();
    String artifactIRI = httpConfig.getBaseUri() + requestPath.substring(0, requestPath.lastIndexOf("/")) + ARTIFACT_FRAGMENT;
    
    // obtain the agent's web id from the request header
    String agentURI = routingContext.request().getHeader("X-Agent-WebID");

    LOGGER.info("Handling Authorization validation for resource with URI: " + artifactIRI 
      + " invoked by agent with WebID: " + agentURI);
    
    // send an AuthorizeAccess request to the WAC Verticle using the wacMessagebox
    this.wacMessagebox
      .sendMessage(new WACMessage.AuthorizeAccess(artifactIRI, agentURI, AuthorizationAccessType.WRITE.getName()))
      .onSuccess(reply -> {
        // if the access is granted, we let the request go through
        LOGGER.info("Access to resource with URI: " + artifactIRI + " granted.");
        routingContext.next();
      })
      .onFailure(t -> {
        // otherwise we return an error
        LOGGER.info("Access to resource with URI: " + artifactIRI + " denied.");
        routingContext.response().setStatusCode(HttpStatus.SC_UNAUTHORIZED).end();
      });

  }
}
