package org.hyperagents.yggdrasil.auth.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;



public class ContextBasedAuthorization {
  private static String CONTEXT_AUTH_INSTANCE_NAMESPACE = "http://example.org/context-auth-instances/";
  
  private Optional<String> resourceName = Optional.empty();
  private String resourceURI;

  private List<AuthorizationAccessType> accessTypes;
  private AuthorizedEntityType authorizedEntityType;
  private String authorizedEntityURI;
  
  private Optional<String> entityName = Optional.empty();
  private String accessConditionsShapeURI;

  // the IRI of this authorization instance
  private String authIRI;

  /**
   * Constructor for a context-based access authorization specifying the protected web resource, the access type, 
   * the requester type (single agent, agent class, agent group) and the SHACL Shapes graph specifying the Context-Based Access Conditions
   * @param resourceUri: URI of the protected web resource
   * @param authorizationTypes: the list of types of ACL access authorization (READ, WRITE, APPEND, CONTROL)
   * @param requesterType: the type of the entity requesting access (AGENT, AGENT_CLASS, AGENT_GROUP)
   * @param accessConditionsShapeURI: the URI of the entry SHACL Shape that will specify the set of context-based conditions access conditions. 
   *    The shapes graph is deployed together with the protected resource and is used to validate the access requests.
   */
  public ContextBasedAuthorization(String resourceURI, List<AuthorizationAccessType> accessTypes, 
                                    AuthorizedEntityType authorizedEntityType, String authorizedEntityURI,
                                    String accessConditionsShapeURI) {
    this.resourceURI = resourceURI;
    this.accessTypes = accessTypes;
    this.authorizedEntityType = authorizedEntityType;
    this.authorizedEntityURI = authorizedEntityURI;

    this.accessConditionsShapeURI = accessConditionsShapeURI;
  }

  /**
   * Constructor for a context-based access authorization specifying the protected web resource, the access type, 
   * the requester type (single agent, agent class, agent group) and the SHACL Shapes graph specifying the Context-Based Access Conditions
   * @param resourceName: name of the protected web resource
   * @param resourceUri: URI of the protected web resource
   * @param authorizationTypes: the list of types of ACL access authorization (READ, WRITE, APPEND)
   * @param requesterName: the name of the entity requesting access
   * @param requesterType: the type of the entity requesting access (AGENT, AGENT_CLASS, AGENT_GROUP)
   * @param requesterIdentifierURI: the URI of the entity requesting access
   * @param accessConditionsShapeURI: the URI of the entry SHACL Shape that will specify the set of context-based conditions access conditions.
   */
  public ContextBasedAuthorization(String resourceName, String resourceUri,  List<AuthorizationAccessType> accessTypes, 
                      String authorizedEntityName, AuthorizedEntityType authorizedEntityType, String authorizedEntityURI,
                      String accessConditionsShapeURI) {
    this.resourceName = Optional.of(resourceName);
    this.resourceURI = resourceUri;
    this.accessTypes = accessTypes;
    this.authorizedEntityType = authorizedEntityType;
    this.authorizedEntityURI = authorizedEntityURI;
    this.entityName = Optional.of(authorizedEntityName);
    this.accessConditionsShapeURI = accessConditionsShapeURI;
  }

  // getters
  public Optional<String> getResourceName() {
    return resourceName;
  }

  public String getResourceURI() {
    return resourceURI;
  }

  public List<AuthorizationAccessType> getAccessTypes() {
    return accessTypes;
  }

  public boolean hasAccessType(AuthorizationAccessType accessType) {
    if (accessType == null) {
      return false;
    }
    return accessTypes.contains(accessType);
  }

  public AuthorizedEntityType getAuthorizedEntityType() {
    return authorizedEntityType;
  }

  public String getAuthorizedEntityURI() {
    return authorizedEntityURI;
  }

  public Optional<String> getEntityName() {
    return entityName;
  }

  public String getAccessConditionsShapeURI() {
    return accessConditionsShapeURI;
  }


