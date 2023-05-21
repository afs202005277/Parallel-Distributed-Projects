import java.util.List;

/**
 The GameRunner class represents a thread that runs a game instance and communicates with a GameCallback (in this case, the server).
 It manages the execution of the game iterations and notifies the callback of game updates.
 */
public class GameRunner extends Thread {
    private Game game;

    private final Object lock;

    private final GameCallback gameCallback;

    private final int index;

    private boolean waiting;

    private boolean hasStarted;

    /**
     Constructs a GameRunner object.
     @param game The game instance to run.
     @param callback The callback interface for game updates.
     @param index The index of the game runner.
     */
    public GameRunner(Game game, GameCallback callback, int index) {
        this.game = game;
        this.gameCallback = callback;
        this.index = index;
        this.lock = new Object();
        waiting = false;
        hasStarted = false;
    }

    /**
     Gets the game index.
     @return the game index.
     */
    public int getIndex() {
        return index;
    }

    /**
     Sets the game instance to run.
     @param game The game instance to set.
     */
    public void setGame(Game game) {
        this.game = game;
    }

    /**
    Populates the users for the game.
    @param usernames The list of usernames to populate.
    */
    public void povoate_users(List<String> usernames) {
        this.game.populateUsers(usernames);
    }

    /**
     Checks if the game runner is ready to start a new game.
     @return True if the game runner is not currently running a game, false otherwise.
     */
    public boolean isReady() {
        return !hasStarted;
    }

    /**
     Starts the game.
     */
    public void startGame() { hasStarted = true;}

    /**
     Sends a message to the game.
     @param username The username of the sender.
     @param message The message to send.
     */
    public void sendMessage(String username, String message) {
        this.game.sendMessage(username, message);
    }

    /**
     Wakes up the thread from waiting state.
     This method is called when the server receives a new message for this game.
     */
    public void wakeUp() {
        synchronized (lock) {
            waiting = false;
            lock.notify();
        }
    }

    /**
     The main execution method of the thread.
     It runs the game iterations, updates the callback, and manages the waiting state (the thread waits until receiving messages).
     */
    @Override
    public void run() {
        do {
            game.nextIteration();
            gameCallback.onUpdate(game, this);
            if (game.isEnded())
                break;
            synchronized (lock) {
                while (waiting) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                waiting = true;
            }
        } while (true);
        game.processEndGame();
        hasStarted = false;
        gameCallback.onUpdate(game, this);
    }
}
