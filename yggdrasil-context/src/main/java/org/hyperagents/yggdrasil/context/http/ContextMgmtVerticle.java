package org.hyperagents.yggdrasil.context.http;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
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

    // The base URI of the Yggdrasil platform
    private String baseURITrailingSlash;

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

    // Add workspace policies alongside artifact policies
    private Map<String, String> workspacePolicies;
    
    // Add HMAS vocabulary constants
    private static final String HMAS_WORKSPACE = "https://purl.org/hmas/Workspace";
    private static final String HMAS_PLATFORM = "https://purl.org/hmas/HypermediaMASPlatform";
    private static final String HMAS_ARTIFACT = "https://purl.org/hmas/Artifact";
    private static final String HMAS_CONTAINS = "https://purl.org/hmas/contains";
    private static final String HMAS_IS_CONTAINED_IN = "https://purl.org/hmas/isContainedIn";
    private static final String HMAS_HOSTS = "https://purl.org/hmas/hosts";
    private static final String ACL_DEFAULT = "http://www.w3.org/ns/auth/acl#default";

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
        
        // Initialize workspace policies map
        workspacePolicies = new HashMap<>();
        
        // get the base and service URIs from the configurations
        this.baseURITrailingSlash = httpConfig.getBaseUriTrailingSlash();
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
        
            // setup handling of messages from the event bus
            final var contextMessageBox = new ContextMessageBox(this.vertx.eventBus(), this.contextManagementConfig);
            contextMessageBox.init();
            setupRequestHandling(contextMessageBox);
            
            startPromise.complete();
        }
        catch (Exception e) {
            LOGGER.error("Error setting up the context management service: " + e.getMessage());
            startPromise.fail(e);
        }
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
        String callbackUri = baseURITrailingSlash + ContextManagementConfig.STREAM_UPDATES_PATH;
        
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

        // Process workspaces first for hierarchy setup
        for (var wsp : env.getWorkspaces()) {
            // Handle workspace-level policies
            if (wsp.getContextAccessPolicyURL().isPresent()) {
                String workspaceURL = httpConfig.getWorkspaceUri(wsp.getName()) + "#workspace";
                workspacePolicies.put(workspaceURL, wsp.getContextAccessPolicyURL().get());
                
                try {
                    IRI contextIRI = SimpleValueFactory.getInstance().createIRI(workspaceURL);
                    URL policyURL = URI.create(wsp.getContextAccessPolicyURL().get()).toURL();
                    contextAccessConditionsRepo.getConnection().add(policyURL, null, RDFFormat.TURTLE, contextIRI);
                    LOGGER.info("Context access conditions for workspace " + workspaceURL + " loaded successfully.");
                } catch (Exception e) {
                    LOGGER.error("Error reading the RDF content of the context access conditions for workspace " + workspaceURL + " from the source: " + wsp.getContextAccessPolicyURL().get() 
                        + ". Reason: " + e.getMessage());
                    throw new Exception("Error setting up context access conditions repository", e);
                }
            }
            
            // Handle artifact-level policies (existing code)
            for (var artifact : wsp.getArtifacts()) {
                if (artifact.getContextAccessPolicyURL().isPresent()) {
                    // form the URL path that will correspond at runtime to this artifact
                    String artifactURL = httpConfig.getArtifactUri(wsp.getName(), artifact.getName()) + "#artifact";
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
                        case ContextMessage.ValidateContextBasedAccess msgContent -> {
                            LOGGER.info("Handling Context-based access validation action...");
                            validateContextBasedAccess(msgContent.accessRequesterURI(), msgContent.accessedResourceURI(), message);
                        }
                        case ContextMessage.ValidateWorkspaceContextBasedAccess msgContent -> {
                            LOGGER.info("Handling Workspace context-based access validation action...");
                            validateContextBasedAccess(msgContent.accessRequesterURI(), msgContent.accessedWorkspaceURI(), message);
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

    // Replace the existing validateContextBasedAccess method with hierarchical version
    private void validateContextBasedAccess(String accessRequesterURI, String accessedResourceURI, Message<ContextMessage> message) {
        LOGGER.info("Starting hierarchical access validation for resource: " + accessedResourceURI + " by requester: " + accessRequesterURI);
        
        // Step 1: Try to find effective access control resource following WAC-like hierarchy
        EffectiveAccessControl effectiveAccessControl = findEffectiveAccessControl(accessedResourceURI);
        
        if (effectiveAccessControl == null) {
            // No access control found in the entire hierarchy - allow free access
            LOGGER.info("Access to resource " + accessedResourceURI + " allowed for requester: " 
                       + accessRequesterURI + ". Reason: No access conditions found in hierarchy.");
            message.reply(true);
            return;
        }
        
        LOGGER.info("Found effective access control: " + effectiveAccessControl.type + " for resource: " + effectiveAccessControl.resourceURI);
        
        // Step 2: Perform context validation using the effective access control
        performContextValidation(accessRequesterURI, accessedResourceURI, effectiveAccessControl, message);
    }

    // Main method to find effective access control following hierarchy
    private EffectiveAccessControl findEffectiveAccessControl(String resourceURI) {
        LOGGER.debug("Finding effective access control for resource: " + resourceURI);
        
        // Step 1: Check if the resource itself has direct access conditions (accessTo)
        EffectiveAccessControl directAccess = checkDirectAccessConditions(resourceURI);
        if (directAccess != null) {
            return directAccess;
        }
        
        // Step 2: If no direct access, traverse hierarchy looking for default access
        return findDefaultAccessInHierarchy(resourceURI);
    }

    // Check for direct access conditions on the resource
    private EffectiveAccessControl checkDirectAccessConditions(String resourceURI) {
        // Check if this resource has direct access policy
        if (isAccessProtected(resourceURI)) {
            String policyURI = getAccessPolicyURI(resourceURI);
            
            // Verify it has hasAccessCondition (not just default)
            if (hasDirectAccessCondition(resourceURI, policyURI)) {
                return new EffectiveAccessControl(resourceURI, policyURI, AccessControlType.DIRECT_ACCESS_TO);
            }
        }
        
        return null;
    }

    // Check if a resource has direct access conditions (hasAccessCondition)
    private boolean hasDirectAccessCondition(String resourceURI, String policyURI) {
        try (RepositoryConnection conn = contextAccessConditionsRepo.getConnection()) {
            IRI resourceIRI = SimpleValueFactory.getInstance().createIRI(resourceURI);
            
            // Query to check if there are any hasAccessCondition statements for this resource
            String query = 
                "PREFIX cashmere: <" + CASHMERE.CASHMERE_NS + "> " +
                "ASK { ?resource cashmere:hasAccessCondition ?condition }";
                
            var booleanQuery = conn.prepareBooleanQuery(query);
            booleanQuery.setBinding("resource", resourceIRI);
            return booleanQuery.evaluate();
                      
        } catch (RepositoryException e) {
            LOGGER.error("Error checking direct access conditions for resource: " + resourceURI, e);
            return false;
        }
    }

    // Find default access by traversing the containment hierarchy
    private EffectiveAccessControl findDefaultAccessInHierarchy(String resourceURI) {
        // Get the containment hierarchy path
        List<String> hierarchyPath = getContainmentHierarchy(resourceURI);
        
        // Traverse from immediate parent to root
        for (String parentURI : hierarchyPath) {
            LOGGER.debug("Checking default access for parent: " + parentURI);
            
            EffectiveAccessControl defaultAccess = checkDefaultAccess(parentURI);
            if (defaultAccess != null) {
                return defaultAccess;
            }
        }
        
        return null; // No access control found in hierarchy
    }

    // Get the containment hierarchy for a resource (from immediate parent to platform)
    private List<String> getContainmentHierarchy(String resourceURI) {
        List<String> hierarchy = new ArrayList<>();
        
        try {
            String currentURI = resourceURI;
            String parentURI = getImmediateParent(currentURI);
            
            while (parentURI != null) {
                hierarchy.add(parentURI);
                currentURI = parentURI;
                parentURI = getImmediateParent(currentURI);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error building containment hierarchy for: " + resourceURI, e);
        }
        
        return hierarchy;
    }

    // Get the immediate parent of a resource in the HMAS hierarchy
    private String getImmediateParent(String resourceURI) {
        try {
            // Check if it's an artifact
            if (resourceURI.contains("/artifacts/")) {
                // Extract workspace URI from artifact URI
                // e.g., http://localhost:8080/workspaces/w1/artifacts/c0#artifact 
                // -> http://localhost:8080/workspaces/w1#workspace
                String workspaceURI = resourceURI.substring(0, resourceURI.indexOf("/artifacts/")) + "#workspace";
                return workspaceURI;
            }
            
            // Check if it's a workspace - query RDF store to find parent
            if (resourceURI.contains("#workspace")) {
                return findParentWorkspaceOrPlatform(resourceURI);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error finding immediate parent for: " + resourceURI, e);
        }
        
        return null;
    }

    // Find parent workspace or platform for a given workspace
    private String findParentWorkspaceOrPlatform(String workspaceURI) {
        try {
            String baseWorkspaceURI = workspaceURI.replace("#workspace", "");
            
            // Check if this is a sub-workspace by looking at URI structure
            String[] pathParts = baseWorkspaceURI.split("/");
            
            if (pathParts.length > 4) { // More than /workspaces/name
                // This might be a sub-workspace, find parent
                String parentPath = String.join("/", Arrays.copyOf(pathParts, pathParts.length - 1));
                return parentPath + "#workspace";
            } else {
                // This is a top-level workspace, parent is platform
                String platformURI = baseWorkspaceURI.substring(0, baseWorkspaceURI.indexOf("/workspaces/")) + "#platform";
                return platformURI;
            }
            
        } catch (Exception e) {
            LOGGER.error("Error finding parent for workspace: " + workspaceURI, e);
            return null;
        }
    }

    // Check if a parent resource has default access rules
    private EffectiveAccessControl checkDefaultAccess(String parentURI) {
        // Check if this parent has default access policies
        if (isAccessProtected(parentURI)) {
            String policyURI = getAccessPolicyURI(parentURI);
            
            // Check if it has default access rules
            if (hasDefaultAccessRules(parentURI, policyURI)) {
                return new EffectiveAccessControl(parentURI, policyURI, AccessControlType.DEFAULT_ACCESS);
            }
        }
        
        return null;
    }

    // Check if a resource has default access rules
    private boolean hasDefaultAccessRules(String resourceURI, String policyURI) {
        try (RepositoryConnection conn = contextAccessConditionsRepo.getConnection()) {
            IRI resourceIRI = SimpleValueFactory.getInstance().createIRI(resourceURI);
            
            // Query to check for default access rules (WAC default property)
            String query = 
                "PREFIX acl: <http://www.w3.org/ns/auth/acl#> " +
                "PREFIX cashmere: <" + CASHMERE.CASHMERE_NS + "> " +
                "ASK { " +
                "  ?authorization acl:default ?resource . " +
                "  ?authorization cashmere:hasAccessCondition ?condition " +
                "}";
                
            var booleanQuery = conn.prepareBooleanQuery(query);
            booleanQuery.setBinding("resource", resourceIRI);
            return booleanQuery.evaluate();
                      
        } catch (RepositoryException e) {
            LOGGER.error("Error checking default access rules for resource: " + resourceURI, e);
            return false;
        }
    }

    // Perform the actual context validation using effective access control
    private void performContextValidation(String accessRequesterURI, String accessedResourceURI, 
                                        EffectiveAccessControl effectiveAccessControl, Message<ContextMessage> message) {
        
        LOGGER.info("Performing context validation using " + effectiveAccessControl.type + 
                   " from resource: " + effectiveAccessControl.resourceURI);
        
        // Create validation repository
        SailRepository contextDataRepo = new SailRepository(new MemoryStore());
        contextDataRepo.init();

        // Add context information
        addStaticContext(contextDataRepo, accessedResourceURI, accessRequesterURI);
        addProfiledContext(contextDataRepo, accessedResourceURI, accessRequesterURI);
        addDynamicContext(contextDataRepo, accessedResourceURI, accessRequesterURI);

        // Get access conditions from the effective access control resource
        Optional<List<Statement>> accessConditions = getEffectiveAccessConditions(
            effectiveAccessControl.resourceURI, effectiveAccessControl.type);
        
        if (accessConditions.isPresent()) {
            // Create validation repository with SHACL
            ShaclSail shaclSailValidation = new ShaclSail(new MemoryStore());
            shaclSailValidation.setLogValidationViolations(true);
            shaclSailValidation.setGlobalLogValidationExecution(true);
            shaclSailValidation.setRdfsSubClassReasoning(true);
            SailRepository validationRepo = new SailRepository(shaclSailValidation);
            validationRepo.init();

            // Customize access conditions for the actual requester and resource
            List<Statement> customAccessConditions = customizeAccessConditions(
                accessConditions.get(), accessRequesterURI, accessedResourceURI);
                
            try (SailRepositoryConnection conn = validationRepo.getConnection()) {
                conn.begin();
                conn.add(customAccessConditions, RDF4J.SHACL_SHAPE_GRAPH);
                conn.add(contextDataRepo.getConnection().getStatements(null, null, null, false));
                conn.commit();

                LOGGER.info("Access to resource " + accessedResourceURI + " allowed for requester: " 
                           + accessRequesterURI + ". Reason: Context validation successful using " + 
                           effectiveAccessControl.type);
                message.reply(true);
                
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof ValidationException) {
                    LOGGER.info("Access to resource " + accessedResourceURI + " denied for requester: " 
                               + accessRequesterURI + ". Reason: " + cause.getMessage() + 
                               " (using " + effectiveAccessControl.type + ")"); 
                    message.fail(403, "Access denied. Reason: " + cause.getMessage());
                } else {
                    LOGGER.error("Error validating access conditions: " + e.getMessage());
                    message.fail(500, "Access validation error");
                }
            } finally {
                validationRepo.shutDown();
            }
        } else {
            // No access conditions found even in effective resource
            LOGGER.info("No access conditions found in effective resource. Access allowed by default.");
            message.reply(true);
        }
        
        contextDataRepo.shutDown();
    }

    // Get access conditions from the effective access control resource
    private Optional<List<Statement>> getEffectiveAccessConditions(String effectiveResourceURI, AccessControlType type) {
        try (RepositoryConnection conn = contextAccessConditionsRepo.getConnection()) {
            IRI resourceIRI = SimpleValueFactory.getInstance().createIRI(effectiveResourceURI);
            
            // Query based on access control type
            String query;
            if (type == AccessControlType.DIRECT_ACCESS_TO) {
                query = "PREFIX cashmere: <" + CASHMERE.CASHMERE_NS + "> " +
                       "CONSTRUCT { ?s ?p ?o } " +
                       "WHERE { " +
                       "  ?resource cashmere:hasAccessCondition ?condition . " +
                       "  ?condition ?p ?o . " +
                       "  OPTIONAL { ?s ?sp ?condition } " +
                       "}";
            } else { // DEFAULT_ACCESS
                query = "PREFIX acl: <http://www.w3.org/ns/auth/acl#> " +
                       "PREFIX cashmere: <" + CASHMERE.CASHMERE_NS + "> " +
                       "CONSTRUCT { ?s ?p ?o } " +
                       "WHERE { " +
                       "  ?authorization acl:default ?resource . " +
                       "  ?authorization cashmere:hasAccessCondition ?condition . " +
                       "  ?condition ?p ?o . " +
                       "  OPTIONAL { ?s ?sp ?condition } " +
                       "}";
            }
            
            var graphQuery = conn.prepareGraphQuery(query);
            graphQuery.setBinding("resource", resourceIRI);
            return Optional.of(Iterations.asList(graphQuery.evaluate()));
                    
        } catch (RepositoryException e) {
            LOGGER.error("Error getting effective access conditions for: " + effectiveResourceURI, e);
            return Optional.empty();
        }
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

    private void addStaticContext(SailRepository contextDataRepo, String accessedResourceURI, String accessRequesterURI) {
        try (RepositoryConnection sourceConn = staticContextRepo.getConnection();
             RepositoryConnection targetConn = contextDataRepo.getConnection()) {
            targetConn.add(sourceConn.getStatements(null, null, null, false));
        } catch (RepositoryException e) {
            LOGGER.error("Error loading static context information into the validation data repository: " + e.getMessage());
        }
    }

    private void addProfiledContext(SailRepository contextDataRepo, String accessedResourceURI, String accessRequesterURI) {
        try (RepositoryConnection sourceConn = profiledContextRepo.getConnection();
             RepositoryConnection targetConn = contextDataRepo.getConnection()) {
            targetConn.add(sourceConn.getStatements(null, null, null, false));
        } catch (RepositoryException e) {
            LOGGER.error("Error loading profiled context information into the validation data repository: " + e.getMessage());
        }
    }

    private void addDynamicContext(SailRepository contextDataRepo, String accessedResourceURI, String accessRequesterURI) {
        // get the list of ContextDomainGroup URIs relevant for this access request
        Optional<List<String>> ctxDomainGroupURIs = getContextDomainGroupURIs(accessedResourceURI, accessRequesterURI);
        
        if (ctxDomainGroupURIs.isPresent()) {
            for (String ctxDomainURI : ctxDomainGroupURIs.get()) {
                // get the ContextDomain object from the contextDomains map
                ContextDomain ctxDomain = contextDomains.get(ctxDomainURI);
                
                if (ctxDomain != null) {
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
    }

    // Update the existing isAccessProtected method to check both artifacts and workspaces
    private boolean isAccessProtected(String resourceURI) {
        return artifactPolicies.containsKey(resourceURI) || workspacePolicies.containsKey(resourceURI);
    }

    // Get the policy URI for a given resource
    private String getAccessPolicyURI(String resourceURI) {
        if (artifactPolicies.containsKey(resourceURI)) {
            return artifactPolicies.get(resourceURI);
        } else if (workspacePolicies.containsKey(resourceURI)) {
            return workspacePolicies.get(resourceURI);
        }
        return null;
    }

    // ============================================================================
    // ======================== Dynamic Context Validation ========================
    // ============================================================================

    private List<Statement> customizeAccessConditions(List<Statement> accessConditions, String accessRequesterURI, String accessedResourceURI) {
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

    // Add inner classes for effective access control
    private static class EffectiveAccessControl {
        public final String resourceURI;
        public final String policyURI;
        public final AccessControlType type;
        
        public EffectiveAccessControl(String resourceURI, String policyURI, AccessControlType type) {
            this.resourceURI = resourceURI;
            this.policyURI = policyURI;
            this.type = type;
        }
    }

    private enum AccessControlType {
        DIRECT_ACCESS_TO,      // hasAccessCondition on the resource itself
        DEFAULT_ACCESS,        // default access on a parent container
        ACCESS_SUBJECT         // other access subjects as per WAC 4.3
    }
}
