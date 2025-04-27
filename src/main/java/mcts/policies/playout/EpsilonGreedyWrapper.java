package mcts.policies.playout;

import java.util.concurrent.ThreadLocalRandom;
import main.collections.FastArrayList;
import other.context.Context;
import other.move.Move;
import other.playout.PlayoutMoveSelector;

public final class EpsilonGreedyWrapper extends PlayoutMoveSelector {
    private final PlayoutMoveSelector wrapped;
    private final double epsilon;

    public EpsilonGreedyWrapper(final PlayoutMoveSelector wrapped, final double epsilon) {
        this.wrapped = wrapped;
        this.epsilon = epsilon;
    }

    @Override
    public Move selectMove(
            final Context context,
            final FastArrayList<Move> maybeLegalMoves,
            final int p,
            final IsMoveReallyLegal isMoveReallyLegal) {
        return wrapped.selectMove(context, maybeLegalMoves, p, isMoveReallyLegal);
    }

    @Override
    public boolean wantsPlayUniformRandomMove() {
        return ThreadLocalRandom.current().nextDouble() < epsilon;
    }
}
