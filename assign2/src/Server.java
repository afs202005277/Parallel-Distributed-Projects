import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));

        Authentication auth = new Authentication("src/tokens.txt");
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        while (true) {
            SocketChannel socketChannel = serverSocketChannel.accept();
            System.out.println("Client connected!");

            // Create a new thread for the incoming connection
            executorService.submit(() -> {
                try {
                    while (true) {
                        ByteBuffer buffer = ByteBuffer.allocate(4096);
                        socketChannel.read(buffer);
                        buffer.flip();
                        String message = new String(buffer.array()).trim();
                        System.out.println("Message received: " + message);

                        // Check if the message is a login request
                        if (message.startsWith("login")) {
                            String[] parts = message.split(" ");
                            String username = parts[1];
                            String password = parts[2]; // to be done later

                            String token = auth.createToken(username);

                            buffer.clear();
                            buffer.put(token.getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                        } else if (message.startsWith("logout")) {
                            String[] parts = message.split(" ");
                            String token = parts[1];
                            auth.invalidateToken(token);
                            buffer.clear();
                            buffer.put("Success!".getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            socketChannel.close();
                            break;
                        } else {
                            socketChannel.write(buffer);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
