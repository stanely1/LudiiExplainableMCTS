package mcts;

import game.Game;
import java.util.List;
import mcts.Node.SimulationResult;
import mcts.policies.backpropagation.BackpropagationFlags;
import mcts.policies.selection.ISelectionPolicy;
import mcts.policies.selection.ScoreBoundedFinalMoveSelectionPolicy;
import mcts.policies.selection.ScoreBoundedSelectionPolicy;
import other.AI;
import other.context.Context;
import other.move.Move;

public class ExplainableMcts extends AI {
    // -------------------------------------------------------------------------
    private int player = -1;

    private Node root;

    private final ISelectionPolicy selectionPolicy;
    private final ISelectionPolicy finalMoveSelectionPolicy;

    private final int backpropagationFlags;

    private int lastActionHistorySize = 0;
    private int lastNumIterations = 0;
    private double lastMoveValue = 0.0;
    private Node lastSelectedNode;
    // -------------------------------------------------------------------------

    public ExplainableMcts(
            final ISelectionPolicy selectionPolicy,
            final ISelectionPolicy finalMoveSelectionPolicy,
            final boolean useScoreBounds) {
        this.friendlyName = "ExplainableMcts";

        if (useScoreBounds) {
            this.selectionPolicy = new ScoreBoundedSelectionPolicy(selectionPolicy);
            this.finalMoveSelectionPolicy = new ScoreBoundedFinalMoveSelectionPolicy(finalMoveSelectionPolicy);
        } else {
            this.selectionPolicy = selectionPolicy;
            this.finalMoveSelectionPolicy = finalMoveSelectionPolicy;
        }

        this.backpropagationFlags = this.selectionPolicy.getBackpropagationFlags()
                | this.finalMoveSelectionPolicy.getBackpropagationFlags();

        System.out.println(String.format(
                "[%s] selection policy: %s; final move selection policy: %s; backpropagation flags: {%s}",
                this.friendlyName,
                this.selectionPolicy.getName(),
                this.finalMoveSelectionPolicy.getName(),
                BackpropagationFlags.flagsToString(this.backpropagationFlags)));
    }

    @Override
    public Move selectAction(
            final Game game,
            final Context context,
            final double maxSeconds,
            final int maxIterations,
            final int maxDepth) {

        // We'll respect any limitations on max seconds and max iterations (don't care about max depth)
        final long stopTime =
                (maxSeconds > 0.0) ? System.currentTimeMillis() + (long) (maxSeconds * 1000L) : Long.MAX_VALUE;
        final int maxIts = (maxIterations >= 0) ? maxIterations : Integer.MAX_VALUE;
        int numIterations = 0;

        initRoot(context);

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
            final SimulationResult simRes = newNode.simulate();
            newNode.propagate(simRes, this.backpropagationFlags);

            numIterations++;
        }

        final Node selectedNode = root.select(this.finalMoveSelectionPolicy);

        this.lastNumIterations = numIterations;
        this.lastMoveValue = selectedNode.getScoreSum(this.player) / selectedNode.getVisitCount();
        this.lastSelectedNode = selectedNode;

        return selectedNode.getMoveFromParent();
    }

    @Override
    public void initAI(final Game game, final int playerID) {
        this.player = playerID;

        this.root = null;
        this.lastActionHistorySize = 0;
        this.lastNumIterations = 0;
        this.lastMoveValue = 0.0;
        this.lastSelectedNode = null;
    }

    @Override
    public void closeAI() {
        this.player = -1;
        this.root = null;
        this.lastActionHistorySize = 0;
        this.lastNumIterations = 0;
        this.lastMoveValue = 0.0;
        this.lastSelectedNode = null;
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
        String analysisReportBase = String.format(
                "[%s] Performed %d iterations, selected node: {visits: %d",
                this.friendlyName, this.lastNumIterations, this.lastSelectedNode.getVisitCount());

        String analysisReport = analysisReportBase + String.format(", score: %f", this.lastMoveValue);

        if ((this.backpropagationFlags & BackpropagationFlags.AMAF_STATS) != 0) {
            final var move = lastSelectedNode.getMoveFromParent();
            final var visitCountAMAF = root.getVisitCountAMAF(move);
            final var scoreAMAF = root.getScoreSumAMAF(move, this.player) / visitCountAMAF;

            analysisReport += String.format(", AMAF visits: %d, AMAF score: %f", visitCountAMAF, scoreAMAF);
        }

        if ((this.backpropagationFlags & BackpropagationFlags.SCORE_BOUNDS) != 0) {
            if (this.lastSelectedNode.isSolved(this.player)) {
                analysisReport = analysisReportBase
                        + String.format(
                                ", solved node with score %f", this.lastSelectedNode.getPessimisticScore(this.player));

                if (this.lastSelectedNode.isWin(this.player)) {
                    analysisReport += " (win)";
                } else if (this.lastSelectedNode.isLoss(this.player)) {
                    analysisReport += " (loss)";
                }
            } else {
                analysisReport += String.format(
                        ", pess: %f, opt: %f",
                        this.lastSelectedNode.getPessimisticScore(this.player),
                        this.lastSelectedNode.getOptimisticScore(this.player));
            }
        }

        analysisReport += "}";
        return analysisReport;
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
}
