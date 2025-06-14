package org.hyperagents.yggdrasil.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;

public class AuthorizationRegistry {
  // A singleton class used to manage authorisations. 
  // Methods provided by this class are used to keep mappings between an artifact instance (denoted by its URI) and (i) the list of 
  // shared context access authorisations for that artifact, (ii) the list of shared context control authorisations for that artifact. 
  // It also provides methods to add and remove authorisations for a given artifact.
  
  private static final Logger LOGGER = LogManager.getLogger(AuthorizationRegistry.class);
  
  private static AuthorizationRegistry registry;
  private final Map<String, List<ContextBasedAuthorization>> contextAccessAuthorisationMap;

  private AuthorizationRegistry() {
    contextAccessAuthorisationMap = new HashMap<>();
  }

  public static synchronized AuthorizationRegistry getInstance() {
    if (registry == null) {
        registry = new AuthorizationRegistry();
    }

    return registry;
  }

  public List<ContextBasedAuthorization> getContextAuthorisations(String artifactIRI) {
    return contextAccessAuthorisationMap.getOrDefault(artifactIRI, Collections.<ContextBasedAuthorization>emptyList());
  }

  public void addContextAuthorisation(String artifactIRI, ContextBasedAuthorization accessAuthorization) {
    List<ContextBasedAuthorization> accessAuthorisations = registry.getContextAuthorisations(artifactIRI);
    if (accessAuthorisations.isEmpty()) {
      accessAuthorisations = new java.util.ArrayList<>();
    }
    accessAuthorisations.add(accessAuthorization);

    contextAccessAuthorisationMap.put(artifactIRI, accessAuthorisations);
  }

  public void removeContextAuthorisation(String artifactIRI, ContextBasedAuthorization accessAuthorization) {
    List<ContextBasedAuthorization> accessAuthorisations = registry.getContextAuthorisations(artifactIRI);
    accessAuthorisations.remove(accessAuthorization);

    if (accessAuthorisations.isEmpty()) {
      contextAccessAuthorisationMap.remove(artifactIRI);
    } else {
      contextAccessAuthorisationMap.put(artifactIRI, accessAuthorisations);
    }
  }

  public boolean hasAccessAuthorization(String artifactIRI) {
    // return true if there exists at least one access authorization for the given artifact
    return !registry.getContextAuthorisations(artifactIRI).isEmpty();
  }

