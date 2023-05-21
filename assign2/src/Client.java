import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;


/**
 * The Client class represents a client that connects to a server and communicates with it using SocketChannel.
 * It allows the user to send messages to the server and receive responses.
 */
public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        final boolean[] play_again = {false};
        String login_command = null;
        String tmp_login_command = null;
        boolean showWelcomeMessage = true;
        do {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("localhost", 8080));
            socketChannel.configureBlocking(false);
            Scanner scanner = new Scanner(System.in);
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            String token = "";
            ConcurrentList<String> inputQueue = new ConcurrentList<>();
            Thread input_thread = new Thread(() -> {
                while (true) {
                    String line = null;
                    try {
                        if (scanner.hasNextLine()) {
                            line = scanner.nextLine();
                            inputQueue.add(line);
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException ie) {
                        if (line.equals("again")) {
                            play_again[0] = true;
                        }
                        if (!inputQueue.isEmpty())
                            inputQueue.remove(0);
                        break;
                    }
                }
            });
            input_thread.start();
            do {
                buffer.clear();
                String message = "";
                if (!inputQueue.isEmpty()) {
                    message = inputQueue.get(0);
                    inputQueue.remove(0);
                }
                if (play_again[0]) {
                    message = login_command;
                    play_again[0] = false;
                }
                assert message != null;
                buffer.put(message.getBytes());
                buffer.flip();
                socketChannel.write(buffer);
                buffer.clear();
                if (message.startsWith("login")) {
                    tmp_login_command = message;
                } else if (message.startsWith("register")) {
                    tmp_login_command = message.replace("register", "login");
                }
                if (socketChannel.isConnected()) {
                    buffer.put(0, new byte[buffer.limit()]);
                    socketChannel.read(buffer);
                    String tmp = new String(buffer.array()).trim();
                    if (!tmp.isBlank()) {
                        if (tmp.contains("DISCONNECT")) {
                            System.out.println(tmp.substring(0, tmp.indexOf("DISCONNECT")));
                            break;
                        }
                        if (tmp.contains("Welcome to our server")) {
                            if (showWelcomeMessage)
                                System.out.println(tmp);
                        } else {
                            System.out.println(tmp);
                        }
                        if (!tmp.startsWith("Error") && !tmp.startsWith("Usage")) {
                            if (tmp.startsWith("Login Token")) {
                                login_command = tmp_login_command;
                                token = tmp.substring(tmp.indexOf(": ") + 2, tmp.indexOf("\n"));
                                final String tok = token;
                                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                    try {
                                        if (socketChannel.isOpen()) {
                                            buffer.clear();
                                            buffer.put(new byte[4096]);
                                            buffer.put(0, ("logout " + tok).getBytes());
                                            buffer.flip();
                                            socketChannel.write(buffer);
                                            buffer.clear();
                                            socketChannel.read(buffer);
                                            socketChannel.close();
                                        }
                                    } catch (IOException e) {
                                        System.out.println("ERROR");
                                    }
                                }));
                            }
                        }
                    }
                }
            }
            while (true);
            buffer.clear();
            buffer.put(("logout " + token).getBytes());
            buffer.flip();
            socketChannel.write(buffer);
            buffer.clear();
            buffer.put(new byte[buffer.limit()]);
            socketChannel.configureBlocking(true);
            socketChannel.read(buffer);
            socketChannel.close();
            input_thread.interrupt();
            System.out.println("Write \"again\" to play again and anything else to disconnect: ");
            input_thread.join();
            showWelcomeMessage = false;
        } while (play_again[0]);
    }
}
