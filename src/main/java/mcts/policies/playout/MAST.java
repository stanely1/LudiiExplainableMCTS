package mcts.policies.playout;

import java.util.concurrent.ThreadLocalRandom;
import main.collections.FastArrayList;
import mcts.policies.backpropagation.BackpropagationFlags;
import other.context.Context;
import other.move.Move;
import other.playout.PlayoutMoveSelector;
import other.trial.Trial;

public final class MAST implements IPlayoutPolicy {
    private double epsilon;

    public MAST() {
        this.epsilon = 0.1;
    }

    public MAST(final double epsilon) {
        this.epsilon = epsilon;
    }

    @Override
    public String getName() {
        return "MAST";
    }

    @Override
    public int getPgetBackpropagationFlagslayoutFlags() {
        return BackpropagationFlags.GLOBAL_ACTION_STATS;
    }

    @Override
    public Trial runPlayout(Context context) {
        return context.game()
                .playout(
                        context,
                        null,
                        -1.0,
                        new EpsilonGreedyWrapper(new MASTMoveSelector(), epsilon),
                        0,
                        -1,
                        ThreadLocalRandom.current());
    }

    private final class MASTMoveSelector extends PlayoutMoveSelector {
        @Override
        public Move selectMove(
                final Context context,
                final FastArrayList<Move> maybeLegalMoves,
                final int p,
                final IsMoveReallyLegal isMoveReallyLegal) {

            return null;
        }
    }
}
