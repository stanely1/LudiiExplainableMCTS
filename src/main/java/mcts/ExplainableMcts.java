package mcts;

import game.Game;
import java.util.List;
import mcts.policies.selection.ISelectionPolicy;
import other.AI;
import other.context.Context;
import other.move.Move;

public class ExplainableMcts extends AI {
    // -------------------------------------------------------------------------
    private int player = -1;
    private String analysisReport;

    private Node root;

    private final ISelectionPolicy selectionPolicy;
    private final ISelectionPolicy finalMoveSelectionPolicy;

    private int lastActionHistorySize = 0;
    private double lastMoveValue = 0.0;
    // -------------------------------------------------------------------------

    public ExplainableMcts(final ISelectionPolicy selectionPolicy, final ISelectionPolicy finalMoveSelectionPolicy) {
        this.friendlyName = "ExplainableMcts";
        this.selectionPolicy = selectionPolicy;
        this.finalMoveSelectionPolicy = finalMoveSelectionPolicy;
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

        while (numIterations < maxIts && System.currentTimeMillis() < stopTime && !wantsInterrupt) {
            Node current = root;

            while (!current.isTerminal() && current.isExpanded()) {
                current = current.select(this.selectionPolicy);
            }

            var newNode = current.expand();

            var utilities = newNode.simulate();
            newNode.propagate(utilities);

            numIterations++;
        }

        final Node selectedNode = root.select(this.finalMoveSelectionPolicy);

        this.lastMoveValue = selectedNode.getScoreSums()[this.player] / selectedNode.getVisitCount();
        this.analysisReport = String.format(
                "[%s] Performed %d iterations, selected node: {visits: %d, score: %f}",
                this.friendlyName, numIterations, selectedNode.getVisitCount(), this.lastMoveValue);

        return selectedNode.getMoveFromParent();
    }

    @Override
    public void initAI(final Game game, final int playerID) {
        this.player = playerID;

        this.analysisReport = null;
        this.root = null;
        this.lastActionHistorySize = 0;
        this.lastMoveValue = 0.0;
    }

    @Override
    public void closeAI() {
        this.player = -1;
        this.analysisReport = null;
        this.root = null;
        this.lastActionHistorySize = 0;
        this.lastMoveValue = 0.0;
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
