import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Server implements GameCallback {
    public static final int BUFFER_SIZE = 4096;
    private final static int RELAX_MMR = 50;
    private final static int RELAX_AFTER_TIME = 10000; // time before relaxing the queue in ms
    public final static String welcomeMessage = "Welcome to our server!\nPlease login or register a new account.\nIf you need any help, you can just send the \"help\" message.";
    private final static CharsetEncoder encoder = (StandardCharsets.UTF_8).newEncoder();
    private static ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final ExecutorService threadPool;
    private final Game gameModel;
    private final ConcurrentList<GameRunner> games = new ConcurrentList<>();
    private final int playersPerGame;
    private boolean startGame = false;
    private int startGameIdx = -1;
    private final Authentication auth;
    // channel -> tokens
    private final ConcurrentHashMap<SocketChannel, String> clientTokens = new ConcurrentHashMap<>();

    // channel -> index of the game where player is playing
    private final ConcurrentHashMap<SocketChannel, Integer> playing = new ConcurrentHashMap<>();

    // channel -> index of the game where player is waiting
    private final ConcurrentList<String> inQueue = new ConcurrentList<>();

    // channel -> index of the game where player is waiting for other players
    private final HashMap<SocketChannel, Integer> waitingForPlayers;

    // username -> index of the game where player was playing but crashed
    private final HashMap<String, Integer> leftInGame;
    private final ConcurrentList<Integer> currentPlayers = new ConcurrentList<>();

    private final ConcurrentList<String> ranks = new ConcurrentList<>();

    public Server(int maxGames, Game game) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);

        threadPool = Executors.newFixedThreadPool(maxGames);
        this.gameModel = game.clone();
        for (int i = 0; i < maxGames; i++) {
            games.add(new GameRunner(gameModel.clone(), this, i));
        }

        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        auth = new Authentication("src/tokens.txt", "src/users.txt", "src/ranks.txt");

        playersPerGame = Game.getNumPlayers();
        waitingForPlayers = new HashMap<>();
        leftInGame = new HashMap<>();

        for (int i = 0; i < maxGames; i++) {
            currentPlayers.add(0);
        }

        for (int i = 0; i < maxGames; i++) {
            ranks.add("");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                auth.clearTokens();
            } catch (IOException e) {
                System.out.println("Error: unable to clear authentication tokens!");
            }
        }));
    }

    public void runServer() throws IOException {
        long startTime = System.nanoTime();
        while (true) {
            selector.selectNow();
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
                    sendMessage(socketChannel, Server.welcomeMessage);

                } else if (key.isReadable()) {
                    // Data received from a client
                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                    int numRead = -1;
                    boolean disconected = false;
                    if (socketChannel.isOpen()) {
                        try {
                            numRead = socketChannel.read(buffer);
                        } catch (IOException ioException) {
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

                        if (playing.get(socketChannel) != null) {
                            int index = playing.get(socketChannel);
                            games.get(index).sendMessage(socketToUsername(socketChannel), message);
                            games.get(index).wakeUp();
                        } else if (message.startsWith("help")) {
                            String usageInstructions = """
                                    Usage instructions:
                                    Login: login <username> <password>
                                    Register: register <username> <password>
                                    """;
                            sendMessage(socketChannel, usageInstructions);
                        } else if (message.startsWith("register")) {
                            String[] parts = message.split(" ");
                            String res;
                            if (parts.length != 3)
                                res = "Usage: register <username> <password>";
                            else {
                                String username = parts[1];
                                String password = parts[2];
                                String tok = auth.registerUser(username, password);
                                Integer nextReady = getNextReady(username);
                                if (tok.contains("Error"))
                                    res = tok;
                                else {
                                    res = gameHandling(socketChannel, nextReady, username, tok);
                                }
                                if (!res.contains("Error:"))
                                    clientTokens.put(socketChannel, tok);
                            }
                            sendMessage(socketChannel, res);
                            if (startGame) {
                                startGame(startGameIdx);
                                startGame = false;
                            }

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
                                        Integer nextReady = getNextReady(username);
                                        res = gameHandling(socketChannel, nextReady, username, tok);
                                    }
                                    if (!res.contains("Error:"))
                                        clientTokens.put(socketChannel, tok);
                                } else {
                                    res = "Error: You are already logged in!";
                                }
                            }
                            sendMessage(socketChannel, res);
                            if (startGame) {
                                startGame(startGameIdx);
                                startGame = false;
                            }

                        } else if (message.startsWith("logout")) {
                            String answer;
                            String[] parts = message.split(" ");
                            boolean canceled = false;
                            if (parts.length != 2) {
                                answer = "Usage: logout <token>";
                            } else {
                                String token = parts[1];
                                if (clientTokens.containsValue(token)) {
                                    String username = auth.getUserName(token);
                                    answer = "Logout successful!";
                                    int nextReady = getNextReady(username);
                                    if (nextReady != -1) {
                                        if (currentPlayers.get(nextReady) < playersPerGame) {
                                            currentPlayers.set(nextReady, Math.max(currentPlayers.get(nextReady) - 1, 0));
                                            playing.remove(socketChannel);
                                            waitingForPlayers.remove(socketChannel);
                                            String m = "Waiting for players [" + currentPlayers.get(nextReady) + " / " + playersPerGame + "]";
                                            m += " Server #" + nextReady;
                                            sendMessageToPlayers(playing, m, nextReady);
                                        } else {
                                            if (playing.containsKey(socketChannel)) {
                                                int idx = playing.get(socketChannel);
                                                leftInGame.put(username, idx);
                                                playing.remove(socketChannel);
                                                waitingForPlayers.remove(socketChannel);
                                                sendMessageToPlayers(playing, username + " has disconected!", idx);
                                            }
                                        }
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
                            sendMessage(socketChannel, answer);
                            if (canceled)
                                socketChannel.close();
                        } else {
                            sendMessage(socketChannel, message);
                        }
                    }
                }
            }

                if ((System.nanoTime() - startTime) / 1000000 > RELAX_AFTER_TIME) {
                    for (int i = 0; i < ranks.size(); i++) {
                        if (!Objects.equals(ranks.get(i), "")) {
                            if (!games.get(i).isReady()) {
                                ranks.set(i, "");
                                continue;
                            }
                            String[] parts = ranks.get(i).split("-");
                            int rank_left = Math.max(Integer.parseInt(parts[0]) - RELAX_MMR, 0);
                            int rank_right = Integer.parseInt(parts[1]) + RELAX_MMR;
                            System.out.println("Relaxing queue on Server #" + i + ": " + rank_left + "-" + rank_right);
                            ranks.set(i, rank_left + "-" + rank_right);
                        }
                    }
                    startTime = System.nanoTime();
            }
        }
    }

    private String socketToUsername(SocketChannel socketChannel) {
        String token = clientTokens.get(socketChannel);
        return tokenToUsername(token);
    }

    private String tokenToUsername(String token) {
        for (Map.Entry<String, String> entry : auth.getTokens().entrySet()) {
            if (Objects.equals(entry.getValue(), token)) {
                return entry.getKey();
            }
        }
        return "";
    }

    private String gameHandling(SocketChannel socketChannel, Integer nextReady, String username, String tok) throws IOException {
        String res;
        res = "Login Token: " + tok + "\nWelcome " + username + "!\n";
        if (leftInGame.containsKey(username)) {
            sendMessageToPlayers(playing, username + " has reconnected!", leftInGame.get(username));
            res += username + " has reconnected!";
            playing.put(socketChannel, leftInGame.get(username));
            leftInGame.remove(username);
        } else {
            if (nextReady.equals(-1)) {
                if (!inQueue.contains(username)) {
                    inQueue.add(username);
                }
                res += "You are in the Queue!\nPosition in Queue: " + (inQueue.indexOf(username) + 1);
            } else if (currentPlayers.get(nextReady) < playersPerGame - 1) {
                currentPlayers.set(nextReady, currentPlayers.get(nextReady) + 1);

                String m = "Waiting for players [" + currentPlayers.get(nextReady) + " / " + playersPerGame + "]";
                m += " Server #" + nextReady;
                res += m;
                sendMessageToPlayers(playing, m, nextReady);
                waitingForPlayers.put(socketChannel, nextReady);
            } else if (currentPlayers.get(nextReady) < playersPerGame) {
                res += "Connected to Server #" + nextReady + "\n";
                currentPlayers.set(nextReady, currentPlayers.get(nextReady) + 1);
                Iterator<Map.Entry<SocketChannel, Integer>> iterator = waitingForPlayers.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<SocketChannel, Integer> entry = iterator.next();
                    SocketChannel player = entry.getKey();
                    Integer value = entry.getValue();
                    if (value.equals(nextReady)) {
                        playing.put(player, nextReady);
                        iterator.remove();
                    }
                }

                playing.put(socketChannel, nextReady);
                startGame = true;
                startGameIdx = nextReady;
            } else {
                if (!inQueue.contains(username)) {
                    inQueue.add(username);
                }
                res += "You are in the Queue!\nPosition in Queue: " + (inQueue.indexOf(username) + 1);
            }
        }
        return res;
    }

    private void startGame(Integer nextReady) throws IOException {
        List<SocketChannel> sockets = sendMessageToPlayers(playing, "Game Starting!\n" + Game.welcomeMessage, nextReady);
        List<String> usernames = new ArrayList<>();
        for (SocketChannel socket : sockets) {
            usernames.add(socketToUsername(socket));
        }
        games.get(nextReady).startGame();
        games.get(nextReady).setGame(gameModel.clone());
        games.get(nextReady).povoate_users(usernames);
        threadPool.submit(games.get(nextReady));
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server(1, new Game("src/ranks.txt"));
        server.runServer();
    }

    private static List<SocketChannel> sendMessageToPlayers(Map<SocketChannel, Integer> clients, String message, Integer index) throws IOException {
        List<SocketChannel> sockets = new ArrayList<>();
        for (SocketChannel client : clients.keySet()) {
            if (clients.get(client).equals(index)) {
                sendMessage(client, message);
                sockets.add(client);
            }
        }
        return sockets;
    }

    private static void updateQueue(Map<SocketChannel, Integer> clients) throws IOException {
        int i = 1;
        for (SocketChannel client : clients.keySet()) {
            String m = "Position in Queue: " + i;
            clients.replace(client, i);
            sendMessage(client, m);
            i++;
        }
    }

    private int getNextReady(String username) {
        for (int i = 0; i < games.size(); i++) {
            if (games.get(i).isReady()) {
                if (ranks.get(i).equals("")) {
                    int rank = auth.getRank(username);
                    int rank1 = rank - RELAX_MMR;
                    int rank2 = rank + RELAX_MMR;
                    ranks.set(i, rank1 + "-" + rank2);
                    return i;
                } else {
                    String[] parts = ranks.get(i).split("-");
                    int rank = auth.getRank(username);
                    if (rank >= Integer.parseInt(parts[0]) && rank <= Integer.parseInt(parts[1]))
                        return i;
                }
            }
        }
        return -1;
    }

    public Map<String, SocketChannel> getUsernamesToSocketChannelsForGame(int gameIndex) {
        Map<String, SocketChannel> usernamesToSocketChannels = new HashMap<>();
        for (Map.Entry<SocketChannel, Integer> entry : playing.entrySet()) {
            SocketChannel socketChannel = entry.getKey();
            int index = entry.getValue();
            String token = clientTokens.get(socketChannel);
            if (index == gameIndex) {
                String username = tokenToUsername(token);
                usernamesToSocketChannels.put(username, socketChannel);
            }
        }
        return usernamesToSocketChannels;
    }

    private static void sendGameMessages(HashMap<String, SocketChannel> receivers, List<String> usernames, List<String> messages) throws IOException {
        // receivers: username -> socketchannel
        for (int i = 0; i < usernames.size(); i++) {
            SocketChannel socketChannel = receivers.get(usernames.get(i));
            sendMessage(socketChannel, messages.get(i));
        }
    }

    @Override
    public void onUpdate(Game game, int index) {
        ArrayList<String> answers = game.getMessageForServer();
        ArrayList<String> usernames = game.getUsernameFromMessageForServer();
        HashMap<String, SocketChannel> usernameToSocket = (HashMap<String, SocketChannel>) getUsernamesToSocketChannelsForGame(index);

        if (!answers.isEmpty() && answers.get(0).contains(Game.getGameOverMessage())) {
            for (int i = 0; i < usernames.size(); i++) {
                playing.remove(usernameToSocket.get(usernames.get(i)));
                answers.set(i, answers.get(i) + "\n" + "DISCONNECT");
            }
            try {
                sendGameMessages(usernameToSocket, usernames, answers);
            } catch (IOException e) {
                System.out.println("Error: unable to send game messages!");
            }

            currentPlayers.set(index, 0);
            ranks.set(index, "");

            for (int i = 0; i < inQueue.size(); i++) {
                String user = inQueue.get(i);
                String token = auth.getToken(user);
                SocketChannel client = null;
                for (SocketChannel socketChannel : clientTokens.keySet())
                    if (clientTokens.get(socketChannel).equals(token)) {
                        client = socketChannel;
                        break;
                    }
                inQueue.remove(user);
                i--;
                int next = getNextReady(user);
                try {
                    String res = gameHandling(client, next, user, token);
                    sendMessage(client, res);
                    if (startGame) {
                        startGame(startGameIdx);
                        startGame = false;
                    }
                } catch (IOException e) {
                    System.out.println("Error: Unable to send messages to clients!");
                }
            }
            if (answers.get(0).contains("points")) {
                for (int i = 0; i < answers.size(); i++) {
                    String answer = answers.get(i);
                    int incrementPoints = Integer.parseInt(answer.substring(answer.indexOf("scored ") + "scored ".length(), answer.indexOf(" points")));
                    auth.updateRank(usernames.get(i), incrementPoints);
                }
            }
        } else {
            try {
                sendGameMessages(usernameToSocket, usernames, answers);
            } catch (IOException e) {
                System.out.println("Error: unable to send game messages!");
            }
        }
    }

    public static void sendMessage(SocketChannel socketChannel, String message) throws IOException {
        buffer.put(0, new byte[buffer.limit()]);
        buffer = encoder.encode(CharBuffer.wrap(message));
        socketChannel.write(buffer);
    }
}
