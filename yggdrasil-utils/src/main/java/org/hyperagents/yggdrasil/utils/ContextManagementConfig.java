package org.hyperagents.yggdrasil.utils;

import java.util.List;
import java.util.Optional;

import org.hyperagents.yggdrasil.model.interfaces.ContextDomainModel;
import org.hyperagents.yggdrasil.model.interfaces.ContextStreamModel;

import io.vertx.core.shareddata.Shareable;

public interface ContextManagementConfig extends Shareable {
    public static final String CONTEXT_SERVICE_PATH = "context/";
    public static final String CONTEXT_STREAMS_PATH = "context/streams/";
    public static final String CONTEXT_DOMAINS_PATH = "context/domains/";
    public static final String STREAM_UPDATES_PATH = "context/streams/updates";
    
    /**
     * Checks if context manamgent is enabled.
     * @return True if context management is enabled, false otherwise.
     */
    boolean isEnabled();

    /**
     * Gets the URI for the context management service.
     * @return The context management service URI.
     */
    String getServiceURI();

    /**
     * Gets the URI for the default RDF graph holding static context information.
     * @return The default static context graph URI.
     */
    String getStaticContextGraphURI();

    /**
     * Gets the URI for the default RDF graph holding profiled context information.
     * @return The default profiled context graph URI.
     */
    String getProfiledContextGraphURI();
    
    /**
     * Retrieve the list of context streams that are indexed and managed by the context management service.
     * @return The list of context streams.
     * 
     */
    List<ContextStreamModel> getContextStreams();

    /**
     * Retrieve the list of context domains that are indexed and managed by the context management service.
     * @return The list of context domains.
     */
    List<ContextDomainModel> getContextDomains();

    /**
     * Retrieve a ContextDomain by its URI.
     * @param domainURI
     * @return The ContextDomain with the given URI, or null if no such domain exists.
     */
    Optional<ContextDomainModel> getContextDomainByURI(String domainURI);

    /**
     * Retrieve a ContextStream by its URI.
     * @param streamURI
     * @return The ContextStream with the given URI, or null if no such stream exists.
     */
    Optional<ContextStreamModel> getContextStreamByURI(String streamURI);
}
