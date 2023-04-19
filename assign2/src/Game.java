public class Game implements Runnable{
    private static final int numPlayers = 4;
    @Override
    public void run() {
        System.out.println("Running!");
    }

    public static int getNumPlayers() {
        return numPlayers;
    }
}
