import java.nio.channels.SocketChannel;
import java.util.List;

public class GameRunner extends Thread {
    private final Game game;

    private final Object lock;

    private final GameCallback gameCallback;

    private final int index;

    private boolean waiting;

    public GameRunner(Game game, GameCallback callback, int index) {
        this.game = game;
        this.gameCallback = callback;
        this.index = index;
        this.lock = new Object();
        waiting = false;
    }

    public void povoate_users(List<String> usernames) {
        this.game.povoate_users(usernames);
    }

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
        } while (!game.isEnded());
        game.processEndGame();
        gameCallback.onUpdate(game, index);
    }
}
