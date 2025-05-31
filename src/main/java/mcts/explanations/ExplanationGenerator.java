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
    private final double prevTurnScore;

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
            final double prevTurnScore,
            final Map<MoveKey, ActionStats> globalActionStats,
            final Map<NGramMoveKey, ActionStats> globalNGramStats,
            final int maxNGramLength,
            final ISelectionPolicy finalMoveSelectionPolicy,
            final int backpropagationFlags,
            final double averageBranchingFactor) {
        this.root = root;
        this.selectedNode = selectedNode;
        this.prevTurnScore = prevTurnScore;
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
        // serialize tree nodes
        // String explanation = String.format("Root: %s\n", serializeNode(root));
        String explanation = String.format("Selected node:\n%s\n", serializeNode(selectedNode));
        if (root.getChildren().size() > 1) {
            explanation += "Other nodes:\n";
            for (final var node : root.getChildren()) {
                if (node != selectedNode) {
                    explanation += serializeNode(node) + "\n";
                }
            }
        }

        // Natural language explanation

        Function<Node, Double> getNodeAverageEval =
                _node -> _node.isSolved(player) ? _node.getPessimisticScore(player) : _node.getAverageScore(player);
        final var avgOutliers = new Outliers(root, selectedNode, getNodeAverageEval);

        final double selectedScore = getNodeAverageEval.apply(selectedNode);
        final double absSelectedScore = Math.abs(selectedScore);
        final double selectedProbability = scoreToProbability(selectedScore);

        final boolean isSolved = selectedNode.isSolved(player);

        // general info on available moves
        // -------------------------------------------------------------------------------------------------------------------------
        // -------------------------------------------------------------------------------------------------------------------------
        // -------------------------------------------------------------------------------------------------------------------------
        List<String> moveCategoryStrings = new ArrayList<>();
        if (!avgOutliers.getVeryGoodNodes().isEmpty()) {
            String s;
            final var veryGoodWorstNode = avgOutliers.getVeryGoodNodes().getLast();
            final var veryGoodWorstProbability = scoreToProbability(getNodeAverageEval.apply(veryGoodWorstNode));
            if (veryGoodWorstProbability == 100.0) {
                s = String.format("%s win", veryGoodWorstNode.isWin(player) ? "proven" : "highly likely");
            } else {
                s = String.format("above %.2f%%", veryGoodWorstProbability);
            }

            moveCategoryStrings.add(String.format(
                    "%d with decisive advantage (%s)",
                    avgOutliers.getVeryGoodNodes().size(), s));
        }
        // -------------------------------------------------------------------------------------------------------------------------
        if (!avgOutliers.getGoodNodes().isEmpty()) {
            moveCategoryStrings.add(String.format(
                    "%d with slight advantage (above %.2f%%)",
                    avgOutliers.getGoodNodes().size(),
                    scoreToProbability(
                            getNodeAverageEval.apply(avgOutliers.getGoodNodes().getLast()))));
        }
        // -------------------------------------------------------------------------------------------------------------------------
        if (!avgOutliers.getNeutralNodes().isEmpty()) {
            moveCategoryStrings.add(String.format(
                    "%d balanced (~50%%)", avgOutliers.getNeutralNodes().size()));
        }
        // -------------------------------------------------------------------------------------------------------------------------
        if (!avgOutliers.getBadNodes().isEmpty()) {
            moveCategoryStrings.add(String.format(
                    "%d with slight disadvantage (below %.2f%%)",
                    avgOutliers.getBadNodes().size(),
                    scoreToProbability(
                            getNodeAverageEval.apply(avgOutliers.getBadNodes().getFirst()))));
        }
        // -------------------------------------------------------------------------------------------------------------------------
        if (!avgOutliers.getVeryBadNodes().isEmpty()) {
            String s;
            final var veryBadBestNode = avgOutliers.getVeryBadNodes().get(0);
            final var veryBadBestProbability = scoreToProbability(getNodeAverageEval.apply(veryBadBestNode));
            if (veryBadBestProbability == 0.0) {
                s = String.format("%s loss", veryBadBestNode.isLoss(player) ? "proven" : "highly likely");
            } else {
                s = String.format("below %.2f%%", veryBadBestProbability);
            }

            moveCategoryStrings.add(String.format(
                    "%d with decisive disadvantage (%s)",
                    avgOutliers.getVeryBadNodes().size(), s));
        }
        // -------------------------------------------------------------------------------------------------------------------------
        // -------------------------------------------------------------------------------------------------------------------------
        // -------------------------------------------------------------------------------------------------------------------------

        final int moveCount = root.getChildren().size();
        explanation += String.format(
                "There %s %d move%s available: %s.\n",
                moveCount == 1 ? "is" : "are",
                moveCount,
                moveCount == 1 ? "" : "s",
                String.join(", ", moveCategoryStrings));

        final var sortedAvgNodes = avgOutliers.getSortedNodes();
        final var worstAvgNode = sortedAvgNodes.get(sortedAvgNodes.size() - 1);
        final var bestAvgNode = sortedAvgNodes.get(0);

        // print selected move
        if (!isSolved) {
            explanation += String.format("Selected move: %s.\n", moveToString(selectedMove));
        }

        // worst node is at least good
        if (!isSolved
                && (avgOutliers.getGoodNodes().contains(worstAvgNode)
                        || avgOutliers.getVeryGoodNodes().contains(worstAvgNode))) {
            explanation += String.format(
                    "Our position is generally advantageous (the estimated win probability for the worst of available moves is %.2f%%).\n",
                    scoreToProbability(getNodeAverageEval.apply(worstAvgNode)));
        }
        // best node is at most bad
        else if (!isSolved
                && (avgOutliers.getBadNodes().contains(bestAvgNode)
                        || avgOutliers.getVeryBadNodes().contains(bestAvgNode))) {
            explanation += String.format(
                    "Our position is generally disadvantageous (the estimated win probability for the best of available moves is %.2f%%).\n",
                    scoreToProbability(getNodeAverageEval.apply(bestAvgNode)));
        }
        // general position info
        else if (!isSolved) {
            explanation += String.format(
                    "Our position is %s (estimated win probability: %.2f%%).\n",
                    absSelectedScore < 0.1
                            ? "balanced"
                            : String.format(
                                    "%s %sadvantageous",
                                    absSelectedScore < 0.4 ? "slightly" : "strongly", selectedScore < 0 ? "dis" : ""),
                    selectedProbability);
        }

        if (isSolved) {
            final var pv = getPV(selectedNode);
            String gameResult = selectedNode.isWin(player) ? "win" : selectedNode.isLoss(player) ? "loss" : "draw";

            explanation +=
                    String.format("Selected move, %s, leads to a proven %s ", moveToString(selectedMove), gameResult);

            if (pv.isEmpty()) {
                explanation += "after this move.\n";
            } else if (pv.size() <= 5) {
                explanation += String.format(
                        "in %d turns. After we play this move, the most probable sequence of following moves will be: ",
                        pv.size());
                explanation += String.join(
                                ", ",
                                pv.stream()
                                        .map(v -> moveToString(v.getMoveFromParent()))
                                        .toList()) + ".\n";
            } else {
                explanation += String.format("in %d turns.\n", pv.size());
            }
        }

        final double prevProbability = scoreToProbability(prevTurnScore);
        final double scoreProbabilityChange = Math.abs(selectedProbability - prevProbability);

        // score change over previous turn
        if (scoreProbabilityChange > 10.0 && !isSolved) {
            explanation += String.format(
                    "The overall estimation of our position %s over the previous turn (%.2f%% %s win probability).\n",
                    selectedProbability > prevProbability ? "improved" : "degraded",
                    scoreProbabilityChange,
                    selectedProbability > prevProbability ? "increased" : "decreased");
        }

        // explanation += basicExplanation();
        // explanation += scoreBoundsExplanation();
        // explanation += mastExplanation();
        // explanation += nstExplanation();
        // explanation += graveExplanation();

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

        // explanation += "\n";

        // Function<Node, Double> getNodeAverageEval = _node -> _node.getScoreSum(player) / _node.getVisitCount();
        // explanation += " " + getOutliersExplanation(getNodeAverageEval, "average score", "in current situation");

        // if ((backpropagationFlags & BackpropagationFlags.AMAF_STATS) != 0) {
        //     Function<Node, Double> getNodeGraveEval = _node -> root.getScoreSumAMAF(_node.getMoveFromParent(),
        // player)
        //             / root.getVisitCountAMAF(_node.getMoveFromParent());
        //     explanation += " "
        //             + getOutliersExplanation(
        //                     getNodeGraveEval, "AMAF score", "in game phases that follow the current state");
        // }

        // if ((backpropagationFlags & BackpropagationFlags.GLOBAL_ACTION_STATS) != 0) {
        //     Function<Node, Double> getMastEval = _node -> {
        //         final var aStats = globalActionStats.get(new MoveKey(_node.getMoveFromParent(), 0));
        //         return aStats.scoreSums[player] / aStats.visitCount;
        //     };
        //     explanation +=
        //             " " + getOutliersExplanation(getMastEval, "MAST score", "in general, regardless of the game
        // phase");
        // }

        // // TODO: NST outliers
        // if ((backpropagationFlags & BackpropagationFlags.GLOBAL_NGRAM_ACTION_STATS) != 0) {
        //     explanation += " TODO: outliers explanaion for NST";
        // }

        // there are moves significantly worse that are in fact very bad
        final var veryBadNodes = avgOutliers.getVeryBadNodes();
        final var muchWorseNodes = avgOutliers.getMuchWorseNodes();
        final var numOfMuchWorseNodesThatAreVeryBad = muchWorseNodes.stream()
                .filter(node -> veryBadNodes.contains(node))
                .count();
        if (numOfMuchWorseNodesThatAreVeryBad > 0) {
            explanation += String.format(
                    "%d of alternative moves %s significantly worse. ",
                    muchWorseNodes.size(), muchWorseNodes.size() == 1 ? "is" : "are");
            if (numOfMuchWorseNodesThatAreVeryBad == muchWorseNodes.size()) {
                if (numOfMuchWorseNodesThatAreVeryBad == 1) {
                    explanation += "It ";
                } else {
                    explanation += "All of them ";
                }
            } else {
                explanation += String.format("%d of them ", numOfMuchWorseNodesThatAreVeryBad);
            }

            final var veryBadBestNode = veryBadNodes.get(0);
            final var veryBadBestProbability = scoreToProbability(getNodeAverageEval.apply(veryBadBestNode));
            if (veryBadBestProbability == 0.0) {
                explanation += String.format(
                        "%s %s defeat.\n",
                        numOfMuchWorseNodesThatAreVeryBad == 1 ? "is" : "are",
                        veryBadBestNode.isLoss(player) ? "a proven" : "highly likely a");
            } else {
                explanation += String.format(
                        "%s estimated winning probability below %.2f%%.\n",
                        numOfMuchWorseNodesThatAreVeryBad == 1 ? "has" : "have", veryBadBestProbability);
            }
        }

        // all nodes were worse
        final var slightlyWorseNodes = avgOutliers.getSlightlyWorseNodes();
        if (!isSolved && moveCount > 1 && slightlyWorseNodes.size() + muchWorseNodes.size() == moveCount - 1) {
            final var secondBestNode = sortedAvgNodes.get(1);
            final var scoreDiff = selectedProbability - scoreToProbability(getNodeAverageEval.apply(secondBestNode));
            explanation += String.format(
                    "The selected move is %s better than all other options (%.2f%% increased win probability over the next best option, %s).\n",
                    slightlyWorseNodes.isEmpty() ? "significantly" : "slightly",
                    scoreDiff,
                    moveToString(secondBestNode.getMoveFromParent()));
        }

        // counterintuitive move
        final var slightlyBetterNodes = avgOutliers.getSlightlyBetterNodes();
        final var muchBetterNodes = avgOutliers.getMuchBetterNodes();
        final var betterCount = slightlyBetterNodes.size() + muchBetterNodes.size();

        final var bestAvgMove = bestAvgNode.getMoveFromParent();

        if (!isSolved && betterCount > 0) {
            final var scoreDiff = scoreToProbability(getNodeAverageEval.apply(bestAvgNode)) - selectedProbability;

            explanation += String.format(
                    "The selected best move, %s, has estimated win probability of %.2f%%, but it was not chosen based on that metric. ",
                    moveToString(selectedMove), selectedProbability);
            if (betterCount == 1) {
                explanation += String.format(
                        "There is one move (%s) with higher win probability, which is better by %.2f%%). ",
                        moveToString(bestAvgMove), scoreDiff);
            } else {
                explanation += String.format(
                        "There are %d moves with higher win probability (best of them, %s, is better by %.2f%%). ",
                        betterCount, moveToString(bestAvgMove), scoreDiff);
            }

            // selected move was better by AMAF
            final var visitCountAMAF = root.getVisitCountAMAF(selectedMove);
            if (visitCountAMAF > 0) {
                final var selectedAMAF = root.getScoreSumAMAF(selectedMove, player) / visitCountAMAF;
                final var otherAMAF = root.getScoreSumAMAF(bestAvgMove, player) / root.getVisitCountAMAF(bestAvgMove);

                final var scoreDiffAMAF = scoreToProbability(selectedAMAF) - scoreToProbability(otherAMAF);

                if (scoreDiffAMAF > 0) {
                    if (betterCount == 1) {
                        explanation += String.format(
                                "However, this move has %s worse AMAF score (%.2f%% worse), which influenced the result. ",
                                scoreDiffAMAF > 20.0 ? "significantly" : "slightly", scoreDiffAMAF);
                    } else {
                        explanation += String.format(
                                "However, these moves have %s worse AMAF scores (%.2f%% worse for %s), which influenced the result. ",
                                scoreDiffAMAF > 20.0 ? "significantly" : "slightly",
                                scoreDiffAMAF,
                                moveToString(bestAvgMove));
                    }
                } else {
                    if (betterCount == 1) {
                        explanation += "However, this move has worse visit count, which influenced the result. ";
                    } else {
                        explanation += "However, these moves have worse visit count, which influenced the result. ";
                    }
                }
            } else {
                if (betterCount == 1) {
                    explanation += "However, this move has worse visit count, which influenced the result. ";
                } else {
                    explanation += "However, these moves have worse visit count, which influenced the result. ";
                }
            }
        }

        // node not solved but we have (upper/lower) score bound = 0
        if (!isSolved) {
            if (selectedNode.getPessimisticScore(player) == 0) {
                explanation += "\nIn this position, it is impossible to lose; the worst achievable result is a draw.\n";
            } else if (selectedNode.getOptimisticScore(player) == 0) {
                explanation += "\nIn this position, it is impossible to win; the best achievable result is a draw.\n";
            }
        }

        // All other moves are proved lost.
        var provenBadNodes = new ForcedMoves(root, selectedNode, finalMoveSelectionPolicy, 1)
                .getNodeStats()
                .get(0)
                .provenBadNodes();
        var remainingNodes =
                sortedAvgNodes.stream().filter(x -> !provenBadNodes.contains(x)).toList();
        var selectedNodeCategory = avgOutliers.getNodeCategories(selectedNode).stream()
                .filter(x -> !x.equals("equal"))
                .toList()
                .get(0);

        if (provenBadNodes.size() > moveCount * 62 / 100 && !remainingNodes.isEmpty()) {
            if (remainingNodes.size() == 1) {
                explanation += "All but one of available moves are proven defeat. ";
                explanation += String.format(
                        "The remaining one (%s) leads to a %s position (estimated win probability is %.2f%%). ",
                        moveToString(selectedMove), selectedNodeCategory, selectedProbability);
            } else {
                explanation +=
                        String.format("All but %d of available moves are proven defeat. ", remainingNodes.size());
                explanation += String.format(
                        "Among the remaining ones %s is the best, and leads to a %s position (estimated win probability is %.2f%%). ",
                        moveToString(selectedMove), selectedNodeCategory, selectedProbability);
            }
        }

        // TODO: use PNS?

        // -------------------------------------------------------------------------------------------------------------------------------------------
        // Forced Moves / Traps / Uncertainty
        //
        // We look deep into the search tree, but to keep things simple we can follow only the principal variation and
        // note what the situation looks like:
        //

        // explanation += "\n";
        // explanation += getForcedMovesExplanation(selectedNode, "the selected move", 0);

        // -------------------------------------------------------------------------------------------------------------------------------------------
        // Traps - forced moves with different selectedNode

        // for (Node wantedNode : root.getChildren()) {
        //     if (wantedNode == selectedNode) continue;
        //     explanation += " " + getForcedMovesExplanation(wantedNode, moveToString(wantedNode.getMoveFromParent()),
        // 1);
        // }

        // -------------------------------------------------------------------------------------------------------------------------------------------

        if ((backpropagationFlags & BackpropagationFlags.GLOBAL_NGRAM_ACTION_STATS) != 0) {
            explanation += String.join(" ", getNST_X());
        }

        return explanation.replaceAll("\\s{2,}", " ");
    }

    private List<String> getNST_X() {
        List<String> explanations = new ArrayList<>();

        // length = 1
        Function<Node, Double> getNstEval_1 = _node -> {
            final var ngram = new Move[1];
            ngram[0] = _node.getMoveFromParent();
            final var aStats = globalNGramStats.get(new NGramMoveKey(ngram, 0));
            return aStats.scoreSums[player] / aStats.visitCount;
        };
        var tempOUTLIERS_1 = new Outliers(root, selectedNode, getNstEval_1);

        // some very good moves (not much of them)
        var veryGoodNodes = tempOUTLIERS_1.getVeryGoodNodes();
        if (!veryGoodNodes.isEmpty()
                && veryGoodNodes.size() < root.getChildren().size() / 7) {
            if (veryGoodNodes.size() == 1) {
                explanations.add(String.format(
                        "One move (%s) is significantly better (at least %.2f%%) than the rest according to the MAST metric.",
                        veryGoodNodes.contains(selectedNode)
                                ? "the selected one"
                                : moveToString(veryGoodNodes.getLast().getMoveFromParent()),
                        scoreToProbability(getNstEval_1.apply(veryGoodNodes.getLast()))));
            } else {
                explanations.add(String.format(
                        "%d moves (%s including the selected one) are significantly better (at least %.2f%%) than the rest according to the MAST metric.",
                        veryGoodNodes.size(),
                        veryGoodNodes.contains(selectedNode) ? "" : "not",
                        scoreToProbability(getNstEval_1.apply(veryGoodNodes.getLast()))));
            }
        }

        // some much better than selected
        var muchBetterNodes = tempOUTLIERS_1.getMuchBetterNodes();
        if (!muchBetterNodes.isEmpty()) {
            if (muchBetterNodes.size() == 1) {
                explanations.add(String.format(
                        "One move (%s) is significantly better (%.2f%% better) than the selected one according to the MAST metric.",
                        moveToString(muchBetterNodes.getLast().getMoveFromParent()),
                        scoreToProbability(getNstEval_1.apply(muchBetterNodes.getLast()))
                                - scoreToProbability(getNstEval_1.apply(selectedNode))));
            } else {
                explanations.add(String.format(
                        "%d moves are significantly better (at least %.2f%% better) than the selected one according to the MAST metric.",
                        muchBetterNodes.size(),
                        scoreToProbability(getNstEval_1.apply(muchBetterNodes.getLast()))
                                - scoreToProbability(getNstEval_1.apply(selectedNode))));
            }
        }

        explanations.add("\n");
        // ---------------------------------------------------------------------------------------------------------------------------------------------------
        // ---------------------------------------------------------------------------------------------------------------------------------------------------
        // ---------------------------------------------------------------------------------------------------------------------------------------------------

        // length = 2
        final var prevMove = root.getContext().trial().lastMove();
        Function<Node, Double> getNstEval_2 = _node -> {
            final var ngram = new Move[2];
            ngram[0] = prevMove;
            ngram[1] = _node.getMoveFromParent();
            final var aStats = globalNGramStats.get(new NGramMoveKey(ngram, 0));
            return aStats.scoreSums[player] / aStats.visitCount;
        };
        var tempOUTLIERS_2 = new Outliers(root, selectedNode, getNstEval_2);

        // some very good moves (not much of them)
        veryGoodNodes = tempOUTLIERS_2.getVeryGoodNodes();
        if (!veryGoodNodes.isEmpty()
                && veryGoodNodes.size() < root.getChildren().size() / 7) {
            if (veryGoodNodes.size() == 1) {
                explanations.add(String.format(
                        "One move (%s) is significantly better (at least %.2f%%) than the rest according to the NST(2) metric.",
                        veryGoodNodes.contains(selectedNode)
                                ? "the selected one"
                                : moveToString(veryGoodNodes.getLast().getMoveFromParent()),
                        scoreToProbability(getNstEval_2.apply(veryGoodNodes.getLast()))));
            } else {
                explanations.add(String.format(
                        "%d moves (%s including the selected one) are significantly better (at least %.2f%%) than the rest according to the NST(2) metric.",
                        veryGoodNodes.size(),
                        veryGoodNodes.contains(selectedNode) ? "" : "not",
                        scoreToProbability(getNstEval_2.apply(veryGoodNodes.getLast()))));
            }
        }

        // some much better than selected
        muchBetterNodes = tempOUTLIERS_2.getMuchBetterNodes();
        if (!muchBetterNodes.isEmpty()) {
            if (muchBetterNodes.size() == 1) {
                explanations.add(String.format(
                        "One move (%s) is significantly better (%.2f%% better) than the selected one according to the NST(2) metric.",
                        moveToString(muchBetterNodes.getLast().getMoveFromParent()),
                        scoreToProbability(getNstEval_2.apply(muchBetterNodes.getLast()))
                                - scoreToProbability(getNstEval_2.apply(selectedNode))));
            } else {
                explanations.add(String.format(
                        "%d moves are significantly better (at least %.2f%% better) than the selected one according to the NST(2) metric.",
                        muchBetterNodes.size(),
                        scoreToProbability(getNstEval_2.apply(muchBetterNodes.getLast()))
                                - scoreToProbability(getNstEval_2.apply(selectedNode))));
            }
        }

        return explanations;
    }

    private String moveToString(final Move move) {
        return move.actions().stream()
                .filter(Action::isDecision)
                .findFirst()
                .map(a -> a.toTurnFormat(root.getContext(), true))
                .orElse(null);
    }

    private List<Node> getPV(Node node) {
        List<Node> result = new ArrayList<>();
        while (node != null && !node.isTerminal()) {
            node = node.select(finalMoveSelectionPolicy);
            result.add(node);
        }
        return result;
    }

    // Probabiliy (%)
    private double scoreToProbability(final double score) {
        return 100.0 * (score + 1.0) / 2.0;
    }

    private String serializeNode(final Node node) {
        final Move move = node.getMoveFromParent();

        String nodeStringBase = String.format("{move: %s, visits: %d", moveToString(move), node.getVisitCount());

        String nodeString =
                nodeStringBase + String.format(", score: %.4f", node.getScoreSum(player) / node.getVisitCount());

        if ((backpropagationFlags & BackpropagationFlags.AMAF_STATS) != 0) {
            final var visitCountAMAF = root.getVisitCountAMAF(move);
            final var scoreAMAF = root.getScoreSumAMAF(move, player) / visitCountAMAF;

            nodeString += String.format(", AMAF visits: %d, AMAF score: %.4f", visitCountAMAF, scoreAMAF);
        }

        if ((backpropagationFlags & BackpropagationFlags.GLOBAL_ACTION_STATS) != 0) {
            final var aStats = globalActionStats.get(new MoveKey(move, 0));
            nodeString += String.format(
                    ", global action visits: %d, global action score: %.4f",
                    aStats.visitCount, aStats.scoreSums[player] / aStats.visitCount);
        }

        if ((backpropagationFlags & BackpropagationFlags.GLOBAL_NGRAM_ACTION_STATS) != 0) {
            final List<Move> reverseActionSequence = new ArrayList<>();
            reverseActionSequence.add(move);
            final var reverseTrialIterator = root.getContext().trial().reverseMoveIterator();

            for (var n = 1; n <= maxNGramLength; n++) {
                final var nGram = new Move[n];
                for (var i = 0; i < n; i++) {
                    nGram[i] = reverseActionSequence.get(n - i - 1);
                }
                final var nGramKey = new NGramMoveKey(nGram, 0);

                if (globalNGramStats.containsKey(nGramKey)) {
                    final var nGramStats = globalNGramStats.get(nGramKey);
                    nodeString += String.format(
                            ", %d-gram visits: %d, %d-gram score: %f",
                            n, nGramStats.visitCount, n, nGramStats.scoreSums[player] / nGramStats.visitCount);
                } else {
                    break;
                }

                if (!reverseTrialIterator.hasNext()) {
                    break;
                }
                reverseActionSequence.add(reverseTrialIterator.next());
            }
        }

        if ((backpropagationFlags & BackpropagationFlags.PROOF_DISPROOF_NUMBERS) != 0) {
            nodeString += String.format(
                    ", proof number: %d, disproof number: %d", node.getProofNumber(), node.getDisproofNumber());
        }

        if ((backpropagationFlags & BackpropagationFlags.SCORE_BOUNDS) != 0) {
            if (node.isSolved(player)) {
                nodeString += String.format(", solved node with score %.4f", node.getPessimisticScore(player));

                if (node.isWin(player)) {
                    nodeString += " (win)";
                } else if (node.isLoss(player)) {
                    nodeString += " (loss)";
                }
            } else {
                nodeString += String.format(
                        ", pess: %.4f, opt: %.4f", node.getPessimisticScore(player), node.getOptimisticScore(player));
            }
        }

        nodeString += "}";
        return nodeString;
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
        String explanation = "NST\n";

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

    private String getOutliersExplanation(
            Function<Node, Double> evalNode, String criteria, String criteriaExplanation) {
        List<String> messages = new ArrayList<>();
        var outliers = new Outliers(root, selectedNode, evalNode);
        int totalChildren = root.getChildren().size();

        // Print the category where the selected move belongs
        outliers.getOutliersMap().entrySet().stream()
                .filter(e -> !e.getKey().equals("equal") && e.getValue().contains(selectedNode))
                .findFirst()
                .ifPresent(e -> {
                    final var category = e.getKey();
                    messages.add(String.format(
                            "The selected move has %s %s, which means it is considered %s %s.",
                            category, criteria, category, criteriaExplanation));
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

        // All or majority of other moves are in the same (relative) category
        messages.add(allOrMajorityInRelativeCategory(
                "worse",
                totalWorse,
                totalChildren - 1,
                "equal",
                equalNodes.size() - 1,
                "better",
                totalBetter,
                criteria));
        messages.add(allOrMajorityInRelativeCategory(
                "better",
                totalBetter,
                totalChildren - 1,
                "equal",
                equalNodes.size() - 1,
                "worse",
                totalWorse,
                criteria));
        messages.add(allOrMajorityInRelativeCategory(
                "equal",
                equalNodes.size() - 1,
                totalChildren - 1,
                "better",
                totalBetter,
                "worse",
                totalWorse,
                criteria));

        // selected node was one of a few equal nodes
        if (equalNodes.size() > 1 && equalNodes.size() < totalChildren / 10) {
            messages.add(String.format(
                    "The selected move was one of %d moves that are equal by the %s criteria.",
                    equalNodes.size(), criteria));
            messages.add(String.format("They were a minority among all %d available moves.", totalChildren));
        }

        // ?? ?? ??
        // print number of nodes in each category
        messages.add(String.format("By the %s criteria we have ", criteria));

        List<String> relativeCategoryMessages = new ArrayList<>();
        relativeCategoryMessages.add(formatRelativeCategory(outliers, "equal"));
        relativeCategoryMessages.add(formatRelativeCategory(outliers, "much better"));
        relativeCategoryMessages.add(formatRelativeCategory(outliers, "slightly better"));
        relativeCategoryMessages.add(formatRelativeCategory(outliers, "slightly worse"));
        relativeCategoryMessages.add(formatRelativeCategory(outliers, "much worse"));

        messages.add(String.join(
                        ", ",
                        relativeCategoryMessages.stream()
                                .filter(s -> !s.isEmpty())
                                .toList()) + ".");

        // TODO: improve explanations below
        // - better natural language templates
        // - if selected node not among 'good' nodes, add explanation why it happened
        // - maybe print those 'very good' moves

        // 1. Dominating solution, One solution that outlies the others
        var veryGoodNodes = outliers.get("very good");
        var goodNodes = outliers.get("good");
        var badNodes = outliers.get("bad");
        var veryBadNodes = outliers.get("very bad");

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
                messages.add(String.format("All moves are considered %s by the %s criteria.", category, criteria));
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
                messages.add(String.format(
                        "%d out of remaining moves %s considered neutral.",
                        neutralNodes.size(), neutralNodes.size() == 1 ? "was" : "were"));
            }
            if (!badNodes.isEmpty() || !veryBadNodes.isEmpty()) {
                int totalBadAndVeryBad = badNodes.size() + veryBadNodes.size();
                String msg = String.format("The remaining %s ", totalBadAndVeryBad == 1 ? "move was" : "moves were");
                if (badNodes.isEmpty()) {
                    msg += "very bad.";
                } else if (veryBadNodes.isEmpty()) {
                    msg += "bad.";
                } else {
                    msg += "bad or very bad.";
                }
                messages.add(msg);
            }
        }

        return String.join(" ", messages);
    }

    private String formatCategory(
            String label, int count, int totalCount, boolean selectedInCategory, String criteria) {
        final String whyNotSelected = selectedInCategory
                ? "."
                : String.format(
                        ", because the selected move had higher visit count, which makes its result more reliable.");
        if (count == 1) {
            return String.format(
                    "There was one %s move (out of %d) by the %s criteria. It is %s the selected one%s",
                    label, totalCount, criteria, selectedInCategory ? "" : "not", whyNotSelected);
        } else {
            return String.format(
                    "There were %d %s moves (out of %d) by the %s criteria. The selected move is %s among them%s",
                    count, label, totalCount, criteria, selectedInCategory ? "" : "not", whyNotSelected);
        }
    }

    private String allOrMajorityInRelativeCategory(
            final String category,
            final int count,
            final int totalCount,
            final String alternativeCategory1,
            final int alternativeCount1,
            final String alternativeCategory2,
            final int alternativeCount2,
            final String criteria) {
        final List<String> messages = new ArrayList<>();

        // all moves in category
        if (count == totalCount) {
            messages.add(String.format(
                    "All other moves were %s %s the selected one by the %s criteria.",
                    category, category.equals("equal") ? "to" : "than", criteria));
        }
        // majority (but not all) in category
        else if (count > totalCount * 8 / 10) {
            messages.add(String.format(
                    "The majority of other moves (%d out of %d) were %s %s the selected one by the %s criteria.",
                    count, totalCount, category, category.equals("equal") ? "to" : "than", criteria));
            if (alternativeCount1 > 0) {
                messages.add(String.format(
                        "%d out of remaining moves %s considered %s %s the selected.",
                        alternativeCount1,
                        alternativeCount1 == 1 ? "was" : "were",
                        alternativeCategory1,
                        alternativeCategory1.equals("equal") ? "to" : "than"));
            }
            if (alternativeCount2 > 0) {
                messages.add(String.format(
                        "The remaining %s %s.",
                        alternativeCount2 == 1 ? "move was" : "moves were", alternativeCategory2));
            }
        }
        return String.join(" ", messages);
    }

    private String formatRelativeCategory(final Outliers outliers, final String category) {
        final var nodes = outliers.get(category);
        if (category.equals("equal") && nodes.size() == 1) {
            return "";
        }
        if (!nodes.isEmpty()) {
            if (nodes.size() > 1) {
                return String.format("%d %s moves", nodes.size(), category);
            } else {
                return String.format("%d %s move", nodes.size(), category);
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
                        "%s %s %d available move%s, which is significanly less than the estimated average branching factor of the game (%f).",
                        depthString,
                        node.getPlayer() == player ? "we have" : "the opponent has",
                        nodeStats.branchingFactor(),
                        nodeStats.branchingFactor() == 1 ? "" : "s",
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
