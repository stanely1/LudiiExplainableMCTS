package mcts.policies.selection;

import mcts.Node;

// "Robust Child" policy
public final class MostVisitedSelectionPolicy implements ISelectionPolicy {
    @Override
    public String getName() {
        return "Robust Child";
    }

    @Override
    public int getBackpropagationFlags() {
        return 0;
    }

    @Override
    public double getNodeValue(Node node) {
        return node.getVisitCount();
    }
}
