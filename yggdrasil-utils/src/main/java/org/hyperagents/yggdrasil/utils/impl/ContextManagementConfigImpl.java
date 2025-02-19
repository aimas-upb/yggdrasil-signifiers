package org.hyperagents.yggdrasil.utils.impl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.model.impl.ContextDomainImpl;
import org.hyperagents.yggdrasil.model.impl.ContextStreamImpl;
import org.hyperagents.yggdrasil.model.interfaces.ContextDomain;
import org.hyperagents.yggdrasil.model.interfaces.ContextStream;
import org.hyperagents.yggdrasil.utils.ContextManagementConfig;
import org.hyperagents.yggdrasil.utils.JsonObjectUtils;

import io.vertx.core.json.JsonObject;

public class ContextManagementConfigImpl implements ContextManagementConfig {

    private static final Logger LOGGER = LogManager.getLogger(ContextManagementConfigImpl.class);
    private static final String CONTEXT_SERVICE_PATH = "context/";

    private static final String STATIC_CONTEXT_KEY = "static-context";
    private static final String PROFILED_CONTEXT_KEY = "profiled-context";
    private static final String CONTEXT_STREAMS_KEY = "context-streams";
    private static final String CONTEXT_DOMAINS_KEY = "context-domains";

    private final String baseURI;

    private final boolean enabled;
    private final String serviceURI;
    private final String staticContextGraphURI;
    private final String profiledContextGraphURI;

    private final List<ContextStream> contextStreams;
    private final List<ContextDomain> contextDomains;

    private final JsonObject contextManagementConfig;

    public ContextManagementConfigImpl(final JsonObject config) {
        contextManagementConfig = JsonObjectUtils.getJsonObject(config, "context-management-config", LOGGER::error)
                .orElse(null);
        
        this.enabled = contextManagementConfig != null
                && JsonObjectUtils.getBoolean(contextManagementConfig, "enabled", LOGGER::error).orElse(false);

        // The service URI is constructed from the base URI and the context service path
        final var httpConfig = JsonObjectUtils.getJsonObject(config, "http-config", LOGGER::error).orElse(null);
        this.baseURI = httpConfig.getString("base-uri", "http://localhost/");
        this.serviceURI = baseURI + CONTEXT_SERVICE_PATH;

        // The static and profiled context graph URIs are read from the context management configuration, if available
        this.staticContextGraphURI = contextManagementConfig != null
                ? JsonObjectUtils.getString(contextManagementConfig, STATIC_CONTEXT_KEY, LOGGER::error).orElse(null)
                : null;
        this.profiledContextGraphURI = contextManagementConfig != null
                ? JsonObjectUtils.getString(contextManagementConfig, PROFILED_CONTEXT_KEY, LOGGER::error).orElse(null)
                : null;

        // The context streams and domains are read from the context management configuration, if available
        this.contextStreams = JsonObjectUtils.getJsonArray(contextManagementConfig, CONTEXT_STREAMS_KEY, LOGGER::error)
                .map(array -> array.stream()
                    .map(obj -> new ContextStreamImpl((JsonObject) obj))
                    .map(ContextStream.class::cast)
                    .toList())
                .orElse(Collections.emptyList());
        
        this.contextDomains = JsonObjectUtils.getJsonArray(contextManagementConfig, CONTEXT_DOMAINS_KEY, LOGGER::error)
                .map(array -> array.stream()
                    .map(obj -> new ContextDomainImpl((JsonObject) obj))
                    .map(ContextDomain.class::cast)
                    .toList())
                .orElse(Collections.emptyList());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getServiceURI() {
        return serviceURI;
    }

    @Override
    public String getStaticContextGraphURI() {
        return staticContextGraphURI;
    }

    @Override
    public String getProfiledContextGraphURI() {
        return profiledContextGraphURI;
    }

    @Override
    public List<ContextStream> getContextStreams() {
        return contextStreams;
    }


    @Override
    public Optional<ContextStream> getContextStreamByURI(String contextStreamURI) {
        for (ContextStream stream : contextStreams) {
            if (stream.getStreamUrl().equals(contextStreamURI)) {
                return Optional.of(stream);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ContextDomain> getContextDomains() {
        return contextDomains;
    }

    @Override
    public Optional<ContextDomain> getContextDomainByURI(String uri) {
        for (ContextDomain domain : contextDomains) {
            if (domain.getDomainUri().equals(uri)) {
                return Optional.of(domain);
            }
        }
        return Optional.empty();
    }
}
