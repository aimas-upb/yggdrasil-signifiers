package org.hyperagents.yggdrasil.model.impl;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.model.interfaces.ContextStreamModel;
import org.hyperagents.yggdrasil.utils.JsonObjectUtils;

import io.vertx.core.json.JsonObject;

public final class ContextStreamModelImpl implements ContextStreamModel {
    private final String streamUri;
    private final String ontologyUrl;
    private final List<String> assertions;
    
    private static final Logger LOGGER = LogManager.getLogger(ContextStreamModelImpl.class);

    public ContextStreamModelImpl(JsonObject contextStreamConfig) {
        this.streamUri = JsonObjectUtils.getString(contextStreamConfig, "stream_uri", LOGGER::error).orElse(null);
        this.ontologyUrl = JsonObjectUtils.getString(contextStreamConfig, "ontology_url", LOGGER::error).orElse(null);
        
        this.assertions = JsonObjectUtils.getJsonArray(contextStreamConfig, "assertions", LOGGER::error)
            .map(array -> array.stream()
                .map(Object::toString)
                .toList())
            .orElse(Collections.emptyList());
    }

    @Override
    public String getStreamUri() {
        return streamUri;
    }

    @Override
    public String getOntologyUrl() {
        return ontologyUrl;
    }

    @Override
    public List<String> getAssertions() {
        return assertions;
    }
}
