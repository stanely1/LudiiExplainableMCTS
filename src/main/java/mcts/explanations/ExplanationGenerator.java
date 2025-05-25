package mcts.explanations;

import java.util.*;
import java.util.function.Function;
import mcts.*;
import mcts.explanations.outliers.Outliers;
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

    private final Move selectedMove;
    private final int player;

    public ExplanationGenerator(
            final Node root,
            final Node selectedNode,
            final Map<MoveKey, ActionStats> globalActionStats,
            final Map<NGramMoveKey, ActionStats> globalNGramStats,
            final int maxNGramLength,
            final ISelectionPolicy finalMoveSelectionPolicy) {
        this.root = root;
        this.selectedNode = selectedNode;
        this.globalActionStats = globalActionStats;
        this.globalNGramStats = globalNGramStats;
        this.maxNGramLength = maxNGramLength;
        this.finalMoveSelectionPolicy = finalMoveSelectionPolicy;

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

        Function<Node, Double> getNodeAverageEval = _node -> _node.getScoreSum(player) / _node.getVisitCount();
        String becauseNodeAverage = "Because node average score";
        explanation += getOutliersExplanation(getNodeAverageEval, becauseNodeAverage);

        return explanation;
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
            explanation += "Since there was only one move available, it was the one chosen.";
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

    private String getOutliersExplanation(Function<Node, Double> evalNode, String becauseString) {
        String outliersExplanation = "";
        var outliers = new Outliers(root, selectedNode, evalNode);

        // 1. Dominating solution, One solution that outlies the others
        if (outliers.getVeryGoodNodes().size() == 1) {
            if (outliers.getVeryGoodNodes().contains(selectedNode)) {
                outliersExplanation += "This selected move outlies other moves " + becauseString + ". ";
            } else {
                outliersExplanation += "There was one very good move, but it wasn't selected. " + becauseString + ". ";
            }
        }

        // 2. a few - we have a few of these, but they are a minority among all available
        if (outliers.getVeryGoodNodes().size() < root.getChildren().size() / 10
                && !outliers.getVeryGoodNodes().isEmpty()) {
            outliersExplanation += String.format(
                    "There is %d very good moves. ", outliers.getVeryGoodNodes().size());

            if (outliers.getVeryGoodNodes().contains(selectedNode)) {
                outliersExplanation += String.format("Selected move was among best moves. ");
            } else {
                outliersExplanation += String.format("Selected move was not one of them. ");
            }
        }

        // 3. None - there are no outliers, everything is similar; three variants: everything is good,
        // everything is
        // average, everything is bad
        // if (outliers.getVeryGoodNodes().size() < root.getChildren().size() / 10) {
        //     outliersExplanation += String.format(
        //             "There is %d very good moves. ", outliers.getVeryGoodNodes().size());

        //     if (outliers.getVeryGoodNodes().contains(selectedNode)) {
        //         outliersExplanation += String.format("Move was among %d best moves. ");
        //     } else {
        //         outliersExplanation += String.format("Selected move was not one of them.");
        //     }
        // }

        // 4. All good except or a few - Overall it's OK, but some are significantly worse.
        // if (outliers.getVeryGoodNodes().size() < root.getChildren().size() / 10) {
        //     outliersExplanation += String.format(
        //             "There is %d very good moves. ", outliers.getVeryGoodNodes().size());

        //     if (outliers.getVeryGoodNodes().contains(selectedNode)) {
        //         outliersExplanation += String.format("Move was among %d best moves. ");
        //     } else {
        //         outliersExplanation += String.format("Selected move was not one of them.");
        //     }
        // }

        return outliersExplanation;
    }
}
