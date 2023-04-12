import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost", 8080));
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter a message to send: ");
        String message = scanner.nextLine();
        byte[] bytesToSend = message.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        buffer.put(bytesToSend);
        buffer.flip();
        socketChannel.write(buffer);
        buffer.clear();
        socketChannel.read(buffer);
        System.out.println("Message received: " + new String(buffer.array()).trim());
        socketChannel.close();
    }
}