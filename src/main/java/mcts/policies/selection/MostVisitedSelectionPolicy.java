package mcts.policies.selection;

import mcts.Node;

// "Robust Child" policy
public final class MostVisitedSelectionPolicy implements ISelectionPolicy {
    @Override
    public double getNodeValue(Node node) {
        return node.getVisitCount();
    }
}
