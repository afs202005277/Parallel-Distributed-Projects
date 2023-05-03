import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        AtomicBoolean play_again = new AtomicBoolean(false);
        String login_command = null;
        String tmp_login_command = null;
        do {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("localhost", 8080));
            socketChannel.configureBlocking(false);
            Scanner scanner = new Scanner(System.in);
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            String token = "";
            BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
            Thread input_thread = new Thread(() -> {
                while (true) {
                    String line = null;
                    try {
                        if (scanner.hasNextLine()) {
                            line = scanner.nextLine();
                            inputQueue.offer(line);
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException ie) {
                        if (line.equals("again")) {
                            play_again.set(true);
                        }
                        inputQueue.poll();
                        break;
                    }
                }
            });
            input_thread.start();
            boolean msgPrinted = false;
            do {
                buffer.clear();
                String message = "";
                if (!inputQueue.isEmpty()) {
                    message = inputQueue.poll();
                    msgPrinted = false;
                }
                if (play_again.get()) {
                    message = login_command;
                    play_again.set(false);
                }
                if (!msgPrinted) {
                    byte[] bytesToSend = message.getBytes();
                    buffer.put(bytesToSend);
                    buffer.flip();
                    socketChannel.write(buffer);
                    buffer.clear();
                }
                if (message.startsWith("login")) {
                    tmp_login_command = message;
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


                        System.out.println(tmp);
                    }
                }
            } while (true);
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
        } while (play_again.get());
    }

}
