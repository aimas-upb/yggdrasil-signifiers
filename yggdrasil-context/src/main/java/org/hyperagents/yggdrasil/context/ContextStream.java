package org.hyperagents.yggdrasil.context;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jena.graph.Graph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.streamreasoning.rsp4j.api.stream.data.DataStream;
import org.streamreasoning.rsp4j.io.DataStreamImpl;


public class ContextStream {
    
    private static final Logger LOGGER = LogManager.getLogger(ContextStream.class);

    private final String streamName;
    private final String streamURI;

    private DataStream<Graph> dataStream;

    private final AtomicLong lastUpdateTimestamp = new AtomicLong(0);

    /**
     * The URI of the ontology that describes the contents of this stream.
     */
    private final Optional<String> ontologyURL;    /**
     * The URIs of the ContextAssertions that are part of this stream.
     */
    private final List<String> contextAssertionTypes = new ArrayList<>();

    public final String getHypermediaRepresentation() {
        try {
            // Create RDF model
            Model model = new LinkedHashModel();
            SimpleValueFactory vf = SimpleValueFactory.getInstance();
            
            // Define namespaces
            String cashmereNS = "https://aimas.cs.pub.ro/ont/cashmere#";
            String consertNS = "http://pervasive.semanticweb.org/ont/2017/07/consert/core#";
            String exNS = "http://example.org/";
            String xsdNS = "http://www.w3.org/2001/XMLSchema#";
            String owlNS = "http://www.w3.org/2002/07/owl#";
            String rdfsNS = "http://www.w3.org/2000/01/rdf-schema#";
            
            IRI streamIRI = vf.createIRI(this.streamURI);
            model.add(streamIRI, 
                     vf.createIRI(cashmereNS + "streamName"), 
                     vf.createLiteral(this.streamName, vf.createIRI(xsdNS + "string")));
            model.add(streamIRI, 
                     vf.createIRI(cashmereNS + "updateMode"), 
                     vf.createIRI(cashmereNS + "TimePeriodic"));
                     
            for (String assertionType : contextAssertionTypes) {
                IRI assertionIRI = vf.createIRI(assertionType);
                
                model.add(streamIRI, vf.createIRI(cashmereNS + "containsAssertion"), assertionIRI);
                AssertionInfo info = readAssertionFromOntology(assertionType);
                if (info != null) {
                    model.add(assertionIRI, 
                             vf.createIRI(consertNS + "assertionAcquisitionType"), 
                             vf.createIRI(info.acquisitionType != null ? info.acquisitionType : consertNS + "Sensed"));
                    
                    if (info.arityType != null) {
                        model.add(assertionIRI, 
                                 vf.createIRI(cashmereNS + "assertionArity"), 
                                 vf.createIRI(info.arityType));
                    }
                    
                    if (info.subjectType != null) {
                        model.add(assertionIRI, 
                                 vf.createIRI(consertNS + "assertionSubject"), 
                                 vf.createIRI(info.subjectType));
                    }
                    
                    if (info.objectType != null) {
                        model.add(assertionIRI, 
                                 vf.createIRI(consertNS + "assertionObject"), 
                                 vf.createIRI(info.objectType));
                    }
                }
            }
            
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, out);
                
                writer.startRDF();
                
                writer.handleNamespace("cashmere", cashmereNS);
                writer.handleNamespace("consert", consertNS);
                writer.handleNamespace("ex", exNS);
                writer.handleNamespace("xsd", xsdNS);
                writer.handleNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
                writer.handleNamespace("rdfs", rdfsNS);
                writer.handleNamespace("owl", owlNS);
                
                model.forEach(writer::handleStatement);
                writer.endRDF();
                
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOGGER.error("Error generating hypermedia representation for stream: " + streamURI, e);
            return "Error generating representation: " + e.getMessage();
        }
    }

    /**
     * Helper class to store assertion information from ontology
     */
    private static class AssertionInfo {
        String arityType;
        String acquisitionType;
        String subjectType;
        String objectType;
    }

    /**
     * Read assertion information from the ontology file
     */
    private AssertionInfo readAssertionFromOntology(String assertionTypeURI) {
        if (ontologyURL.isEmpty()) {
            LOGGER.warn("No ontology URL provided for stream: " + streamURI);
            return null;
        }

        try {
            AssertionInfo info = new AssertionInfo();
            
            // Parse the ontology file
            String ontologyPath = ontologyURL.get();
            if (ontologyPath.startsWith("file://")) {
                ontologyPath = ontologyPath.substring(7); // Remove "file://" prefix
            }
            
            // Read and parse the ontology file
            final Model ontologyModel;
            try (FileInputStream fis = new FileInputStream(ontologyPath)) {
                ontologyModel = Rio.parse(fis, "", RDFFormat.TURTLE);
            }
            
            SimpleValueFactory vf = SimpleValueFactory.getInstance();
            IRI assertionIRI = vf.createIRI(assertionTypeURI);
            
            // Find rdfs:subClassOf statements to determine arity
            String consertNS = "http://pervasive.semanticweb.org/ont/2017/07/consert/core#";
            String rdfsNS = "http://www.w3.org/2000/01/rdf-schema#";
            String owlNS = "http://www.w3.org/2002/07/owl#";
            
            IRI rdfsSubClassOf = vf.createIRI(rdfsNS + "subClassOf");
            
            ontologyModel.filter(assertionIRI, rdfsSubClassOf, null).forEach(stmt -> {
                String objectValue = stmt.getObject().stringValue();
                if (objectValue.contains("BinaryContextAssertion")) {
                    info.arityType = consertNS + "BinaryContextAssertion";
                } else if (objectValue.contains("UnaryContextAssertion")) {
                    info.arityType = consertNS + "UnaryContextAssertion";
                } else {
                    info.arityType = consertNS + "NaryContextAssertion";
                }
            });
            
            // Look for OWL restrictions to find subject and object types
            IRI owlOnProperty = vf.createIRI(owlNS + "onProperty");
            IRI owlAllValuesFrom = vf.createIRI(owlNS + "allValuesFrom");
            IRI assertionSubject = vf.createIRI(consertNS + "assertionSubject");
            IRI assertionObject = vf.createIRI(consertNS + "assertionObject");
            IRI assertionAcquisitionType = vf.createIRI(consertNS + "assertionAcquisitionType");
            
            // Find restrictions in the ontology
            ontologyModel.forEach(stmt -> {
                if (stmt.getSubject().toString().startsWith("_:")) { // Blank node (restriction)
                    // Check if this restriction is about our assertion
                    boolean isOurRestriction = ontologyModel.filter(assertionIRI, rdfsSubClassOf, stmt.getSubject()).size() > 0;
                    
                    if (isOurRestriction) {
                        // Find what property this restriction is about
                        ontologyModel.filter(stmt.getSubject(), owlOnProperty, null).forEach(propStmt -> {
                            String propertyURI = propStmt.getObject().stringValue();
                            
                            // Find the allValuesFrom for this restriction
                            ontologyModel.filter(stmt.getSubject(), owlAllValuesFrom, null).forEach(valueStmt -> {
                                String valueType = valueStmt.getObject().stringValue();
                                
                                if (propertyURI.equals(assertionSubject.stringValue())) {
                                    info.subjectType = valueType;
                                } else if (propertyURI.equals(assertionObject.stringValue())) {
                                    info.objectType = valueType;
                                } else if (propertyURI.equals(assertionAcquisitionType.stringValue())) {
                                    info.acquisitionType = valueType;
                                }
                            });
                        });
                    }
                }
            });
            
            return info;
            
        } catch (IOException e) {
            LOGGER.error("Error reading ontology file for assertion: " + assertionTypeURI, e);
            return null;
        } catch (Exception e) {
            LOGGER.error("Error parsing ontology for assertion: " + assertionTypeURI, e);
            return null;
        }
    }

    public ContextStream(String streamURI, String ontologyURL, List<String> contextAssertionTypes) {
        
        this.streamURI = streamURI;
        this.streamName = extractStreamName(streamURI);
        this.ontologyURL = Optional.ofNullable(ontologyURL);

        if (contextAssertionTypes != null) {
            this.contextAssertionTypes.addAll(contextAssertionTypes);
        }

        // initialize the data stream
        dataStream = new DataStreamImpl<>(streamURI);
    }

    /**
     * Returns the URI of the stream.
     * @return A string representing the URI of the stream.
     */
    public String getStreamURI() {
        return streamURI;
    }

    /**
     * Returns the local name of the stream, taken from the last path segment of the stream URI.
     * @return A string representing the local name of the stream.
     */
    public String getStreamName() {
        return streamName;
    }
    
    /**
     * Returns the URL to the ontology that describes the ContextAssertion and ContextEntity types in this stream.
     * @return The URL to the ontology that describes the ContextAssertion and ContextEntity types in this stream. May be empty if no ontology was provided.
     */
    public Optional<String> getOntologyURL() {
        return ontologyURL;
    }

    /**
     * Returns the types of ContextAssertions that are part of this stream.
     * @return The URIs of the ContextAssertions that are part of this stream.
     */
    public List<String> getContextAssertionTypes() {
        return contextAssertionTypes;
    }

    /**
     * Returns whether this stream contains only a single type of ContextAssertion.
     * @return True if the stream contains only a single type of ContextAssertion, false otherwise.
     */
    public boolean isSingleAssertionStream() {
        return contextAssertionTypes.size() == 1;
    }

    /**
     * Returns the timestamp of the last update to this stream in milliseconds.
     * @return The timestamp of the last update to this stream.
     */
    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp.get();
    }

    /**
     * Get the data stream to which this Context Stream writes its updates.
     * @return The DataStream to which this Context Stream writes its updates.
     */
    public DataStream<Graph> getDataStream() {
        return dataStream;
    }

    /**
     * Update the stream with a new graph and timestamp of the update.
     * @param graph The new RDF graph containing the updated data.
     * @param timestamp The timestamp of the update in milliseconds.
     */
    public void updateStream(Graph graph, long timestamp) {
        dataStream.put(graph, timestamp);
        lastUpdateTimestamp.set(timestamp);
    }

    /**
     * Set the data stream to which this ContextStream writes its updates. 
     * Such a request can come when registering the ContextStream with a CSPARQL engine, for example.
     * @param dataStream The DataStream to which this ContextStream writes its updates.
     */
    public void setWritableStream(DataStream<Graph> dataStream) {
        this.dataStream = dataStream;
    }

    /**
     * Extracts the stream name from the URI (last path segment).
     *
     * @param uri The full URI of the stream
     * @return The stream name (last path segment of the URI)
     */
    private String extractStreamName(String uri) {
        try {
            String path = URI.create(uri).getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                // If there's no path or it's just a root path, use the host as the name
                return URI.create(uri).getHost();
            }
            
            // Get the last segment of the path
            String[] segments = path.split("/");
            String lastSegment = segments[segments.length - 1];
            
            // If the last segment is empty (uri ends with /), use the previous segment
            if (lastSegment.isEmpty() && segments.length > 1) {
                lastSegment = segments[segments.length - 2];
            }
            
            return lastSegment.isEmpty() ? "unnamed" : lastSegment;
        } catch (Exception e) {
            // In case of any parsing issues, return a default name
            return "unnamed";
        }
    }

}
