package org.hyperagents.yggdrasil.utils;

import io.vertx.core.shareddata.Shareable;
import java.util.List;

public interface WACConfig extends Shareable {
    /**
     * Checks if Web Access Control is enabled.
     * @return True if Web Access Control is enabled, false otherwise.
     */
    boolean isEnabled();
    
    List<WorkspacePolicy> getWorkspacePolicies();
    
    interface WorkspacePolicy {
        String getWorkspaceUri();
        String getPolicyUrl();
    }
}
