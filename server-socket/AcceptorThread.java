import java.util.Collection;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AcceptorThread implements Runnable {

    /**
        * This is basically just a boolean but with a little bit of magic behind it. If two threads try and edit a
        * normal boolean value at one time, the update can occasionally get lost. An atomic boolean basically makes
        * sure that it always has an up to date value and nothing gets lost.
        * <p>
        * If you want to know more they use something called Compare-and-Swap behind the scenes and that contains lots
        * of detail about how software works to avoid threading issues but thats well outside the scope
        */
    private AtomicBoolean running = new AtomicBoolean(true);

    /**
        * This will contain all the sockets that we've accepted. This object will be shared between the two threads
        * so this should always be a concurrent type. I've just used the collection interface here because we don't
        * actually care what type of collection it is, we just want to know that we can add things to it (and that
        * it should have concurrent support but we can't guarantee that easily from this class)
        */
    private Collection<Socket> collectionOfSockets;

    /**
        * This is just the server socket from the other thread. We need a copy of this so we can accept new client!
        */
    private ServerSocket socket;

    /**
        * We take in the reference for where we should add sockets and the server socket we should use to read from
        */
    public AcceptorThread(Collection<Socket> collectionOfSockets, ServerSocket socket) {
        this.collectionOfSockets = collectionOfSockets;
        this.socket = socket;
    }

    /**
        * To stop the thread we need to set the value on the atomic boolean. Then this should take at most 10 seconds
        * to stop the thread when this is called because thats as long as we said .accept() could block for
        */
    public void stop() {
        this.running.set(false);
    }

    @Override
    public void run() {
        // This is a little bit of protection and it sets how long calling .accept() can block for before it throws
        // an exception. This is useful if we want to be able to gracefully stop this thread. Its not required but
        // it can be nice. Because if we wanted to stop the server and we didn't have this, this thread would
        // refuse to stop until it accepted a client and could get to the top of the while loop again. This forces
        // it to do that every 10 seconds.
        // As with everything networked, this can throw an exception so handle that
        try {
            this.socket.setSoTimeout(10000);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        // .get() just returns us the actual boolean value for this variable and its guaranteed to not do any weird
        // stuff if another thread is trying to set this right now
        while (this.running.get()) {
            try {

                // Accept a new client, wait for it to read and then add it to our collection of sockets and then
                // well go to the top of the while loop and do this all again.
                Socket newClient = this.socket.accept();
                this.collectionOfSockets.add(newClient);

            } catch (IOException e) {
                // Check if this is because we timed out, if so its not actually a problem so just ignore it
                if (e instanceof SocketTimeoutException) continue;


                // This could be a whole host of problems so figure out what you need to deal with here
                e.printStackTrace();
            }
        }
    }

}