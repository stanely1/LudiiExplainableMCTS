package mcts.policies.selection;

import mcts.Node;
import mcts.policies.backpropagation.BackpropagationFlags;

public final class ScoreBoundedFinalMoveSelectionPolicy implements ISelectionPolicy {
    private final ISelectionPolicy wrappedPolicy;

    public ScoreBoundedFinalMoveSelectionPolicy(final ISelectionPolicy wrappedPolicy) {
        this.wrappedPolicy = wrappedPolicy;
    }

    @Override
    public String getName() {
        return "Score Bounded " + wrappedPolicy.getName();
    }

    @Override
    public int getBackpropagationFlags() {
        return BackpropagationFlags.SCORE_BOUNDS | wrappedPolicy.getBackpropagationFlags();
    }

    @Override
    public double getNodeValue(Node node) {
        final var parentNode = node.getParent();
        final var player = parentNode.getPlayer();

        if (node.isLoss(player)) {
            // don't select losing move
            return Double.NEGATIVE_INFINITY;
        } else if (node.isWin(player)) {
            // always select winning move
            return Double.POSITIVE_INFINITY;
        } else {
            return wrappedPolicy.getNodeValue(node);
        }
    }
}
