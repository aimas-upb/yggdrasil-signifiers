package org.hyperagents.yggdrasil.context;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jena.graph.Graph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.streamreasoning.rsp4j.api.stream.data.DataStream;
import org.streamreasoning.rsp4j.io.DataStreamImpl;


public class ContextStream {
    
    private static final Logger LOGGER = LogManager.getLogger(ContextStream.class);

    private final String streamName;
    private final String streamURI;

    private DataStream<Graph> dataStream;

    private final AtomicLong lastUpdateTimestamp = new AtomicLong(0);

    /**
     * The URI of the ontology that describes the contents of this stream.
     */
    private final Optional<String> ontologyURL;

    /**
     * The URIs of the ContextAssertions that are part of this stream.
     */
    private final List<String> contextAssertionTypes = new ArrayList<>();

    public ContextStream(String streamURI, String ontologyURL, List<String> contextAssertionTypes) {
        
        this.streamURI = streamURI;
        this.streamName = extractStreamName(streamURI);
        this.ontologyURL = Optional.ofNullable(ontologyURL);

        if (contextAssertionTypes != null) {
            this.contextAssertionTypes.addAll(contextAssertionTypes);
        }

        // initialize the data stream
        dataStream = new DataStreamImpl<>(streamURI);
    }

    /**
     * Returns the URI of the stream.
     * @return A string representing the URI of the stream.
     */
    public String getStreamURI() {
        return streamURI;
    }

    /**
     * Returns the local name of the stream, taken from the last path segment of the stream URI.
     * @return A string representing the local name of the stream.
     */
    public String getStreamName() {
        return streamName;
    }
    
    /**
     * Returns the URL to the ontology that describes the ContextAssertion and ContextEntity types in this stream.
     * @return The URL to the ontology that describes the ContextAssertion and ContextEntity types in this stream. May be empty if no ontology was provided.
     */
    public Optional<String> getOntologyURL() {
        return ontologyURL;
    }

    /**
     * Returns the types of ContextAssertions that are part of this stream.
     * @return The URIs of the ContextAssertions that are part of this stream.
     */
    public List<String> getContextAssertionTypes() {
        return contextAssertionTypes;
    }

    /**
     * Returns whether this stream contains only a single type of ContextAssertion.
     * @return True if the stream contains only a single type of ContextAssertion, false otherwise.
     */
    public boolean isSingleAssertionStream() {
        return contextAssertionTypes.size() == 1;
    }

    /**
     * Returns the timestamp of the last update to this stream in milliseconds.
     * @return The timestamp of the last update to this stream.
     */
    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp.get();
    }

    /**
     * Get the data stream to which this Context Stream writes its updates.
     * @return The DataStream to which this Context Stream writes its updates.
     */
    public DataStream<Graph> getDataStream() {
        return dataStream;
    }

    /**
     * Update the stream with a new graph and timestamp of the update.
     * @param graph The new RDF graph containing the updated data.
     * @param timestamp The timestamp of the update in milliseconds.
     */
    public void updateStream(Graph graph, long timestamp) {
        dataStream.put(graph, timestamp);
        lastUpdateTimestamp.set(timestamp);
    }

    /**
     * Set the data stream to which this ContextStream writes its updates. 
     * Such a request can come when registering the ContextStream with a CSPARQL engine, for example.
     * @param dataStream The DataStream to which this ContextStream writes its updates.
     */
    public void setWritableStream(DataStream<Graph> dataStream) {
        this.dataStream = dataStream;
    }

    /**
     * Extracts the stream name from the URI (last path segment).
     *
     * @param uri The full URI of the stream
     * @return The stream name (last path segment of the URI)
     */
    private String extractStreamName(String uri) {
        try {
            String path = URI.create(uri).getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                // If there's no path or it's just a root path, use the host as the name
                return URI.create(uri).getHost();
            }
            
            // Get the last segment of the path
            String[] segments = path.split("/");
            String lastSegment = segments[segments.length - 1];
            
            // If the last segment is empty (uri ends with /), use the previous segment
            if (lastSegment.isEmpty() && segments.length > 1) {
                lastSegment = segments[segments.length - 2];
            }
            
            return lastSegment.isEmpty() ? "unnamed" : lastSegment;
        } catch (Exception e) {
            // In case of any parsing issues, return a default name
            return "unnamed";
        }
    }

}
