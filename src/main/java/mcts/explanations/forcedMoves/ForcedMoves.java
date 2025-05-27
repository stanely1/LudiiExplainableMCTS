package mcts.explanations.forcedMoves;

import java.util.ArrayList;
import java.util.List;
import mcts.Node;
import mcts.policies.selection.ISelectionPolicy;

public class ForcedMoves {
    private final List<NodeStats> nodeStats = new ArrayList<>();
    private final List<Node> principalVariation = new ArrayList<>();

    public ForcedMoves(
            final Node root, final Node selectedNode, final ISelectionPolicy selectionPolicy, final int maxDepth) {
        // build Principal Variation
        var node = root;
        int depth = 1;
        while (node != null && !node.isTerminal() && depth <= maxDepth) {
            principalVariation.add(node);

            final int branchingFactor =
                    node.getChildren().size() + node.getUnexpandedMoves().size();
            final int player = node.getPlayer();
            final List<Node> provenBadNodes = node.getChildren().stream()
                    .filter(n -> n.isLoss(player) /*|| n.getDisproofNumber() == 0*/)
                    .toList();

            nodeStats.add(new NodeStats(branchingFactor, provenBadNodes));

            node = node == root ? selectedNode : node.select(selectionPolicy);
            depth++;
        }
    }

    public record NodeStats(int branchingFactor, List<Node> provenBadNodes) {}

    // --------------------------------------------------------------------------------------------------
    public List<NodeStats> getNodeStats() {
        return nodeStats;
    }

    public List<Node> getPrincipalVariation() {
        return principalVariation;
    }
}
