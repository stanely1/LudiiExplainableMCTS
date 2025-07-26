package mcts.policies.selection;

import mcts.Node;

public interface ISelectionPolicy {
    public String getName();

    public int getBackpropagationFlags();

    public double getNodeValue(Node node);
}
