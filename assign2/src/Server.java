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

public class Server implements GameCallback {
    public static final int BUFFER_SIZE = 4096;
    private final static int RELAX_MMR = 50;
    private final static int RELAX_AFTER_TIME = 10000;
    public final static String welcomeMessage = "Welcome to our server!\nPlease login or register a new account.\nIf you need any help, you can just send the \"help\" message.";
    private final static CharsetEncoder encoder = (StandardCharsets.UTF_8).newEncoder();
    private static ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final ExecutorService threadPool;
    private final Game gameModel;
    private final ConcurrentList<Triplet<GameRunner, String, Integer>> gamesAndRanks = new ConcurrentList<>();
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
    private final HashMap<SocketChannel, Integer> waitingForPlayers = new HashMap<>();

    // username -> index of the game where player was playing but crashed
    private final HashMap<String, Integer> leftInGame = new HashMap<>();

    /**
     Constructs a Server object with the specified maximum number of games and game model.
     @param maxGames The maximum number of games that the server can handle simultaneously.
     @param game The game model to be used for each game instance.
     @throws IOException If an I/O error occurs when opening the server socket channel.
     */
    public Server(int maxGames, Game game) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);
        threadPool = Executors.newFixedThreadPool(maxGames);
        this.gameModel = game.clone();
        for (int i = 0; i < maxGames; i++) {
            gamesAndRanks.add(new Triplet<>(new GameRunner(gameModel.clone(), this, i), "", 0));
        }
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        auth = new Authentication("db/users.txt", "db/ranks.txt");
        playersPerGame = Game.getNumPlayers();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                auth.clearTokens();
            } catch (IOException e) {
                System.out.println("Error: unable to clear authentication tokens!");
            }
        }));
    }

    /**
     Runs the server and handles client connections and incoming messages.
     The server continuously listens for incoming requests using non-blocking communication channels and performs the necessary actions based on the received messages.
     It handles client login, registration and logout.
     It also handles "help" messages and if the server receives an unknown message, it will echo it back to the sender.
     All gameplay-related interactions are not processed by the server and are simply redirected to the respective game for processing.
     The server periodically (10 seconds intervals) relaxes the matchmaking queue and starts the games when ready.
     @throws IOException if an I/O error occurs while running the server.
     */
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
                        int nextReady = -1;
                        if (playing.containsKey(socketChannel)) nextReady = playing.get(socketChannel);
                        else if (waitingForPlayers.containsKey(socketChannel)) nextReady = waitingForPlayers.get(socketChannel);

                        if (nextReady != -1) {
                            if (gamesAndRanks.get(nextReady).getVal3() < playersPerGame) {
                                gamesAndRanks.get(nextReady).setVal3(Math.max(gamesAndRanks.get(nextReady).getVal3() - 1, 0));
                                if (gamesAndRanks.get(nextReady).getVal3() == 0) gamesAndRanks.get(nextReady).setVal2("");
                                playing.remove(socketChannel);
                                waitingForPlayers.remove(socketChannel);
                                String m = "Waiting for players [" + gamesAndRanks.get(nextReady).getVal3() + " / " + playersPerGame + "]";
                                m += " GameServer #" + nextReady;
                                sendMessageToPlayers(playing, m, nextReady);
                            } else {
                                if (playing.containsKey(socketChannel)) {
                                    int idx = playing.get(socketChannel);
                                    leftInGame.put(socketToUsername(socketChannel), idx);
                                    playing.remove(socketChannel);
                                    waitingForPlayers.remove(socketChannel);
                                    sendMessageToPlayers(playing, socketToUsername(socketChannel) + " has disconnected!", idx);
                                }
                            }
                        }
                        auth.invalidateToken(usernameToToken(socketToUsername(socketChannel)));
                        clientTokens.remove(socketChannel);
                        System.out.println("Client disconnected: " + socketChannel.getRemoteAddress());
                        key.cancel();
                        socketChannel.close();
                    } else {
                        buffer.flip();
                        String message = new String(buffer.array(), 0, buffer.limit()).trim();
                        buffer.clear();
                        System.out.println("Message received from " + socketChannel.getRemoteAddress() + ": " + message);

                        if (playing.get(socketChannel) != null && !message.startsWith("logout")) {
                            int index = playing.get(socketChannel);
                            gamesAndRanks.get(index).getVal1().sendMessage(socketToUsername(socketChannel), message);
                            gamesAndRanks.get(index).getVal1().wakeUp();
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
                                    int nextReady = -1;
                                    if (playing.containsKey(socketChannel)) nextReady = playing.get(socketChannel);
                                    else if (waitingForPlayers.containsKey(socketChannel)) nextReady = waitingForPlayers.get(socketChannel);


                                    if (nextReady != -1) {
                                        if (gamesAndRanks.get(nextReady).getVal3() < playersPerGame) {
                                            gamesAndRanks.get(nextReady).setVal3(Math.max(gamesAndRanks.get(nextReady).getVal3() - 1, 0));
                                            if (gamesAndRanks.get(nextReady).getVal3() == 0) gamesAndRanks.get(nextReady).setVal2("");
                                            playing.remove(socketChannel);
                                            waitingForPlayers.remove(socketChannel);
                                            String m = "Waiting for players [" + gamesAndRanks.get(nextReady).getVal3() + " / " + playersPerGame + "]";
                                            m += " GameServer #" + nextReady;
                                            sendMessageToPlayers(playing, m, nextReady);
                                        } else {
                                            if (playing.containsKey(socketChannel)) {
                                                int idx = playing.get(socketChannel);
                                                leftInGame.put(username, idx);
                                                playing.remove(socketChannel);
                                                waitingForPlayers.remove(socketChannel);
                                                sendMessageToPlayers(playing, username + " has disconnected!", idx);
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
                for (int i = 0; i < gamesAndRanks.size(); i++) {
                    if (!Objects.equals(gamesAndRanks.get(i).getVal2(), "")) {
                        if (!gamesAndRanks.get(i).getVal1().isReady()) {
                            gamesAndRanks.get(i).setVal2("");
                            continue;
                        }
                        String[] parts = gamesAndRanks.get(i).getVal2().split("-");
                        int rank_left = Math.max(Integer.parseInt(parts[0]) - RELAX_MMR, 0);
                        int rank_right = Integer.parseInt(parts[1]) + RELAX_MMR;
                        System.out.println("Relaxing queue on Server #" + i + ": " + rank_left + "-" + rank_right);
                        gamesAndRanks.get(i).setVal2(rank_left + "-" + rank_right);


                        for (int j=0;j<inQueue.size();j++) {
                            String username = inQueue.get(j);
                            SocketChannel socketChannel = usernameToSocket(username);
                            if (socketChannel != null) {
                                Integer nextReady = getNextReady(username);
                                if (nextReady == -1) {
                                    sendMessage(socketChannel, "Still in queue! Relaxing the rank match.");
                                } else {
                                    String tok = usernameToToken(username);
                                    String answer = gameHandling(socketChannel, nextReady, username, tok);
                                    sendMessage(socketChannel, answer.split("\n")[2]);
                                }

                                if (startGame) {
                                    startGame(nextReady);
                                    startGame = false;
                                }
                            }
                        }
                    }
                }
                startTime = System.nanoTime();
            }
        }
    }

    /**
     Retrieves the username associated with the given SocketChannel.
     @param socketChannel the SocketChannel for which to retrieve the associated username.
     @return the username associated with the SocketChannel, or null if not found.
     */
    private String socketToUsername(SocketChannel socketChannel) {
        String token = clientTokens.get(socketChannel);
        return tokenToUsername(token);
    }


    /**
     Retrieves the username associated with the given token.
     @param token the token for which to retrieve the associated username.
     @return the username associated with the token, or an empty string if not found.
     */
    private String tokenToUsername(String token) {
        for (Map.Entry<String, String> entry : auth.getTokens().entrySet()) {
            if (Objects.equals(entry.getValue(), token)) {
                return entry.getKey();
            }
        }
        return "";
    }

    /**

     Retrieves the token associated with the given username.
     @param username the username for which to retrieve the associated token.
     @return the token associated with the username, or an empty string if not found.
     */
    private String usernameToToken(String username) {
        String token = "";
        for (Map.Entry<String, String> entry : auth.getTokens().entrySet()) {
            if (Objects.equals(entry.getKey(), username)) {
                token = entry.getValue();
                break;
            }
        }

        return token;
    }

    /**
     Retrieves the SocketChannel associated with the given username.
     @param username the username for which to retrieve the associated SocketChannel.
     @return the SocketChannel associated with the username, or null if not found.
     */
    private SocketChannel usernameToSocket(String username) {
        String token = usernameToToken(username);
        for (Map.Entry<SocketChannel, String> entry : clientTokens.entrySet()) {
            if (Objects.equals(entry.getValue(), token)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     Handles the game logic and interactions for a client.
     Determines the appropriate actions based on the client's status, such as login, reconnection, joining the queue,
     joining a game server, or being placed in the queue.
     @param socketChannel the SocketChannel associated with the client.
     @param nextReady the index of the next available game server, or -1 if none are currently available.
     @param username the username of the client.
     @param tok the login token associated with the client.
     @return a message indicating the result of the game handling and any additional instructions or information for the client.
     @throws IOException if an I/O error occurs while handling the game.
     */
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
            } else if (gamesAndRanks.get(nextReady).getVal3() < playersPerGame - 1) {
                gamesAndRanks.get(nextReady).setVal3(gamesAndRanks.get(nextReady).getVal3() + 1);

                String m = "Waiting for players [" + gamesAndRanks.get(nextReady).getVal3() + " / " + playersPerGame + "]";
                m += " Server #" + nextReady;
                res += m;
                sendMessageToPlayers(playing, m, nextReady);
                waitingForPlayers.put(socketChannel, nextReady);
                inQueue.remove(username);
            } else if (gamesAndRanks.get(nextReady).getVal3() < playersPerGame) {
                res += "Connected to Server #" + nextReady + "\n";
                gamesAndRanks.get(nextReady).setVal3(gamesAndRanks.get(nextReady).getVal3() + 1);
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
                inQueue.remove(username);
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

    /**
     Starts the game on the specified game server.
     Sends a game starting message to all players on the server and initializes the game.
     @param nextReady the index of the game server on which to start the game.
     @throws IOException if an I/O error occurs while starting the game.
     */
    private void startGame(Integer nextReady) throws IOException {
        List<SocketChannel> sockets = sendMessageToPlayers(playing, "Game Starting!\n" + Game.welcomeMessage, nextReady);
        List<String> usernames = new ArrayList<>();
        for (SocketChannel socket : sockets) {
            usernames.add(socketToUsername(socket));
        }
        gamesAndRanks.get(nextReady).getVal1().startGame();
        gamesAndRanks.get(nextReady).getVal1().setGame(gameModel.clone());
        gamesAndRanks.get(nextReady).getVal1().povoate_users(usernames);
        threadPool.submit(gamesAndRanks.get(nextReady).getVal1());
    }

    /**
     The entry point of the server application.
     Creates a new Server instance with the specified number of game servers and the game configuration,
     then starts the server by calling the runServer() method.
     @param args command-line arguments (not used).
     @throws IOException if an I/O error occurs while running the server.
     */
    public static void main(String[] args) throws IOException {
        Server server = new Server(2, new Game());
        server.runServer();
    }

    /**
     Sends a message to all players on the specified game server.
     Returns a list of the SocketChannel instances representing the players who received the message.
     @param clients the map of SocketChannel instances representing the connected clients, with their corresponding game server index.
     @param message the message to send to the players.
     @param index the index of the game server on which the players are located.
     @return a list of SocketChannel instances representing the players who received the message.
     @throws IOException if an I/O error occurs while sending the message.
     **/
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

    /**
     Retrieves the index of the next available game server that is ready to accept players.
     If the user's rank falls within the relaxed rank range of a ready game server, that server is prioritized.
     If no ready game server is found, -1 is returned.
     @param username the username of the user.
     @return the index of the next available game server, or -1 if none is available.
     */
    private int getNextReady(String username) {

        Comparator<Triplet<GameRunner, String, Integer>> comparator = new Comparator<Triplet<GameRunner, String, Integer>>() {
            @Override
            public int compare (Triplet < GameRunner, String, Integer > t1, Triplet < GameRunner, String, Integer > t2){
                if (t1.getVal3() > t2.getVal3())
                    return -1;
                else if (t1.getVal3() < t2.getVal3())
                    return 1;
                else
                    return 0;
            }
        };

        ConcurrentList<Triplet<GameRunner, String, Integer>> gamesOrdered = new ConcurrentList<>();
        gamesOrdered.addAll(gamesAndRanks);

        gamesOrdered.sort(comparator);

        for (int i = 0; i < gamesOrdered.size(); i++) {
            if (gamesOrdered.get(i).getVal1().isReady()) {
                if (gamesOrdered.get(i).getVal2().equals("")) {
                    int rank = auth.getRank(username);
                    int rank1 = rank - RELAX_MMR;
                    int rank2 = rank + RELAX_MMR;
                    gamesOrdered.get(i).setVal2(rank1 + "-" + rank2);
                    return gamesOrdered.get(i).getVal1().getIndex();
                } else {
                    String[] parts = gamesOrdered.get(i).getVal2().split("-");
                    int rank = auth.getRank(username);
                    if (rank >= Integer.parseInt(parts[0]) && rank <= Integer.parseInt(parts[1]))
                        return gamesOrdered.get(i).getVal1().getIndex();
                }
            }
        }
        return -1;
    }

    /**
     Retrieves a map of usernames to SocketChannel instances for players in a specific game server.
     @param gameIndex the index of the game server.
     @return a map of usernames to SocketChannel instances for players in the specified game server.
     */
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

    /**
     Sends game messages to the specified receivers.
     @param receivers a map of usernames to SocketChannel instances representing the receivers.
     @param usernames a list of usernames corresponding to the receivers.
     @param messages a list of messages to be sent.
     @throws IOException if an I/O error occurs while sending the messages.
     */
    private static void sendGameMessages(HashMap<String, SocketChannel> receivers, List<String> usernames, List<String> messages) throws IOException {
        // receivers: username -> socketchannel
        for (int i = 0; i < usernames.size(); i++) {
            SocketChannel socketChannel = receivers.get(usernames.get(i));
            sendMessage(socketChannel, messages.get(i));
        }
    }

    /**
     When the game finishes processing one iteration, this function is called to:
     - Send the game's messages to all the clients that are playing this game
     - Inform the server that the game ended therefore the respective Game Runner can be used to run another Game instance
     - Update each player points, when the game finishes
     @param game The game object.
     @param runner The associated game runner.
     */
    @Override
    public void onUpdate(Game game, GameRunner runner) {

        int index = 0;
        for (int i = 0; i < gamesAndRanks.size(); i++) {
            if (gamesAndRanks.get(i).getVal1().equals(runner))
                index = i;
        }


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

            gamesAndRanks.get(index).setVal3(0);
            gamesAndRanks.get(index).setVal2("");

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
                    if (client != null){
                        sendMessage(client, res);
                        if (startGame) {
                            startGame(startGameIdx);
                            startGame = false;
                        }
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
    /**
     Sends a message to a specific SocketChannel.
     @param socketChannel The SocketChannel to send the message to.
     @param message The message to be sent.
     @throws IOException If an error occurs while sending the message.
     */
    public static void sendMessage(SocketChannel socketChannel, String message) throws IOException {
        buffer.put(0, new byte[buffer.limit()]);
        buffer = encoder.encode(CharBuffer.wrap(message));
        socketChannel.write(buffer);
    }
}
