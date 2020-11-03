import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReadMessageThread implements Runnable {

    /**
     * See {@link AcceptorThread#running} for deets
     */
    private AtomicBoolean running = new AtomicBoolean(true);

    /**
     * See {@link AcceptorThread#collectionOfSockets} for deets
     */
    private Collection<Socket> socketCollection;

    /**
     * This will contain all partially read messages. See the run() method for details on why we do this. This is
     * indexed by the hashCode() of the socket because this is a nice unique identifier for the sockets and a nice
     * way to keep track of everything
     */
    private Map<Integer, String> currentlyReadMessages = new ConcurrentHashMap<>();

    /**
     * This will be the character that messages will be split on, this can be anything you want.
     */
    private char delimiter;

    /**
     * This will be the listener that is called whenever we finally process a message
     */
    private OnMessageListener listener;

    public ReadMessageThread(Collection<Socket> socketCollection, char delimiter, OnMessageListener listener) {
        this.socketCollection = socketCollection;
        this.delimiter = delimiter;
        this.listener = listener;
    }

    @Override
    public void run() {
        while (running.get()) {
            Iterator<Socket> iterator = this.socketCollection.iterator();

            while (iterator.hasNext()) {
                // Everything in networking throws an exception, handle them properly instead
                try {
                    // Get the next available socket that we can use and fetch its input stream so we can check if
                    // we can read from it
                    Socket socket = iterator.next();
                    InputStream stream = socket.getInputStream();

                    // There's no point even trying to read from the socket if theres no data available
                    if (stream.available() > 0) {
                        // We're going to use this to store the data we've read during this run to keep track of
                        // everything
                        ByteArrayOutputStream temporaryBuffer = new ByteArrayOutputStream();

                        // While we still have data (available() > 0) and we haven't reached the end-of-connection
                        // style end of the stream (read != -1).
                        int read;
                        while (stream.available() > 0 && (read = stream.read()) != -1) {
                            if (read == this.delimiter) {
                                // This means we've reached the end of one message! So we want to combine this with
                                // whatever message we've got cached

                                // This is a bit of a hacky way to do things but its nice. Basically remove will return
                                // the associated value when its removed or null if it doesn't. So held is basically
                                // null if we didn't have part of a message or an actual string if we did so we make
                                // sure we have an empty string if we didn't have one and concatenate what we've just
                                // read to produce one nice long message
                                String held = this.currentlyReadMessages.remove(socket.hashCode());
                                if (held == null) held = "";

                                // This is everything we've read in *for this message*. But before we can do anything we
                                // need to do a bit of housekeeping
                                String finalMessage = held + temporaryBuffer.toString();

                                // Reset the temporary buffer, we've got one whole message so we don't need to keep the
                                // buffer around as it will be confusing so we just empty it so new bytes will just
                                // fill it up with the next message
                                temporaryBuffer.reset();

                                // Then we want to call the listener for this message to do something with it
                                this.listener.onMessage(socket, finalMessage);
                            } else {
                                // Otherwise if this wasn't the delimiter we just want to save it in the buffer until
                                // it is the delimiter or we exit out of this loop
                                temporaryBuffer.write(read);
                            }
                        }

                        // Now we're outside of this loop it means we have no more data to read. We want to check if we
                        // have any data in the buffer and if we do, we need to save that into our map of partially read
                        // messages so we keep that data for the next loop
                        if (temporaryBuffer.size() > 0) {
                            // We use the same hacky method as above to get the current value in the map if there is one
                            String held = this.currentlyReadMessages.remove(socket.hashCode());
                            if (held == null) held = "";

                            // And then save it back into the map for safe keeping
                            this.currentlyReadMessages.put(socket.hashCode(), held + temporaryBuffer.toString());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}