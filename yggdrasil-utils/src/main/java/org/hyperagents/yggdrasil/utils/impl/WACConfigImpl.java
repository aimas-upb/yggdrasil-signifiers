package org.hyperagents.yggdrasil.utils.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.utils.JsonObjectUtils;
import org.hyperagents.yggdrasil.utils.WACConfig;

import io.vertx.core.json.JsonObject;

public class WACConfigImpl implements WACConfig {
    private static final Logger LOGGER = LogManager.getLogger(WACConfigImpl.class);

    private final JsonObject wacConfig;
    private final boolean enabled;

    public WACConfigImpl(final JsonObject config) {
        this.wacConfig = JsonObjectUtils.getJsonObject(config, "wac", LOGGER::error)
                .orElse(null);
        
        this.enabled = wacConfig != null && wacConfig.getBoolean("enabled", false);
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
