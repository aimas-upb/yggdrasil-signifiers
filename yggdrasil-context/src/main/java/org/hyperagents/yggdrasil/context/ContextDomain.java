package org.hyperagents.yggdrasil.context;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.context.http.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.streamreasoning.rsp4j.api.engine.config.EngineConfiguration;
import org.streamreasoning.rsp4j.api.querying.ContinuousQuery;
import org.streamreasoning.rsp4j.api.sds.SDSConfiguration;
import org.streamreasoning.rsp4j.api.stream.data.DataStream;
import org.streamreasoning.rsp4j.csparql2.engine.CSPARQLEngine;
import org.streamreasoning.rsp4j.csparql2.engine.JenaContinuousQueryExecution;
import org.streamreasoning.rsp4j.csparql2.sysout.ResponseFormatterFactory;


/**
 * The ContextDomain class wraps information about the context domain created by instances of a ContextAssertion.
 * Starting from the URI of the ContextAssertion (the ContextDimension), the URI of the ContextEntity playing the object role in the ContextAssertion,
 * and the context management endpoint of an Yggdrasil platform instance, it specifies 
 * the endpoint where information about the context domain can be retrieved.
 * 
 * The class also provides a method to retrieve the URI of the ContextDomainGroup, which is a group of ContextEntities sharing the same context domain.
 * It keeps a reference to the SailRepository used to store information about membership of ContextEntities to the ContextDomainGroup of this ContextDomain.
 * It also keeps a reference to the RSPQL query that infers the membership of ContextEntities to the ContextDomainGroup, 
 * and it instantiates a RSPQL query engine to execute the query.
 */
