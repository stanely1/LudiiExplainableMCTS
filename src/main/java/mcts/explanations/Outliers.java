package mcts.explanations;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mcts.Node;

public class Outliers {

    private final List<Node> equalNodes = new ArrayList<>();
    private final List<Node> slightlyWorseNodes = new ArrayList<>();
    private final List<Node> muchWorseNodes = new ArrayList<>();

    private final List<Node> slightlyBetterNodes = new ArrayList<>();
    private final List<Node> muchBetterNodes = new ArrayList<>();

    public Outliers(final Node root, final Node selectedNode, final Function<Node, Double> nodeRankFunction) {

        double selectedRank = nodeRankFunction.apply(selectedNode);
        List<Node> children = root.getChildren();
        for (Node c : children) {
            var tempRank = nodeRankFunction.apply(c);

            // chyba absolutna roznica ma wiekszy sens, zakladamy ze wartosci z przedzialu [-1, 1]
            if (Math.abs(tempRank - selectedRank) <= 1e-8) {
                equalNodes.add(c);
            } else if (Math.abs(tempRank - selectedRank) < 0.1) {
                // 5% = 0.1
                if (tempRank > selectedRank) slightlyBetterNodes.add(c);
                else slightlyWorseNodes.add(c);

            } else if (tempRank > selectedRank) {
                muchBetterNodes.add(c);
            } else {
                muchWorseNodes.add(c);
            }
        }
    }

    public List<Node> getEqualNodes() {
        return equalNodes;
    }

    public List<Node> getSlightlyWorseNodes() {
        return slightlyWorseNodes;
    }

    public List<Node> getMuchWorseNodes() {
        return muchWorseNodes;
    }

    public List<Node> getSlightlyBetterNodes() {
        return slightlyBetterNodes;
    }

    public List<Node> getMuchBetterNodes() {
        return muchBetterNodes;
    }
}
