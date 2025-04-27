package mcts.policies.selection;

import mcts.Node;

public final class UCB1SelectionPolicy implements ISelectionPolicy {
    @Override
    public String getName() {
        return "UCB1";
    }

    @Override
    public int getBackpropagationFlags() {
        return 0;
    }

    @Override
    public double getNodeValue(Node node) {
        final var parentNode = node.getParent();
        final var currentPlayerID = parentNode.getContext().state().mover();

        final double twoParentLog = 2.0 * Math.log(Math.max(1, parentNode.getVisitCount()));
        final double exploit = node.getScoreSum(currentPlayerID) / node.getVisitCount();
        final double explore = Math.sqrt(twoParentLog / node.getVisitCount());
        final double ucb1Value = exploit + explore;

        return ucb1Value;
    }
}
