package org.hyperagents.yggdrasil.context;

import org.apache.jena.graph.Graph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.streamreasoning.rsp4j.api.stream.data.DataStream;
import org.streamreasoning.rsp4j.io.DataStreamImpl;

public abstract class LocalContextStream extends DataStreamImpl<Graph> implements Runnable {
  
  /*
   * The name of the stream.
   */
  protected String streamName;

  /*
   * The URI of the stream.
   */ 
  protected String streamURI;

  /*
   * The interval in seconds at which the stream generates new context data.
   */
  protected int generateEveryMillis;

  /*
   * The stream that is writable.
   */
  protected DataStream<Graph> stream;

  /** 
   * Running status of the stream.
   */
  protected boolean running = false;

  /**
   * The internal, last timestamp of the stream.
   */
  protected long lastTimestamp = 0;

  /**
   * The thread that runs the stream.
   */
  protected Thread streamThread;

  private static final Logger LOGGER = LogManager.getLogger(LocalContextStream.class);

  /**
   * Constructor for the LocalContextStream class.
   * @param streamName: the name of the stream
   * @param streamURI: the URI of the stream
   * @param generateEvery: the interval in seconds at which the stream generates new context data
   */
  protected LocalContextStream(String streamName, String streamURI, int generateEvery) {
    super(streamURI);

    this.streamName = streamName;
    this.streamURI = streamURI;
    this.generateEveryMillis = generateEvery * 1000;
  }

  /**
   * Gets the name of the stream.
   * @return the name of the stream
   */
  public String getStreamName() {
    return streamName;
  }

  /**
   * Gets the URI of the stream.
   * @return the URI of the stream
   */
  public String getStreamURI() {
    return streamURI;
  }

  

  /**
   * Sets the writable stream.
   * @param stream: the stream to set
   */
  public void setWritableStream(DataStream<Graph> stream) {
    this.stream = stream;
  }

  /**
   * Starts the stream.
   */
  public void start() {
    if (!running) {
      running = true;
      streamThread = new Thread(this);
      streamThread.start();
    }
  }

  /**
   * Stops the stream.
   */
  public void stop() {
    running = false;
    // join the thread
    try {
      streamThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Generic method to obtain an item to be inserted in the stream.
   * This method is called by the run method to generate new context data.
   * 
   * @param generationTimestamp: the timestamp at which the data is generated
   * @return the item to be inserted in the stream
   */
  protected abstract Graph generateData(long generationTimestamp);


  /**
   * The run method of the LocalContextStream class.
   * This method generates new context data at the specified interval and inserts it into the stream.
   */
  @Override
  public void run() {
    while (running) {
      Graph data = generateData(lastTimestamp);
      // Log the entry
      
      // LOGGER.info("Generated new context data for stream " + streamName + " at timestamp " + System.currentTimeMillis() 
      //         + ", internal ts: " + lastTimestamp + ": " + data);

      stream.put(data, lastTimestamp);
      try {
        // Sleep for the specified interval
        Thread.sleep(generateEveryMillis);
      } catch (InterruptedException e) {
        LOGGER.error("Error while sleeping the thread: " + e.getMessage());
      }

      // Update the last timestamp
      lastTimestamp += generateEveryMillis;
    }
  }
}
