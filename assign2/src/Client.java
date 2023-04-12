import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost", 8080));
        Scanner scanner = new Scanner(System.in);
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        String token = "";
        do{
            buffer.clear();
            System.out.print("Enter a message to send: ");
            String message = scanner.nextLine();
            if (Objects.equals(message, "")){
                System.out.println("empty");
                break;
            }
            byte[] bytesToSend = message.getBytes();
            buffer.put(bytesToSend);
            buffer.flip();
            socketChannel.write(buffer);
            buffer.clear();
            socketChannel.read(buffer);
            if (message.startsWith("login")){
                token = new String(buffer.array()).trim();
                System.out.println(token);
            }
            System.out.println("Message received: " + new String(buffer.array()).trim());
        } while (true);
        buffer.clear();
        buffer.put(("logout " + token).getBytes());
        buffer.flip();
        socketChannel.write(buffer);
        buffer.clear();
        socketChannel.read(buffer);
        System.out.println("Message received: " + new String(buffer.array()).trim());
        socketChannel.close();
    }
}