package org.hyperagents.yggdrasil.context;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jena.graph.Graph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.streamreasoning.rsp4j.io.DataStreamImpl;


public class ContextStream extends DataStreamImpl<Graph> {
    
    private static final Logger LOGGER = LogManager.getLogger(ContextStream.class);

    private final String streamName;
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
        super(streamURI);
        this.streamName = extractStreamName(streamURI);
        this.ontologyURL = Optional.ofNullable(ontologyURL);

        if (contextAssertionTypes != null) {
            this.contextAssertionTypes.addAll(contextAssertionTypes);
        }
    }

    public URI getStreamURI() {
        return URI.create(stream_uri);
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
