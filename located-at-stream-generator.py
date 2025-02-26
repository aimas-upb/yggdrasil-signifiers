import rdflib
import uuid
import time
import json
import requests
from datetime import datetime
import logging

# Set up logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class RDFGraphGenerator:
    def __init__(self, config_file):
        """Initialize the RDF graph generator with a configuration file."""
        self.config = self.load_config(config_file)
        self.prefixes = {
            'ex': 'http://example.org/',
            'consert': 'http://pervasive.semanticweb.org/ont/2017/07/consert/core#',
            'ann': 'http://pervasive.semanticweb.org/ont/2017/07/consert/annotation#'
        }
        
    def load_config(self, config_file):
        """Load configuration from a JSON file."""
        try:
            with open(config_file, 'r') as file:
                return json.load(file)
        except Exception as e:
            logger.error(f"Failed to load configuration: {e}")
            raise
    
    def create_rdf_graph(self):
        """Create an RDF graph with the specified content."""
        g = rdflib.Graph()
        
        # Add prefixes
        for prefix, uri in self.prefixes.items():
            g.bind(prefix, uri)
        
        # Generate UUIDs for assertion and annotation
        assertion_id = f"urn:uuid:{uuid.uuid4()}"
        annotation_id = f"urn:uuid:{uuid.uuid4()}"
        
        # Get current time in milliseconds
        current_time_ms = int(datetime.now().timestamp() * 1000)
        
        # Create the assertion
        assertion_type = rdflib.URIRef(self.config["assertionType"])
        assertion_subject = rdflib.URIRef(self.config["assertionSubject"])
        assertion_object = rdflib.URIRef(self.config["assertionObject"])
        
        # Add triples for the assertion
        g.add((rdflib.URIRef(assertion_id), rdflib.RDF.type, assertion_type))
        g.add((rdflib.URIRef(assertion_id), rdflib.RDF.type, rdflib.URIRef(self.prefixes['consert'] + 'BinaryContextAssertion')))
        g.add((rdflib.URIRef(assertion_id), rdflib.URIRef(self.prefixes['consert'] + 'assertionSubject'), assertion_subject))
        g.add((rdflib.URIRef(assertion_id), rdflib.URIRef(self.prefixes['consert'] + 'assertionObject'), assertion_object))
        g.add((rdflib.URIRef(assertion_id), rdflib.URIRef(self.prefixes['ann'] + 'hasAnnotation'), rdflib.URIRef(annotation_id)))
        
        # Add triples for the annotation
        g.add((rdflib.URIRef(annotation_id), rdflib.RDF.type, rdflib.URIRef(self.prefixes['ann'] + 'NumericTimestampAnnotation')))
        g.add((rdflib.URIRef(annotation_id), rdflib.URIRef(self.prefixes['ann'] + 'hasValue'), rdflib.Literal(current_time_ms)))
        
        return g, current_time_ms
    
    def send_update(self, graph, timestamp=None):
        """Send the serialized graph as a POST request."""
        try:
            # Serialize the graph to Turtle format
            turtle_serialized = graph.serialize(format='turtle')
            
            # Prepare the WebSub payload
            payload = {
                'hub.mode': 'update_stream',
                'hub.url': self.config['streamURI'],
                'hub.payload': {
                    "graph_serialized": turtle_serialized,
                    "timestamp_ms": timestamp if timestamp is not None else int(datetime.now().timestamp() * 1000)
                }
            }
            
            # Send POST request
            response = requests.post(
                self.config['hubURI'],
                data=payload
            )
            
            # Log the result
            if response.status_code == 200 or response.status_code == 204:
                logger.info(f"Update sent successfully. Status code: {response.status_code}")
            else:
                logger.error(f"Failed to send update. Status code: {response.status_code}, Response: {response.text}")
                
        except Exception as e:
            logger.error(f"Error sending update: {e}")
    
    def run(self):
        """Run the generator continuously."""
        generate_every = self.config.get('generateEvery', 60)  # Default to 60 seconds if not specified
        
        logger.info(f"Starting RDF graph generator. Generating every {generate_every} seconds.")
        logger.info(f"Hub URI: {self.config['hubURI']}")
        logger.info(f"Stream URI: {self.config['streamURI']}")
        
        try:
            while True:
                # Create and send the graph
                graph, ts = self.create_rdf_graph()
                self.send_update(graph, timestamp=ts)
                
                # Wait for the specified interval
                time.sleep(generate_every)
        except KeyboardInterrupt:
            logger.info("Generator stopped by user.")
        except Exception as e:
            logger.error(f"Unexpected error: {e}")
            raise

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description='Generate RDF graphs and send them as WebSub updates.')
    parser.add_argument('config', help='Path to the configuration JSON file')
    args = parser.parse_args()
    
    generator = RDFGraphGenerator(args.config)
    generator.run()
