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
import org.hyperagents.yggdrasil.model.interfaces.ContextDomainModel;
import org.hyperagents.yggdrasil.utils.ContextManagementConfig;
import org.hyperagents.yggdrasil.utils.RdfModelUtils;

import ch.unisg.ics.interactions.wot.td.ThingDescription;
import ch.unisg.ics.interactions.wot.td.affordances.ActionAffordance;
import ch.unisg.ics.interactions.wot.td.affordances.Form;
import ch.unisg.ics.interactions.wot.td.schemas.ObjectSchema;
import ch.unisg.ics.interactions.wot.td.schemas.StringSchema;
import ch.unisg.ics.interactions.wot.td.security.SecurityScheme;
import ch.unisg.ics.interactions.wot.td.io.TDGraphWriter;
import ch.unisg.ics.interactions.wot.td.schemas.ArraySchema;
import ch.unisg.ics.interactions.wot.td.schemas.NumberSchema;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;

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
    private final ContextMessageBox contextMessageBox;

    // List of ContextStreams that are being managed by the Context Management service
    private final List<String> managedContextStreamURIs = new ArrayList<>();
    private final List<String> managedContextDomainURIs = new ArrayList<>();

    public ContextMgmtHandler(Vertx vertx, final ContextManagementConfig contextManagementConfig) {
        this.vertx = vertx;
        this.contextMessageBox = new ContextMessageBox(vertx.eventBus(), contextManagementConfig);

        // Initialize the ContextStreams that are being managed by the Context Management service
        for (ContextStreamModel streamModel : contextManagementConfig.getContextStreams()) {
            managedContextStreamURIs.add(streamModel.getStreamUri());
        }

        // Initialize the ContextDomains that are being managed by the Context Management service
        for (ContextDomainModel domainModel : contextManagementConfig.getContextDomains()) {
            managedContextDomainURIs.add(domainModel.getDomainUri());
        }
        LOGGER.info("Context Management Handler initialized with {} managed Context Streams and {} managed Context Domains.",
            managedContextStreamURIs.size(), managedContextDomainURIs.size());
    }
    /**
     * Method to handle a request to retrieve the static context graph managed by the Context Management Service.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleStaticContextRetrieval(RoutingContext context) {
        LOGGER.info("Handling Static Context retrieval action...");
        
        this.contextMessageBox.sendMessage(new ContextMessage.GetStaticContext())
            .onSuccess(r -> {
                LOGGER.info("Static Context retrieved successfully");
                context.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/turtle")
                    .end(r.body().toString());
            })
            .onFailure(t -> {
                LOGGER.error("Error retrieving Static Context", t);
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", t.getMessage()).encode());
            });
    }

    /**
     * Method to handle a request to retrieve the profiled context graph managed by the Context Management Service.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleProfiledContextRetrieval(RoutingContext context) {
        LOGGER.info("Handling Profiled Context retrieval action...");
        
        // Get the context assertion type from query parameters
        String contextAssertionType = context.request().getParam("contextAssertionType");
        if (contextAssertionType == null || contextAssertionType.isEmpty()) {
            LOGGER.warn("Missing or empty contextAssertionType");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing required 'contextAssertionType' parameter").encode());
            return;
        }

        this.contextMessageBox.sendMessage(new ContextMessage.GetProfiledContext(contextAssertionType))
            .onSuccess(r -> {
                LOGGER.info("Profiled Context retrieved successfully for type: " + contextAssertionType);
                context.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/turtle")
                    .end(r.body().toString());
            })
            .onFailure(t -> {
                LOGGER.error("Error retrieving Profiled Context for type: " + contextAssertionType, t);
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", t.getMessage()).encode());
            });
    }

    public void handleContextStreamRepresentation(RoutingContext context) {
      LOGGER.info("Handling Context Service Representation retrieval action...");
        final String streamURI = context.request().absoluteURI();
        if (streamURI == null || streamURI.isEmpty()) {
            LOGGER.warn("Missing or empty stream URI in request");
            context.response().setStatusCode(400).end("Missing or empty stream URI");
            return;
        }
        this.contextMessageBox.sendMessage(new ContextMessage.GetContextStreamRepresentation(streamURI))
            .onSuccess(r -> {
                LOGGER.info("Context Service Representation retrieved successfully.");
                context.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(r.body().toString());
            })
            .onFailure(t -> {
                LOGGER.error("Error retrieving Context Service Representation", t);
                context.response().setStatusCode(500).end();
            });
      
    }
    
    /**
     * Method to handle a request to retrieve the context service representation of an Yggdrasil environment.
     * @param context: the Vert.x routing context of the request
     */
    public void handleContextServiceRepresentation(RoutingContext context) {
      LOGGER.info("Handling Context Service Representation retrieval action...");

      String baseUri = context.request().absoluteURI();
      if (baseUri.endsWith("/")) {
          baseUri = baseUri.substring(0, baseUri.length() - 1);
      }

      ThingDescription.Builder td = new ThingDescription.Builder("Context Management Service")
          .addThingURI(baseUri + "#contextservice")
          .addSemanticType("https://purl.org/hmas/ContextManagementService")
          .addSecurityScheme("nosec", SecurityScheme.getNoSecurityScheme());
          
      // Property affordances
      td.addAction(
          new ActionAffordance.Builder("getStaticContext",
              new Form.Builder(baseUri + "/graphs/static")
                  .setMethodName("GET")
                  .setContentType("text/turtle")
                  .build())
              .addSemanticType("https://purl.org/hmas/StaticContextProperty")
              .build()
      );
      
      td.addAction(
          new ActionAffordance.Builder("getProfiledAssertion",
              new Form.Builder(baseUri + "/graphs/profile")
                  .setMethodName("GET")
                  .setContentType("text/turtle")
                  .build())
              .addSemanticType("https://purl.org/hmas/ProfiledContextProperty")
              .build()
      );

    //   Action affordances for validation
      td.addAction(
          new ActionAffordance.Builder("containsAssertion",
              new Form.Builder(baseUri + "/assertions/contains")
                  .setMethodName("POST")
                  .setContentType("application/json")
                  .build())
              .addSemanticType("https://purl.org/hmas/ContainsAssertionAction")
              .addInputSchema(
                  new ObjectSchema.Builder()
                      .addProperty("type", new StringSchema.Builder().build())
                      .build())
              .build()
      );
      
    //   Action affordances for managing context
      td.addAction(
          new ActionAffordance.Builder("addStaticContext",
              new Form.Builder(baseUri + "/graphs/static")
                  .setMethodName("POST")
                  .setContentType("text/turtle")
                  .build())
              .addSemanticType("https://purl.org/hmas/AddStaticContextAction")
              .build()
      );
      
      td.addAction(
          new ActionAffordance.Builder("addProfiledContext",
              new Form.Builder(baseUri + "/graphs/profiled")
                  .setMethodName("POST")
                  .setContentType("text/turtle")
                  .build())
              .addSemanticType("https://purl.org/hmas/AddProfiledContextAction")
              .build()
      );
      
    //   Action affordances for context streams
      td.addAction(
          new ActionAffordance.Builder("addContextStream",
              new Form.Builder(baseUri + "/streams")
                  .setMethodName("POST") 
                  .setContentType("application/json")
                  .build())
              .addSemanticType("https://purl.org/hmas/AddContextStreamAction")
              .addInputSchema(
                  new ObjectSchema.Builder()
                      .addProperty("streamURI", new StringSchema.Builder().build())
                      .addProperty("streamConfig", new ObjectSchema.Builder().build())
                      .build())
              .build()
      );
      
      td.addAction(
          new ActionAffordance.Builder("removeContextStream",
              new Form.Builder(baseUri + "/streams")
                  .setMethodName("DELETE")
                  .build())
              .addSemanticType("https://purl.org/hmas/RemoveContextStreamAction")
              .addUriVariable("streamURI", new StringSchema.Builder().build())
              .build()
      );
      
        //   Action affordances for context domains
        td.addAction(
            new ActionAffordance.Builder("addContextDomain",
                new Form.Builder(baseUri + "/domains")
                    .setMethodName("POST")
                    .setContentType("application/json")
                    .build())
                .addSemanticType("https://purl.org/hmas/AddContextDomainAction")
                .addInputSchema(
                    new ObjectSchema.Builder()
                        .addProperty("contextDomainURI", new StringSchema.Builder().build())
                        .addProperty("contextDomainConfig", 
                            new ObjectSchema.Builder()
                                .addProperty("engineConfigURL", new StringSchema.Builder().build())
                                .addProperty("membershipRules", new ArraySchema.Builder()
                                    .addItem(new StringSchema.Builder().build()).build())
                                .addProperty("requiredStreamURIs", new ArraySchema.Builder()
                                    .addItem(new StringSchema.Builder().build()).build())
                                .build())
                        .build())
                .build()
        );
      
      td.addAction(
          new ActionAffordance.Builder("removeContextDomain",
              new Form.Builder(baseUri + "/domains")
                  .setMethodName("DELETE")
                  .build())
              .addSemanticType("https://purl.org/hmas/RemoveContextDomainAction")
              .addUriVariable("domainURI", new StringSchema.Builder().build())
              .build()
      );
      
      td.addAction(
          new ActionAffordance.Builder("addMembershipRule",
              new Form.Builder(baseUri + "/domains/{domainURI}/rules")
                  .setMethodName("POST")
                  .setContentType("application/json")
                  .build())
              .addSemanticType("https://purl.org/hmas/AddMembershipRuleAction")
              .addUriVariable("domainURI", new StringSchema.Builder().build())
              .addInputSchema(
                  new ObjectSchema.Builder()
                      .addProperty("ruleContent", new StringSchema.Builder().build())
                      .build())
              .build()
      );
      
      td.addAction(
          new ActionAffordance.Builder("removeMembershipRule",
              new Form.Builder(baseUri + "/domains/rules")
                  .setMethodName("DELETE")
                  .build())
              .addSemanticType("https://purl.org/hmas/RemoveMembershipRuleAction") 
              .addUriVariable("domainURI", new StringSchema.Builder().build())
              .addUriVariable("ruleID", new StringSchema.Builder().build())
              .build()
      );

      td.addAction(
          new ActionAffordance.Builder("validateContextBasedAccess", 
              new Form.Builder(baseUri + "/access/validate")
                  .setMethodName("POST")
                  .setContentType("application/json")
                  .build())
              .addSemanticType("https://purl.org/hmas/ValidateContextBasedAccessAction")
              .addInputSchema(
                  new ObjectSchema.Builder()
                      .addProperty("accessRequesterURI", new StringSchema.Builder().build())
                      .addProperty("accessedResourceURI", new StringSchema.Builder().build())
                      .build())
              .build()
      );

      td.addAction(
          new ActionAffordance.Builder("updateContextStream",
              new Form.Builder(baseUri + "/streams/updates")
                  .setMethodName("POST")
                  .setContentType("application/json")
                  .build())
              .addSemanticType("https://purl.org/hmas/UpdateContextStreamAction")
              .addInputSchema(
                  new ObjectSchema.Builder()
                      .addProperty("streamURI", new StringSchema.Builder().build())
                      .addProperty("streamContent", new StringSchema.Builder().build())
                      .addProperty("updateTimestamp", new NumberSchema.Builder().build())
                      .build())
              .build()
      );

      // Add RDF metadata about context management
      Model serviceMetadata = new LinkedHashModel();
      IRI serviceIri = RdfModelUtils.createIri(baseUri + "#contextservice");
      
      // Link to context streams
      for (String streamURI : managedContextStreamURIs) {
          serviceMetadata.add(
              serviceIri,
              RdfModelUtils.createIri("https://purl.org/hmas/hasContextStream"),
              RdfModelUtils.createIri(streamURI)
          );
      }

      for (String domainURI : managedContextDomainURIs) {
          serviceMetadata.add(
              serviceIri,
              RdfModelUtils.createIri("https://purl.org/hmas/hasContextDomain"), 
              RdfModelUtils.createIri(domainURI)
          );
      }

      // Add metadata
      td.addGraph(serviceMetadata);
      
      // Add profile metadata
      Model profileModel = new LinkedHashModel();
      profileModel.add(
          serviceIri,
          RdfModelUtils.createIri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
          RdfModelUtils.createIri("https://purl.org/hmas/ResourceProfile")
      );
      profileModel.add(
          serviceIri, 
          RdfModelUtils.createIri("https://purl.org/hmas/isProfileOf"),
          RdfModelUtils.createIri(baseUri)
      );
      td.addGraph(profileModel);

      // Serialize TD with proper namespaces
      String serializedTD = new TDGraphWriter(td.build())
          .setNamespace("td", "https://www.w3.org/2019/wot/td#")
          .setNamespace("htv", "http://www.w3.org/2011/http#") 
          .setNamespace("hctl", "https://www.w3.org/2019/wot/hypermedia#")
          .setNamespace("wotsec", "https://www.w3.org/2019/wot/security#")
          .setNamespace("js", "https://www.w3.org/2019/wot/json-schema#")
          .setNamespace("hmas", "https://purl.org/hmas/")
          .setNamespace("websub", "https://purl.org/hmas/websub/")
          .write();
      
      context.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/td+json")
          .end(serializedTD);
    }

    public void handleGetContextDomain(RoutingContext context) {
        LOGGER.info("Handling Context Domain retrieval action..." + " Context: " + context);
        final String contextURI = context.request().absoluteURI();
        if (contextURI == null || contextURI.isEmpty()) {
            LOGGER.warn("Context URI is missing or empty");
            context.response().setStatusCode(400).end("Missing or empty context URI");
            return;
        }

        this.contextMessageBox.sendMessage(new ContextMessage.ContextDomainRepresentation(contextURI))
            .onSuccess(r -> {
                LOGGER.info("Contexts retrieved successfully");
                context.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(r.body());
            })
            .onFailure(t -> {
                LOGGER.error("Error retrieving contexts", t);
                context.response().setStatusCode(500).end();
            });
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
            if (!requestBody.containsKey("graph_serialized") || !requestBody.containsKey("timestamp_ms")) {
                LOGGER.warn("Missing graph payload or update timestamp in WebSub notification for stream update: " + streamUri);
                routingContext.response().setStatusCode(400).end("Missing payload");
                return;
            }

            // Check that the payload contains the graph and timestamp
            String graphSerialized = requestBody.getString("graph_serialized");
            long timestampMs = requestBody.getLong("timestamp_ms", Long.valueOf(0));
            
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

    /**
     * Method to handle a request to validate if the Context Management service maintains instances 
     * of a given ContextAssertion type in both static and profiled context repositories.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleContainsAssertion(RoutingContext context) {
        LOGGER.info("Handling ContainsAssertion validation action...");
        
        // Get the request body as JSON
        JsonObject requestBody = context.body().asJsonObject();
        if (requestBody == null) {
            LOGGER.warn("Missing request body for containsAssertion");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing request body").encode());
            return;
        }
        
        // Get the context assertion type from the request body
        String contextAssertionType = requestBody.getString("type");
        if (contextAssertionType == null || contextAssertionType.isEmpty()) {
            LOGGER.warn("Missing or empty context assertion type parameter");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing required 'type' parameter").encode());
            return;
        }

        this.contextMessageBox.sendMessage(new ContextMessage.ContainsAssertion(contextAssertionType))
            .onSuccess(r -> {
                LOGGER.info("ContainsAssertion validation completed for type: " + contextAssertionType);

                JsonObject result = new JsonObject(r.body().toString());
                context.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(result.encode());
            })
            .onFailure(t -> {
                LOGGER.error("Error validating ContainsAssertion for type: " + contextAssertionType, t);
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", t.getMessage()).encode());
            });
    }

    /**
     * Method to handle a request to add RDF data to the static context repository.
     * The request body should contain RDF data in Turtle format.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleAddStaticContext(RoutingContext context) {
        LOGGER.info("Handling AddStaticContext action...");
        
        // Get the request body as string (expecting Turtle RDF)
        String rdfContent = context.body().asString();
        if (rdfContent == null || rdfContent.trim().isEmpty()) {
            LOGGER.warn("Missing or empty RDF content in request body");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing or empty RDF content in request body").encode());
            return;
        }

        // Validate that the content-type is text/turtle
        String contentType = context.request().getHeader("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("text/turtle")) {
            LOGGER.warn("Invalid content type. Expected 'text/turtle', got: " + contentType);
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Invalid content type. Expected 'text/turtle'").encode());
            return;
        }

        this.contextMessageBox.sendMessage(new ContextMessage.AddStaticContext(rdfContent))
            .onSuccess(r -> {
                LOGGER.info("Static context added successfully");
                context.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Static context added successfully")
                        .put("addedStatements", r.body().toString())
                        .encode());
            })
            .onFailure(t -> {
                LOGGER.error("Error adding static context", t);
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", t.getMessage()).encode());
            });
    }

    /**
     * Method to handle a request to add RDF data to the profiled context repository.
     * The request body should contain RDF data in Turtle format with profiled ContextAssertions,
     * ContextAnnotations, and ContextEntities.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleAddProfiledContext(RoutingContext context) {
        LOGGER.info("Handling AddProfiledContext action...");
        
        String rdfContent = context.body().asString();
        if (rdfContent == null || rdfContent.trim().isEmpty()) {
            LOGGER.warn("Missing or empty RDF content in request body");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing or empty RDF content in request body").encode());
            return;
        }

        String contentType = context.request().getHeader("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("text/turtle")) {
            LOGGER.warn("Invalid content type for addProfiledContext. Expected 'text/turtle', got: " + contentType);
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Invalid content type. Expected 'text/turtle'").encode());
            return;
        }

        this.contextMessageBox.sendMessage(new ContextMessage.AddProfiledContext(rdfContent))
            .onSuccess(r -> {
                LOGGER.info("Profiled context added successfully");
                context.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Profiled context added successfully")
                        .put("addedStatements", r.body().toString())
                        .encode());
            })
            .onFailure(t -> {
                LOGGER.error("Error adding profiled context", t);
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", t.getMessage()).encode());
            });
    }

    /**
     * Method to handle a request to add and track a new ContextStream.
     * The request body should contain JSON with streamURI and streamConfig.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleAddContextStream(RoutingContext context) {
        LOGGER.info("Handling AddContextStream action...");
        
        JsonObject requestBody = context.body().asJsonObject();
        if (requestBody == null) {
            LOGGER.warn("Missing request body for addContextStream");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing request body").encode());
            return;
        }

        String contentType = context.request().getHeader("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            LOGGER.warn("Invalid content type for addContextStream. Expected 'application/json', got: " + contentType);
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Invalid content type. Expected 'application/json'").encode());
            return;
        }

        String streamURI = requestBody.getString("streamURI");
        JsonObject streamConfigJson = requestBody.getJsonObject("streamConfig");
        
        if (streamURI == null || streamURI.trim().isEmpty()) {
            LOGGER.warn("Missing or empty streamURI in request body");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing required 'streamURI' parameter").encode());
            return;
        }

        if (streamConfigJson == null) {
            LOGGER.warn("Missing or empty streamConfig in request body");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing required 'streamConfig' parameter").encode());
            return;
        }
        String streamConfig = streamConfigJson.encode();

        this.contextMessageBox.sendMessage(new ContextMessage.AddContextStream(streamURI, streamConfig))
            .onSuccess(r -> {
                if (!managedContextStreamURIs.contains(streamURI)) {
                    LOGGER.info("Adding new stream URI to managed context streams: " + streamURI);
                    managedContextStreamURIs.add(streamURI);
                } else {
                    LOGGER.info("Stream URI already exists in managed context streams: " + streamURI);
                }
                LOGGER.info("Updated managed context streams: " + managedContextStreamURIs);

                context.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Context stream added and indexed successfully")
                        .put("streamURI", streamURI)
                        .encode());
            })
            .onFailure(t -> {
                LOGGER.error("Error adding context stream", t);
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", t.getMessage()).encode());
            });
    }
    
    /**
     * Method to handle a request to remove a context stream from the Context Management Service.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleRemoveContextStream(RoutingContext context) {
        LOGGER.info("Handling RemoveContextStream action...");
        
        String streamURI = context.pathParam("streamid");
        if (streamURI == null || streamURI.trim().isEmpty()) {
            LOGGER.warn("Missing streamURI path parameter");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing required 'streamid' path parameter").encode());
            return;
        }

        // Reconstruct full stream URI from path parameter
        String baseUrl = context.request().absoluteURI().substring(0, 
            context.request().absoluteURI().lastIndexOf("/context/streams/"));
        String fullStreamURI = baseUrl + "/context/streams/" + streamURI;

        if (!managedContextStreamURIs.contains(fullStreamURI)) {
            LOGGER.warn("Stream URI is not currently managed: " + fullStreamURI);
            context.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Context stream not found or not currently managed").encode());
            return;
        }

        this.contextMessageBox.sendMessage(new ContextMessage.RemoveContextStream(fullStreamURI))
            .onSuccess(r -> {
                managedContextStreamURIs.remove(fullStreamURI);
                LOGGER.info("Removed stream URI from managed context streams: " + fullStreamURI);
                LOGGER.info("Updated managed context streams: " + managedContextStreamURIs);

                context.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Context stream removed successfully")
                        .put("streamURI", fullStreamURI)
                        .encode());
            })
            .onFailure(t -> {
                LOGGER.error("Error removing context stream", t);
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", t.getMessage()).encode());
            });
    }
    
    /**
     * Method to handle a request to add a new ContextDomain to the Context Management Service.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleAddContextDomain(RoutingContext context) {
        LOGGER.info("Handling Context Domain addition action...");
        
        JsonObject requestBody = context.body().asJsonObject();
        if (requestBody == null) {
            LOGGER.warn("Request body is null");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Request body is required").encode());
            return;
        }

        // Extract required fields from the request body
        String contextDomainURI = requestBody.getString("contextDomainURI");
        JsonObject contextDomainConfigJson = requestBody.getJsonObject("contextDomainConfig");

        if (contextDomainURI == null || contextDomainURI.trim().isEmpty()) {
            LOGGER.warn("Missing or empty contextDomainURI in request body");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing required 'contextDomainURI' parameter").encode());
            return;
        }

        if (contextDomainConfigJson == null) {
            LOGGER.warn("Missing or empty contextDomainConfig in request body");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing required 'contextDomainConfig' parameter").encode());
            return;
        }

        String contextDomainConfig = contextDomainConfigJson.encode();

        // Send the message to the Context Management Verticle
        this.contextMessageBox.sendMessage(new ContextMessage.AddContextDomain(
                contextDomainURI, contextDomainConfig))
            .onSuccess(r -> {
                LOGGER.info("Context domain added successfully: " + contextDomainURI);
                context.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Context domain added successfully")
                        .put("contextDomainURI", contextDomainURI)
                        .encode());
                // Add the new domain URI to the managed context domains
                if (!managedContextDomainURIs.contains(contextDomainURI)) {
                    managedContextDomainURIs.add(contextDomainURI);
                    LOGGER.info("Added new context domain URI to managed context domains: " + contextDomainURI);
                } else {
                    LOGGER.info("Context domain URI already exists in managed context domains: " + contextDomainURI);
                }
                LOGGER.info("Updated managed context domains: " + managedContextDomainURIs);
            })
            .onFailure(t -> {
                LOGGER.error("Error adding context domain: " + contextDomainURI, t);
                if (t instanceof ReplyException) {
                    ReplyException re = (ReplyException) t;
                    context.response()
                        .setStatusCode(re.failureCode())
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", re.getMessage()).encode());
                } else {
                    context.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", "Internal server error").encode());
                }
            });
    }

    /**
     * Method to handle a request to remove a context domain.
     * 
     * @param context The Vert.x routing context of the request
     */
    public void handleRemoveContextDomain(RoutingContext context) {
        LOGGER.info("Handling RemoveContextDomain action...");
        
        String domainURI = context.pathParam("domainid");
        if (domainURI == null || domainURI.trim().isEmpty()) {
            LOGGER.warn("Missing domainURI path parameter");
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Missing required 'domainid' path parameter").encode());
            return;
        }

        // Reconstruct full domain URI from path parameter
        String baseUrl = context.request().absoluteURI().substring(0, 
            context.request().absoluteURI().lastIndexOf("/context/domains/"));
        String fullDomainURI = baseUrl + "/context/domains/" + domainURI;

        if (!managedContextDomainURIs.contains(fullDomainURI)) {
            LOGGER.warn("Domain URI is not currently managed: " + fullDomainURI);
            context.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "Context domain not found or not currently managed").encode());
            return;
        }

        this.contextMessageBox.sendMessage(new ContextMessage.RemoveContextDomain(fullDomainURI))
            .onSuccess(r -> {
                managedContextDomainURIs.remove(fullDomainURI);
                context.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Context domain removed successfully")
                        .put("contextDomainURI", fullDomainURI)
                        .encode());
            })
            .onFailure(t -> {
                LOGGER.error("Error removing context domain: " + fullDomainURI, t);
                if (t instanceof ReplyException) {
                    ReplyException re = (ReplyException) t;
                    context.response()
                        .setStatusCode(re.failureCode())
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", re.getMessage()).encode());
                } else {
                    context.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("error", "Internal server error").encode());
                }
            });
    }
}
