package org.hyperagents.yggdrasil.model.interfaces;

import java.util.List;

public interface ContextDomain {
    /**
     * Retrieve the URI of the ContextDomain.
     * @return the URI of the ContextDomain
     */
    String getDomainUri();

    /**
     * Retrieve the list of ContextStreams which are required by the membership rule to infer the membership of an agent in the domain.
     * @return the list of ContextStreams
     */
    List<String> getStreams();

    /**
     * Retrieve the list of membership rules that define the membership of an agent in the domain.
     * @return the list of membership rules
     */
    List<String> getMembershipRules();

    /**
     * Retrieve the URL of the engine configuration file that contains the CSPARQL engine specifications used to run inference rules that define
     * the membership of an agent in the domain.
     * @return the URL of the engine configuration file
     */
    String getEngineConfigUrl();

    /**
     * Boolean to check if the domain uses a single stream or multiple streams.
     * @return True if the domain uses a single stream, false otherwise.
     */
    boolean usesSingleStream();

    /**
     * Retrieve the primary stream URI of the domain. 
     * This is a helper method that is useful in the case where there is a single Stream in use to define the ContextDomain.
     * This will usually be the first stream in the list of streams.
     * @return the primary stream URI
     */
    String getPrimaryStreamURI();
}
