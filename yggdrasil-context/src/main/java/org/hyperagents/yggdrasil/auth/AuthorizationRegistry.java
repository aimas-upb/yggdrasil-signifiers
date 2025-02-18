package org.hyperagents.yggdrasil.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;

public class AuthorizationRegistry {
  // A singleton class used to manage authorisations. 
  // Methods provided by this class are used to keep mappings between an artifact instance (denoted by its URI) and (i) the list of 
  // shared context access authorisations for that artifact, (ii) the list of shared context control authorisations for that artifact. 
  // It also provides methods to add and remove authorisations for a given artifact.
  
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

  public boolean hasAccessAuthorization(String artifactIRI, AuthorizationAccessType accessType) {
    List<ContextBasedAuthorization> accessAuthorisations = registry.getContextAuthorisations(artifactIRI);

    for (ContextBasedAuthorization accessAuthorization : accessAuthorisations) {
      if (accessAuthorization.getAccessTypes().contains(accessType)) {
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
