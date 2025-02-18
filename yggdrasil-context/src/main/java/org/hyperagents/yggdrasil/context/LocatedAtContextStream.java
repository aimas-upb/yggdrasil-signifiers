package org.hyperagents.yggdrasil.context;

import java.util.UUID;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;

public class LocatedAtContextStream extends LocalContextStream {
  private static final String LOCATED_AT_STREAM_NAME = "<LocatedAt>";
  private static final String LOCATED_AT_STREAM_URI = "http://example.org/environments/upb_hmas/ctxmgmt/streams/LocatedAt";
  
  private static final String EXAMPLE_BASE_IRI 		    = "http://example.org/";
  private static final String ANNOTATION_IRI 	        = "http://pervasive.semanticweb.org/ont/2017/07/consert/annotation#";
  private static final String TIMESTAMP_ANN           = ANNOTATION_IRI + "NumericTimestampAnnotation";
  private static final String ANN_HAS_VALUE           = ANNOTATION_IRI + "hasValue";

  private static final String ASSERTION_SUBJECT_IRI = "http://pervasive.semanticweb.org/ont/2017/07/consert/core#assertionSubject";
  private static final String ASSERTION_OBJECT_IRI 	= "http://pervasive.semanticweb.org/ont/2017/07/consert/core#assertionObject";

  private static final String LOCATED_AT_ASSERTION    = EXAMPLE_BASE_IRI + "LocatedAt";
  private static final String SUBJECT_ALEX            = EXAMPLE_BASE_IRI + "alexAgent";
  private static final String OBJECT_LAB308           = EXAMPLE_BASE_IRI + "lab308";

  /** 
  * Constructor for the LocatedAtContextStream class. By default, the stream generates new context data every 5 seconds.
  */
  public LocatedAtContextStream() {
    super(LOCATED_AT_STREAM_NAME, LOCATED_AT_STREAM_URI, 5);
  }

  /**
   * Constructor for the LocatedAtContextStream class.
   * @param generateEvery: the interval in seconds at which the stream generates new context data
   */
  public LocatedAtContextStream(int generateEvery) {
    super(LOCATED_AT_STREAM_NAME, LOCATED_AT_STREAM_URI, generateEvery);
  }

  @Override
  public Graph generateData(long generationTimestamp) {
    // Generate a new RDF graph with the assertion that the artifact is located at a specific location
    Model m = ModelFactory.createDefaultModel();
        
    // generate a Jena URN for the PersonLocation assertion instance
    Resource assertionInstance = m.createResource(stream_uri + "/" + UUID.randomUUID().toString());

    m.add(m.createStatement(assertionInstance, RDF.type, 
        ResourceFactory.createResource(LOCATED_AT_ASSERTION)));
    m.add(m.createStatement(assertionInstance, 
        ResourceFactory.createProperty(ASSERTION_SUBJECT_IRI), 
        ResourceFactory.createResource(SUBJECT_ALEX)));
    m.add(m.createStatement(assertionInstance, 
        ResourceFactory.createProperty(ASSERTION_OBJECT_IRI), 
        ResourceFactory.createResource(OBJECT_LAB308)));

    // add the timestamp annotation
    Resource timestampAnnotation = m.createResource("urn:uuid:" + UUID.randomUUID().toString());
    m.add(m.createStatement(timestampAnnotation, RDF.type, TIMESTAMP_ANN));
    m.add(m.createStatement(timestampAnnotation, 
        ResourceFactory.createProperty(ANN_HAS_VALUE), 
        m.createTypedLiteral(generationTimestamp)));
    
    // add the annotation to the assertion
    m.add(m.createStatement(assertionInstance, 
        ResourceFactory.createProperty(ANNOTATION_IRI + "hasAnnotation"), 
        timestampAnnotation));
    
    return m.getGraph();
  }
  
}
