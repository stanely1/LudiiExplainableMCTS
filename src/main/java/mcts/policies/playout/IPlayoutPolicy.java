package mcts.policies.playout;

import java.util.Map;
import mcts.ActionStats;
import other.context.Context;
import other.trial.Trial;
import search.mcts.MCTS.MoveKey;

public interface IPlayoutPolicy {
    public String getName();

    public int getBackpropagationFlags();

    public Trial runPlayout(Context context);

    public void setGlobalActionStats(final Map<MoveKey, ActionStats> globalActionStats);
}
