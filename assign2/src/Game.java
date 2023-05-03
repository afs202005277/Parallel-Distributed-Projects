import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.Random;

public class Game implements Cloneable {

    private static final int numPlayers = 2; // must be even
    private static final int interactions_before_end_game = 4;

    public static final String welcomeMessage = "Welcome to our game!\nTo play this game, you only need to write some messages in your keyboard and check what the other players have done in the meantime!\nThe game ends after " + (interactions_before_end_game + 1) + " interactions with players.\n";

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

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

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

    public void povoate_users(List<String> usernames) {
        this.usernames = (ArrayList<String>) usernames;
        for (int i = 0; i < usernames.size(); i++)
            this.usernames_points.add(0);
    }

    public static int getNumPlayers() {
        return numPlayers;
    }

    public void nextIteration() {
        while (!this.messages.isEmpty()) {
            iterations++;
            game_logic(username_message.get(0));
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


    public ArrayList<String> getUsernames() {
        return this.usernames;
    }

    public ArrayList<Integer> getUserPoints() {
        return this.usernames_points;
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

    private void add_score(String username, int addition) {
        int i = usernames.indexOf(username);
        usernames_points.set(i, usernames_points.get(i) + addition);
    }

    private void game_logic(String username) {
        Random rand = new Random();
        String message_action_user = "Walked";
        String other_players_message_action = "walked";
        if (rand.nextFloat() < 0.1) {
            message_action_user = "Planted the bomb";
            other_players_message_action = "planted the bomb";
            this.add_score(username, 3);
            if (usernames.indexOf(username) < numPlayers / 2)
                game_score += 3;
            else
                game_score -= 3;
        } else if (rand.nextFloat() < 0.3) {
            message_action_user = "Killed a player";
            other_players_message_action = "killed a player";
            this.add_score(username, 2);
            if (usernames.indexOf(username) < numPlayers / 2)
                game_score += 2;
            else
                game_score -= 2;
        } else if (rand.nextFloat() < 0.5) {
            message_action_user = "Assisted on a kill";
            other_players_message_action = "assisted on a kill";
            this.add_score(username, 1);
            if (usernames.indexOf(username) < numPlayers / 2)
                game_score += 1;
            else
                game_score -= 1;
        } else if (rand.nextFloat() < 0.25) {
            message_action_user = "You died";
            other_players_message_action = "died";
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
