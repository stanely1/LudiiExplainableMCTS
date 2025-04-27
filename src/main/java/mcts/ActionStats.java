package mcts;

public final class ActionStats {
    public int visitCount = 0;
    public final double[] scoreSums;

    public ActionStats(final int playerCount) {
        this.scoreSums = new double[playerCount + 1];
    }
}
