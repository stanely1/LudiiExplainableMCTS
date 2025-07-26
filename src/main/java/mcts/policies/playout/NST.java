package mcts.policies.playout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import main.collections.FastArrayList;
import mcts.ActionStats;
import mcts.policies.IGlobalNGramStatsUser;
import mcts.policies.backpropagation.BackpropagationFlags;
import other.context.Context;
import other.move.Move;
import other.playout.PlayoutMoveSelector;
import other.trial.Trial;
import search.mcts.MCTS.NGramMoveKey;

public final class NST implements IPlayoutPolicy, IGlobalNGramStatsUser {
    private final int maxNGramLength;
    private final double epsilon;

    private Map<NGramMoveKey, ActionStats> globalNGramStats;

    public NST() {
        this(3, 0.1);
    }

    public NST(final int maxNGramLength, final double epsilon) {
        this.maxNGramLength = maxNGramLength;
        this.epsilon = epsilon;
    }

    @Override
    public String getName() {
        return String.format("NST with max N-gram length: %d (ε-greedy, ε=%f)", maxNGramLength, epsilon);
    }

    @Override
    public void setGlobalNGramStats(final Map<NGramMoveKey, ActionStats> globalNGramStats) {
        this.globalNGramStats = globalNGramStats;
    }

    @Override
    public int getBackpropagationFlags() {
        return BackpropagationFlags.GLOBAL_NGRAM_ACTION_STATS;
    }

    @Override
    public int getMaxNGramLength() {
        return maxNGramLength;
    }

    @Override
    public Trial runPlayout(Context context) {
        return context.game()
                .playout(
                        context,
                        null,
                        -1.0,
                        new EpsilonGreedyWrapper(new NSTMoveSelector(maxNGramLength, globalNGramStats), epsilon),
                        0,
                        -1,
                        ThreadLocalRandom.current());
    }

    private final class NSTMoveSelector extends PlayoutMoveSelector {
        private final int maxNGramLength;
        private final Map<NGramMoveKey, ActionStats> globalNGramStats;

        public NSTMoveSelector(final int maxNGramLength, final Map<NGramMoveKey, ActionStats> globalNGramStats) {
            this.maxNGramLength = maxNGramLength;
            this.globalNGramStats = globalNGramStats;
        }

        @Override
        public Move selectMove(
                final Context context,
                final FastArrayList<Move> maybeLegalMoves,
                final int p,
                final IsMoveReallyLegal isMoveReallyLegal) {

            Move bestMove = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            int numBestFound = 0;

            for (final var move : maybeLegalMoves) {
                if (!isMoveReallyLegal.checkMove(move)) {
                    continue;
                }

                final List<Move> reverseActionSequence = new ArrayList<>();
                reverseActionSequence.add(move);
                final var reverseTrialIterator = context.trial().reverseMoveIterator();

                int numNGramsConsidered = 0;
                double nGramsScoreSum = 0.0;

                for (var n = 1; n <= maxNGramLength; n++) {
                    final var nGram = new Move[n];
                    for (var i = 0; i < n; i++) {
                        nGram[i] = reverseActionSequence.get(n - i - 1);
                    }

                    final var nGramStats = globalNGramStats.get(new NGramMoveKey(nGram, 0));

                    if (nGramStats == null) {
                        if (n == 1) {
                            nGramsScoreSum = -1.0;
                            numNGramsConsidered = 1;
                        }
                        break;
                    } else {
                        nGramsScoreSum += nGramStats.scoreSums[p] / nGramStats.visitCount;
                        numNGramsConsidered++;
                    }

                    if (!reverseTrialIterator.hasNext()) {
                        break;
                    }
                    reverseActionSequence.add(reverseTrialIterator.next());
                }

                final double moveScore = nGramsScoreSum / numNGramsConsidered;

                if (moveScore > bestScore) {
                    bestScore = moveScore;
                    bestMove = move;
                    numBestFound = 1;
                } else if (moveScore == bestScore && ThreadLocalRandom.current().nextInt() % ++numBestFound == 0) {
                    bestMove = move;
                }
            }

            // if (bestMove == null) {
            //     System.err.println("NST: no move found");
            // }
            // System.err.println("NST: selected move with score: " + bestScore);
            // System.err.println("NST: map size: " + globalNGramStats.size());
            return bestMove;
        }
    }
}
