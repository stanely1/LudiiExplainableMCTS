package mcts.policies.selection;

import mcts.Node;

public final class RaveSelectionPolicy implements ISelectionPolicy {
    private final double BIAS;

    public RaveSelectionPolicy() {
        this.BIAS = 1e-6;
    }

    public RaveSelectionPolicy(final double bias) {
        this.BIAS = bias;
    }

    @Override
    public String getName() {
        return "GRAVE";
    }

    @Override
    public double getNodeValue(Node node) {
        final var parentNode = node.getParent();
        final var currentPlayerID = parentNode.getContext().state().mover();

        final double w = node.getScoreSum(currentPlayerID);
        final double p = node.getVisitCount();

        final double wa = node.getScoreAMAF(currentPlayerID);
        final double pa = node.getVisitCountAMAF();

        // β formula from Tristan Cazenave's GRAVE paper
        final double beta = pa / (pa + p + BIAS * pa * p);

        // Complete GRAVE value: blend per‑node and AMAF means by β
        return (1.0 - beta) * (w / p) + beta * (wa / pa);
    }
}
