package mcts.policies.playout;

import java.util.concurrent.ThreadLocalRandom;
import other.context.Context;
import other.trial.Trial;

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
}
