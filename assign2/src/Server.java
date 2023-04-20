import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements GameCallback{
    public static final int BUFFER_SIZE = 4096;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final ExecutorService threadPool;
    private final ArrayList<GameRunner> games;
    private final int playersPerGame;
    private final Authentication auth;
    // channel -> tokens
    private final HashMap<SocketChannel, String> clientTokens;

    // channel -> index of the game where player is playing
    private final HashMap<SocketChannel, Integer> playing;

    // channel -> index of the game where player is waiting
    private final HashMap<SocketChannel, Integer> inQueue;

    // username -> index of the game where player was playing but crashed
    private final HashMap<String, Integer> leftInGame;
    
    private int currentPlayers;

    public Server(int maxGames) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);

        threadPool = Executors.newFixedThreadPool(maxGames);
        games = new ArrayList<>();
        for (int i = 0; i < maxGames; i++) {
            games.add(new GameRunner(new Game(), this, i));
        }

        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        auth = new Authentication("src/tokens.txt", "src/users.txt");
        clientTokens = new HashMap<>();

        playersPerGame = Game.getNumPlayers();
        currentPlayers = 0;
        playing = new HashMap<>();
        inQueue = new HashMap<>();
        leftInGame = new HashMap<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                auth.clearTokens();
            } catch (IOException e) {
                System.out.println("ERROR");
            }
        }));
    }
    
    public void runServer() throws IOException {
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
                        Integer nextReady = getNextReady(games);
                        buffer.flip();
                        String message = new String(buffer.array(), 0, buffer.limit()).trim();
                        buffer.clear();
                        System.out.println("Message received from " + socketChannel.getRemoteAddress() + ": " + message);

                        if (playing.get(socketChannel) != null){

                            int index = playing.get(socketChannel);
                            games.get(index).sendMessage(auth.getTokens().get(clientTokens.get(socketChannel)), message);
                        } else if (message.startsWith("register")) {
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
                                    if (currentPlayers < playersPerGame - 1) {
                                        currentPlayers++;
                                        String m = "Waiting for players [" + currentPlayers + " / " + playersPerGame + "]";
                                        res += m;
                                        sendMessageToPlayers(playing, m, nextReady);
                                        playing.put(socketChannel, nextReady);
                                    } else if (currentPlayers < playersPerGame) {
                                        currentPlayers++;
                                        res += "Game Starting!";
                                        sendMessageToPlayers(playing, "Game Starting!", nextReady);
                                        playing.put(socketChannel, nextReady);
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
                            if (parts.length != 3) {
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
                                        if (leftInGame.containsKey(username)) {
                                            sendMessageToPlayers(playing, username + " has reconnected!", leftInGame.get(username));
                                            res += username + " has reconnected!";
                                            playing.put(socketChannel, leftInGame.get(username));
                                        } else {
                                            if (currentPlayers < playersPerGame - 1) {
                                                currentPlayers++;
                                                String m = "Waiting for players [" + currentPlayers + " / " + playersPerGame + "]";
                                                res += m;
                                                sendMessageToPlayers(playing, m, nextReady);
                                                playing.put(socketChannel, nextReady);
                                            } else if (currentPlayers < playersPerGame) {
                                                currentPlayers++;
                                                res += "Game Starting!";
                                                playing.put(socketChannel, nextReady);
                                                List<SocketChannel> sockets = sendMessageToPlayers(playing, "Game Starting!", nextReady);
                                                List<String> usernames = new ArrayList<>();
                                                for (SocketChannel socket: sockets){
                                                    usernames.add(auth.getTokens().get(clientTokens.get(socket)));
                                                }
                                                games.get(nextReady).povoate_users(usernames);
                                                threadPool.submit(games.get(nextReady));
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
                            if (parts.length != 2) {
                                answer = "Usage: logout <token>";
                            } else {
                                String token = parts[1];
                                if (clientTokens.containsValue(token)) {
                                    answer = "Success!";
                                    if (currentPlayers < playersPerGame) {
                                        currentPlayers--;
                                        playing.remove(socketChannel);
                                        String m = "Waiting for players [" + currentPlayers + " / " + playersPerGame + "]";
                                        sendMessageToPlayers(playing, m, nextReady);
                                    } else {
                                        if (playing.containsKey(socketChannel)) {
                                            String username = auth.getUserName(token);
                                            int idx = playing.get(socketChannel);
                                            leftInGame.put(username, idx);
                                            playing.remove(socketChannel);
                                            sendMessageToPlayers(playing, username + " has disconected!", idx);
                                        }
                                    }
                                    if (inQueue.containsKey(socketChannel)) {
                                        inQueue.remove(socketChannel);
                                        updateQueue(inQueue);
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

    public static void main(String[] args) throws IOException {
        Server server = new Server(10);
        server.runServer();
    }

    private static List<SocketChannel> sendMessageToPlayers(Map<SocketChannel, Integer> clients, String message, Integer index) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        List<SocketChannel> sockets = new ArrayList<>();
        for (SocketChannel client : clients.keySet()) {
            if (clients.get(client).equals(index)) {
                buffer.put(new byte[BUFFER_SIZE]);
                buffer.put(0, message.getBytes());
                buffer.flip();
                client.write(buffer);
                buffer.clear();
                sockets.add(client);
            }
        }
        return sockets;
    }

    private static void updateQueue(Map<SocketChannel, Integer> clients) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
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

    private static int getNextReady(List<GameRunner> games){
        for (int i=0;i<games.size();i++){
            if (!games.get(i).isAlive())
                return i;
        }
        return -1;
    }

    public static Map<String, SocketChannel> getUsernamesToSocketChannelsForGame(Map<SocketChannel, String> clientTokens, Map<SocketChannel, Integer> playing, Map<String, String> tokensToUsername, int gameIndex) {

        Map<String, SocketChannel> usernamesToSocketChannels = new HashMap<>();

        for (Map.Entry<SocketChannel, Integer> entry : playing.entrySet()) {
            SocketChannel socketChannel = entry.getKey();
            int index = entry.getValue();
            String token = clientTokens.get(socketChannel);

            if (index == gameIndex) {
                String username = tokensToUsername.get(token);
                usernamesToSocketChannels.put(username, socketChannel);
            }
        }

        return usernamesToSocketChannels;
    }

    private static void sendGameMessages(HashMap<String, SocketChannel> receivers, List<String> usernames, List<String> messages) throws IOException {
        // hashmap: username -> socketchannel
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        for (int i = 0; i<usernames.size();i++){
            SocketChannel socketChannel = receivers.get(usernames.get(i));
            byteBuffer.put(messages.get(i).getBytes());
            byteBuffer.flip();
            socketChannel.write(byteBuffer);
        }
    }

    @Override
    public void onUpdate(Game game, int index) {
        ArrayList<String> answers = game.getMessageForServer();
        ArrayList<String> usernames = game.getUsernameFromMessageForServer();
        HashMap<String, SocketChannel> usernameToSocket = (HashMap<String, SocketChannel>) getUsernamesToSocketChannelsForGame(clientTokens, playing, auth.getTokens(), index);
        try {
            sendGameMessages(usernameToSocket, usernames, answers);
            System.out.println("Game " + index + "finished!");
        } catch (IOException e) {
            System.out.println("Unable to send game messages!");
        }
    }
}
