package org.hyperagents.yggdrasil.context.http;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.eventbus.messageboxes.ContextMessageBox;
import org.hyperagents.yggdrasil.eventbus.messages.ContextMessage;
import org.hyperagents.yggdrasil.model.interfaces.ContextStreamModel;
import org.hyperagents.yggdrasil.utils.ContextManagementConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ContextMgmtHandler {
    private static final Logger LOGGER = LogManager.getLogger(ContextMgmtHandler.class);
    
    // Pattern to extract URI from Link header
    private static final Pattern LINK_PATTERN = Pattern.compile("<([^>]*)>\\s*;\\s*rel\\s*=\\s*\"?self\"?");
    
    private final Vertx vertx;
    private final ObjectMapper mapper = new ObjectMapper();

    // The message box to send internal messages to the ContextManagement Verticle implementing the Context Service
    private final ContextMessageBox contextMessageBox;

    // List of ContextStreams that are being managed by the Context Management service
    private final List<String> managedContextStreamURIs = new ArrayList<>();

    public ContextMgmtHandler(Vertx vertx, final ContextManagementConfig contextManagementConfig) {
        this.vertx = vertx;
        this.contextMessageBox = new ContextMessageBox(vertx.eventBus(), contextManagementConfig);

        // Initialize the ContextStreams that are being managed by the Context Management service
        for (ContextStreamModel streamModel : contextManagementConfig.getContextStreams()) {
            managedContextStreamURIs.add(streamModel.getStreamUri());
        }
    }

    /**
     * Method to handle a request to retrieve the context service representation of an Yggdrasil environment.
     * @param context: the Vert.x routing context of the request
     */
    public void handleContextServiceRepresentation(RoutingContext context) {
      LOGGER.info("Handling Context Service Representation retrieval action..." + " Context: " + context);
      // TODO: Implement the logic to retrieve the context service representation of an Yggdrasil environment
    }


    /**
     * Handles WebSub subscription verification requests that occur when the subscription for receiving Context Stream updates is created.
     * @param context: the Vert.x routing context of the request
     */
    public void handleVerifyContextStreamSubscription(RoutingContext context) {
      MultiMap params = context.request().params();
      
      String mode = params.get("hub.mode");
      String challenge = params.get("hub.challenge");
      String topic = params.get("hub.topic");
      
      // Verify that this is a topic we're interested in
      if ("subscribe".equals(mode) && challenge != null && managedContextStreamURIs.contains(topic)) {
          // If the topic is one that is registered with the Context Management service, we still have to
          // verify with the Context Management Verticle that it is being actively tracked for updates before sending the challenge response
          this.contextMessageBox.sendMessage(new ContextMessage.VerifyContextStreamSubscription(topic))
            .onSuccess(r -> {
                LOGGER.info("Subscription verified for topic: " + topic);
                context.response()
                     .setStatusCode(200)
                     .putHeader("Content-Type", "text/plain")
                     .end(challenge);
            })
            .onFailure(t -> {
                LOGGER.error("Error verifying subscription for topic: " + topic, t);
                context.response().setStatusCode(404).end();
            });
      } else {
          context.response().setStatusCode(404).end();
          LOGGER.warn("Invalid subscription verification request for topic: " + topic + ". No such topic is managed by the Context Management service.");
      }
    }


    /**
     * Handles WebSub content delivery requests.
     */
    public void handleContextStreamUpdate(RoutingContext routingContext) {
        try {
            // Extract the stream URI from the Link header
            String linkHeader = routingContext.request().getHeader("Link");
            if (linkHeader == null) {
                LOGGER.warn("Missing Link header in WebSub notification");
                routingContext.response().setStatusCode(400).end("Missing Link header");
                return;
            }
            
            // Check first that the updated stream is one currently managed by the Context Management service
            String streamUri = extractSelfLink(linkHeader);
            if (streamUri == null || !managedContextStreamURIs.contains(streamUri)) {
                LOGGER.warn("Unknown or invalid stream URI in Link header: " + linkHeader);
                routingContext.response().setStatusCode(404).end("Unknown stream");
                return;
            }
            
            // Check that the update payload is present
            final var requestBody = routingContext.body().asJsonObject();
            if (!requestBody.containsKey("hub.payload")) {
                LOGGER.warn("Missing payload in WebSub notification for stream: " + streamUri);
                routingContext.response().setStatusCode(400).end("Missing payload");
                return;
            }

            // Check that the payload contains the graph and timestamp
            JsonObject payloadObj = requestBody.getJsonObject("hub.payload");
            String graphSerialized = payloadObj.getString("graph_serialized");
            long timestampMs = payloadObj.getLong("timestamp_ms", Long.valueOf(0));
            
            if (graphSerialized == null || graphSerialized.isEmpty()) {
                LOGGER.warn("Received empty graph in payload for stream: " + streamUri);
                routingContext.response()
                     .setStatusCode(400)
                     .putHeader("Content-Type", "application/json")
                     .end(new JsonObject().put("error", "Empty graph").encode());
                return;
            }
            
            // Send a message to the Context Management Verticle to process the graph update
            // If the update succeeds, send a 200 OK response, otherwise send back the failure code received from the Context Management Verticle
            this.contextMessageBox.sendMessage(new ContextMessage.ContextStreamUpdate(streamUri, graphSerialized, timestampMs))
              .onSuccess(r -> {
                  LOGGER.info("Received and processed graph update for stream: " + " (" + streamUri + ") with timestamp: " + timestampMs);
                  LOGGER.info("Graph update was: \n" + graphSerialized);

                  routingContext.response().setStatusCode(200).end();
              })
              .onFailure(t -> {
                  // set a status code and construct a JSON payload with the error message, if it exists or the phrase "unknown error" otherwise
                  var statusCode = 500;
                  var errorMessage = "unknown error";

                  if (t instanceof ReplyException e) {
                      statusCode = e.failureCode();

                      if (e.getMessage() != null) {
                          errorMessage = e.getMessage();
                      }
                  }
                  
                  LOGGER.error("Error handling WebSub callback", t);
                  routingContext.response()
                      .setStatusCode(statusCode)
                      .putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("error", errorMessage).encode());
              });
        } catch (Exception e) {
            LOGGER.error("Error handling WebSub callback", e);
            routingContext.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }
    
    /**
     * Extracts the self link from a Link header.
     *
     * @param linkHeader The Link header value
     * @return The URI from the Link header with rel=self, or null if not found
     */
    private String extractSelfLink(String linkHeader) {
        Matcher matcher = LINK_PATTERN.matcher(linkHeader);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
