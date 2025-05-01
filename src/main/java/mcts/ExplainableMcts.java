package mcts;

import game.Game;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mcts.Node.SimulationResult;
import mcts.policies.IGlobalActionStatsUser;
import mcts.policies.IGlobalNGramStatsUser;
import mcts.policies.backpropagation.BackpropagationFlags;
import mcts.policies.playout.IPlayoutPolicy;
import mcts.policies.selection.ISelectionPolicy;
import mcts.policies.selection.ScoreBoundedFinalMoveSelectionPolicy;
import mcts.policies.selection.ScoreBoundedSelectionPolicy;
import other.AI;
import other.context.Context;
import other.move.Move;
import search.mcts.MCTS.MoveKey;
import search.mcts.MCTS.NGramMoveKey;

public class ExplainableMcts extends AI {
    // -------------------------------------------------------------------------
    private int player = -1;

    private Node root;

    private final ISelectionPolicy selectionPolicy;
    private final ISelectionPolicy finalMoveSelectionPolicy;
    private final IPlayoutPolicy playoutPolicy;

    private final int backpropagationFlags;

    private int lastActionHistorySize = 0;
    private int lastNumIterations = 0;
    private double lastMoveValue = 0.0;
    private Node lastSelectedNode;
    private Move lastSelectedMove;

    private String analysisReport;

    // Global tables for MAST/NST (i.e action/n-gram statistics)

    // TODO:
    // - Decay stats over time
    // (see: https://cris.maastrichtuniversity.nl/ws/portalfiles/portal/37539340/c6529.pdf/ chapter 2.5.2)

    private final Map<MoveKey, ActionStats> globalActionStats = new HashMap<>();
    private final Map<NGramMoveKey, ActionStats> globalNGramStats = new HashMap<>();

    private int maxNGramLength = 0;

    // -------------------------------------------------------------------------

    public ExplainableMcts(
            final ISelectionPolicy selectionPolicy,
            final ISelectionPolicy finalMoveSelectionPolicy,
            final IPlayoutPolicy playoutPolicy,
            final boolean useScoreBounds) {
        this.friendlyName = "ExplainableMcts";

        if (useScoreBounds) {
            this.selectionPolicy = new ScoreBoundedSelectionPolicy(selectionPolicy);
            this.finalMoveSelectionPolicy = new ScoreBoundedFinalMoveSelectionPolicy(finalMoveSelectionPolicy);
        } else {
            this.selectionPolicy = selectionPolicy;
            this.finalMoveSelectionPolicy = finalMoveSelectionPolicy;
        }

        this.playoutPolicy = playoutPolicy;

        if (this.playoutPolicy instanceof IGlobalActionStatsUser globalActionStatsPlayoutPolicy) {
            globalActionStatsPlayoutPolicy.setGlobalActionStats(globalActionStats);
        }

        if (this.playoutPolicy instanceof IGlobalNGramStatsUser globalNGramStatsPlayoutPolicy) {
            globalNGramStatsPlayoutPolicy.setGlobalNGramStats(globalNGramStats);
            this.maxNGramLength = globalNGramStatsPlayoutPolicy.getMaxNGramLength();
        }

        this.backpropagationFlags = this.selectionPolicy.getBackpropagationFlags()
                | this.finalMoveSelectionPolicy.getBackpropagationFlags()
                | this.playoutPolicy.getBackpropagationFlags();

        System.out.println(String.format(
                "[%s] selection policy: %s; final move selection policy: %s; playout policy: %s; backpropagation flags: {%s}",
                this.friendlyName,
                this.selectionPolicy.getName(),
                this.finalMoveSelectionPolicy.getName(),
                this.playoutPolicy.getName(),
                BackpropagationFlags.flagsToString(this.backpropagationFlags)));
    }

