import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        while (true) {
            SocketChannel socketChannel = serverSocketChannel.accept();
            System.out.println("Client connected!");
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            socketChannel.read(buffer);
            buffer.flip();
            String message = new String(buffer.array()).trim();
            System.out.println("Message received: " + message);
            socketChannel.write(buffer);
            socketChannel.close();
        }
    }
}