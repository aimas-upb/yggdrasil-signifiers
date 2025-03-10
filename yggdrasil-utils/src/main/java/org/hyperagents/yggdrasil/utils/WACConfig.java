package org.hyperagents.yggdrasil.utils;

import io.vertx.core.shareddata.Shareable;

public interface WACConfig extends Shareable {
    /**
     * Checks if Web Access Control is enabled.
     * @return True if Web Access Control is enabled, false otherwise.
     */
    boolean isEnabled();
}
