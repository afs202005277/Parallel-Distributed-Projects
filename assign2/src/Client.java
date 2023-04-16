import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws IOException {

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost", 8080));
        Scanner scanner = new Scanner(System.in);
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        String token = "";
        boolean canWrite = true;
        do {
            buffer.clear();
            String message = "";
            if (canWrite) {
                System.out.print("Enter a message to send: ");
                message = scanner.nextLine();
                if (Objects.equals(message, "")) {
                    break;
                }
                byte[] bytesToSend = message.getBytes();
                buffer.put(bytesToSend);
                buffer.flip();
                socketChannel.write(buffer);
                buffer.clear();
            }
            socketChannel.read(buffer);
            String tmp = new String(buffer.array()).trim();
            if (canWrite) {
                if (message.startsWith("login") || message.startsWith("register")) {
                    if (!tmp.startsWith("Error") && !tmp.startsWith("Usage")) {
                        token = tmp.substring(tmp.indexOf(": ") + 2, tmp.indexOf("\n"));
                        final String tok = token;
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                try {
                                    buffer.clear();
                                    buffer.put(new byte[4096]);
                                    buffer.put(0, ("logout " + tok).getBytes());
                                    buffer.flip();
                                    socketChannel.write(buffer);
                                    buffer.clear();
                                    socketChannel.read(buffer);
                                    socketChannel.close();
                                } catch (IOException e) {
                                    System.out.println("ERROR");
                                }
                        }));
                        canWrite = false;
                    }
                }
            }
            else {
                if (tmp.contains("Game Starting!"))
                    canWrite = false;
            }

            System.out.println(tmp);
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
