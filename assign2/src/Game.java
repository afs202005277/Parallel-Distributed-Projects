import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.Random;

public class Game {

    private static final int numPlayers = 2; // must be even
    private static final int interactions_before_end_game = 20;


    private ArrayList<String> message_for_server;
    private ArrayList<String> username_message_for_server;

    private int game_score = 0;

    private ArrayList<String> usernames;
    private ArrayList<Integer> usernames_points;

    private ArrayList<String> username_message;
    private ArrayList<String> messages;

    private int iterations;

    private boolean changed = false;

    public boolean isEnded() {
        return iterations >= Game.interactions_before_end_game;
    }

    public void sendMessage(String username, String message) {
        username_message.add(username);
        messages.add(message);
        System.out.println(messages);
        System.out.println("INSIDE GAME:" + message);
    }

    public Game() {
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

    public boolean hasChanged(){return changed;}

    public void nextIteration() {
        System.out.println("outside is empty");

        while (!this.messages.isEmpty()) {
            iterations++;
            game_logic(username_message.get(0));
            messages.remove(0);
            username_message.remove(0);
        }
    }

    public void processEndGame() {
        for (String user : this.usernames) {
            this.message_for_server.add("GAME_OVER");
            this.username_message_for_server.add(user);
        }

        for (int i = 0; i < usernames_points.size(); i++) {
            if (game_score < 0 && i < numPlayers / 2) {
                usernames_points.set(i, -usernames_points.get(i));
            }
            if (game_score > 0 && i >= numPlayers / 2) {
                usernames_points.set(i, -usernames_points.get(i));
            }
        }
    }

    public ArrayList<String> getUsernames() {
        return this.usernames;
    }

    public ArrayList<Integer> getUserPoints() {
        return this.usernames_points;
    }

    public ArrayList<String> getMessageForServer() {
        return this.message_for_server;
    }

    public ArrayList<String> getUsernameFromMessageForServer() {
        return this.username_message_for_server;
    }

    private void add_score(String username, int addition) {
        int i = usernames.indexOf(username);
        usernames_points.set(i, usernames_points.get(i) + addition);
    }

    private void game_logic(String username) {
        Random rand = new Random();
        String message_action = "Walked";
        if (rand.nextFloat() < 0.01) {
            message_action = "Planted the bomb";
            this.add_score(username, 3);
            if (usernames.indexOf(username) < numPlayers / 2)
                game_score += 3;
            else
                game_score -= 3;
        } else if (rand.nextFloat() < 0.05) {
            message_action = "Killed a player";
            this.add_score(username, 2);
            if (usernames.indexOf(username) < numPlayers / 2)
                game_score += 2;
            else
                game_score -= 2;
        } else if (rand.nextFloat() < 0.15) {
            message_action = "Assisted on a kill";
            this.add_score(username, 1);
            if (usernames.indexOf(username) < numPlayers / 2)
                game_score += 1;
            else
                game_score -= 1;
        } else if (rand.nextFloat() < 0.10) {
            message_action = "Player died";
        }
        this.message_for_server.add(message_action);
        this.username_message_for_server.add(username);
    }
}
