package mcts.policies;

import java.util.Map;
import mcts.ActionStats;
import search.mcts.MCTS.NGramMoveKey;

public interface IGlobalNGramStatsUser {
    public void setGlobalNGramStats(final Map<NGramMoveKey, ActionStats> globalNGramStats);

    public int getMaxNGramLength();
}