    @Override
    public Move selectAction(
            final Game game,
            final Context context,
            final double maxSeconds,
            final int maxIterations,
            final int maxDepth) {

        // We'll respect any limitations on max seconds and max iterations (don't care
        // about max depth)
        final long stopTime =
                (maxSeconds > 0.0) ? System.currentTimeMillis() + (long) (maxSeconds * 1000L) : Long.MAX_VALUE;
        final int maxIts = (maxIterations >= 0) ? maxIterations : Integer.MAX_VALUE;
        int numIterations = 0;

        initRoot(context);

        // TODO: fixed num of iterations + fixed seed
        while (numIterations < maxIts
                && System.currentTimeMillis() < stopTime
                && !wantsInterrupt
                && !root.isSolved(this.player)) {
            Node current = root;
            int currentPlayer = this.player;

            while (!current.isTerminal() && current.isExpanded() && !current.isSolved(currentPlayer)) {
                current = current.select(this.selectionPolicy);
                currentPlayer = current.getPlayer();
            }

            final Node newNode = current.expand();
            final SimulationResult simRes = newNode.simulate(playoutPolicy);
            newNode.propagate(simRes, this.backpropagationFlags);
            propagateGlobalStats(simRes);

            numIterations++;
        }

        this.lastSelectedNode = root.select(this.finalMoveSelectionPolicy);

        this.lastNumIterations = numIterations;
        this.lastMoveValue = lastSelectedNode.getScoreSum(this.player) / lastSelectedNode.getVisitCount();
        this.lastSelectedMove = lastSelectedNode.getMoveFromParent();

        final String debugString = generateDebugString();
        final String explanation = generateExplanation();

        this.analysisReport = debugString + "\n" + explanation + "\n";

        return this.lastSelectedMove;
    }

    @Override
    public void initAI(final Game game, final int playerID) {
        this.player = playerID;

        this.root = null;
        this.lastActionHistorySize = 0;
        this.lastNumIterations = 0;
        this.lastMoveValue = 0.0;
        this.lastSelectedNode = null;
        this.lastSelectedMove = null;
    }

    @Override
    public void closeAI() {
        this.player = -1;
        this.root = null;
        this.lastActionHistorySize = 0;
        this.lastNumIterations = 0;
        this.lastMoveValue = 0.0;
        this.lastSelectedNode = null;
        this.lastSelectedMove = null;
    }

    @Override
    public boolean supportsGame(final Game game) {
        return !game.isStochasticGame() && game.isAlternatingMoveGame();
    }

    @Override
    public double estimateValue() {
        return lastMoveValue;
    }

    @Override
    public String generateAnalysisReport() {
        return analysisReport;
    }

    private String generateDebugString() {
        String debugStringBase = String.format(
                "[%s] Performed %d iterations, selected node: {visits: %d",
                this.friendlyName, this.lastNumIterations, this.lastSelectedNode.getVisitCount());

        String debugString = debugStringBase + String.format(", score: %.4f", this.lastMoveValue);

        if ((this.backpropagationFlags & BackpropagationFlags.AMAF_STATS) != 0) {
            final var visitCountAMAF = root.getVisitCountAMAF(lastSelectedMove);
            final var scoreAMAF = root.getScoreSumAMAF(lastSelectedMove, this.player) / visitCountAMAF;

            debugString += String.format(", AMAF visits: %d, AMAF score: %.4f", visitCountAMAF, scoreAMAF);
        }

        if ((this.backpropagationFlags & BackpropagationFlags.GLOBAL_ACTION_STATS) != 0) {
            final var aStats = globalActionStats.get(new MoveKey(lastSelectedMove, 0));
            debugString += String.format(
                    ", global action visits: %d, global action score: %.4f",
                    aStats.visitCount, aStats.scoreSums[this.player] / aStats.visitCount);
        }

        if ((this.backpropagationFlags & BackpropagationFlags.SCORE_BOUNDS) != 0) {
            if (this.lastSelectedNode.isSolved(this.player)) {
                debugString = debugStringBase
                        + String.format(
                                ", solved node with score %.4f",
                                this.lastSelectedNode.getPessimisticScore(this.player));

                if (this.lastSelectedNode.isWin(this.player)) {
                    debugString += " (win)";
                } else if (this.lastSelectedNode.isLoss(this.player)) {
                    debugString += " (loss)";
                }
            } else {
                debugString += String.format(
                        ", pess: %.4f, opt: %.4f",
                        this.lastSelectedNode.getPessimisticScore(this.player),
                        this.lastSelectedNode.getOptimisticScore(this.player));
            }
        }

        debugString += "}";
        return debugString;
    }

