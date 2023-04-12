import java.io.*;
import java.util.Hashtable;
import java.util.UUID;

public class Authentication {
    private final Hashtable<String, String> tokens;
    private final String filename;

    public Authentication(String filename) throws IOException {
        this.filename = filename;
        tokens = new Hashtable<>();
        loadTokensFromFile();
    }


    private void loadTokensFromFile() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
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
        return token;
    }

    public synchronized void invalidateToken(String user) throws IOException {
        tokens.remove(user);
        saveTokensToFile();
    }

    private void saveTokensToFile() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        for (String key : tokens.keySet()) {
            writer.write(key + ":" + tokens.get(key) + "\n");
        }
        writer.close();
    }
}
