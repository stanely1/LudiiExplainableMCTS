package mcts.policies.playout;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import mcts.ActionStats;
import other.context.Context;
import other.trial.Trial;
import search.mcts.MCTS.MoveKey;

public final class UniformPlayoutPolicy implements IPlayoutPolicy {
    @Override
    public String getName() {
        return "Uniform";
    }

    @Override
    public int getBackpropagationFlags() {
        return 0;
    }

    @Override
    public Trial runPlayout(Context context) {
        return context.game().playout(context, null, -1.0, null, 0, -1, ThreadLocalRandom.current());
    }

    @Override
    public void setGlobalActionStats(final Map<MoveKey, ActionStats> globalActionStats) {}
}
