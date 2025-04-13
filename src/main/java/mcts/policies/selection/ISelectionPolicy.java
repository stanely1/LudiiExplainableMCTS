package mcts.policies.selection;

import mcts.Node;

public interface ISelectionPolicy {
    // TODO:
    // maybe do all selection here (i.e. select best child, not only compute node value) (?)
    // it's done that way in Ludii
    // public Node select(Node node);

    // TODO: INode ???
    public double getNodeValue(Node node);
}
