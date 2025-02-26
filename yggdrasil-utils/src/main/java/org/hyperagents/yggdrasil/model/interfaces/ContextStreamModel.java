package org.hyperagents.yggdrasil.model.interfaces;

import java.util.List;

public interface ContextStreamModel {
    /** 
     * Retrieve the URL relative to deployment of the Yggdrasil instance, that provides the stream. 
     * @return the URL of the stream
     */
    String getStreamUri();

    /**
     * Retrieve the URL of the ontology that contains the definitions of ContextAssertions and ContextEntities used in the stream.
     * @return the URL of the ontology
     */
    String getOntologyUrl();

    /**
     * Retrieve the list of assertions that are part of the stream.
     * @return the list of assertions
     */
    List<String> getAssertions();
}
