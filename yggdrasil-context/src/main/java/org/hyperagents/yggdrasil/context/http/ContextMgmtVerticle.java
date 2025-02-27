package org.hyperagents.yggdrasil.context.http;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.common.exception.ValidationException;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.context.ContextDomain;
import org.hyperagents.yggdrasil.context.ContextStream;
import org.hyperagents.yggdrasil.eventbus.messageboxes.ContextMessageBox;
import org.hyperagents.yggdrasil.eventbus.messages.ContextMessage;
import org.hyperagents.yggdrasil.model.interfaces.ContextDomainModel;
import org.hyperagents.yggdrasil.model.interfaces.ContextStreamModel;
import org.hyperagents.yggdrasil.model.interfaces.Environment;
import org.hyperagents.yggdrasil.utils.ContextManagementConfig;
import org.hyperagents.yggdrasil.utils.HttpInterfaceConfig;
import org.hyperagents.yggdrasil.utils.WebSubConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class ContextMgmtVerticle extends AbstractVerticle {
    // Logger
    private static final Logger LOGGER = LogManager.getLogger(ContextMgmtVerticle.class);

    // the configuration object for the context management service
    private ContextManagementConfig contextManagementConfig;

    // The URI of the context management service
    private String serviceURI;

    // The RDF store for the static context information
    private SailRepository staticContextRepo;

    // The RDF store for the profiled context information
    private SailRepository profiledContextRepo;

    // The list of ContextStream objects that represent the dynamic context information streams
    private final Map<String, ContextStream> contextStreamMap = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    // A Map linking the URI of the ContextDomain to the ContextDomain object
    private Map<String, ContextDomain> contextDomains;
    
    // A SailRepository object containing Named Graphs with SHACL shapes that define the context access conditions required for 
    // access to a particular Artifact.
    private SailRepository contextAccessConditionsRepo;
    private Map<String, String> artifactPolicies;

    @Override
    public void start(final Promise<Void> startPromise) {
        // retrieve the configuration object for the context management service
        this.contextManagementConfig = this.vertx.sharedData()
            .<String, ContextManagementConfig>getLocalMap("context-management-config")
            .get("default");
        final var environment = this.vertx.sharedData()
            .<String, Environment>getLocalMap("environment")
            .get("default");
        final var httpConfig = this.vertx.sharedData()
            .<String, HttpInterfaceConfig>getLocalMap("http-config")
            .get("default");
        final var notificationConfig = this.vertx.sharedData()
            .<String, WebSubConfig>getLocalMap("notification-config")
            .get("default");

        // initialize the map of context domains, the map of dynamic context assertions and the map of artifact policies
        contextDomains = new HashMap<>();
        artifactPolicies = new HashMap<>();
        
        // get the service URI from the configuration
        this.serviceURI = contextManagementConfig.getServiceURI();

        try {
            setupStaticContextRepo(contextManagementConfig);
            setupProfiledContextRepo(contextManagementConfig);
            
            // Set up the Context Stream monitoring by subscribing to the WebSub notifications for ContextStream updates
            initializeContextStreams(contextManagementConfig, httpConfig, notificationConfig);

            // Set up the Context Domain Groups and Context Domains
            setupCDGMembershipRepo(contextManagementConfig);

            // Set up the context access conditions repository
            setupContextAccessConditionsRepo(contextManagementConfig, httpConfig, environment);
        }
        catch (Exception e) {
            LOGGER.error("Error setting up the context management service: " + e.getMessage());
            startPromise.fail(e);
        }
        
        // setup handling of messages from the event bus
        final var contextMessageBox = new ContextMessageBox(this.vertx.eventBus(), this.contextManagementConfig);
        contextMessageBox.init();
        setupRequestHandling(contextMessageBox);
        
        startPromise.complete();
    }

    private void setupStaticContextRepo(ContextManagementConfig config) throws Exception {
        // First, set up the static context repository. We set it up as a SailRepository over an in-memory store.
        staticContextRepo = new SailRepository(new MemoryStore());
        
        try {
            if (config.getStaticContextGraphURI() == null) {
                LOGGER.warn("No source of default static context information provided in the configuration.");
                return;
            }
            
            URL staticContextURL = URI.create(config.getStaticContextGraphURI()).toURL();

            // open the URL stream and load the contents of the RDF file (in turtle format) into the static context repository
            staticContextRepo.getConnection().add(staticContextURL, "http://example.org/", RDFFormat.TURTLE);

        } catch (MalformedURLException e) {
            LOGGER.error("Malformed URL for source of default static context information: " + config.getStaticContextGraphURI() + ". Reason: " + e.getMessage());
            throw new Exception("Error setting up static context graph", e);
        } catch (RDFParseException e) {
            LOGGER.error("Error parsing the RDF content of the default static context information: " + config.getStaticContextGraphURI() + ". Reason: " + e.getMessage());
            throw new Exception("Error setting up static context graph", e);
        } catch (RepositoryException e) {
            LOGGER.error("Error adding the RDF content of the default static context information to the static repository: " + ". Reason: " + e.getMessage());
            throw new Exception("Error setting up static context graph", e);
        } catch (IOException e) {
            LOGGER.error("Error reading the RDF content of the default static context information from the source: " + config.getStaticContextGraphURI() + ". Reason: " + e.getMessage());
            throw new Exception("Error setting up static context graph", e);
        } 
    }

    private void setupProfiledContextRepo(ContextManagementConfig config) throws Exception {
        // Set up the profiled context repository. We set it up as a SailRepository over an in-memory store.
        profiledContextRepo = new SailRepository(new MemoryStore());
        
        try {
            if (config.getProfiledContextGraphURI() == null) {
                LOGGER.warn("No source of profiled context information provided in the configuration.");
                return;
            }
            
            URL profiledContextURL = URI.create(config.getProfiledContextGraphURI()).toURL();
            
            // Open the URL stream and load the contents of the RDF file (in turtle format) into the profiled context repository
            profiledContextRepo.getConnection().add(profiledContextURL, "http://example.org/", RDFFormat.TURTLE);

        } catch (MalformedURLException e) {
            LOGGER.error("Malformed URL for source of profiled context information: " + config.getProfiledContextGraphURI() + ". Reason: " + e.getMessage());
            throw new Exception("Error setting up profiled context graph", e);
        } catch (RDFParseException e) {
            LOGGER.error("Error parsing the RDF content of the profiled context information: " + config.getProfiledContextGraphURI() + ". Reason: " + e.getMessage());
            throw new Exception("Error setting up profiled context graph", e);
        } catch (RepositoryException e) {
            LOGGER.error("Error adding the RDF content of the profiled context information to the profiled repository: " + ". Reason: " + e.getMessage());
            throw new Exception("Error setting up profiled context graph", e);
        } catch (IOException e) {
            LOGGER.error("Error reading the RDF content of the profiled context information from the source: " + config.getProfiledContextGraphURI() + ". Reason: " + e.getMessage());
            throw new Exception("Error setting up profiled context graph", e);
        } 
    }

    private void setupCDGMembershipRepo(ContextManagementConfig config) throws Exception {
        // Set up the ContextDomains. 
        for (ContextDomainModel contextDomainModel : config.getContextDomains()) {
            // get the list of stream URIs to which the context domain membership rules require subscription
            List<String> requiredStreamURIs = contextDomainModel.getStreams();

            // If the list of required stream URIs is not covered by the contextStreamMap, throw an exception
            if (!contextStreamMap.keySet().containsAll(requiredStreamURIs)) {
                throw new Exception("Error setting up context domain group membership repository: Required context streams not found." 
                    + "Missing streams: " + Set.copyOf(requiredStreamURIs).removeAll(contextStreamMap.keySet()));
            }

            List<ContextStream> requiredContextStreams = requiredStreamURIs.stream()
                .map(streamURI -> contextStreamMap.get(streamURI))
                .toList();

            ContextDomain contextDomain = new ContextDomain(contextDomainModel.getDomainUri(), 
                                                            contextDomainModel.getEngineConfigUrl(), 
                                                            contextDomainModel.getMembershipRules(),
                                                            requiredContextStreams); 
            contextDomains.put(contextDomainModel.getDomainUri(), contextDomain);
        }
    }

    // ============================================================================
    // =================== Methods for context stream monitoring ==================
    // ============================================================================
    /**
     * Initializes all context streams from the configuration and subscribes them to the WebSub hub.
     */
    private void initializeContextStreams(ContextManagementConfig contextManagementConfig, HttpInterfaceConfig httpConfig, WebSubConfig webSubConfig) {
        List<ContextStreamModel> streamConfigs = contextManagementConfig.getContextStreams();
        
        for (var streamInfo : streamConfigs) {
            // Create a new ContextStream for each stream URI
            String streamURI = streamInfo.getStreamUri();
            ContextStream stream = new ContextStream(streamURI, streamInfo.getOntologyUrl(), streamInfo.getAssertions());
            contextStreamMap.put(streamURI, stream);
            
            // Subscribe to the WebSub hub for this stream
            try {
                subscribeToHub(httpConfig, webSubConfig, streamURI);
                LOGGER.info("Subscribed to stream: " + streamInfo + " (name: " + stream.getStreamName() + ")");
            } catch (IOException e) {
                LOGGER.error("Failed to subscribe to stream: " + streamInfo, e);
            }
        }
    }

    /**
     * Subscribes to the WebSub hub for a specific stream.
     *
     * @param streamUri The URI of the stream to subscribe to
     */
    private void subscribeToHub(HttpInterfaceConfig httpConfig, WebSubConfig webSubConfig, String streamUri) throws IOException {
        String hubUri = webSubConfig.getWebSubHubUri();
        String callbackUri = serviceURI + ContextManagementConfig.STREAM_UPDATES_PATH;
        
        HttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(hubUri);
        
        JsonObject json = new JsonObject();
        json.put("hub.mode", "subscribe");
        json.put("hub.topic", streamUri);
        json.put("hub.callback", callbackUri);
        StringEntity entity = new StringEntity(json.encode());
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-Type", "application/json");
        
        HttpResponse response = httpClient.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();
        
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Failed to subscribe to WebSub hub. Status code: " + statusCode);
        }
    }


    private void setupContextAccessConditionsRepo(ContextManagementConfig ctxConfig, HttpInterfaceConfig httpConfig, Environment env)
            throws Exception {
        ShaclSail shaclSail = new ShaclSail(new MemoryStore());
        contextAccessConditionsRepo = new SailRepository(shaclSail);

        // read the artifact-policies JSON object from the known-artifacts part of the envConfig
        for (var wsp : env.getWorkspaces()) {
            for (var artifact : wsp.getArtifacts()) {
                if (artifact.getContextAccessPolicyURL().isPresent()) {
                    // form the URL path that will correspond at runtime to this artifact
                    String artifactPath = "/workspaces/" + wsp.getName() + "/artifacts/" + artifact.getName() + "#artifact";
                    String artifactURL = httpConfig.getBaseUri() + artifactPath;
                    artifactPolicies.put(artifactURL, artifact.getContextAccessPolicyURL().get());

                    // Dereference the policy URI as a file and add the contents to the contextAccessConditionsRepo.
                    // They are added in a named graph with the artifact URI as the graph name.
                    try {
                        IRI contextIRI = SimpleValueFactory.getInstance().createIRI(artifactURL);
                        URL policyURL = URI.create(artifact.getContextAccessPolicyURL().get()).toURL();
                        contextAccessConditionsRepo.getConnection().add(policyURL, null, RDFFormat.TURTLE, contextIRI);
                    } catch (MalformedURLException e) {
                        LOGGER.error("Malformed URL for source of context access conditions for artifact " + artifactURL + ": " + artifact.getContextAccessPolicyURL().get() 
                            + ". Reason: " + e.getMessage());
                        throw new Exception("Error setting up context access conditions repository", e);
                    } catch (RDFParseException e) {
                        LOGGER.error("Error parsing the RDF content of the context access conditions for artifact " + artifactURL + ": " + artifact.getContextAccessPolicyURL().get() 
                            + ". Reason: " + e.getMessage());
                            throw new Exception("Error setting up context access conditions repository", e);
                    } catch (RepositoryException e) {
                        LOGGER.error("Error adding the RDF content of the context access conditions for artifact " + artifactURL + " to the context access conditions repository: " 
                            + ". Reason: " + e.getMessage());
                            throw new Exception("Error setting up context access conditions repository", e);
                    } catch (IOException e) {
                        LOGGER.error("Error reading the RDF content of the context access conditions for artifact " + artifactURL + " from the source: " + artifact.getContextAccessPolicyURL().get() 
                            + ". Reason: " + e.getMessage());
                        throw new Exception("Error setting up context access conditions repository", e);
                    }
                }
            }
        }
    }

    // ============================================================================
    // =================== Methods for handling context requests ==================
    // ============================================================================
    private void setupRequestHandling(ContextMessageBox contextMessageBox) {
         contextMessageBox.receiveMessages(
            message -> {
                LOGGER.info("Handling Context Request...");
                
                try {
                    switch (message.body()) {
                        case ContextMessage.ValidateContextBasecAccess msgContent -> {
                            LOGGER.info("Handling Context-based access validation action...");
                            validateContextBasedAccess(msgContent.accessRequesterURI(), msgContent.accessedResourceURI(), message);
                        }
                        case ContextMessage.GetStaticContext msgContent -> {
                            LOGGER.info("Handling GetStaticContext action...");
                            message.reply(staticContextRepo.getConnection().getStatements(null, null, null, false));
                        }
                        case ContextMessage.GetProfiledContext msgContent -> {
                            // TODO: Implement the GetProfiledContext action such that we retrieve all statements related to the ContextAssertion
                            // referenced by the msgContent.contextAssertionType() from the profiledContextRepo
                            LOGGER.info("Handling GetProfiledContext action...");
                            message.reply(profiledContextRepo.getConnection().getStatements(null, null, null, false));
                        }
                        case ContextMessage.ContextStreamUpdate streamUpdate -> {
                            LOGGER.info("Received request to update context stream: " + streamUpdate.streamURI());  
                            updateContextStream(streamUpdate.streamURI(), streamUpdate.updateContent(), streamUpdate.updateTimestamp(), message);
                        }
                        default -> {
                            LOGGER.warn("Received an unknown message type: " + message.body().getClass().getName());
                            message.fail(HttpStatus.SC_BAD_REQUEST, "Unknown message type.");
                        }
                    }
                }
                catch (final IllegalArgumentException e) {
                    LOGGER.error(e);
                    message.fail(HttpStatus.SC_BAD_REQUEST, "Arguments badly formatted.");
                } catch (final UncheckedIOException e) {
                    LOGGER.error(e);
                    message.fail(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Store request failed.");
                }
        });  
    }

    private void updateContextStream(String streamURI, String graphSerialized, long updateTimestamp, Message<ContextMessage> message) {
        // Get the ContextStream object corresponding to the streamURI
        ContextStream stream = contextStreamMap.get(streamURI);
        
        if (stream == null) {
            message.fail(HttpStatus.SC_NOT_FOUND, "Unknown stream: " + streamURI);
            return;
        }
        
        // Transform the updateContent into a set of RDF statements
        // Parse the RDF graph from Turtle serialization
        Graph graph = RDFParser.create()
            .fromString(graphSerialized)
            .lang(Lang.TURTLE)
            .toGraph();

        // Update the stream with the new content
        stream.updateStream(graph, updateTimestamp);
        
        // Reply to the message with a success message
        message.reply("Stream " + streamURI + " updated successfully.");
    }

    // ============================================================================
    // =================== Methods for context validation =========================
    // ============================================================================

    private boolean isAccessProtected(String artifactURI) {
        return artifactPolicies.containsKey(artifactURI);
    }


    private Optional<List<Statement>> getAccessConditions(String artifactURI) {
        // get the URI of the named graph containing the access conditions for the artifact
        String accessConditionsGraphURI = artifactPolicies.get(artifactURI);

        if (accessConditionsGraphURI == null) {
            return Optional.empty();
        }

        // get the statements in the named graph
        try (RepositoryConnection conn = contextAccessConditionsRepo.getConnection()) {
            return Optional.of(Iterations.asList(conn.getStatements(null, null, null, true, conn.getValueFactory().createIRI(artifactURI))));
        } catch (RepositoryException e) {
            LOGGER.error("Error accessing the context access conditions repository: " + e.getMessage());
            return Optional.empty();
        }
    }


    private List<Statement> customizeAccessConditions(List<Statement> accessConditions, String accessRequesterURI, String accessedArtifactURI) {
        // Iterate through all the statements in the accessConditions list and replace the `cashmere:accessRequester` object placeholder 
        // with the actual accessRequesterURI
        accessConditions.replaceAll(stmt -> {
            if (stmt.getObject().stringValue().equals(CASHMERE.accessRequester.stringValue())) {
                return SimpleValueFactory.getInstance().createStatement(stmt.getSubject(), stmt.getPredicate(), SimpleValueFactory.getInstance().createIRI(accessRequesterURI));
            } else {
                return stmt;
            }
        });
        
        // return the customized access conditions
        return accessConditions;
    }


    private void validateContextBasedAccess(String accessRequesterURI, String accessedResourceURI, Message<ContextMessage> message) {
        // To validate context access we must bring together the static, profiled and dynamic context information
        // from their respective repositories and validate the access request against them.
        // In the current version we assume that all context repositories are handled at platform level and are 
        // available directly to the ContextMgmtVerticle.
        // TODO: handle the case when the context repositories are managed by the artifacts themselves.

        // If the artifact is not access protected, allow access by default
        if (!isAccessProtected(accessedResourceURI)) {
            message.reply(true);
            LOGGER.info("Access to resource " + accessRequesterURI + " allowed for access requester: " 
                        + accessRequesterURI + ". Reason: No access conditions found.");
            return;
        }

        // Create an in-memory RDF store that will contain the union of the static, profiled and dynamic context information
        // The store will be set up as a ShaclSail object to allow for SHACL validation of the access conditions against the context information
        SailRepository contextDataRepo = new SailRepository(new MemoryStore());
        contextDataRepo.init();

        // start adding the static context information to the validationDataRepo
        addStaticContext(contextDataRepo, accessedResourceURI, accessRequesterURI);

        // start adding the profiled context information to the validationDataRepo
        addProfiledContext(contextDataRepo, accessedResourceURI, accessRequesterURI);

        // start adding the dynamic context information to the validationDataRepo
        addDynamicContext(contextDataRepo, accessedResourceURI, accessRequesterURI);

        // now we need to add the SHACL shapes graph containing the access conditions for the artifact to the validationDataRepo
        Optional<List<Statement>> accessConditions = getAccessConditions(accessedResourceURI);
        if (accessConditions.isPresent()) {
            // create a Validation Repository as a ShaclSail in memory store
            ShaclSail shaclSailValidation = new ShaclSail(new MemoryStore());
            shaclSailValidation.setLogValidationViolations(true);
            shaclSailValidation.setGlobalLogValidationExecution(true);
            shaclSailValidation.setRdfsSubClassReasoning(true);
            SailRepository validationRepo = new SailRepository(shaclSailValidation);
            validationRepo.init();

            // Replace the `cashmere:accessRequester` object placeholder in the access conditions with the actual accessRequesterURI
            List<Statement> customAccessConditions = customizeAccessConditions(accessConditions.get(), accessRequesterURI, accessedResourceURI);
            try (SailRepositoryConnection conn = validationRepo.getConnection()) {
                // load the access conditions into the profiledContextRepo, under the RDF4J.SHACL_SHAPE_GRAPH context
                conn.begin();
                conn.add(customAccessConditions, RDF4J.SHACL_SHAPE_GRAPH);
                
                // for debug: serialize the contents of the validationRepo into a temporary turtle file
                File tempValidationRepoFile = new File("/home/alex/OneDrive/AI-MAS/projects/2022-CASHMERE/dev/yggdrasil-cashmere/src/test/resources/validationRepo.ttl");
                Utils.serializeRepoConnection(conn, tempValidationRepoFile, RDF4J.SHACL_SHAPE_GRAPH);
                
                // conn.commit();

                // // load the contents of the contextDataRepo into the validationRepo
                // conn.begin();
                conn.add(contextDataRepo.getConnection().getStatements(null, null, null, false));

                // validate the access conditions against the profiled context repository
                conn.commit();

                // for debug: serialize the contents of the validationRepo into a temporary turtle file
                File tempDataRepoFile = new File("/home/alex/OneDrive/AI-MAS/projects/2022-CASHMERE/dev/yggdrasil-cashmere/src/test/resources/dataRepo.ttl");
                Utils.serializeRepoConnection(conn, tempDataRepoFile);

                LOGGER.info("Access to artifact " + accessedResourceURI + " allowed for access requester: " 
                        + accessRequesterURI + ". Reason: Context validation successful.");
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof ValidationException) {
                    LOGGER.info("Access to artifact " + accessedResourceURI + " denied for access requester: " 
                        + accessRequesterURI + ". Reason:  " + cause.getMessage()); 
                } else {
                    LOGGER.error("Error validating the access conditions of artifact: " + accessedResourceURI 
                        + " for requester: " + accessRequesterURI +  " against the profiled context repository: " + e.getMessage());
                }

                // in case of error, deny access
                message.fail(403, "Access denied. Reason: " + (cause != null ? cause.getMessage() : "Unknown error"));
            }
            finally {
                // close the validationRepo
                validationRepo.shutDown();
            }
        }
        else {
            // If no access conditions are found, allow access by default
            LOGGER.info("No access conditions found in policy for artifact " + accessedResourceURI + ". Access allowed by default.");
            message.reply(true);
            return;
        }

        // If all validations are successful, allow access
        message.reply(true);
    }

    private void addStaticContext(SailRepository contextDataRepo, String accessedArtifactURI, String accessRequesterURI) {
        try (RepositoryConnection conn = contextDataRepo.getConnection()) {
            // load the contents of the staticContextRepo into the validationDataRepo
            conn.add(staticContextRepo.getConnection().getStatements(null, null, null, false));
        } catch (RepositoryException e) {
            LOGGER.error("Error loading the static context information into the validation data repository: " + e.getMessage());
        }
    }

    private void addProfiledContext(SailRepository contextDataRepo, String accessedArtifactURI, String accessRequesterURI) {
        try (RepositoryConnection conn = contextDataRepo.getConnection()) {
            // load the contents of the profiledContextRepo into the validationDataRepo
            conn.add(profiledContextRepo.getConnection().getStatements(null, null, null, false));
        } catch (RepositoryException e) {
            LOGGER.error("Error loading the profiled context information into the validation data repository: " + e.getMessage());
        }
    }

    private void addDynamicContext(SailRepository contextDataRepo, String accessedArtifactURI, String accessRequesterURI) {
        // This method is not yet implemented. It should load the dynamic context information into the validationDataRepo.
        // The dynamic context information is stored in RDF streams that are managed by the ContextMgmtVerticle.
        // The RDF streams are identified by the URIs of the dynamic ContextAssertions.
        // The dynamic context information is updated by the artifacts themselves.
        // The dynamic context information is used to validate the access request against the dynamic context conditions.
        Optional<List<String>> contextDomainGroupURIs = getContextDomainGroupURIs(accessedArtifactURI, accessRequesterURI);
        
        if (contextDomainGroupURIs.isPresent()) {
            for (String ctxGroupURI : contextDomainGroupURIs.get()) {
                // get the ContextDomain URI from the contextDomainGroupURI
                String ctxDomainURI = ContextDomain.getDomainFromGroup(ctxGroupURI);

                // get the ContextDomain object from the contextDomains map
                ContextDomain ctxDomain = contextDomains.get(ctxDomainURI);
                
                // get the context domain membership statements from the context domain object
                Optional<List<Statement>> membershipStatements = ctxDomain.getMembershipStatements();
                
                // add the membership statements to the validationDataRepo
                if (membershipStatements.isPresent()) {
                    try (RepositoryConnection conn = contextDataRepo.getConnection()) {
                        conn.add(membershipStatements.get());
                    } catch (RepositoryException e) {
                        LOGGER.error("Error loading the dynamic context information into the validation data repository: " + e.getMessage());
                    }
                }
            }
        }
    }

    // ============================================================================
    // ======================== Dynamic Context Validation ========================
    // ============================================================================

    private Optional<List<String>> getContextDomainGroupURIs(String accessedArtifactURI, String accessRequesterURI) {
        // open a connection to the contextAccessConditionsRepo
        try (RepositoryConnection conn = contextAccessConditionsRepo.getConnection()) {
            // prepare the query to retrieve the ContextDomainGroup URIs, running it on the named graph of the artifact
            String query = 
                "PREFIX cashmere: <" + CASHMERE.CASHMERE_NS + "> " +
                "PREFIX sh: <http://www.w3.org/ns/shacl#> " +
                "SELECT ?domainCond ?ctxGroupURI "
                + "WHERE {"
                +   "?domainCond a cashmere:ContextDomainCondition ."
                +   "?domainCond sh:property [sh:hasValue ?ctxGroupURI] ."
                + "}";

            // execute the query
            return Optional.of(Iterations.asList(conn.prepareTupleQuery(query).evaluate()).stream()
                .map(binding -> binding.getValue("ctxGroupURI").stringValue())
                .toList());
        } catch (RepositoryException e) {
            LOGGER.error("Error accessing the context access conditions repository of the artifact " + accessedArtifactURI 
                    + " by access requester " + accessRequesterURI + ": " + e.getMessage());
        }
        
        return Optional.empty();
    }

}
