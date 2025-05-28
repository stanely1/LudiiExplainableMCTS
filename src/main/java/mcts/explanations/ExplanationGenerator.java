package mcts.explanations;

import java.util.*;
import java.util.function.Function;
import mcts.*;
import mcts.explanations.forcedMoves.ForcedMoves;
import mcts.explanations.outliers.Outliers;
import mcts.policies.backpropagation.BackpropagationFlags;
import mcts.policies.selection.ISelectionPolicy;
import other.action.Action;
import other.move.Move;
import search.mcts.MCTS.*;

public class ExplanationGenerator {
    private final Node root;
    private final Node selectedNode;

    private final Map<MoveKey, ActionStats> globalActionStats;
    private final Map<NGramMoveKey, ActionStats> globalNGramStats;
    private final int maxNGramLength;

    private final ISelectionPolicy finalMoveSelectionPolicy;

    private final int backpropagationFlags;
    private final double averageBranchingFactor;

    private final Move selectedMove;
    private final int player;

    public ExplanationGenerator(
            final Node root,
            final Node selectedNode,
            final Map<MoveKey, ActionStats> globalActionStats,
            final Map<NGramMoveKey, ActionStats> globalNGramStats,
            final int maxNGramLength,
            final ISelectionPolicy finalMoveSelectionPolicy,
            final int backpropagationFlags,
            final double averageBranchingFactor) {
        this.root = root;
        this.selectedNode = selectedNode;
        this.globalActionStats = globalActionStats;
        this.globalNGramStats = globalNGramStats;
        this.maxNGramLength = maxNGramLength;
        this.finalMoveSelectionPolicy = finalMoveSelectionPolicy;
        this.backpropagationFlags = backpropagationFlags;
        this.averageBranchingFactor = averageBranchingFactor;

        this.selectedMove = this.selectedNode.getMoveFromParent();
        this.player = root.getPlayer();
    }

    public String generateExplanation() {
        String explanation = String.format("Selected move: %s.\n", moveToString(selectedMove));

        explanation += basicExplanation();
        explanation += scoreBoundsExplanation();
        explanation += mastExplanation();
        explanation += nstExplanation();
        explanation += graveExplanation();

        // -------------------------------------------------------------------------------------------------------------------------------------------
        // Outliers 🥰
        //
        // Relative score with respect to the *selected move*
        // getEqualNodes, getSlightlyWorseNodes, getMuchWorseNodes, getSlightlyBetterNodes,
        // getMuchBetterNodes
        //
        //
        // Absolute score
        // getNeutralNodes, getBadNodes, getVeryBadNodes, getGoodNodes, getVeryGoodNodes
        // -------------------------------------------------------------------------------------------------------------------------------------------
        //         Warianty sytuacji (outliers):
        // 1. Dominating solution, One solution that outlies the others
        // 2. A few - we have a few of these, but they are a minority among all available
        // 3. None - there are no outliers, everything is similar; three variants: everything is good,
        // everything is
        // average, everything is bad
        // 4. All good except or a few - Overall it's OK, but some are significantly worse.
        // -------------------------------------------------------------------------------------------------------------------------------------------

        explanation += "\n";

        Function<Node, Double> getNodeAverageEval = _node -> _node.getScoreSum(player) / _node.getVisitCount();
        explanation += " " + getOutliersExplanation(getNodeAverageEval, "average score");

        if ((backpropagationFlags & BackpropagationFlags.AMAF_STATS) != 0) {
            Function<Node, Double> getNodeGraveEval = _node -> root.getScoreSumAMAF(_node.getMoveFromParent(), player)
                    / root.getVisitCountAMAF(_node.getMoveFromParent());
            explanation += " " + getOutliersExplanation(getNodeGraveEval, "AMAF score");
        }

        if ((backpropagationFlags & BackpropagationFlags.GLOBAL_ACTION_STATS) != 0) {
            Function<Node, Double> getMastEval = _node -> {
                final var aStats = globalActionStats.get(new MoveKey(_node.getMoveFromParent(), 0));
                return aStats.scoreSums[player] / aStats.visitCount;
            };
            explanation += " " + getOutliersExplanation(getMastEval, "MAST score");
        }

        // -------------------------------------------------------------------------------------------------------------------------------------------
        // Forced Moves / Traps / Uncertainty
        //
        // We look deep into the search tree, but to keep things simple we can follow only the principal variation and
        // note what the situation looks like:
        //

        explanation += "\n";
        explanation += getForcedMovesExplanation(selectedNode, "the selected move", 0);

        // -------------------------------------------------------------------------------------------------------------------------------------------
        //
        // TODO: Traps - forced moves with different selectedNode

        for (Node wantedNode : root.getChildren()) {
            if (wantedNode == selectedNode) continue;
            explanation += getForcedMovesExplanation(wantedNode, moveToString(wantedNode.getMoveFromParent()), 1);
        }

        // -------------------------------------------------------------------------------------------------------------------------------------------
        return explanation.replaceAll("\\s{2,}", " ");
    }