  // create equals and hashcode
  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof ContextBasedAuthorization)) {
      return false;
    }
    ContextBasedAuthorization auth = (ContextBasedAuthorization) obj;
    return auth.resourceURI.equals(resourceURI) &&
           auth.accessTypes.equals(accessTypes) &&
           auth.authorizedEntityType.equals(authorizedEntityType) &&
           auth.accessConditionsShapeURI.equals(accessConditionsShapeURI);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + resourceURI.hashCode();
    result = 31 * result + accessTypes.hashCode();
    result = 31 * result + authorizedEntityType.hashCode();
    result = 31 * result + accessConditionsShapeURI.hashCode();
    return result;
  }

  // Generate an RDF graph Model of this Authorization instance, using the org.eclipse.rdf4j.model.util.ModelBuilder 
  // and the corresponding ACL ontology vocabulary. Return a tuple of the authorization instance URI and the Model.
  public Map<IRI, Model> toModel() {
    ModelBuilder builder = new ModelBuilder();
    builder.setNamespace("acl", ACL.NS);
    builder.setNamespace("rdf", RDF.NAMESPACE);
    builder.setNamespace("cashmere", CASHMERE.CASHMERE_NS);

    ValueFactory rdfVals = SimpleValueFactory.getInstance();
    
    // create a blank node for the authorization instance
    if (authIRI == null) {
      authIRI = CONTEXT_AUTH_INSTANCE_NAMESPACE + "node-" + rdfVals.createBNode().getID();
    }
    IRI authInstance = rdfVals.createIRI(authIRI);

    // describe the authorization instance
    builder.add(authInstance, RDF.TYPE, CASHMERE.ContextBasedAuthorization);
    builder.add(authInstance, ACL.accessTo, rdfVals.createIRI(resourceURI));
    
    // add the access types
    if (accessTypes != null)
      for (AuthorizationAccessType accessType : accessTypes) {
        builder.add(authInstance, ACL.mode, rdfVals.createIRI(accessType.getUri()));
      }

    // set the property of the entity being authorized by looking up the property from the entity type
    builder.add(authInstance, rdfVals.createIRI(authorizedEntityType.getProperty()), rdfVals.createIRI(authorizedEntityURI));

    // set the property of the access conditions shape
    builder.add(authInstance, CASHMERE.hasAccessCondition, rdfVals.createIRI(accessConditionsShapeURI));

    // return a tuple of the authorization instance URI and the Model
    return new HashMap<IRI, Model>() {{
      put(authInstance, builder.build());
    }};
  }


  // Static method that parses a CASHMERE ontology based model of a shared context access authorizations 
  // and returns a list of SharedContextAccessAuthorization objects
  public static List<ContextBasedAuthorization> fromModel(Model contextAuthModel) {
    List<ContextBasedAuthorization> sharedCtxAuths = new ArrayList<>();
    
    // identify the subject of the authorization 
    Set<Resource> ctxAuthorizations = contextAuthModel.filter(null, RDF.TYPE, CASHMERE.ContextBasedAuthorization).subjects();
    
    // for each authorization, get the resource to which access is being given, the access type, and the ContextDomain Group URI  receiving access
    for (Resource sharedCtxAuth : ctxAuthorizations) {
      try {
        String accessedResourceUri = contextAuthModel.filter(sharedCtxAuth, ACL.accessTo, null).iterator().next().getObject().stringValue();
        List<AuthorizationAccessType> accessTypes = 
          contextAuthModel.filter(sharedCtxAuth, ACL.mode, null).objects()
            .stream().map(accessTypeIRI -> AuthorizationAccessType.fromUri(accessTypeIRI.stringValue()).get()).collect(Collectors.toList());
        
        
        Set<Value> accessConditionShapes = contextAuthModel.filter(sharedCtxAuth, CASHMERE.hasAccessCondition, null).objects();
        for (Value accessConditionShape : accessConditionShapes) {
          String accessConditionShapeURI = accessConditionShape.stringValue();
          
          // create the authorization object
          ContextBasedAuthorization ctxAccessAuth = new ContextBasedAuthorization(accessedResourceUri, accessTypes, 
              AuthorizedEntityType.AGENT, CASHMERE.accessRequester.stringValue(), accessConditionShapeURI);
          
          // add the authorization to the list
          sharedCtxAuths.add(ctxAccessAuth);
        }
      }
      catch (Exception e) {
        System.out.println("Error parsing shared context access authorization with URI " + sharedCtxAuth.stringValue() + 
            " in model \n" + contextAuthModel.toString());
        System.out.println("Reason: " + e.getMessage());
        System.out.println();
      }
    }

    return sharedCtxAuths;
  }
}
