import java.nio.channels.SocketChannel;
import java.util.List;

public class GameRunner extends Thread{
    private final Game game;
    private final GameCallback gameCallback;

    private final int index;

    public GameRunner(Game game, GameCallback callback, int index) {
        this.game = game;
        this.gameCallback = callback;
        this.index = index;
    }

    public void povoate_users(List<String> usernames){
        this.game.povoate_users(usernames);
    }

    public void sendMessage(String username, String message){
        this.game.sendMessage(username, message);
    }
    @Override
    public void run() {
        do {
            System.out.println("inside runner");
            game.nextIteration();
            gameCallback.onUpdate(game, index);
        } while (!game.isEnded());
        game.processEndGame();
    }
}
