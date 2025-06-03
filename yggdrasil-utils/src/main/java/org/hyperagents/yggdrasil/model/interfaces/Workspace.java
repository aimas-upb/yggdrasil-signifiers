package org.hyperagents.yggdrasil.model.interfaces;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * An interface representing a Workspace in the Yggdrasil model.
 *
 * <p>A Workspace is a container that can hold artifacts and other workspaces.
 * Each Workspace has a unique name.
 */
public interface Workspace {
  String getName();

  Optional<Path> getMetaData();

  Optional<String> getParentName();

  Set<Artifact> getArtifacts();

  Set<YggdrasilAgent> getAgents();

  Optional<Path> getRepresentation();
  
  Optional<String> getContextAccessPolicyURL();
}
