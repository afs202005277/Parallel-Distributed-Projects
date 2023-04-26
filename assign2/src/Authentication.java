import java.io.*;
import java.util.Hashtable;
import java.util.UUID;

public class Authentication {
    // username -> token
    private final Hashtable<String, String> tokens;
    private final Hashtable<String, Integer> ranks;
    private final String tokensFileName;
    private final String usersFileName;

    private final String rankFileName;

    public Hashtable<String, String> getTokens() {
        return tokens;
    }

    public Authentication(String filename, String usersFileName, String rankFileName) throws IOException {
        this.tokensFileName = filename;
        this.usersFileName = usersFileName;
        this.rankFileName = rankFileName;
        tokens = new Hashtable<>();
        ranks = new Hashtable<>();
        loadTokensFromFile();
        loadRanksFromFile();
    }

    public synchronized String login(String username, String password) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(usersFileName));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts[0].equals(username)) {
                if (parts[1].equals(password)) {
                    // Credentials are valid, generate access token
                    return this.createToken(username);
                } else {
                    // Password is incorrect
                    reader.close();
                    return "Error: Incorrect password";
                }
            }
        }
        // Username not found, create new user and generate access token
        return "Error: User not found!";
    }

    private void loadRanksFromFile() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(rankFileName));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts.length == 2) {
                ranks.put(parts[0], Integer.valueOf(parts[1]));
            }
        }
        reader.close();
    }


    private void loadTokensFromFile() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(tokensFileName));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts.length == 2) {
                tokens.put(parts[0], parts[1]);
            }
        }
        reader.close();
    }

    public synchronized String createToken(String username) throws IOException {
        String token = UUID.randomUUID().toString();
        tokens.put(username, token);
        saveTokensToFile();
        saveRanksToFile();
        return token;
    }

    public synchronized String getToken(String username) {
        return tokens.get(username);
    }

    public synchronized String getUserName(String token) {
        for (String key : tokens.keySet()) {
            if (tokens.get(key).equals(token)) {
                return key;
            }
        }
        return null;
    }

    public synchronized void clearTokens() throws IOException{
        tokens.clear();
        saveTokensToFile();
        saveRanksToFile();
    }

    public synchronized void invalidateToken(String token) throws IOException {
        String username = null;
        for (String key : tokens.keySet()) {
            if (tokens.get(key).equals(token)) {
                username = key;
                break;
            }
        }
        if (username != null) {
            tokens.remove(username);
            saveTokensToFile();
            saveRanksToFile();
        }
    }

    public synchronized Integer getRank(String username) {
        return ranks.get(username);
    }

    public synchronized String registerUser(String username, String password) throws IOException {
        // Check if username already exists
        BufferedReader reader = new BufferedReader(new FileReader(usersFileName));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts.length == 2 && parts[0].equals(username)) {
                reader.close();
                return "Error: Username already exists.";
            }
        }
        reader.close();

        // Append new user to file
        BufferedWriter writer = new BufferedWriter(new FileWriter(usersFileName, true));
        writer.write(username + ":" + password + "\n");
        writer.close();

        ranks.put(username, 500); //Starting rank

        // Generate and return access token
        return this.createToken(username);
    }


    private void saveTokensToFile() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(tokensFileName));
        for (String key : tokens.keySet()) {
            writer.write(key + ":" + tokens.get(key) + "\n");
        }
        writer.close();
    }

    private void saveRanksToFile() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(rankFileName));
        for (String key : ranks.keySet()) {
            writer.write(key + ":" + ranks.get(key) + "\n");
        }
        writer.close();
    }

    public boolean isLoggedIn(String username) {
        return tokens.get(username) != null;
    }
}
