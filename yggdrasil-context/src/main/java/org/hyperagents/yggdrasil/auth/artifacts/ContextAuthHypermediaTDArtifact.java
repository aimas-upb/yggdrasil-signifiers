package org.hyperagents.yggdrasil.auth.artifacts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.AuthorizedEntityType;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;
import org.hyperagents.yggdrasil.cartago.artifacts.HypermediaTDArtifact;

import cartago.CartagoException;


public abstract class ContextAuthHypermediaTDArtifact extends HypermediaTDArtifact {
    private static final String HASH_ARTIFACT = "#artifact";

    // The list of Authorization objects for this ContextAuthenticated Hypermedia Artifact
    protected List<ContextBasedAuthorization> authorizations = new ArrayList<>();

    protected abstract void registerSharedContextAutorizations();

    // method to get the list of authorizations
    public List<ContextBasedAuthorization> getAuthorizations() {
        return authorizations;
    }

    // method to register an authorization object
    public void registerAuthorization(ContextBasedAuthorization auth) {
        authorizations.add(auth);
    }

    // method to remove an authorization object
    public void removeAuthorization(ContextBasedAuthorization auth) {
        authorizations.remove(auth);
    }

    // method to remove all authorizations to a resource identified by its URI
    public void removeAuthorizations(String resourceUri) {
        authorizations.removeIf(auth -> auth.getResourceURI().equals(resourceUri));
    }

    // method to remove all agit uthorizations granted to an entity identified by its URI
    public void removeAuthorizationsToEntity(String requesterIdentifierURI) {
        authorizations.removeIf(auth -> auth.getAuthorizedEntityURI().equals(requesterIdentifierURI));
    }

    // method to register an authorization by its components
    public void registerAuthorization(String resourceName, String resourceUri,  
                                      AuthorizationAccessType accessType, AuthorizedEntityType requesterType, 
                                      String requesterName, String requesterIdentifierUri, String accessConditionsShapeUri) {
        ContextBasedAuthorization auth = new ContextBasedAuthorization(resourceName, resourceUri, Arrays.asList(accessType), 
              requesterName, requesterType, requesterIdentifierUri, accessConditionsShapeUri);
        authorizations.add(auth);
    }

    // Override the setupOperations method to add the authorizations.
    @Override
    public void setupOperations() throws CartagoException {
      super.setupOperations();
      
      // first we register the shared context authorizations
      registerSharedContextAutorizations();

      // then we use the authorizations to add the corresponding metadata to the artifact's description
      addAuthorizationMetadata();
    }

    private void addAuthorizationMetadata() {
      // We loop through the list of authorizations and add the corresponding triples to the metadata graph. 
      ModelBuilder authorisationsModel = new ModelBuilder();
      final var authThingUri = getArtifactUri() + HASH_ARTIFACT;
      
      // set the semantic type of the artifact to ContextAuthorizedResource
      authorisationsModel.add(authThingUri, RDF.TYPE, CASHMERE.ContextAuthorizedResource);

      for (ContextBasedAuthorization auth : getAuthorizations()) {
        Map<IRI, Model> authTriples = auth.toModel();
        // We use authTriples.keySet().iterator().next() as the IRI identifying the authorization specification, 
        // because we know that the authTriples map contains only one entry
        IRI authIRI = authTriples.keySet().iterator().next();
        if (auth.getAccessTypes().contains(AuthorizationAccessType.CONTROL)){
          // If the access type is control, we add the hasControlAuthorization property. 
          authorisationsModel.add(authThingUri, CASHMERE.hasControlAuthorization, authIRI);  
        }
        else {
          // Otherwise, we add the hasAccessAuthorization property
          authorisationsModel.add(authThingUri, CASHMERE.hasAccessAuthorization, authIRI);
        }
        
        // Add all contents of authModel to the authorisationsModel
        Model authModel = authTriples.get(authIRI);
        authModel.forEach(statement -> authorisationsModel.add(statement.getSubject(), statement.getPredicate(), statement.getObject()));
        
      }

      // add the authorizationModel as metadata
      addMetadata(authorisationsModel.build());
    }
}