    private String moveToString(final Move move) {
        return move.actions().stream()
                .filter(Action::isDecision)
                .findFirst()
                .map(a -> a.toTurnFormat(root.getContext(), true))
                .orElse(null);
    }

    private String basicExplanation() {
        String explanation = "";

        // comparison with other moves
        if (root.getChildren().size() == 1) {
            explanation += "Since there was only one move available, it was the only one chosen.";
        } else {
            double selectedMoveScore = this.finalMoveSelectionPolicy.getNodeValue(selectedNode);

            List<String> equalMoveStrings = new ArrayList<>();
            List<String> slightlyWorseMoveStrings = new ArrayList<>();
            List<String> muchWorseMoveStrings = new ArrayList<>();

            for (final var childNode : root.getChildren()) {
                if (childNode == selectedNode) {
                    continue;
                }

                final var childScore = this.finalMoveSelectionPolicy.getNodeValue(childNode);
                if (childScore == selectedMoveScore) {
                    equalMoveStrings.add(moveToString(childNode.getMoveFromParent()));
                } else if (childScore / selectedMoveScore > 0.75) {
                    slightlyWorseMoveStrings.add(moveToString(childNode.getMoveFromParent()));
                } else {
                    muchWorseMoveStrings.add(moveToString(childNode.getMoveFromParent()));
                }
            }

            // TODO: print moves only if there is not too much of them?
            List<String> sentencesToPrint = new ArrayList<>();
            if (!equalMoveStrings.isEmpty()) {
                sentencesToPrint.add(String.format(
                        "The selected move has the same estimated value as following moves: %s. It was chosen randomly from among them.",
                        String.join(", ", equalMoveStrings)));
            }
            if (!slightlyWorseMoveStrings.isEmpty()) {
                sentencesToPrint.add(String.format(
                        "The following moves are considered slightly worse than the one chosen: %s.",
                        String.join(", ", slightlyWorseMoveStrings)));
            }
            if (!muchWorseMoveStrings.isEmpty()) {
                sentencesToPrint.add("The selected move was significantly better than all other options.");
            }

            explanation += String.join(" ", sentencesToPrint);
        }

        return explanation;
    }

    private String scoreBoundsExplanation() {
        String explanation = "";

        if (selectedNode.isSolved(player)) {
            explanation += " ";

            String gameResult;
            if (selectedNode.isWin(player)) {
                gameResult = "win";
                explanation += "This move leads to a state where we win regardless of the opponent's actions.";
            } else if (selectedNode.isLoss(player)) {
                gameResult = "loss";
                explanation +=
                        "This move leads to a loss, assuming the opponent plays optimally. It was selected because all available moves result in a loss.";
            } else {
                gameResult = "draw";
                explanation += "This move leads to a draw.";
            }

            // print PV
            explanation += " After we play this move ";
            var node = selectedNode;
            while (!node.isTerminal()) {
                final var nextNode = node.select(this.finalMoveSelectionPolicy);
                final var currentPlayer = node.getPlayer();
                if (currentPlayer == this.player) {
                    explanation += String.format("we will play %s, ", moveToString(nextNode.getMoveFromParent()));
                } else {
                    explanation += String.format(
                            "player %d will most likely play %s, ",
                            currentPlayer, moveToString(nextNode.getMoveFromParent()));
                }

                explanation += "then ";
                node = nextNode;
            }

            explanation += String.format("the game ends with a %s.", gameResult);
        }

        return explanation;
    }

