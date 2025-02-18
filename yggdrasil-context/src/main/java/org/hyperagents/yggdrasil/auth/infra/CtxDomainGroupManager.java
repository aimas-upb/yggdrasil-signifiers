package org.hyperagents.yggdrasil.auth.infra;

import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

public class CtxDomainGroupManager {

  // The URI of this Context Domain
  protected String ctxDomainUri;

  // The Corese graph model that contains the Context Domain Group memberships of individual agents
  // The contents of the model are included in the /memberships graph, relative to the URI of this Context Domain
  protected SailRepository membershipsRepository;

  public CtxDomainGroupManager(String ctxDomainUri) {
    this.ctxDomainUri = ctxDomainUri;
    this.membershipsRepository = new SailRepository(new MemoryStore());
  }


  // create the static main for testing, where we launch a SPARQL query to an endpoint identified by an URL, making
  // an ASK query to check if an agent is a member of a group
  public static void main(String[] args) {
    String ctxDomainMembershipUri = "http://example.org/Lab308ContextDomainGroup/memberships";
    String sparqlEndpointURI = "http://localhost:8080/Lab308ContextDomainGroup/sparql";
    
    String askQuery = "ASK { GRAPH <" + ctxDomainMembershipUri + "> { <http://example.org/Lab308ContextDomainGroup> <http://www.w3.org/2006/vcard/ns#member> <http://example.org/profiles#alex> } }";
    String deleteQuery = "delete data { GRAPH <" + ctxDomainMembershipUri + "> { <http://example.org/Lab308ContextDomainGroup> <http://www.w3.org/2006/vcard/ns#member> <http://example.org/profiles#alex> } }";
    String updateQuery = "insert data { GRAPH <" + ctxDomainMembershipUri + "> { <http://example.org/Lab308ContextDomainGroup> <http://www.w3.org/2006/vcard/ns#member> <http://example.org/profiles#alex> } }";
    //String askQuery = "ASK {<http://example.org/Lab308ContextDomainGroup> <http://www.w3.org/2006/vcard/ns#member> <http://example.org/profiles#alex>}";
    
    // get a RDF4J Repository reference to the sparql endpoint
    Repository endpoint = new SPARQLRepository(sparqlEndpointURI);
    endpoint.init();
    
    try {
    	// get the initial result
    	RepositoryConnection conn = endpoint.getConnection();
    	boolean result = conn.prepareBooleanQuery(askQuery).evaluate();
    	System.out.println("The ASK query is initially: " + result);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    
    try {
    	// now remove the membership of alex and then query again
    	RepositoryConnection conn = endpoint.getConnection();
    	//Update update = conn.prepareUpdate(updateQuery);
    	TupleQuery delete = conn.prepareTupleQuery(deleteQuery);
    	TupleQueryResult delRes = delete.evaluate();
    	System.out.println("Executed delete: " + delRes.next());
    	
    	boolean result = conn.prepareBooleanQuery(askQuery).evaluate();
    	System.out.println("The ASK query after delete is: " + result);
    	
    	TupleQuery update = conn.prepareTupleQuery(updateQuery);
    	TupleQueryResult updateRes = update.evaluate();
    	System.out.println("Executed update: " + updateRes.next());
    	
    	result = conn.prepareBooleanQuery(askQuery).evaluate();
    	System.out.println("The ASK query after reinsertion is: " + result);
    }
    catch (Exception e) {
    	e.printStackTrace();
    }
        
    endpoint.shutDown();
  }
}
