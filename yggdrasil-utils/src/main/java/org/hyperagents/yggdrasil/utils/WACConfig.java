package org.hyperagents.yggdrasil.utils;

import io.vertx.core.shareddata.Shareable;
import java.util.Map;

public interface WACConfig extends Shareable {
    /**
     * Checks if Web Access Control is enabled.
     * @return True if Web Access Control is enabled, false otherwise.
     */
    boolean isEnabled();
    
    /**
     * Gets workspace policies as a map from workspace URI to policy file path.
     * @return Map of workspace URI to policy file path
     */
    Map<String, String> getWorkspacePolicies();
}