    private String mastExplanation() {
        String explanation = "";

        final var moveKey = new MoveKey(selectedMove, 0);
        if (globalActionStats.containsKey(moveKey)) {
            final var moveStats = globalActionStats.get(moveKey);
            if (moveStats.scoreSums[this.player] / moveStats.visitCount > 0.25) {
                explanation += " This move generally performs well, regardless of when it is played.";
            }
        }
        return explanation;
    }

    private String nstExplanation() {
        String explanation = "";

        final List<Move> reverseActionSequence = new ArrayList<>();
        reverseActionSequence.add(selectedMove);
        final var reverseTrialIterator = root.getContext().trial().reverseMoveIterator();

        for (var n = 1; n <= maxNGramLength; n++) {
            final var nGram = new Move[n];
            for (var i = 0; i < n; i++) {
                nGram[i] = reverseActionSequence.get(n - i - 1);
            }
            final var nGramKey = new NGramMoveKey(nGram, 0);

            if (globalNGramStats.containsKey(nGramKey)) {
                final var nGramStats = globalNGramStats.get(nGramKey);
                if (nGramStats.scoreSums[this.player] / nGramStats.visitCount > 0.25) {
                    explanation += " ";

                    switch (n) {
                        case 1 -> explanation += "This move generally performs well, regardless of when it is played.";
                        case 2 -> explanation +=
                                "This move generally performs well when played after the previous move.";
                        default -> explanation += String.format(
                                "This move generally performs well when played after a sequence of %d preceding moves.",
                                n - 1);
                    }
                }
            } else {
                break;
            }

            if (!reverseTrialIterator.hasNext()) {
                break;
            }
            reverseActionSequence.add(reverseTrialIterator.next());
        }

        return explanation;
    }

    private String graveExplanation() {
        String explanation = "";

        final var visitCountAMAF = root.getVisitCountAMAF(selectedMove);
        if (visitCountAMAF > 0) {
            final var scoreAMAF = root.getScoreSumAMAF(selectedMove, this.player) / visitCountAMAF;
            if (scoreAMAF > 0.5) {
                explanation += " This move tends to perform well in game phases that follow the current state.";
            }
        }
        return explanation;
    }

