package mcts.policies.playout;

import other.context.Context;
import other.trial.Trial;

public interface IPlayoutPolicy {
    public String getName();

    public int getPgetBackpropagationFlagslayoutFlags();

    public Trial runPlayout(Context context);
}
