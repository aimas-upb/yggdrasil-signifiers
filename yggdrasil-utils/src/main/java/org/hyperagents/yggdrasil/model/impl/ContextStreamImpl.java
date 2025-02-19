package org.hyperagents.yggdrasil.model.impl;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.model.interfaces.ContextStream;
import org.hyperagents.yggdrasil.utils.JsonObjectUtils;

import io.vertx.core.json.JsonObject;

public final class ContextStreamImpl implements ContextStream {
    private final String streamUrl;
    private final String ontologyUrl;
    private final String generatorClass;
    private final Boolean singleAssertion;
    private final List<String> assertions;
    
    private static final Logger LOGGER = LogManager.getLogger(ContextStreamImpl.class);

    public ContextStreamImpl(JsonObject contextStreamConfig) {
        this.streamUrl = JsonObjectUtils.getString(contextStreamConfig, "stream_url", LOGGER::error).orElse(null);
        this.ontologyUrl = JsonObjectUtils.getString(contextStreamConfig, "ontology_url", LOGGER::error).orElse(null);
        
        this.generatorClass = JsonObjectUtils.getString(contextStreamConfig, "generator_class", LOGGER::error).orElse(null);
        
        this.singleAssertion = JsonObjectUtils.getBoolean(contextStreamConfig, "single_assertion", LOGGER::error).orElse(null);
        this.assertions = JsonObjectUtils.getJsonArray(contextStreamConfig, "assertions", LOGGER::error)
            .map(array -> array.stream()
                .map(Object::toString)
                .toList())
            .orElse(Collections.emptyList());
    }

    @Override
    public String getStreamUrl() {
        return streamUrl;
    }

    @Override
    public String getOntologyUrl() {
        return ontologyUrl;
    }

    @Override
    public String getGeneratorClass() {
        return generatorClass;
    }

    @Override
    public Boolean isSingleAssertion() {
        return singleAssertion;
    }

    @Override
    public List<String> getAssertions() {
        return assertions;
    }
}
