import java.util.List;

public class GameRunner extends Thread {
    private Game game;

    private final Object lock;

    private final GameCallback gameCallback;

    private final int index;

    private boolean waiting;

    private boolean hasStarted;

    public GameRunner(Game game, GameCallback callback, int index) {
        this.game = game;
        this.gameCallback = callback;
        this.index = index;
        this.lock = new Object();
        waiting = false;
        hasStarted = false;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public void povoate_users(List<String> usernames) {
        this.game.populateUsers(usernames);
    }

    public boolean isReady() {
        return !hasStarted;
    }

    public void startGame() { hasStarted = true;}

    public void finishGame() { hasStarted = false; }

    public void sendMessage(String username, String message) {
        this.game.sendMessage(username, message);
    }

    public void wakeUp() {
        synchronized (lock) {
            waiting = false;
            lock.notify();
        }
    }

    @Override
    public void run() {
        do {
            game.nextIteration();
            gameCallback.onUpdate(game, index);
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
        gameCallback.onUpdate(game, index);
    }
}
