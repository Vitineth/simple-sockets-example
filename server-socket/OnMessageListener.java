import java.net.Socket;

public interface OnMessageListener {

    void onMessage(Socket socket, String message);

}