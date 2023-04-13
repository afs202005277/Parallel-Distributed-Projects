import java.io.IOException;
import java.net.InetSocketAddress;
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

        Authentication auth = new Authentication("src/tokens.txt");
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
                    if (socketChannel.isOpen())
                        numRead = socketChannel.read(buffer);
                    if (numRead == -1) {
                        // Client disconnected
                        key.cancel();
                        socketChannel.close();
                        System.out.println("Client disconnected: " + socketChannel.getRemoteAddress());
                    } else {
                        buffer.flip();
                        String message = new String(buffer.array(), 0, buffer.limit()).trim();
                        buffer.clear();
                        System.out.println("Message received from " + socketChannel.getRemoteAddress() + ": " + message);

                        // Check if the message is a login request
                        if (message.startsWith("login")) {
                            String[] parts = message.split(" ");
                            String username = parts[1];
                            String password = parts[2]; // to be done later

                            String token = auth.createToken(username);
                            clientTokens.put(socketChannel, token);

                            buffer.put(token.getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();

                        } else if (message.startsWith("logout")) {
                            String[] parts = message.split(" ");
                            String token = parts[1];
                            auth.invalidateToken(token);
                            clientTokens.remove(socketChannel);
                            buffer.put(new byte[BUFFER_SIZE]);
                            buffer.put(0, "Success!".getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();
                            key.cancel();
                            System.out.println("Client disconnected: " + socketChannel.getRemoteAddress());
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
