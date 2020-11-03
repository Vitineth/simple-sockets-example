import java.net.ServerSocket;
import java.net.ServerSocket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.IOException;
import java.net.Socket;
import java.util.Collection;

public class EchoServer{

    /**
     * This example uses three threads:
     *   * main
     *   * AcceptorThread
     *   * ReadMessageThread
     *      
     * And they all share the list of sockets.
     */

    public static void main(String[] args) throws IOException {
        // This is the actual server bit so create our server socket
        ServerSocket socket = new ServerSocket(48321);

        // Create a concurrent store for our sockets
        Collection<Socket> sockets = new CopyOnWriteArrayList<>();

        // And define a listener, in this case we're just going to echo things back and forth with the client. We use
        // lambdas for this to make things shorter.
        OnMessageListener listener = (socket1, message) -> {
            // Write it back with what they said and a new line to make things print nicely
            try {
                socket1.getOutputStream().write(("You said: " + message.trim() + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        // And then we want to create our two runnables
        AcceptorThread acceptorThread = new AcceptorThread(sockets, socket);
        ReadMessageThread readMessageThread = new ReadMessageThread(sockets, '\n', listener);

        // And finally start them
        new Thread(acceptorThread).start();
        new Thread(readMessageThread).start();
    }
}
