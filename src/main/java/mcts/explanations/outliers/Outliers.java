package mcts.explanations.outliers;

import java.util.*;
import java.util.function.Function;
import mcts.Node;

public class Outliers {
    // ---------------------------------------------------------------------------------------------------------
    // all nodes sorted in descending order by the score
    private final List<Node> sortedNodes;

    // ---------------------------------------------------------------------------------------------------------
    // score relative to selected node
    private final List<Node> equalNodes = new ArrayList<>();

    private final List<Node> slightlyWorseNodes = new ArrayList<>();
    private final List<Node> muchWorseNodes = new ArrayList<>();

    private final List<Node> slightlyBetterNodes = new ArrayList<>();
    private final List<Node> muchBetterNodes = new ArrayList<>();

    // ---------------------------------------------------------------------------------------------------------
    // absolute score
    private final List<Node> neutralNodes = new ArrayList<>();

    private final List<Node> badNodes = new ArrayList<>();
    private final List<Node> veryBadNodes = new ArrayList<>();

    private final List<Node> goodNodes = new ArrayList<>();
    private final List<Node> veryGoodNodes = new ArrayList<>();

    // ---------------------------------------------------------------------------------------------------------
    // mapping category to nodes
    private final Map<String, List<Node>> outliersMap = new HashMap<>();

    public Outliers(Node root, Node selectedNode, Function<Node, Double> nodeRankFunction) {
        this(root, selectedNode, nodeRankFunction, OutliersThresholds.defaultThresholds());
    }

    public Outliers(
            Node root, Node selectedNode, Function<Node, Double> nodeRankFunction, OutliersThresholds thresholds) {
        double selectedRank = nodeRankFunction.apply(selectedNode);
        sortedNodes = root.getChildren().stream()
                .sorted((x, y) -> Double.compare(nodeRankFunction.apply(y), nodeRankFunction.apply(x)))
                .toList();

        for (Node c : sortedNodes) {
            double tempRank = nodeRankFunction.apply(c);

            // populate relative lists
            double diff = Math.abs(tempRank - selectedRank);
            if (diff <= thresholds.getEpsilon()) {
                equalNodes.add(c);

            } else if (diff < thresholds.getSlightDiff()) {
                if (tempRank > selectedRank) {
                    slightlyBetterNodes.add(c);
                } else {
                    slightlyWorseNodes.add(c);
                }

            } else if (tempRank > selectedRank) {
                muchBetterNodes.add(c);

            } else {
                muchWorseNodes.add(c);
            }

            // ---------------------------------------------------------------------------------------------------------
            // populate absolute lists
            double absVal = Math.abs(tempRank);
            if (absVal <= thresholds.getLowAbsVal()) {
                neutralNodes.add(c);

            } else if (absVal < thresholds.getHighAbsVal()) {
                if (tempRank > 0.0) {
                    goodNodes.add(c);
                } else {
                    badNodes.add(c);
                }

            } else if (tempRank > 0.0) {
                veryGoodNodes.add(c);

            } else {
                veryBadNodes.add(c);
            }
        }
        populateMap();
    }

    private void populateMap() {
        outliersMap.put("equal", equalNodes);
        outliersMap.put("slightly worse", slightlyWorseNodes);
        outliersMap.put("much worse", muchWorseNodes);
        outliersMap.put("slightly better", slightlyBetterNodes);
        outliersMap.put("much better", muchBetterNodes);
        outliersMap.put("neutral", neutralNodes);
        outliersMap.put("bad", badNodes);
        outliersMap.put("very bad", veryBadNodes);
        outliersMap.put("good", goodNodes);
        outliersMap.put("very good", veryGoodNodes);
    }

    public List<Node> getSortedNodes() {
        return sortedNodes;
    }

    public Map<String, List<Node>> getOutliersMap() {
        return Collections.unmodifiableMap(outliersMap);
    }

    public List<Node> get(String category) {
        return outliersMap.getOrDefault(category, Collections.emptyList());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Relative getters
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

    // ---------------------------------------------------------------------------------------------------------
    // Absolute getters
    public List<Node> getNeutralNodes() {
        return neutralNodes;
    }

    public List<Node> getBadNodes() {
        return badNodes;
    }

    public List<Node> getVeryBadNodes() {
        return veryBadNodes;
    }

    public List<Node> getGoodNodes() {
        return goodNodes;
    }

    public List<Node> getVeryGoodNodes() {
        return veryGoodNodes;
    }
}
