import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Client {
    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("192.168.56.1", 8080));
        String ipv4 = InetAddress.getLocalHost().getHostAddress();
        String message = ipv4;
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        socketChannel.write(buffer);
        buffer.clear();
        socketChannel.read(buffer);
        System.out.println(new String(buffer.array()).trim());
        socketChannel.close();
    }
}