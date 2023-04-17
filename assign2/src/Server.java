import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Server {
    private static final int BUFFER_SIZE = 4096;

    private static void sendMessageToPlayers(ByteBuffer buffer, List<SocketChannel> clients, String message) throws IOException {
        for (SocketChannel client : clients) {
            buffer.put(new byte[BUFFER_SIZE]);
            buffer.put(0, message.getBytes());
            buffer.flip();
            client.write(buffer);
            buffer.clear();
        }
    }
    
    private static void updateQueue(ByteBuffer buffer, HashMap<SocketChannel, Integer> clients) throws IOException {
        int i = 1;
        for (SocketChannel client : clients.keySet()) {
            buffer.put(new byte[BUFFER_SIZE]);
            String m = "Position in Queue: " + i;
            clients.replace(client, i);
            buffer.put(0, m.getBytes());
            buffer.flip();
            client.write(buffer);
            buffer.clear();
            i++;
        }
    }

    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        Authentication auth = new Authentication("src/tokens.txt", "src/users.txt");
        Map<SocketChannel, String> clientTokens = new HashMap<>();

        final int PLAYERS = 2;
        int currentPlayers = 0;
        List<SocketChannel> playing = new ArrayList<>();
        HashMap<SocketChannel, Integer> inQueue = new HashMap<>();
        List<String> leftInGame = new ArrayList<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                auth.clearTokens();
            } catch (IOException e) {
                System.out.println("ERROR");
            }
        }));

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
                    boolean disconected = false;
                    if (socketChannel.isOpen()) {
                        try {
                            numRead = socketChannel.read(buffer);
                        } catch (SocketException socketException) {
                            disconected = true;
                        }
                    }

                    if (numRead == -1 || disconected) {
                        auth.invalidateToken(clientTokens.get(socketChannel));
                        System.out.println("Client disconnected: " + socketChannel.getRemoteAddress());
                        key.cancel();
                        socketChannel.close();
                    } else {
                        buffer.flip();
                        String message = new String(buffer.array(), 0, buffer.limit()).trim();
                        buffer.clear();
                        System.out.println("Message received from " + socketChannel.getRemoteAddress() + ": " + message);

                        if (message.startsWith("register")) {
                            String[] parts = message.split(" ");
                            String res;
                            if (parts.length != 3)
                                res = "Usage: register <username> <password>";
                            else {
                                String username = parts[1];
                                String password = parts[2]; // to be done later
                                String tok = auth.registerUser(username, password);
                                if (tok.contains("Error"))
                                    res = tok;
                                else {
                                    res = "Login Token: " + tok + "\nWelcome " + username + "!\n";
                                    if (currentPlayers < PLAYERS - 1) {
                                        currentPlayers++;
                                        String m = "Waiting for players [" + currentPlayers + " / " + PLAYERS + "]";
                                        res += m;
                                        sendMessageToPlayers(buffer, playing, m);
                                        playing.add(socketChannel);
                                    } else if (currentPlayers < PLAYERS) {
                                        currentPlayers++;
                                        res += "Game Starting!";
                                        sendMessageToPlayers(buffer, playing, "Game Starting!");
                                        playing.add(socketChannel);
                                    } else {
                                        inQueue.put(socketChannel, inQueue.size() + 1);
                                        res += "You are in the Queue!\n Position in Queue: " + inQueue.get(socketChannel);
                                    }
                                }
                                if (!res.contains("Error:"))
                                    clientTokens.put(socketChannel, tok);
                            }
                            buffer.put(res.getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();
                        } else if (message.startsWith("login")) {
                            String res;
                            String[] parts = message.split(" ");
                            if (parts.length != 3){
                                res = "Usage: login <username> <password>";
                            } else {
                                String username = parts[1];
                                String password = parts[2];
                                if (!auth.isLoggedIn(username)) {
                                    String tok = auth.login(username, password);
                                    if (tok.contains("Error"))
                                        res = tok;
                                    else {
                                        res = "Login Token: " + tok + "\nWelcome " + username + "!\n";
                                        if (leftInGame.contains(username)) {
                                            sendMessageToPlayers(buffer, playing, username + " has reconnected!");
                                            res += username + " has reconnected!";
                                            playing.add(socketChannel);
                                        }
                                        else {
                                            if (currentPlayers < PLAYERS - 1) {
                                                currentPlayers++;
                                                String m = "Waiting for players [" + currentPlayers + " / " + PLAYERS + "]";
                                                res += m;
                                                sendMessageToPlayers(buffer, playing, m);
                                                playing.add(socketChannel);
                                            } else if (currentPlayers < PLAYERS) {
                                                currentPlayers++;
                                                res += "Game Starting!";
                                                sendMessageToPlayers(buffer, playing, "Game Starting!");
                                                playing.add(socketChannel);
                                            } else {
                                                inQueue.put(socketChannel, inQueue.size() + 1);
                                                res += "You are in the Queue!\nPosition in Queue: " + inQueue.get(socketChannel);
                                            }
                                        }

                                    }
                                    if (!res.contains("Error:"))
                                        clientTokens.put(socketChannel, tok);
                                } else {
                                    res = "Error: You are already logged in!";
                                }
                            }
                            buffer.put(new byte[BUFFER_SIZE]);
                            buffer.put(0, res.getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();

                        } else if (message.startsWith("logout")) {
                            String answer;
                            String[] parts = message.split(" ");
                            boolean canceled = false;
                            if (parts.length != 2){
                                answer = "Usage: logout <token>";
                            } else{
                                String token = parts[1];
                                if (clientTokens.containsValue(token)) {
                                    answer = "Success!";
                                    if (currentPlayers < PLAYERS) {
                                        currentPlayers--;
                                        playing.remove(socketChannel);
                                        String m = "Waiting for players [" + currentPlayers + " / " + PLAYERS + "]";
                                        sendMessageToPlayers(buffer, playing, m);
                                    } else {
                                       if (playing.contains(socketChannel)) {
                                           String username = auth.getUserName(token);
                                           leftInGame.add(username);
                                           playing.remove(socketChannel);
                                           sendMessageToPlayers(buffer, playing, username + " has disconected!");
                                       }
                                    }
                                    if (inQueue.keySet().contains(socketChannel)) {
                                        inQueue.remove(socketChannel);
                                        updateQueue(buffer, inQueue);
                                    }
                                        
                                    auth.invalidateToken(token);
                                    clientTokens.remove(socketChannel);
                                    key.cancel();
                                    canceled = true;
                                    System.out.println("Client disconnected: " + socketChannel.getRemoteAddress());
                                } else {
                                    answer = "Invalid token!";
                                }
                            }
                            buffer.put(new byte[BUFFER_SIZE]);
                            buffer.put(0, answer.getBytes());
                            buffer.flip();
                            socketChannel.write(buffer);
                            buffer.clear();
                            if (canceled)
                                socketChannel.close();

                        } else {
                            // Echo back the received message
                            buffer.put(new byte[BUFFER_SIZE]);
                            buffer.put(0, (message).getBytes());
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
