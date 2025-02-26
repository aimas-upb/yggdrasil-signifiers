package org.hyperagents.yggdrasil.context.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.context.ContextStream;
import org.hyperagents.yggdrasil.model.interfaces.ContextStreamModel;
import org.hyperagents.yggdrasil.utils.ContextManagementConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ContextMgmtHandler {
    private static final Logger LOGGER = LogManager.getLogger(ContextMgmtHandler.class);
    
    // Pattern to extract URI from Link header
    private static final Pattern LINK_PATTERN = Pattern.compile("<([^>]*)>\\s*;\\s*rel\\s*=\\s*\"?self\"?");
    
    private final Vertx vertx;
    private final Map<String, ContextStream> streamMap = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public ContextMgmtHandler(Vertx vertx, final ContextManagementConfig contextManagementConfig) {
        this.vertx = vertx;
      
        List<ContextStreamModel> streamConfigs = contextManagementConfig.getContextStreams();
        
        for (var streamInfo : streamConfigs) {
            // Create a new ContextStream for each stream URI
            String streamURI = streamInfo.getStreamUri();
            ContextStream stream = new ContextStream(streamURI, streamInfo.getOntologyUrl(), streamInfo.getAssertions());
            streamMap.put(streamURI, stream);
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
    public void handleSubscriptionVerification(RoutingContext context) {
      MultiMap params = context.request().params();
      
      String mode = params.get("hub.mode");
      String challenge = params.get("hub.challenge");
      String topic = params.get("hub.topic");
      
      // Verify that this is a topic we're interested in
      if ("subscribe".equals(mode) && challenge != null && streamMap.containsKey(topic)) {
          context.response()
                 .setStatusCode(200)
                 .putHeader("Content-Type", "text/plain")
                 .end(challenge);
          LOGGER.info("Subscription verified for topic: " + topic);
      } else {
          context.response().setStatusCode(404).end();
          LOGGER.warn("Invalid subscription verification request for topic: " + topic);
      }
    }


    /**
     * Handles WebSub content delivery requests.
     */
    public void handleContentDelivery(RoutingContext context) {
        try {
            // Extract the stream URI from the Link header
            String linkHeader = context.request().getHeader("Link");
            if (linkHeader == null) {
                LOGGER.warn("Missing Link header in WebSub notification");
                context.response().setStatusCode(400).end("Missing Link header");
                return;
            }
            
            String streamUri = extractSelfLink(linkHeader);
            if (streamUri == null || !streamMap.containsKey(streamUri)) {
                LOGGER.warn("Unknown or invalid stream URI in Link header: " + linkHeader);
                context.response().setStatusCode(404).end("Unknown stream");
                return;
            }
            
            ContextStream stream = streamMap.get(streamUri);
            final var requestBody = context.body().asJsonObject();
            
            if (!requestBody.containsKey("hub.payload")) {
                LOGGER.warn("Missing payload in WebSub notification for stream: " + streamUri);
                context.response().setStatusCode(400).end("Missing payload");
                return;
            }

            JsonObject payloadObj = requestBody.getJsonObject("hub.payload");
            
            String graphSerialized = payloadObj.getString("graph_serialized");
            long timestampMs = payloadObj.getLong("timestamp_ms", Long.valueOf(0));
            
            if (graphSerialized == null || graphSerialized.isEmpty()) {
                LOGGER.warn("Received empty graph in payload for stream: " + streamUri);
                context.response()
                     .setStatusCode(400)
                     .putHeader("Content-Type", "application/json")
                     .end(new JsonObject().put("error", "Empty graph").encode());
                return;
            }
            
            // Parse the RDF graph from Turtle serialization
            Graph graph = RDFParser.create()
                    .fromString(graphSerialized)
                    .lang(Lang.TURTLE)
                    .toGraph();
            
            // Put the graph into the corresponding ContextStream
            stream.put(graph, timestampMs);
            
            LOGGER.info("Received and processed graph update for stream: " + stream.getStreamName() + 
                    " (" + streamUri + ") with timestamp: " + timestampMs);
            context.response().setStatusCode(200).end();
            
        } catch (Exception e) {
            LOGGER.error("Error handling WebSub callback", e);
            context.response()
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
