import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Server {
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        Authentication auth = new Authentication("src/tokens.txt", "src/users.txt");
        Map<SocketChannel, String> clientTokens = new HashMap<>();

        while (true) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    // New client connection
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    System.out.println("Client connected: " + socketChannel.getRemoteAddress());
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);

                } else if (key.isReadable()) {
                    // Data received from a client
                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                    int numRead = -1;
                    boolean disconected = false;
                    if (socketChannel.isOpen()) {
                        try {
                            numRead = socketChannel.read(buffer);
                        } catch (SocketException socketException) {
                            disconected = true;
                        }
                    }

                    if (numRead == -1 || disconected) {
                        auth.invalidateToken(clientTokens.get(socketChannel));
                        System.out.println("Client disconnected: " + socketChannel.getRemoteAddress());
                        key.cancel();
                        socketChannel.close();
                    } else {
                        buffer.flip();
                        String message = new String(buffer.array(), 0, buffer.limit()).trim();
                        buffer.clear();
                        System.out.println("Message received from " + socketChannel.getRemoteAddress() + ": " + message);

                        if (message.startsWith("register")) {
                            String[] parts = message.split(" ");
                            String res;
                            if (parts.length != 3)
                                res = "Usage: register <username> <password>";
                            else {
                                String username = parts[1];
                                String password = parts[2]; // to be done later
                                res = auth.registerUser(username, password);
                                if (!res.contains("Error:"))
                                    clientTokens.put(socketChannel, res);
                            }
                            buffer.put(res.getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();
                        } else if (message.startsWith("login")) {
                            String res;
                            String[] parts = message.split(" ");
                            if (parts.length != 3){
                                res = "Usage: login <username> <password>";
                            } else{
                                String username = parts[1];
                                String password = parts[2];
                                if (!auth.isLoggedIn(username)) {
                                    res = auth.login(username, password);
                                    if (!res.contains("Error:"))
                                        clientTokens.put(socketChannel, res);
                                } else {
                                    res = "Error: You are already logged in!";
                                }
                            }
                            buffer.put(res.getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();

                        } else if (message.startsWith("logout")) {
                            String answer;
                            String[] parts = message.split(" ");
                            boolean canceled = false;
                            if (parts.length != 2){
                                answer = "Usage: logout <token>";
                            } else{
                                String token = parts[1];
                                if (clientTokens.containsValue(token)) {
                                    auth.invalidateToken(token);
                                    clientTokens.remove(socketChannel);
                                    answer = "Success!";
                                    key.cancel();
                                    canceled = true;
                                    System.out.println("Client disconnected: " + socketChannel.getRemoteAddress());
                                } else {
                                    answer = "Invalid token!";
                                }
                            }
                            buffer.put(new byte[BUFFER_SIZE]);
                            buffer.put(0, answer.getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();
                            if (canceled)
                                socketChannel.close();

                        } else {
                            // Echo back the received message
                            buffer.put((message).getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();
                        }
                    }
                }
            }
        }
    }
}
