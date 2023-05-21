import java.util.*;
import java.util.Random;

public class Game implements Cloneable {

    private static final int numPlayers = 4; // must be even
    private static final int interactions_before_end_game = 4;

    public static final String welcomeMessage = "Welcome to our game!\nTo play this game, you only need to write some messages in your keyboard and check what the other players have done in the meantime!\nThe game ends after " + interactions_before_end_game + " interactions with players.\n";

    private static final String game_over_message = "Game over: ";

    private final List<Double> gameLogicProbs;

    private final List<Triplet<Integer, String, String>> gameLogicValues;

    private ArrayList<String> message_for_server;
    private ArrayList<String> username_message_for_server;

    private int game_score = 0;

    private ArrayList<String> usernames;
    private ArrayList<Integer> usernames_points;

    private ArrayList<String> username_message;
    private ArrayList<String> messages;
    private int iterations;

    /**
     * Gets the game over message.
     *
     * @return The game over message.
     */
    public static String getGameOverMessage() {
        return game_over_message;
    }

    /**
     * Checks if the game has ended.
     *
     * @return true if the game has ended, false otherwise.
     */
    public boolean isEnded() {
        return iterations >= Game.interactions_before_end_game;
    }

    /**
     * Stores the message into the "messages" list, as well as the username of the client who sent it.
     * The purpose of this function is to make the connection between the server and the game.
     * @param username  The username of the player.
     * @param message   The message sent by the player.
     */
    public void sendMessage(String username, String message) {
        username_message.add(username);
        messages.add(message);
    }

    /**
     * Creates a new Game instance.
     * Also instantiates the values of the gameLogic hashmaps with the different game actions and respective probabilities.
     */
    public Game() {
        iterations = 0;
        message_for_server = new ArrayList<>();
        username_message_for_server = new ArrayList<>();
        usernames_points = new ArrayList<>();
        username_message = new ArrayList<>();
        this.messages = new ArrayList<>();
        gameLogicProbs = new ArrayList<>();
        gameLogicValues = new ArrayList<>();

        Triplet<Integer, String, String> triplet = new Triplet<>(3, "Planted the bomb", "planted the bomb");

        gameLogicProbs.add(0.1);
        gameLogicValues.add(triplet);

        triplet = new Triplet<>(2, "Killed a player", "killed a player");

        gameLogicProbs.add(0.3);
        gameLogicValues.add(triplet);

        triplet = new Triplet<>(1, "Assisted on a kill", "assisted on a kill");

        gameLogicProbs.add(0.5);
        gameLogicValues.add(triplet);

        triplet = new Triplet<>(-1, "You died", "died");
        gameLogicProbs.add(0.75);
        gameLogicValues.add(triplet);

        triplet = new Triplet<>(0, "Walked", "walked");

        gameLogicProbs.add(1.0);
        gameLogicValues.add(triplet);

    }

    /**
     * Populates the list of usernames for the game.
     *
     * @param usernames  The list of usernames.
     */
    public void populateUsers(List<String> usernames) {
        this.usernames = (ArrayList<String>) usernames;
        for (int i = 0; i < usernames.size(); i++)
            this.usernames_points.add(0);
    }

    /**
     Gets the number of players in the game.
     @return The number of players.
     */
    public static int getNumPlayers() {
        return numPlayers;
    }

    /**
     Advances the game to the next iteration.
     Executes game logic for pending messages until the game ends or there are no more messages.
     */
    public void nextIteration() {
        while (!this.messages.isEmpty() && !isEnded()) {
            iterations++;
            gameLogic(username_message.get(0));
            messages.remove(0);
            username_message.remove(0);
        }
    }

    /**
     Processes the end of the game.
     Adjusts the scores of the players based on the game score.
     Generate messages for each player with their final score.
     */
    public void processEndGame() {
        for (int i = 0; i < usernames_points.size(); i++) {
            if (game_score < 0 && i < numPlayers / 2) {
                usernames_points.set(i, -usernames_points.get(i));
            }
            if (game_score > 0 && i >= numPlayers / 2) {
                usernames_points.set(i, -usernames_points.get(i));
            }
        }

        for (int i = 0; i < usernames.size(); i++) {
            this.message_for_server.add(game_over_message + "You scored " + usernames_points.get(i) + " points!\n");
            this.username_message_for_server.add(usernames.get(i));
        }
    }

    /**
     Retrieves the messages generated for the server.
     Clears the message list after retrieval.
     @return The messages for the server.
     */
    public ArrayList<String> getMessageForServer() {
        ArrayList<String> res = new ArrayList<>(message_for_server);
        message_for_server.clear();
        return res;
    }

    /**
     Retrieves the usernames associated with the messages generated for the server.
     Clears the username list after retrieval.
     @return The usernames associated with the messages.
     */
    public ArrayList<String> getUsernameFromMessageForServer() {
        ArrayList<String> res = new ArrayList<>(username_message_for_server);
        username_message_for_server.clear();
        return res;
    }

    /**
     Adds the given score to the specified user's points.
     @param username The username of the player.
     @param addition The score to be added.
     */
    private void addScore(String username, int addition) {
        int i = usernames.indexOf(username);
        usernames_points.set(i, usernames_points.get(i) + addition);
    }

    /**
     Executes the game logic for the given player's message.
     Determines the outcome of the message based on probabilities and updates scores accordingly.
     @param username The username of the player.
     */
    private void gameLogic(String username) {
        double random = (new Random()).nextFloat();
        String message_action_user = "";
        String other_players_message_action = "";

        for (int i = 0; i < gameLogicProbs.size(); i++)
            if (random < gameLogicProbs.get(i)) {
                int val = gameLogicValues.get(i).getVal1();
                message_action_user = gameLogicValues.get(i).getVal2();
                other_players_message_action = gameLogicValues.get(i).getVal3();
                this.addScore(username, val);
                if (usernames.indexOf(username) < numPlayers / 2)
                    game_score += val;
                else
                    game_score -= val;
                break;
            }

        for (String user : usernames) {
            if (Objects.equals(user, username)) {
                this.message_for_server.add(message_action_user);
                this.username_message_for_server.add(username);
            } else {
                this.message_for_server.add(username + " " + other_players_message_action);
                this.username_message_for_server.add(user);
            }
        }
    }

    /**
     Creates a deep copy of the current Game object.
     @return A clone of the Game object.
     */
    @Override
    public Game clone() {
        try {
            Game clone = (Game) super.clone();
            clone.iterations = 0;
            clone.message_for_server = new ArrayList<>();
            clone.username_message_for_server = new ArrayList<>();
            clone.usernames_points = new ArrayList<>();
            clone.username_message = new ArrayList<>();
            clone.messages = new ArrayList<>();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
