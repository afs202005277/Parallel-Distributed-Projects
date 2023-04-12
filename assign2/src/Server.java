import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));

        Authentication auth = new Authentication("src/tokens.txt");

        while (true) {
            SocketChannel socketChannel = serverSocketChannel.accept();
            System.out.println("Client connected!");
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
            } else {
                socketChannel.write(buffer);
            }

            socketChannel.close();
        }
    }
}
