package org.hyperagents.yggdrasil.model.interfaces;

import java.util.List;

public interface ContextStream {
    /** 
     * Retrieve the URL relative to deployment of the Yggdrasil instance, that provides the stream. 
     * @return the URL of the stream
     */
    String getStreamUrl();

    /**
     * Retrieve the URL of the ontology that contains the definitions of ContextAssertions and ContextEntities used in the stream.
     * @return the URL of the ontology
     */
    String getOntologyUrl();

    /**
     * Retrieve the Java qualified name of the class that implements a generator for the stream
     * @return the URL of the context stream or null if no generator is available
     */
    String getGeneratorClass();

    /**
     * Boolean to check if the stream contains a single assertion or a list of assertions.
     * @return True if the stream contains a single assertion, false otherwise.
     */
    Boolean isSingleAssertion();

    /**
     * Retrieve the list of assertions that are part of the stream.
     * @return the list of assertions
     */
    List<String> getAssertions();
}
