package org.hyperagents.yggdrasil.utils.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.utils.JsonObjectUtils;
import org.hyperagents.yggdrasil.utils.WACConfig;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class WACConfigImpl implements WACConfig {
    private static final Logger LOGGER = LogManager.getLogger(WACConfigImpl.class);

    private final JsonObject wacConfig;
    private final boolean enabled;
    private final Map<String, String> workspacePolicies;

    public WACConfigImpl(final JsonObject config) {
        this.wacConfig = JsonObjectUtils.getJsonObject(config, "wac", LOGGER::error)
                .orElse(null);
        
        this.enabled = wacConfig != null && wacConfig.getBoolean("enabled", false);
        this.workspacePolicies = parseWorkspacePolicies();
    }

    private Map<String, String> parseWorkspacePolicies() {
        Map<String, String> policies = new HashMap<>();
        
        if (wacConfig != null && wacConfig.containsKey("workspace-policies")) {
            JsonArray policiesArray = wacConfig.getJsonArray("workspace-policies");
            for (Object policyObj : policiesArray) {
                if (policyObj instanceof JsonObject policy) {
                    String workspaceUri = policy.getString("workspace-uri");
                    String policyFile = policy.getString("policy-file");
                    if (workspaceUri != null && policyFile != null) {
                        policies.put(workspaceUri, policyFile);
                        LOGGER.info("Loaded workspace policy: {} -> {}", workspaceUri, policyFile);
                    }
                }
            }
        }
        
        return policies;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public Map<String, String> getWorkspacePolicies() {
        return new HashMap<>(workspacePolicies);
    }
}
