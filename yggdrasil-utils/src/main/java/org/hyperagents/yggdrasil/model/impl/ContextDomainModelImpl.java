package org.hyperagents.yggdrasil.model.impl;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.model.interfaces.ContextDomainModel;
import org.hyperagents.yggdrasil.utils.JsonObjectUtils;

import io.vertx.core.json.JsonObject;


public final class ContextDomainModelImpl implements ContextDomainModel {
    private static final Logger LOGGER = LogManager.getLogger(ContextDomainModelImpl.class);
    
    private final String domainUri;
    private final List<String> streams;
    private final List<String> membershipRules;
    private final String engineConfigUrl;

    public ContextDomainModelImpl(JsonObject contextDomainConfig) {
        this.domainUri = JsonObjectUtils.getString(contextDomainConfig, "domain_uri", LOGGER::error).orElse(null);

        this.streams = JsonObjectUtils.getJsonArray(contextDomainConfig, "streams", LOGGER::error)
            .map(array -> array.stream()
                .map(Object::toString)
                .toList())
            .orElse(Collections.emptyList());

        this.membershipRules = JsonObjectUtils.getJsonArray(contextDomainConfig, "membership_rules", LOGGER::error)
            .map(array -> array.stream()
                .map(Object::toString)
                .toList())
            .orElse(Collections.emptyList());

        this.engineConfigUrl = JsonObjectUtils.getString(contextDomainConfig, "engine_config_url", LOGGER::error).orElse(null);
    }

    @Override
    public String getDomainUri() {
        return domainUri;
    }

    @Override
    public List<String> getStreams() {
        return streams;
    }

    @Override
    public List<String> getMembershipRules() {
        return membershipRules;
    }

    @Override
    public String getEngineConfigUrl() {
        return engineConfigUrl;
    }

    @Override
    public String getPrimaryStreamURI() {
        return streams.isEmpty() ? null : streams.get(0);
    }

    @Override
    public boolean usesSingleStream() {
        return streams.size() == 1;
    }
}