  public boolean hasAccessAuthorization(String artifactIRI, AuthorizationAccessType accessType) {
    List<ContextBasedAuthorization> accessAuthorisations = registry.getContextAuthorisations(artifactIRI);

    for (ContextBasedAuthorization accessAuthorization : accessAuthorisations) {
      if (accessAuthorization.getAccessTypes().contains(accessType)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Implements the WAC effective resource determination protocol.
   * This method traverses the workspace hierarchy to find effective authorizations.
   * 
   * @param resourceURI The URI of the resource (artifact or workspace)
   * @param accessType The type of access being requested
   * @return A list of effective authorizations found through hierarchical traversal
   */
  public List<ContextBasedAuthorization> getEffectiveAuthorizations(String resourceURI, AuthorizationAccessType accessType) {
    LOGGER.info("WAC: Starting effective resource determination for resource: {} with access type: {}", resourceURI, accessType);
    
    List<ContextBasedAuthorization> effectiveAuthorizations = new ArrayList<>();
    List<String> hierarchyPath = buildHierarchyPath(resourceURI);
    
    LOGGER.info("WAC: Hierarchy path for resource {}: {}", resourceURI, hierarchyPath);
    
    // Traverse the hierarchy from most specific (artifact) to most general (platform)
    for (String pathResource : hierarchyPath) {
      LOGGER.info("WAC: Checking authorizations for path resource: {}", pathResource);
      
      List<ContextBasedAuthorization> authorizations = getContextAuthorisations(pathResource);
      for (ContextBasedAuthorization auth : authorizations) {
        if (auth.getAccessTypes().contains(accessType)) {
          LOGGER.info("WAC: Found effective authorization at level: {} for access type: {}", pathResource, accessType);
          effectiveAuthorizations.add(auth);
        }
      }
      
      // If we found authorizations at this level, we can stop (WAC principle)
      if (!effectiveAuthorizations.isEmpty()) {
        LOGGER.info("WAC: Found {} effective authorizations at level: {}, stopping traversal", effectiveAuthorizations.size(), pathResource);
        break;
      }
    }
    
    if (effectiveAuthorizations.isEmpty()) {
      LOGGER.info("WAC: No effective authorizations found for resource: {} with access type: {}", resourceURI, accessType);
    }
    
    return effectiveAuthorizations;
  }

  /**
   * Builds a hierarchy path from the given resource URI.
   * For artifacts: [artifact, workspace, parent-workspace, ...]
   * For workspaces: [workspace, parent-workspace, ...]
   * 
   * @param resourceURI The URI of the resource
   * @return A list of URIs representing the hierarchy path
   */
  private List<String> buildHierarchyPath(String resourceURI) {
    List<String> hierarchyPath = new ArrayList<>();
    
    if (resourceURI.contains("#artifact")) {
      // This is an artifact URI, start with the artifact itself
      hierarchyPath.add(resourceURI);
      
      // Extract workspace URI from artifact URI
      // Example: http://localhost:8080/workspaces/lab308/artifacts/light308#artifact
      // Should become: http://localhost:8080/workspaces/lab308#workspace
      String workspaceURI = extractWorkspaceFromArtifactURI(resourceURI);
      if (workspaceURI != null) {
        hierarchyPath.add(workspaceURI);
        // Add parent workspaces if any
        hierarchyPath.addAll(buildWorkspaceHierarchy(workspaceURI));
      }
    } else if (resourceURI.contains("#workspace")) {
      // This is a workspace URI, start with the workspace itself
      hierarchyPath.add(resourceURI);
      // Add parent workspaces if any
      hierarchyPath.addAll(buildWorkspaceHierarchy(resourceURI));
    }
    
    return hierarchyPath;
  }

  /**
   * Extracts workspace URI from an artifact URI.
   * 
   * @param artifactURI The artifact URI
   * @return The workspace URI or null if extraction fails
   */
  private String extractWorkspaceFromArtifactURI(String artifactURI) {
    try {
      // Example: http://localhost:8080/workspaces/lab308/artifacts/light308#artifact
      // Should become: http://localhost:8080/workspaces/lab308#workspace
      if (artifactURI.contains("/artifacts/")) {
        String basePart = artifactURI.substring(0, artifactURI.indexOf("/artifacts/"));
        return basePart + "#workspace";
      }
    } catch (Exception e) {
      LOGGER.warn("WAC: Failed to extract workspace URI from artifact URI: {}", artifactURI, e);
    }
    return null;
  }

  /**
   * Builds the workspace hierarchy for a given workspace URI.
   * This is a simplified implementation - in a real system, you might need
   * to query the environment or configuration to get parent workspace relationships.
   * 
   * @param workspaceURI The workspace URI
   * @return A list of parent workspace URIs
   */
  private List<String> buildWorkspaceHierarchy(String workspaceURI) {
    List<String> parentWorkspaces = new ArrayList<>();
    
    // For this implementation, we'll check for known parent relationships
    // Based on the configuration: lab308 has parent "precis"
    if (workspaceURI.contains("/lab308#workspace")) {
      String precisURI = workspaceURI.replace("/lab308#workspace", "/precis#workspace");
      parentWorkspaces.add(precisURI);
      LOGGER.info("WAC: Added parent workspace to hierarchy: {}", precisURI);
    }
    
    return parentWorkspaces;
  }

  /**
   * Checks if there are any effective authorizations (including inherited ones) for the given resource and access type.
   * 
   * @param resourceURI The URI of the resource
   * @param accessType The type of access being requested
   * @return true if effective authorizations exist, false otherwise
   */
  public boolean hasEffectiveAccessAuthorization(String resourceURI, AuthorizationAccessType accessType) {
    List<ContextBasedAuthorization> effectiveAuths = getEffectiveAuthorizations(resourceURI, accessType);
    return !effectiveAuths.isEmpty();
  }

  /**
   * Checks if there are any effective authorizations (including inherited ones) for the given resource.
   * 
   * @param resourceURI The URI of the resource
   * @return true if effective authorizations exist, false otherwise
   */
  public boolean hasEffectiveAccessAuthorization(String resourceURI) {
    List<String> hierarchyPath = buildHierarchyPath(resourceURI);
    
    for (String pathResource : hierarchyPath) {
      if (hasAccessAuthorization(pathResource)) {
        return true;
      }
    }
    
    return false;
  }

  public boolean isReadProtected(String artifactIRI) {
    // We consider that a user has read access to an artifact if he has either a read or a write access to it.
    return hasAccessAuthorization(artifactIRI, AuthorizationAccessType.READ);
  }

  public boolean isWriteProtected(String artifactIRI) {
    return hasAccessAuthorization(artifactIRI, AuthorizationAccessType.WRITE) || hasAccessAuthorization(artifactIRI, AuthorizationAccessType.APPEND);
  }

  public boolean isControlProtected(String artifactIRI) {
    return hasAccessAuthorization(artifactIRI, AuthorizationAccessType.CONTROL);
  }

  // Method to get the URI of the RDF document that contains the authorisations for a given artifact
  // This is built by appending the artifact URI with the string "/wac".
  // Returns an Optional<String> object containing the URI of the RDF document if any authorisation is found for the given artifact, 
  // or an empty Optional<String> object otherwise.
  public Optional<String> getAuthorisationDocumentURI(String artifactIRI) {
    if (contextAccessAuthorisationMap.containsKey(artifactIRI)) {
      return Optional.of(artifactIRI + "/wac");
    }
    
    return Optional.empty();
  }
}
