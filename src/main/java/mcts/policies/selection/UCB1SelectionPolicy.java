package mcts.policies.selection;

import mcts.Node;

public final class UCB1SelectionPolicy implements ISelectionPolicy {
    @Override
    public double getNodeValue(Node node) {
        // final double twoParentLog = 2.0 * Math.log(Math.max(1, this.visitCount));
        // final int currentPlayerID = node.context.state().mover();

        // final double exploit = node.scoreSums[currentPlayerID] / node.visitCount;
        // final double explore = Math.sqrt(twoParentLog / node.visitCount);
        // final double ucb1Value = exploit + explore;

        // return ucb1Value;
        return 0;
    }
}
