package org.hyperagents.yggdrasil.cartago;

import io.vertx.core.json.JsonObject;

import java.util.*;

/**
 * A singleton used to manage CArtAgO artifacts. An equivalent implementation can be obtained with
 * local maps in Vert.x. Can be refactored using async shared maps to run over a cluster.
 */
public class HypermediaArtifactRegistry {
  private static HypermediaArtifactRegistry REGISTRY;

  // Maps a workspace name to the name of the hosting environment
  private final Map<String, String> workspaceEnvironmentMap;

  // Maps an artifact type IRI to the canonical names of the corresponding CArtAgO artifact class
  // E.g.: "https://ci.mines-stetienne.fr/kg/ontology#PhantomX_3D" ->
  // "org.hyperagents.yggdrasil.cartago.artifacts.PhantomX3D"
  private final Map<String, String> artifactSemanticTypes;

  // Maps the Cname of a CArtAgO artifact to a semantic description of the artifact's HTTP interface
  // exposed by Yggdrasil
  private final Map<String, String> artifactTemplateDescriptions;

  // Maps an HTTP request to an action name. The HTTP request is currently identified by
  // [HTTP_Method] + [HTTP_Target_URI].
  private final Map<String, String> artifactActionRouter;

  // Maps the IRI of an artifact to an API key to be used for that artifact
  private final Map<String, String> artifactAPIKeys;

  private String httpPrefix = "http://localhost:8080";

  private HypermediaArtifactRegistry() {
    this.workspaceEnvironmentMap = new Hashtable<>();
    this.artifactSemanticTypes = new Hashtable<>();
    this.artifactTemplateDescriptions = new Hashtable<>();
    this.artifactActionRouter = new Hashtable<>();
    this.artifactAPIKeys = new Hashtable<>();
  }

  public static synchronized HypermediaArtifactRegistry getInstance() {
    if (REGISTRY == null) {
        REGISTRY = new HypermediaArtifactRegistry();
    }

    return REGISTRY;
  }

  public void register(final HypermediaArtifact artifact) {
    this.artifactTemplateDescriptions.put(artifact.getArtifactId().getName(), artifact.getHypermediaDescription());

    final var actions = artifact.getActionAffordances();

    for (final var actionName : actions.keySet()) {
      for (final var action : actions.get(actionName)) {
        action.getFirstForm().ifPresent(value -> {
          if (value.getMethodName().isPresent()) {
            this.artifactActionRouter.put(value.getMethodName().get() + value.getTarget(), actionName);
          }
        });
      }
    }
  }

  public void addWorkspace(final String envName, final String workspaceName) {
    this.workspaceEnvironmentMap.put(workspaceName, envName);
  }

  public Optional<String> getEnvironmentForWorkspace(final String workspaceName) {
    return Optional.ofNullable(this.workspaceEnvironmentMap.get(workspaceName));
  }

  public void addArtifactTemplates(final JsonObject artifactTemplates) {
    Optional.ofNullable(artifactTemplates)
            .ifPresent(t -> t.forEach(e -> this.artifactSemanticTypes.put(e.getKey(), (String) e.getValue())));
  }

  public Set<String> getArtifactTemplates() {
    return this.artifactSemanticTypes.keySet();
  }

  public Optional<String> getArtifactSemanticType(final String artifactTemplate) {
    return this.artifactSemanticTypes
               .keySet()
               .stream()
               .filter(a -> this.artifactSemanticTypes.get(a).equals(artifactTemplate))
               .findFirst();
  }

  public Optional<String> getArtifactTemplate(final String artifactClass) {
    return Optional.ofNullable(this.artifactSemanticTypes.get(artifactClass));
  }

  public String getArtifactDescription(final String artifactName) {
    return this.artifactTemplateDescriptions.get(artifactName);
  }

  public String getActionName(final String method, final String requestURI) {
    return this.artifactActionRouter.get(method + requestURI);
  }

  public void setAPIKeyForArtifact(final String artifactId, final String apiKey) {
    this.artifactAPIKeys.put(artifactId, apiKey);
  }

  public String getAPIKeyForArtifact(final String artifactId) {
    return this.artifactAPIKeys.get(artifactId);
  }

  public void setHttpPrefix(final String prefix) {
    this.httpPrefix = prefix;
  }

  public String getHttpPrefix() {
    return this.httpPrefix;
  }

  public String getHttpEnvironmentsPrefix() {
    return this.getHttpPrefix() + "/environments/";
  }

  public String getHttpWorkspacesPrefix(final String envId) {
    return this.getHttpEnvironmentsPrefix() + envId + "/workspaces/";
  }

  public String getHttpArtifactsPrefix(final String workspaceName) {
    return this.getEnvironmentForWorkspace(workspaceName)
               .map(i -> this.getHttpWorkspacesPrefix(i) + workspaceName + "/artifacts/")
               .orElseThrow(() -> new IllegalArgumentException("Workspace " + workspaceName + " not found in any environment."));
  }
}
