package mcts.policies.selection;

import mcts.Node;

public interface ISelectionPolicy {
    public double getNodeValue(Node node);
}
