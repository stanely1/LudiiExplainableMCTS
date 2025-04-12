package mcts;

import game.Game;
import mcts.policies.selection.ISelectionPolicy;
import other.AI;
import other.context.Context;
import other.move.Move;

public class ExplainableMcts extends AI {
    // -------------------------------------------------------------------------
    private int player = -1;
    private String analysisReport;

    private final ISelectionPolicy selectionPolicy;
    private final ISelectionPolicy finalMoveSelectionPolicy;
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
        Node root = new Node(null, null, context);

        // We'll respect any limitations on max seconds and max iterations (don't care about max depth)
        final long stopTime =
                (maxSeconds > 0.0) ? System.currentTimeMillis() + (long) (maxSeconds * 1000L) : Long.MAX_VALUE;
        final int maxIts = (maxIterations >= 0) ? maxIterations : Integer.MAX_VALUE;

        int numIterations = 0;

        while (numIterations < maxIts && System.currentTimeMillis() < stopTime && !wantsInterrupt) {
            Node current = root;

            while (!current.isTerminal() && current.isExpanded()) {
                current = current.select(this.selectionPolicy);
            }

            var newNode = current.expand();

            var utilities = newNode.simulate();
            Node.propagate(newNode, utilities);

            numIterations++;
        }

        this.analysisReport = "[" + this.friendlyName + "] " + numIterations + " iterations";
        return root.select(this.finalMoveSelectionPolicy).getMoveFromParent();
    }

    @Override
    public void initAI(final Game game, final int playerID) {
        this.player = playerID;
    }

    @Override
    public boolean supportsGame(final Game game) {
        if (game.isStochasticGame()) return false;

        if (!game.isAlternatingMoveGame()) return false;

        return true;
    }

    @Override
    public String generateAnalysisReport() {
        return analysisReport;
    }
}