    private String generateExplanation() {
        // TODO: explanations here
        String explanation = "I selected this move because it was the best.";

        return explanation;
    }

    private void initRoot(final Context context) {
        // Tree reuse
        if (root != null) {
            // get action history for current state
            final List<Move> actionHistory = context.trial().generateCompleteMovesList();

            // calculate number of moves we need to apply from previous root
            int offsetActionToTraverse = actionHistory.size() - lastActionHistorySize;

            if (offsetActionToTraverse < 0) {
                root = null;
            }

            // apply moves to find new root
            while (offsetActionToTraverse > 0) {
                final Move move = actionHistory.get(actionHistory.size() - offsetActionToTraverse);
                root = root.getChildByMove(move);

                if (root == null) {
                    break;
                }

                --offsetActionToTraverse;
            }
        }

        if (root == null) {
            root = new Node(null, null, context);
        } else {
            root.detachFromParent();
        }

        lastActionHistorySize = context.trial().numMoves();
    }

    private void propagateGlobalStats(final SimulationResult simRes) {
        if ((this.backpropagationFlags & BackpropagationFlags.GLOBAL_ACTION_STATS) != 0) {
            propagateGlobalActionStats(simRes);
        }

        if ((this.backpropagationFlags & BackpropagationFlags.GLOBAL_NGRAM_ACTION_STATS) != 0) {
            propagateGlobalNGramStats(simRes);
        }
    }

    private void propagateGlobalActionStats(final SimulationResult simRes) {
        // System.err.println("Updating global stats");

        final var leafContext = simRes.context();
        final var utilities = simRes.utilities();

        final var playerCount = root.getGame().players().count();
        final var fullActionHistory = leafContext.trial().generateCompleteMovesList();

        final var firstActionIndex = root.getContext().trial().numMoves();
        final var actionHistory = fullActionHistory.subList(firstActionIndex, fullActionHistory.size());

        for (final var act : actionHistory) {
            final var moveKey = new MoveKey(act, 0);
            if (!globalActionStats.containsKey(moveKey)) {
                globalActionStats.put(moveKey, new ActionStats(playerCount));
            }
            final var stats = globalActionStats.get(moveKey);

            stats.visitCount++;
            for (var p = 1; p <= playerCount; p++) {
                stats.scoreSums[p] += utilities[p];
            }
        }

        // TODO: needed here? why setting it once didn't work ?????
        if (this.playoutPolicy instanceof IGlobalActionStatsUser globalActionStatsPlayoutPolicy) {
            globalActionStatsPlayoutPolicy.setGlobalActionStats(globalActionStats);
        }
        // System.err.println("MCTS: map size: " + globalActionStats.size());
    }

    private void propagateGlobalNGramStats(final SimulationResult simRes) {
        // System.err.println("updating global ngram stats");

        final var leafContext = simRes.context();
        final var utilities = simRes.utilities();

        final var playerCount = root.getGame().players().count();
        final var fullActionHistory = leafContext.trial().generateCompleteMovesList();
        final var firstActionIndex = root.getContext().trial().numMoves();

        for (var i = firstActionIndex; i < fullActionHistory.size(); i++) {
            for (var j = Math.max(0, i - maxNGramLength + 1); j <= i; j++) {
                final var n = i - j + 1;
                final var nGram = new Move[n];
                for (var k = 0; k < n; k++) {
                    nGram[k] = fullActionHistory.get(j + k);
                }

                final var nGramKey = new NGramMoveKey(nGram, 0);
                if (!globalNGramStats.containsKey(nGramKey)) {
                    globalNGramStats.put(nGramKey, new ActionStats(playerCount));
                }
                final var stats = globalNGramStats.get(nGramKey);

                stats.visitCount++;
                for (var p = 1; p <= playerCount; p++) {
                    stats.scoreSums[p] += utilities[p];
                }
            }
        }

        if (this.playoutPolicy instanceof IGlobalNGramStatsUser globalNGramStatsPlayoutPolicy) {
            globalNGramStatsPlayoutPolicy.setGlobalNGramStats(globalNGramStats);
        }

        // System.err.println("MCTS: map size: " + globalNGramStats.size());
    }
}
