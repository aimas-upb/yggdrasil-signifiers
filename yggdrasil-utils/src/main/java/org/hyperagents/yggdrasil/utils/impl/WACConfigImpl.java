package org.hyperagents.yggdrasil.utils.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.utils.JsonObjectUtils;
import org.hyperagents.yggdrasil.utils.WACConfig;

import io.vertx.core.json.JsonObject;
import java.util.List;

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
    
    @Override
    public List<WorkspacePolicy> getWorkspacePolicies() {
        if (wacConfig == null || !wacConfig.containsKey("workspace-policies")) {
            return List.of();
        }
        
        return wacConfig.getJsonArray("workspace-policies")
            .stream()
            .map(obj -> (JsonObject) obj)
            .map(policy -> new WorkspacePolicyImpl(
                policy.getString("workspace-uri"),
                policy.getString("policy-url")
            ))
            .map(WorkspacePolicy.class::cast)
            .toList();
    }
    
    private static class WorkspacePolicyImpl implements WorkspacePolicy {
        private final String workspaceUri;
        private final String policyUrl;
        
        public WorkspacePolicyImpl(String workspaceUri, String policyUrl) {
            this.workspaceUri = workspaceUri;
            this.policyUrl = policyUrl;
        }
        
        @Override
        public String getWorkspaceUri() { return workspaceUri; }
        
        @Override
        public String getPolicyUrl() { return policyUrl; }
    }
}
