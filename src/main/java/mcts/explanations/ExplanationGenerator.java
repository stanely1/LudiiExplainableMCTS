package mcts.explanations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import mcts.ActionStats;
import mcts.Node;
import mcts.policies.selection.ISelectionPolicy;
import other.action.Action;
import other.move.Move;
import search.mcts.MCTS.MoveKey;
import search.mcts.MCTS.NGramMoveKey;

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
        Function<Move, String> moveToString = move -> move.actions().stream()
                .filter(Action::isDecision)
                .findFirst()
                .map(a -> a.toTurnFormat(root.getContext(), true))
                .orElse(null);

        String explanation = String.format("Selected move: %s.\n", moveToString.apply(selectedMove));

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
                    equalMoveStrings.add(moveToString.apply(childNode.getMoveFromParent()));
                } else if (childScore / selectedMoveScore > 0.75) {
                    slightlyWorseMoveStrings.add(moveToString.apply(childNode.getMoveFromParent()));
                } else {
                    muchWorseMoveStrings.add(moveToString.apply(childNode.getMoveFromParent()));
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

        // maybe solver can tell us that all moves are loss, except from the one we chose (rare event?)

        // Score Bounds (Solver)
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
                    explanation += String.format("we will play %s, ", moveToString.apply(nextNode.getMoveFromParent()));
                } else {
                    explanation += String.format(
                            "player %d will most likely play %s, ",
                            currentPlayer, moveToString.apply(nextNode.getMoveFromParent()));
                }

                explanation += "then ";
                node = nextNode;
            }

            explanation += String.format("the game ends with a %s.", gameResult);
        }

        // MAST
        final var moveKey = new MoveKey(selectedMove, 0);
        if (globalActionStats.containsKey(moveKey)) {
            final var moveStats = globalActionStats.get(moveKey);
            if (moveStats.scoreSums[this.player] / moveStats.visitCount > 0.25) {
                if (!explanation.equals("")) explanation += " ";
                explanation += "This move generally performs well, regardless of when it is played.";
            }
        }

        // NST
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
                    if (!explanation.equals("")) explanation += " ";

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

        // AMAF (GRAVE)
        final var visitCountAMAF = root.getVisitCountAMAF(selectedMove);
        if (visitCountAMAF > 0) {
            final var scoreAMAF = root.getScoreSumAMAF(selectedMove, this.player) / visitCountAMAF;
            if (scoreAMAF > 0.5) {
                if (!explanation.equals("")) explanation += " ";
                explanation += "This move tends to perform well in game phases that follow the current state.";
            }
        }

        if (explanation.equals("")) {
            explanation = "This move was selected because it is currently the best available option.";
        }

        return explanation;
    }
}
