package mcts.policies.selection;

import mcts.Node;

public final class MostVisitedSelectionPolicy implements ISelectionPolicy {
    @Override
    public double getNodeValue(Node node) {
        return node.getVisitCount();
    }
}
