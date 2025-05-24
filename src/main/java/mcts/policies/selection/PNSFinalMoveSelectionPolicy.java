package mcts.policies.selection;

import mcts.Node;
import mcts.policies.backpropagation.BackpropagationFlags;

public final class PNSFinalMoveSelectionPolicy implements ISelectionPolicy {
    private final ISelectionPolicy wrappedPolicy;

    public PNSFinalMoveSelectionPolicy(final ISelectionPolicy wrappedPolicy) {
        this.wrappedPolicy = wrappedPolicy;
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
        if (node.getProofNumber() == 0) {
            // select this move if PNS proved win
            return Double.POSITIVE_INFINITY;
            // } else if (node.getDisproofNumber() == 0) {
            //     // NOT SURE IF THIS IS CORRECT
            //     // don't select disproved moves
            //     return Double.NEGATIVE_INFINITY;
        } else {
            return wrappedPolicy.getNodeValue(node);
        }
    }
}