    private String getOutliersExplanation(Function<Node, Double> evalNode, String criteria) {
        List<String> messages = new ArrayList<>();
        var outliers = new Outliers(root, selectedNode, evalNode);
        int totalChildren = root.getChildren().size();

        // Print the category where the selected move belongs
        outliers.getOutliersMap().entrySet().stream()
                .filter(e -> !e.getKey().equals("equal") && e.getValue().contains(selectedNode))
                .findFirst()
                .ifPresent(e -> {
                    messages.add(String.format(
                            "The selected node is considered %s by the %s criteria.", e.getKey(), criteria));
                });

        // if there is only 1 move, no further explanations needed
        if (totalChildren == 1) {
            return String.join(" ", messages);
        }

        // TODO: in code below, generalize similar cases and extract them into functions

        // sibling comparison
        final var slightlyWorseNodes = outliers.get("slightly worse");
        final var muchWorseNodes = outliers.get("much worse");
        final var totalWorse = slightlyWorseNodes.size() + muchWorseNodes.size();

        final var slightlyBetterNodes = outliers.get("slightly better");
        final var muchBetterNodes = outliers.get("much better");
        final var totalBetter = slightlyBetterNodes.size() + muchBetterNodes.size();

        final var equalNodes = outliers.get("equal");

        // all moves were worse than the selected
        if (totalWorse > 0 && totalWorse == totalChildren - 1) {
            messages.add(
                    String.format("All other moves were worse than the selected one by the %s criteria.", criteria));
        }
        // majority (but not all) worse than selected
        else if (totalWorse > (totalChildren - 1) * 8 / 10) {
            messages.add(String.format(
                    "The majority of other moves (%d out of %d) were worse than the selected one by the %s criteria.",
                    totalWorse, totalChildren - 1, criteria));
            if (equalNodes.size() > 1) {
                messages.add(String.format(
                        "%d out of remaining moves were considered equal to the selected.", equalNodes.size() - 1));
            }
            if (totalBetter > 0) {
                messages.add("The remaining nodes were better.");
            }
        }

        // all moves were better than the selected
        if (totalBetter > 0 && totalBetter == totalChildren - 1) {
            messages.add(
                    String.format("All other moves were better than the selected one by the %s criteria.", criteria));
        }
        // majority (but not all) better than selected
        else if (totalBetter > (totalChildren - 1) * 8 / 10) {
            messages.add(String.format(
                    "The majority of other moves (%d out of %d) were better than the selected one by the %s criteria.",
                    totalBetter, totalChildren - 1, criteria));
            if (equalNodes.size() > 1) {
                messages.add(String.format(
                        "%d out of remaining moves were considered equal to the selected.", equalNodes.size() - 1));
            }
            if (totalWorse > 0) {
                messages.add("The remaining nodes were worse.");
            }
        }

        // all moves equal
        if (equalNodes.size() == totalChildren) {
            messages.add(String.format("All moves are equal by the %s criteria.", criteria));
        }
        // majority (but not all) equal
        else if (equalNodes.size() - 1 > (totalChildren - 1) * 8 / 10) {
            messages.add(String.format(
                    "The majority of other moves (%d out of %d) were equal to the selected one by the %s criteria.",
                    equalNodes.size() - 1, totalChildren - 1, criteria));
            if (totalBetter > 0) {
                messages.add(String.format(
                        "%d out of remaining moves were considered better than the selected.", totalBetter));
            }
            if (totalWorse > 0) {
                messages.add("The remaining nodes were worse.");
            }
        }
        // selected node was one of a few equal nodes
        else if (equalNodes.size() > 1 && equalNodes.size() < totalChildren / 10) {
            messages.add(String.format(
                    "The selected move was one of %d moves that are equal by the %s criteria.",
                    equalNodes.size(), criteria));
            messages.add(String.format("They were a minority among all %d available moves.", totalChildren));
        }

        // ?? ?? ??
        // print number of nodes in each category

        messages.add(String.format("By the %s criteria there are ", criteria));
        messages.add(formatRelativeCategory(outliers, "equal"));
        messages.add(formatRelativeCategory(outliers, "much better"));
        messages.add(formatRelativeCategory(outliers, "slightly better"));
        messages.add(formatRelativeCategory(outliers, "slightly worse"));
        messages.add(formatRelativeCategory(outliers, "much worse"));

        messages.set(messages.size() - 1, messages.get(messages.size() - 1).replaceAll(",$", "."));

        // TODO: improve explanations below
        // - better natural language templates
        // - if selected node not among 'good' nodes, add explanation why it happened
        // - maybe print those 'very good' moves

        // 1. Dominating solution, One solution that outlies the others
        var veryGoodNodes = outliers.get("very good");
        var goodNodes = outliers.get("good");

        if (veryGoodNodes.size() == 1) {
            messages.add(formatCategory(
                    "very good", veryGoodNodes.size(), totalChildren, veryGoodNodes.contains(selectedNode), criteria));
        } else if (goodNodes.size() == 1 && veryGoodNodes.isEmpty()) {
            messages.add(formatCategory(
                    "good", goodNodes.size(), totalChildren, goodNodes.contains(selectedNode), criteria));
        }
        // 2. a few - we have a few of these, but they are a minority among all available
        else if (veryGoodNodes.size() < totalChildren / 10 && !veryGoodNodes.isEmpty()) {
            messages.add(formatCategory(
                    "very good", veryGoodNodes.size(), totalChildren, veryGoodNodes.contains(selectedNode), criteria));
        } else if (goodNodes.size() < totalChildren / 10 && !goodNodes.isEmpty() && veryGoodNodes.isEmpty()) {
            messages.add(formatCategory(
                    "good", goodNodes.size(), totalChildren, goodNodes.contains(selectedNode), criteria));
        }

        // 3. None - there are no outliers, everything is similar; three variants: everything is good,
        for (Map.Entry<String, List<Node>> entry : outliers.getOutliersMap().entrySet()) {
            String category = entry.getKey();
            List<Node> nodes = entry.getValue();

            if (!category.equals("equal") && nodes.size() == totalChildren) {
                messages.add(String.format("All nodes are in %s category by the %s criteria.", category, criteria));
                // break;
            }
        }

        // 4. All good except or a few - Overall it's OK, but some are significantly worse.
        int totalGoodAndVeryGood = goodNodes.size() + veryGoodNodes.size();
        if (totalGoodAndVeryGood < totalChildren && totalGoodAndVeryGood > totalChildren * 8 / 10) {
            messages.add(formatCategory(
                    "good and very good",
                    totalGoodAndVeryGood,
                    totalChildren,
                    goodNodes.contains(selectedNode) || veryGoodNodes.contains(selectedNode),
                    criteria));

            var neutralNodes = outliers.get("neutral");
            if (!neutralNodes.isEmpty()) {
                messages.add(String.format("%d out of remaining moves were considered neutral.", neutralNodes.size()));
            }
            if (!outliers.get("bad").isEmpty() || !outliers.get("veryBad").isEmpty()) {
                messages.add("The remaining nodes were bad or very bad.");
            }
        }

        return String.join(" ", messages);
    }