public class ContextDomain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextDomain.class);
    
    // Context management endpoint
    private String contextManagementEndpoint;
    
    // ContextDimension and ContextEntity configuration
    private String contextDimensionURI;
    private Optional<String> contextEntityURI = Optional.empty();
    private String contextDomainURI;

    // ContextDomain content stream of the ContextAssertion updates
    private Optional<String> assertionStreamURI = Optional.empty();
    private Optional<String> streamGeneratorClass = Optional.empty();
    private LocalContextStream assertionStream;

    // Query engine configuration and membership rule query
    private String membershipRuleQueryURI;
    private String engineConfigURI;
    private CSPARQLEngine membershipRuleQueryEngine;
    private ContinuousQuery membershipRuleQuery;
    private DataStream<Graph> membershipRuleQueryResultStream;

    // RDF store for the graph denoting the ContextDomainGroup memberships
    private SailRepository cdgMembershipRepo;

    /**
     * Construct a ContextDomain specification by providing the URI of the ContextAssertion, the URI of the ContextEntity, and the context management endpoint.
     * @param contextAssertionURI: the URI of the ContextAssertion serving as ContextDimension
     * @param contextEntityURI: the URI of the ContextEntity playing the object role in the ContextAssertion
     * @param contextManagementEndpoint: the context management endpoint of an Yggdrasil platform instance
     * @param assertionStreamURI: the URI of the RDF stream where the updates of the ContextAssertion are published
     */
    public ContextDomain(String contextManagementEndpoint, String contextAssertionURI, String contextEntityURI, 
                        Optional<String> assertionStreamURI, Optional<String> streamGeneratorClass,
                        String membershipRuleQueryURI, String engineConfigURI) {
        // set the context management endpoint
        this.contextManagementEndpoint = contextManagementEndpoint;
        
        this.contextDimensionURI = contextAssertionURI;
        this.contextEntityURI = Optional.of(contextEntityURI);
        
        // get the local name of the ContextDimension
        String contextDimensionLocalName = Utils.getConceptLocalName(contextAssertionURI);

        // get the local name of the contextEntityURI
        String contextEntityLocalName = Utils.getConceptLocalName(contextEntityURI);
        this.contextDomainURI = contextManagementEndpoint + "/domains/" + contextEntityLocalName + "Domain";

        // if an assertionStreamURI is provided, set it
        if (assertionStreamURI.isPresent()) {
            this.assertionStreamURI = assertionStreamURI;
        }
        else {
            // Otherwise, deduce the name of the RDF stream from the contextEntityURI LocalName
            this.assertionStreamURI = Optional.of(contextManagementEndpoint + "/streams/" + contextEntityLocalName);
        }

        // if a streamGeneratorClass is provided, set it and instantiate the assertionStream 
        // TODO: this will have to be redone once a web-based stream generator is implemented
        if (streamGeneratorClass.isPresent()) {
            this.streamGeneratorClass = streamGeneratorClass;
            this.assertionStream = setupAssertionStream(this.assertionStreamURI.get(), streamGeneratorClass.get());
        }

        // set the URI of the RSPQL query that infers the membership of ContextEntities to the ContextDomainGroup
        this.membershipRuleQueryURI = membershipRuleQueryURI;

        // Set up the RDF store for the named graphs denoting the ContextDomainGroup memberships
        // We set it up as a SailRepository over an in-memory store, as we do not need to persist the membership information for the time being.
        this.cdgMembershipRepo = new SailRepository(new MemoryStore());


        // set the URI of the RSPQL query engine configuration
        this.engineConfigURI = engineConfigURI;
        
        try {
            initEngine();
        } catch (MalformedURLException e) {
            System.err.println("Error while initializing the RSPQL query engine for the ContextDomain " + contextDomainURI);
            e.printStackTrace();
        } catch (ConfigurationException e) {
            System.err.println("Error while initializing the RSPQL query engine for the ContextDomain " + contextDomainURI);
            e.printStackTrace();
        }
    }

    private String getStreamBaseURI() {
        return contextManagementEndpoint + "/streams";
    }   

    private String getDomainsBaseURI() {
        return contextManagementEndpoint + "/domains";
    }

    private LocalContextStream setupAssertionStream(String assertionStreamURI, String generatorClass) {
        // get the base URI for the RDF streams
        String streamBaseURI = getStreamBaseURI();
        
        try {
            // Load the class by name
            Class<?> clazz = Class.forName(generatorClass);

            // Get the constructor that takes a long argument
            java.lang.reflect.Constructor<?> constructor = clazz.getConstructor();

            // Instantiate the class with the provided argument
            LocalContextStream streamInstance = (LocalContextStream)constructor.newInstance();

            // Now you can use the instance of the class
            LOGGER.info("Instantiated the stream generator class " + generatorClass + " for the assertion stream " + assertionStreamURI);

            return streamInstance;
        } catch (Exception e) {
            LOGGER.error("Error while instantiating the stream generator class " + generatorClass + ": " + e.getMessage());
            return null;
        }
    }

    private void initEngine() throws MalformedURLException, ConfigurationException {
        // get the content of the engineConfigURI (a .properties file path) as an absolute path
        String configFilePath = new URL(engineConfigURI).getPath();
        SDSConfiguration config = new SDSConfiguration(configFilePath);

        LOGGER.info("Loading the RSPQL query engine configuration from " + configFilePath);
        EngineConfiguration ec = new EngineConfiguration(engineConfigURI);

        // Instantiate the RSPQL query engine
        this.membershipRuleQueryEngine = new CSPARQLEngine(0, ec);

        // register the assertion stream with the engine
        DataStream<Graph> engineRegisteredStream = this.membershipRuleQueryEngine.register(this.assertionStream);
        this.assertionStream.setWritableStream(engineRegisteredStream);
        
        // register the membership rule query with the engine
        JenaContinuousQueryExecution cqe = (JenaContinuousQueryExecution)membershipRuleQueryEngine.register(Utils.parseRSPQLQuery(this.membershipRuleQueryURI), config);
        ContinuousQuery query = cqe.query();
        query.setConstruct();
        cqe.addQueryFormatter(ResponseFormatterFactory.getConstructResponseSysOutFormatter("Turtle", false));

        // get the result stream of the CONSTRUCT query and create a consumer that will push the results to the CDG membership repository
        this.membershipRuleQueryResultStream = (DataStream<Graph>)cqe.outstream();
        this.membershipRuleQueryResultStream.addConsumer((g, t) -> updateMembershipConsumer(g, t));

        // Start the LocatedAt context stream - TODO: this will have to be redone once a web-based stream generator is implemented
        this.assertionStream.start();
    }

    private void updateMembershipConsumer(Graph g, long t) {
        // Add the contents of the graph g to the CDG membership repository
        // The content will always be a single triple, with the subject being the URI of the agent and 
        // the object being the URI of the ContextDomainGroup. The predicate is always cashmere:memberIn.
        
        // LOGGER.info("Received the result of the membership rule query for the ContextDomain " + contextDomainURI + " at time " + t);
        // LOGGER.info("Content of the graph: " + g.toString());

        try (RepositoryConnection conn = cdgMembershipRepo.getConnection()) {
            // iterate over the triples in the graph and add them to the repository
            ExtendedIterator<Triple> graphIt = g.find();
            while (graphIt.hasNext()) {
                Triple triple = graphIt.next();
                
                IRI pred = conn.getValueFactory().createIRI(triple.getPredicate().getURI());
                
                // if the predicate is a cashmere:memberIn, add the statement to the repository
                if (pred.equals(CASHMERE.memberIn)) {
                    Resource subj = conn.getValueFactory().createIRI(triple.getSubject().getURI());
                    Resource obj = conn.getValueFactory().createIRI(triple.getObject().getURI());
                    Statement stmt = conn.getValueFactory().createStatement(subj, pred, obj);

                    // add the statement to the repository if it is not already present
                    if (!conn.hasStatement(stmt, false)) {
                        conn.add(stmt);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error while adding the result of the membership rule query to the CDG membership repository: " + e.getMessage());
        }
    }

    public String getContextDimensionURI() {
        return contextDimensionURI;
    }

    public String getContextDomainURI() {
        return contextDomainURI;
    }

    public Optional<String> getContextEntityURI() {
        return contextEntityURI;
    }

    public String getContextDomainGroupURI() {
        // add the path element "group" to the contextDomainURI
        return contextDomainURI + "/group";
    }

    public boolean verifyMembership(String accessRequesterURI) {
        // check if the accessRequesterURI is a member of the ContextDomainGroup
        try (RepositoryConnection conn = cdgMembershipRepo.getConnection()) {
            // create the statement to check
            Resource accessRequester = conn.getValueFactory().createIRI(accessRequesterURI);
            Resource contextDomainGroup = conn.getValueFactory().createIRI(getContextDomainGroupURI());
            Statement stmt = conn.getValueFactory().createStatement(accessRequester, CASHMERE.memberIn, contextDomainGroup);

            // check if the statement is present in the repository
            return conn.hasStatement(stmt, false);
        } catch (Exception e) {
            LOGGER.error("Error while verifying the membership of the agent " + accessRequesterURI + " in the ContextDomainGroup " + getContextDomainGroupURI() + ": " + e.getMessage());
            return false;
        }
    }

    public Optional<List<Statement>> getMembershipStatements() {
        return getMembershipStatements(null);
    }
    
    public Optional<List<Statement>> getMembershipStatements(String accessRequesterURI) {
        try (RepositoryConnection conn = cdgMembershipRepo.getConnection()) {
            if (accessRequesterURI == null)
                return Optional.of(Iterations.asList(conn.getStatements(null, CASHMERE.memberIn, null)));
            else 
                return Optional.of(Iterations.asList(conn.getStatements(
                    conn.getValueFactory().createIRI(accessRequesterURI), CASHMERE.memberIn, null)));
        } catch (Exception e) {
            LOGGER.error("Error while retrieving the membership statements of the ContextDomainGroup " + getContextDomainGroupURI() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    // =============================================================================================================
    // Auxiliary functions to get the URI of the ContextDomainGroup from the URI of the ContextDomain and vice versa
    // =============================================================================================================
    public static String getGroupFromDomain(String contextDomainURI) {
        // add the path element "group" to the contextDomainURI
        return contextDomainURI + "/group";
    }

    public static String getDomainFromGroup(String contextDomainGroupURI) {
        // remove the path element "group" from the contextDomainGroupURI
        return contextDomainGroupURI.replace("/group", "");
    }
}
