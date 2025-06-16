package mcts.policies.selection;

import mcts.Node;
import mcts.policies.backpropagation.BackpropagationFlags;

public final class PNSFinalMoveSelectionPolicy implements ISelectionPolicy {
    private final ISelectionPolicy wrappedPolicy;
    private int proofPlayer = 0;

    public PNSFinalMoveSelectionPolicy(final ISelectionPolicy wrappedPolicy) {
        this.wrappedPolicy = wrappedPolicy;
    }

    public void setProofPlayer(final int proofPlayer) {
        this.proofPlayer = proofPlayer;
    }

    @Override
    public String getName() {
        return wrappedPolicy.getName() + " with PNS";
    }

    @Override
    public int getBackpropagationFlags() {
        return BackpropagationFlags.PROOF_DISPROOF_NUMBERS | wrappedPolicy.getBackpropagationFlags();
    }

    @Override
    public double getNodeValue(Node node) {
        final var player = node.getParent().getPlayer();

        if (player == proofPlayer && node.getProofNumber() == 0) {
            // select this move if PNS proved win (only for our player)
            return Double.POSITIVE_INFINITY;
        } else {
            return wrappedPolicy.getNodeValue(node);
        }
    }
}