    private String formatCategory(
            String label, int count, int totalCount, boolean selectedInCategory, String criteria) {
        String template = String.format(
                "There are %d %s moves (out of %d) by the %s criteria; the selected move is %s among them.",
                count, label, totalCount, criteria, selectedInCategory ? "" : "not");
        return template;
    }

    private String formatRelativeCategory(final Outliers outliers, final String category) {
        final var nodes = outliers.get(category);
        if (category.equals("equal") && nodes.size() == 1) {
            return "";
        }
        if (!nodes.isEmpty()) {
            if (nodes.size() > 1) {
                return String.format("%d moves %s,", nodes.size(), category);
            } else {
                return "";
            }
        }
        return "";
    }

    private String getForcedMovesExplanation(Node wantedNode, String wantedNodeString, int startDepth) {
        final List<String> messages = new ArrayList<>();

        int depth = 3;
        final ForcedMoves forcedMoves = new ForcedMoves(root, wantedNode, finalMoveSelectionPolicy, depth);
        depth = Integer.min(depth, forcedMoves.getPrincipalVariation().size());

        final List<String> movesFromPV = new ArrayList<>();
        movesFromPV.add(String.format("After we play %s, ", wantedNodeString));
        for (int i = 2; i < depth; i++) {
            final var node = forcedMoves.getPrincipalVariation().get(i);
            if (node.getParent().getPlayer() == this.player) {
                movesFromPV.add(String.format("we will play %s, then ", moveToString(node.getMoveFromParent())));
            } else {
                movesFromPV.add(String.format(
                        "the opponent will most likely play %s, then ", moveToString(node.getMoveFromParent())));
            }
        }

        String depthString = "";

        // TODO: print PV similar as in scoreBoundExplanation
        for (int i = startDepth; i < depth; i++) {
            final var node = forcedMoves.getPrincipalVariation().get(i);
            final var nodeStats = forcedMoves.getNodeStats().get(i);
            if (i > 0) depthString += movesFromPV.get(i - 1);

            // limited mobility (much less moves than average branching factor)
            if (nodeStats.branchingFactor() < averageBranchingFactor / 8) {
                messages.add(String.format(
                        "%s %s %d available moves, which is significanly less than the estimated average branching factor of the game (%f).",
                        depthString,
                        node.getPlayer() == player ? "we have" : "the opponent has",
                        nodeStats.branchingFactor(),
                        averageBranchingFactor));
                messages.add(String.format(
                        "In this state %s forced to choose from limited number of options.",
                        node.getPlayer() == player ? "we are" : "the opponent is"));
                depthString = "";
            }

            // significant amount of available moves are proven to be bad
            if (nodeStats.provenBadNodes().size() > nodeStats.branchingFactor() / 2) {
                messages.add(String.format(
                        "%s the majority of available moves (%d out of %d) are proven to lead %s to loss.",
                        depthString,
                        nodeStats.provenBadNodes().size(),
                        nodeStats.branchingFactor(),
                        node.getPlayer() == player ? "us" : "the opponent"));
                messages.add(String.format(
                        "Thus in this state %s forced to choose from limited number of options.",
                        node.getPlayer() == player ? "we are" : "the opponent is"));
                depthString = "";
            }
        }

        return String.join(" ", messages);
    }
}
