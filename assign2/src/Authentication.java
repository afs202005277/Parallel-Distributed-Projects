import java.io.*;
import java.util.Map;
import java.util.UUID;

public class Authentication {
    // username -> token
    private final ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<>();

    //username -> rank
    private final ConcurrentHashMap<String, Integer> ranks = new ConcurrentHashMap<>();
    private final String usersFileName;

    private final String rankFileName;

    public Map<String, String> getTokens() {
        return tokens;
    }

    /**
     * Constructs an Authentication object with the specified file names for tokens, users, and ranks.
     *
     * @param usersFileName  The file name for users.
     * @param rankFileName   The file name for ranks.
     * @throws IOException  If an error occurs while loading the tokens or ranks from files.
     */
    public Authentication(String usersFileName, String rankFileName) throws IOException {
        this.usersFileName = usersFileName;
        this.rankFileName = rankFileName;

        createFileIfNotExists(usersFileName);
        createFileIfNotExists(rankFileName);

        loadRanksFromFile();
    }

    private void createFileIfNotExists(String fileName) throws IOException {
        File file = new File(fileName);
        File parentDirectory = file.getParentFile();

        if (!parentDirectory.exists()) {
            boolean created = parentDirectory.mkdirs();
            if (!created) {
                throw new IOException("Failed to create directory: " + parentDirectory);
            }
        }

        if (!file.exists()) {
            boolean created = file.createNewFile();
            if (!created) {
                throw new IOException("Failed to create file: " + fileName);
            }
        }
    }

    /**
     * Performs user login with the given username and password.
     *
     * @param username  The username.
     * @param password  The password.
     * @return The access token if login is successful, or an error message if login fails.
     * @throws IOException  If an error occurs while reading the user file.
     */
    public String login(String username, String password) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(usersFileName));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts[0].equals(username)) {
                if (parts[1].equals(password)) {
                    return this.createToken(username);
                } else {
                    reader.close();
                    return "Error: Incorrect password";
                }
            }
        }
        return "Error: User not found!";
    }

    /**
     * Loads and stores the ranks from the ranks file into a hashmap.
     *
     * @throws IOException  If an error occurs while reading the ranks file.
     */
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

    /**
     * Creates an access token for the specified username.
     *
     * @param username  The username.
     * @return The generated access token.
     * @throws IOException  If an error occurs while saving the tokens or ranks to files.
     */
    public String createToken(String username) throws IOException {
        String token = UUID.randomUUID().toString();
        tokens.put(username, token);
        saveRanksToFile();
        return token;
    }

    /**
     * Retrieves the access token for the specified username.
     *
     * @param username  The username.
     * @return The access token associated with the username.
     */
    public String getToken(String username) {
        return tokens.get(username);
    }

    /**
     * Retrieves the username associated with the specified access token.
     *
     * @param token  The access token.
     * @return The username associated with the access token, or null if the token is invalid.
     */
    public String getUserName(String token) {
        for (String key : tokens.keySet()) {
            if (tokens.get(key).equals(token)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Clears all tokens and saves the changes to files.
     *
     * @throws IOException  If an error occurs while saving the tokens or ranks to files.
     */
    public void clearTokens() throws IOException{
        tokens.clear();
        saveRanksToFile();
    }

    /**
     * Invalidates the specified access token by removing it from the tokens map and saving the changes to files.
     *
     * @param token  The access token to invalidate.
     * @throws IOException  If an error occurs while saving the tokens or ranks to files.
     */
    public void invalidateToken(String token) throws IOException {
        String username = null;
        for (String key : tokens.keySet()) {
            if (tokens.get(key).equals(token)) {
                username = key;
                break;
            }
        }
        if (username != null) {
            tokens.remove(username);
            saveRanksToFile();
        }
    }

    /**
     * Retrieves the rank of the specified username.
     *
     * @param username  The username.
     * @return The rank of the username.
     */
    public Integer getRank(String username) {
        return ranks.get(username);
    }

    /**
     * Updates the rank of the specified username by incrementing it with the given value.
     *
     * @param username  The username.
     * @param increment The value to increment the rank by.
     */
    public void updateRank(String username, Integer increment) {
        Integer lastVal = ranks.get(username);
        ranks.replace(username, lastVal + increment);
    }

    /**
     * Registers a new user with the specified username and password.
     *
     * @param username  The username of the new user.
     * @param password  The password of the new user.
     * @return The access token generated for the new user in case of success, or an error message otherwise.
     * @throws IOException  If an error occurs while saving the user or tokens to files.
     */
    public String registerUser(String username, String password) throws IOException {
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
        BufferedWriter writer = new BufferedWriter(new FileWriter(usersFileName, true));
        writer.write(username + ":" + password + "\n");
        writer.close();

        ranks.put(username, 500); //Starting rank

        return this.createToken(username);
    }

    /**
     Saves the ranks map to the ranks file.
     @throws IOException If an error occurs while writing to the ranks file.
     */
    private void saveRanksToFile() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(rankFileName));
        for (String key : ranks.keySet()) {
            writer.write(key + ":" + ranks.get(key) + "\n");
        }
        writer.close();
    }

    /**
     Checks if the specified username is currently logged in.
     @param username The username.
     @return true if the username is logged in, false otherwise.
     */
    public boolean isLoggedIn(String username) {
        return tokens.get(username) != null;
    }
}
