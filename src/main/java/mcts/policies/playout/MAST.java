package mcts.policies.playout;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import main.collections.FastArrayList;
import mcts.ActionStats;
import mcts.policies.backpropagation.BackpropagationFlags;
import other.context.Context;
import other.move.Move;
import other.playout.PlayoutMoveSelector;
import other.trial.Trial;
import search.mcts.MCTS.MoveKey;

public final class MAST implements IPlayoutPolicy {
    private final double epsilon;
    private Map<MoveKey, ActionStats> globalActionStats;

    public MAST() {
        this(0.1);
    }

    public MAST(final double epsilon) {
        this.epsilon = epsilon;
    }

    @Override
    public String getName() {
        return String.format("MAST (ε-greedy, ε=%f)", epsilon);
    }

    @Override
    public void setGlobalActionStats(final Map<MoveKey, ActionStats> globalActionStats) {
        this.globalActionStats = globalActionStats;
    }

    @Override
    public int getBackpropagationFlags() {
        return BackpropagationFlags.GLOBAL_ACTION_STATS;
    }

    @Override
    public Trial runPlayout(Context context) {
        return context.game()
                .playout(
                        context,
                        null,
                        -1.0,
                        new EpsilonGreedyWrapper(new MASTMoveSelector(globalActionStats), epsilon),
                        0,
                        -1,
                        ThreadLocalRandom.current());
    }

    private final class MASTMoveSelector extends PlayoutMoveSelector {
        private final Map<MoveKey, ActionStats> globalActionStats;

        public MASTMoveSelector(final Map<MoveKey, ActionStats> globalActionStats) {
            this.globalActionStats = globalActionStats;
        }

        @Override
        public Move selectMove(
                final Context context,
                final FastArrayList<Move> maybeLegalMoves,
                final int p,
                final IsMoveReallyLegal isMoveReallyLegal) {

            // get best legal move
            Move bestMove = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            int numBestFound = 0;

            for (final var move : maybeLegalMoves) {
                final var aStats = globalActionStats.get(new MoveKey(move, 0));

                if (isMoveReallyLegal.checkMove(move)) {
                    final double tempScore = (aStats == null) ? -1.0 : aStats.scoreSums[p] / aStats.visitCount;
                    if (tempScore > bestScore) {
                        bestScore = tempScore;
                        bestMove = move;
                        numBestFound = 1;
                    } else if (tempScore == bestScore && ThreadLocalRandom.current().nextInt() % ++numBestFound == 0) {
                        bestMove = move;
                    }
                }
            }

            // if (bestMove == null) {
            //     System.err.println("MAST: no move found");
            // }
            // System.err.println("MAST: selected move with score: " + bestScore);
            // System.err.println("MAST: map size: " + globalActionStats.size());
            return bestMove;
        }
    }
}
