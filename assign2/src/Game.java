import java.util.*;
import java.util.Random;

public class Game implements Cloneable {

    private static final int numPlayers = 2; // must be even
    private static final int interactions_before_end_game = 4;

    public static final String welcomeMessage = "Welcome to our game!\nTo play this game, you only need to write some messages in your keyboard and check what the other players have done in the meantime!\nThe game ends after " + interactions_before_end_game + " interactions with players.\n";

    private static final String game_over_message = "Game over: ";

    private String fileName;

    private ArrayList<String> message_for_server;
    private ArrayList<String> username_message_for_server;

    private int game_score = 0;

    private ArrayList<String> usernames;
    private ArrayList<Integer> usernames_points;

    private ArrayList<String> username_message;
    private ArrayList<String> messages;

    private int iterations;

    public static String getGameOverMessage() {
        return game_over_message;
    }

    public boolean isEnded() {
        return iterations >= Game.interactions_before_end_game;
    }

    public void sendMessage(String username, String message) {
        username_message.add(username);
        messages.add(message);
    }

    public Game(String fileName) {
        this.fileName = fileName;
        iterations = 0;
        message_for_server = new ArrayList<>();
        username_message_for_server = new ArrayList<>();
        usernames_points = new ArrayList<>();
        username_message = new ArrayList<>();
        this.messages = new ArrayList<>();
    }

    public void populateUsers(List<String> usernames) {
        this.usernames = (ArrayList<String>) usernames;
        for (int i = 0; i < usernames.size(); i++)
            this.usernames_points.add(0);
    }

    public static int getNumPlayers() {
        return numPlayers;
    }

    public void nextIteration() {
        while (!this.messages.isEmpty() && !isEnded()) {
            iterations++;
            gameLogic(username_message.get(0));
            messages.remove(0);
            username_message.remove(0);
        }
    }

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

    public ArrayList<String> getMessageForServer() {
        ArrayList<String> res = new ArrayList<>(message_for_server);
        message_for_server.clear();
        return res;
    }

    public ArrayList<String> getUsernameFromMessageForServer() {
        ArrayList<String> res = new ArrayList<>(username_message_for_server);
        username_message_for_server.clear();
        return res;
    }

    private void addScore(String username, int addition) {
        int i = usernames.indexOf(username);
        usernames_points.set(i, usernames_points.get(i) + addition);
    }

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

    @Override
    public Game clone() {
        try {
            Game clone = (Game) super.clone();
            clone.fileName = fileName;
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
