package mcts.policies;

import java.util.Map;
import mcts.ActionStats;
import search.mcts.MCTS.MoveKey;

public interface IGlobalActionStatsUser {
    public void setGlobalActionStats(final Map<MoveKey, ActionStats> globalActionStats);
}
