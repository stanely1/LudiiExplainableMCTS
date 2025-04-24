package mcts.policies.selection;

import mcts.Node;

public final class GraveSelectionPolicy implements ISelectionPolicy {
    private final double BIAS;
    private final int ref;

    public GraveSelectionPolicy() {
        this.BIAS = 1e-6;
        this.ref = 100;
    }

    public GraveSelectionPolicy(final double bias, final int ref) {
        this.BIAS = bias;
        this.ref = ref;
    }

    @Override
    public String getName() {
        String name = String.format("GRAVE with bias: %f, ref: %d", BIAS, ref);
        if (ref == 0) {
            name += " (RAVE)";
        }
        return name;
    }

    @Override
    public double getNodeValue(Node node) {
        final var parentNode = node.getParent();
        final var currentPlayerID = parentNode.getContext().state().mover();

        // if (refNode == null || node.getVisitCount() > ref) {
        //     System.err.println("ref node set with visits count: " + node.getVisitCount());
        //     refNode = node;
        // }

        Node refNode = node;
        // while (refNode.getParent() != null && refNode.getVisitCount() <= ref) {
        //     refNode = refNode.getParent();
        // }

        final double w = node.getScoreSum(currentPlayerID);
        final double p = node.getVisitCount();

        final double wa = refNode.getScoreAMAF(currentPlayerID);
        final double pa = refNode.getVisitCountAMAF();

        // β formula from Tristan Cazenave's GRAVE paper
        final double beta = pa / (pa + p + BIAS * pa * p);

        // Complete GRAVE value: blend per‑node and AMAF means by β
        return (1.0 - beta) * (w / p) + beta * (wa / pa);
    }
}
