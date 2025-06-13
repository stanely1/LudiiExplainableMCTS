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
        /********************************************** Tree structure **********************************************/
        String explanation = serializeAllNodes();

        /*************************************** Natural language explanation ***************************************/
        final Function<Node, Double> getNodeAverageEval =
                _node -> _node.isSolved(player) ? _node.getPessimisticScore(player) : _node.getAverageScore(player);

        final var avgOutliers = new Outliers(root, selectedNode, getNodeAverageEval);
        final var selectedProbability = scoreToProbability(getNodeAverageEval.apply(selectedNode));
        final var isSolved = selectedNode.isSolved(player);

        // General info on available moves
        explanation += getGeneralInfoOnAvailableMoves(avgOutliers, getNodeAverageEval);

        if (isSolved) {
            // Explanation for solved node
            explanation += getSolverExplanation();
        } else {
            // Selected move and general position info
            explanation += String.format("Selected move: %s.\n", moveToString(selectedMove));
            explanation += getGeneralPositionInfo(avgOutliers, getNodeAverageEval);
            explanation += getScoreChangeOverPrevTurnInfo(selectedProbability);
        }

        // There are significantly worse moves that are in fact very bad
        explanation += getMuchWorseMovesThatAreVeryBadInfo(avgOutliers, getNodeAverageEval);

        if (!isSolved) {
            // All other moves are worse
            explanation += getAllMovesWorseInfo(avgOutliers, getNodeAverageEval, selectedProbability);

            // Counterintuitive move
            explanation += getCounterintuitiveMoveExplanation(avgOutliers, getNodeAverageEval, selectedProbability);

            // Node not solved but we have (upper/lower) score bound = 0
            if (selectedNode.getPessimisticScore(player) == 0) {
                explanation += "In this position, it is impossible to lose; the worst achievable result is a draw.\n";
            } else if (selectedNode.getOptimisticScore(player) == 0) {
                explanation += "In this position, it is impossible to win; the best achievable result is a draw.\n";
            }
        }

        // All other moves are proven loss
        explanation += getAllOtherMovesAreProvenLossInfo(avgOutliers, selectedProbability);

        // TODO: use PNS?

        // MAST
        if ((backpropagationFlags & BackpropagationFlags.GLOBAL_ACTION_STATS) != 0) {
            explanation += getMastExplanation();
        }

        // NST
        if ((backpropagationFlags & BackpropagationFlags.GLOBAL_NGRAM_ACTION_STATS) != 0) {
            explanation += String.join(" ", getNstExplanations());
        }

        return explanation.replaceAll("\\s{2,}", " ");
    }

    private String serializeAllNodes() {
        String result = String.format("Selected node:\n%s\n", serializeNode(selectedNode));
        if (root.getChildren().size() > 1) {
            result += "Other nodes:\n";
            for (final var node : root.getChildren()) {
                if (node != selectedNode) {
                    result += serializeNode(node) + "\n";
                }
            }
        }
        return result;
    }

    private String getGeneralInfoOnAvailableMoves(
            final Outliers outliers, final Function<Node, Double> nodeEvalFunction) {
        final List<String> moveCategoryStrings = new ArrayList<>();

        // Moves with decisive advantage
        if (!outliers.getVeryGoodNodes().isEmpty()) {
            String decisiveAdvantageDefinition;
            final var veryGoodWorstNode = outliers.getVeryGoodNodes().getLast();
            final var veryGoodWorstProbability = scoreToProbability(nodeEvalFunction.apply(veryGoodWorstNode));
            if (veryGoodWorstProbability == 100.0) {
                decisiveAdvantageDefinition =
                        String.format("%s win", veryGoodWorstNode.isWin(player) ? "proven" : "highly likely");
            } else {
                decisiveAdvantageDefinition = String.format("above %.2f%%", veryGoodWorstProbability);
            }

            moveCategoryStrings.add(String.format(
                    "%d with decisive advantage (%s)",
                    outliers.getVeryGoodNodes().size(), decisiveAdvantageDefinition));
        }
        // Moves with slight advantage
        if (!outliers.getGoodNodes().isEmpty()) {
            moveCategoryStrings.add(String.format(
                    "%d with slight advantage (above %.2f%%)",
                    outliers.getGoodNodes().size(),
                    scoreToProbability(
                            nodeEvalFunction.apply(outliers.getGoodNodes().getLast()))));
        }
        // Balanced moves
        if (!outliers.getNeutralNodes().isEmpty()) {
            moveCategoryStrings.add(String.format(
                    "%d balanced (~50%%)", outliers.getNeutralNodes().size()));
        }
        // Moves with slight disadvantage
        if (!outliers.getBadNodes().isEmpty()) {
            moveCategoryStrings.add(String.format(
                    "%d with slight disadvantage (below %.2f%%)",
                    outliers.getBadNodes().size(),
                    scoreToProbability(
                            nodeEvalFunction.apply(outliers.getBadNodes().getFirst()))));
        }
        // Moves with decisive disadvantage
        if (!outliers.getVeryBadNodes().isEmpty()) {
            String decisiveDisadvantageDefinition;
            final var veryBadBestNode = outliers.getVeryBadNodes().getFirst();
            final var veryBadBestProbability = scoreToProbability(nodeEvalFunction.apply(veryBadBestNode));
            if (veryBadBestProbability == 0.0) {
                decisiveDisadvantageDefinition =
                        String.format("%s loss", veryBadBestNode.isLoss(player) ? "proven" : "highly likely");
            } else {
                decisiveDisadvantageDefinition = String.format("below %.2f%%", veryBadBestProbability);
            }

            moveCategoryStrings.add(String.format(
                    "%d with decisive disadvantage (%s)",
                    outliers.getVeryBadNodes().size(), decisiveDisadvantageDefinition));
        }

        // Summary
        final int moveCount = root.getChildren().size();
        return String.format(
                "There %s %d move%s available: %s.\n",
                moveCount == 1 ? "is" : "are",
                moveCount,
                moveCount == 1 ? "" : "s",
                String.join(", ", moveCategoryStrings));
    }

    private String getGeneralPositionInfo(final Outliers outliers, final Function<Node, Double> nodeEvalFunction) {
        final var sortedNodes = outliers.getSortedNodes();
        final var worstNode = sortedNodes.getLast();
        final var bestNode = sortedNodes.getFirst();

        // worst node is at least good
        if (outliers.getGoodNodes().contains(worstNode)
                || outliers.getVeryGoodNodes().contains(worstNode)) {
            // TODO: if probablility == 100%, print "highly likely a win"
            return String.format(
                    "Our position is generally advantageous (the estimated win probability for the worst of available moves is %.2f%%).\n",
                    scoreToProbability(nodeEvalFunction.apply(worstNode)));
        }
        // best node is at most bad
        else if (outliers.getBadNodes().contains(bestNode)
                || outliers.getVeryBadNodes().contains(bestNode)) {
            // TODO: if probablility == 0%, print "highly likely a loss"
            return String.format(
                    "Our position is generally disadvantageous (the estimated win probability for the best of available moves is %.2f%%).\n",
                    scoreToProbability(nodeEvalFunction.apply(bestNode)));
        }
        // general position info
        else {
            final double selectedScore = nodeEvalFunction.apply(selectedNode);
            final double absSelectedScore = Math.abs(selectedScore);
            final double selectedProbability = scoreToProbability(selectedScore);

            return String.format(
                    "Our position is %s (estimated win probability: %.2f%%).\n",
                    absSelectedScore < 0.1
                            ? "balanced"
                            : String.format(
                                    "%s %sadvantageous",
                                    absSelectedScore < 0.4 ? "slightly" : "strongly", selectedScore < 0 ? "dis" : ""),
                    selectedProbability);
        }
    }

    private String getScoreChangeOverPrevTurnInfo(final double selectedProbability) {
        final double prevProbability = scoreToProbability(prevTurnScore);
        final double scoreProbabilityChange = Math.abs(selectedProbability - prevProbability);

        if (scoreProbabilityChange > 10.0) {
            return String.format(
                    "The overall estimation of our position %s over the previous turn (%.2f%% %s win probability).\n",
                    selectedProbability > prevProbability ? "improved" : "degraded",
                    scoreProbabilityChange,
                    selectedProbability > prevProbability ? "increased" : "decreased");
        }
        return "";
    }

    private String getSolverExplanation() {
        final var pv = getPV(selectedNode);
        final String gameResult = selectedNode.isWin(player) ? "win" : selectedNode.isLoss(player) ? "loss" : "draw";

        String explanation =
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

        return explanation;
    }

    private String getMuchWorseMovesThatAreVeryBadInfo(
            final Outliers outliers, final Function<Node, Double> nodeEvalFunction) {
        String explanation = "";

        final var veryBadNodes = outliers.getVeryBadNodes();
        final var muchWorseNodes = outliers.getMuchWorseNodes();
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

            final var veryBadBestNode = veryBadNodes.getFirst();
            final var veryBadBestProbability = scoreToProbability(nodeEvalFunction.apply(veryBadBestNode));
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

        return explanation;
    }

    private String getAllMovesWorseInfo(
            final Outliers outliers, final Function<Node, Double> nodeEvalFunction, final double selectedProbability) {
        final var muchWorseNodes = outliers.getMuchWorseNodes();
        final var slightlyWorseNodes = outliers.getSlightlyWorseNodes();
        final var moveCount = root.getChildren().size();

        if (moveCount > 1 && slightlyWorseNodes.size() + muchWorseNodes.size() == moveCount - 1) {
            final var secondBestNode = outliers.getSortedNodes().get(1);
            final var scoreDiff = selectedProbability - scoreToProbability(nodeEvalFunction.apply(secondBestNode));
            return String.format(
                    "The selected move is %s better than all other options (%.2f%% increased win probability over the next best option, %s).\n",
                    slightlyWorseNodes.isEmpty() ? "significantly" : "slightly",
                    scoreDiff,
                    moveToString(secondBestNode.getMoveFromParent()));
        }
        return "";
    }

    private String getCounterintuitiveMoveExplanation(
            final Outliers outliers, final Function<Node, Double> nodeEvalFunction, final double selectedProbability) {
        String explanation = "";

        final var slightlyBetterNodes = outliers.getSlightlyBetterNodes();
        final var muchBetterNodes = outliers.getMuchBetterNodes();
        final var betterCount = slightlyBetterNodes.size() + muchBetterNodes.size();

        final var bestNode = outliers.getSortedNodes().getFirst();
        final var bestMove = bestNode.getMoveFromParent();

        if (betterCount > 0) {
            final var scoreDiff = scoreToProbability(nodeEvalFunction.apply(bestNode)) - selectedProbability;

            explanation += String.format(
                    "The selected best move, %s, has estimated win probability of %.2f%%, but it was not chosen based on that metric. ",
                    moveToString(selectedMove), selectedProbability);
            if (betterCount == 1) {
                explanation += String.format(
                        "There is one move (%s) with higher win probability, which is better by %.2f%%. ",
                        moveToString(bestMove), scoreDiff);
            } else {
                explanation += String.format(
                        "There are %d moves with higher win probability (best of them, %s, is better by %.2f%%). ",
                        betterCount, moveToString(bestMove), scoreDiff);
            }

            final String worseVisitCountString = String.format(
                    "However, %s worse visit count, which influenced the result. ",
                    betterCount == 1 ? "this move has" : "these moves have");

            // selected move was better by AMAF
            final var visitCountAMAF = root.getVisitCountAMAF(selectedMove);
            if (visitCountAMAF > 0) {
                final var selectedAMAF = root.getScoreSumAMAF(selectedMove, player) / visitCountAMAF;
                final var otherAMAF = root.getScoreSumAMAF(bestMove, player) / root.getVisitCountAMAF(bestMove);

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
                                moveToString(bestMove));
                    }
                } else {
                    explanation += worseVisitCountString;
                }
            } else {
                explanation += worseVisitCountString;
            }
        }

        return explanation;
    }

    private String getAllOtherMovesAreProvenLossInfo(final Outliers outliers, final double selectedProbability) {
        String explanation = "";

        final var provenBadNodes = new ForcedMoves(root, selectedNode, finalMoveSelectionPolicy, 1)
                .getNodeStats()
                .getFirst()
                .provenBadNodes();
        final var remainingNodes = outliers.getSortedNodes().stream()
                .filter(x -> !provenBadNodes.contains(x))
                .toList();
        final var selectedNodeCategory = outliers.getNodeCategories(selectedNode).stream()
                .filter(x -> !x.equals("equal"))
                .toList()
                .getFirst();
        final var isSolved = selectedNode.isSolved(player);

        if (provenBadNodes.size() > root.getChildren().size() * 62 / 100 && !remainingNodes.isEmpty()) {
            if (remainingNodes.size() == 1) {
                explanation += "All but one of available moves are proven defeat. ";
                if (isSolved) {
                    explanation += String.format(
                            "The remaining one (%s) leads to a proven %s. ",
                            moveToString(selectedMove),
                            selectedNode.isWin(player) ? "win" : selectedNode.isLoss(player) ? "loss" : "draw");
                } else {
                    explanation += String.format(
                            "The remaining one (%s) leads to a %s position (estimated win probability is %.2f%%). ",
                            moveToString(selectedMove), selectedNodeCategory, selectedProbability);
                }
            } else {
                explanation +=
                        String.format("All but %d of available moves are proven defeat. ", remainingNodes.size());
                if (isSolved) {
                    explanation += String.format(
                            "Among the remaining ones, %s is the best, and leads to a proven %s. ",
                            moveToString(selectedMove),
                            selectedNode.isWin(player) ? "win" : selectedNode.isLoss(player) ? "loss" : "draw");
                } else {
                    explanation += String.format(
                            "Among the remaining ones, %s is the best, and leads to a %s position (estimated win probability is %.2f%%). ",
                            moveToString(selectedMove), selectedNodeCategory, selectedProbability);
                }
            }
        }

        return explanation;
    }

    private String getMastExplanation() {
        final Function<Node, Double> evalFunction = node -> {
            final var aStats = globalActionStats.get(new MoveKey(node.getMoveFromParent(), 0));
            return aStats.scoreSums[player] / aStats.visitCount;
        };

        return getPositiveOutliersExplanation(evalFunction, "MAST");
    }

    private List<String> getNstExplanations() {
        final List<String> explanations = new ArrayList<>();

        final var moveHistoryLength =
                selectedNode.getContext().trial().generateCompleteMovesList().size();

        for (var i = 1; i <= Math.min(maxNGramLength, moveHistoryLength); i++) {
            final var evalFunction = getNstEvalFunction(i);
            final var metricName = i == 1 ? "MAST" : String.format("NST(%d)", i);
            explanations.add(getPositiveOutliersExplanation(evalFunction, metricName));
            explanations.add("\n");
        }

        return explanations;
    }

    private Function<Node, Double> getNstEvalFunction(final int nGramLength) {
        return node -> {
            final var nGram = new Move[nGramLength];
            final var reverseTrialIterator = node.getContext().trial().reverseMoveIterator();

            for (var i = nGramLength - 1; i >= 0; i--) {
                nGram[i] = reverseTrialIterator.next();
            }

            final var aStats = globalNGramStats.get(new NGramMoveKey(nGram, 0));
            return aStats.scoreSums[player] / aStats.visitCount;
        };
    }

    private String getPositiveOutliersExplanation(final Function<Node, Double> evalFunction, final String metricName) {
        final List<String> explanations = new ArrayList<>();

        final var outliers = new Outliers(root, selectedNode, evalFunction);

        // some very good moves (not much of them)
        final var veryGoodNodes = outliers.getVeryGoodNodes();
        if (!veryGoodNodes.isEmpty()
                && veryGoodNodes.size() < root.getChildren().size() / 7) {
            // TODO: print probability diff over the other moves rather than absolute value -> (at least %.2f%% better)
            // OR maybe "N moves have very high MAST value (at least X% win probability)" ???
            if (veryGoodNodes.size() == 1) {
                explanations.add(String.format(
                        "One move (%s) is significantly better (at least %.2f%%) than the rest according to the %s metric.",
                        veryGoodNodes.contains(selectedNode)
                                ? "the selected one"
                                : moveToString(veryGoodNodes.getLast().getMoveFromParent()),
                        scoreToProbability(evalFunction.apply(veryGoodNodes.getLast())),
                        metricName));
            } else {
                explanations.add(String.format(
                        "%d moves (%sincluding the selected one) are significantly better (at least %.2f%%) than the rest according to the %s metric.",
                        veryGoodNodes.size(),
                        veryGoodNodes.contains(selectedNode) ? "" : "not ",
                        scoreToProbability(evalFunction.apply(veryGoodNodes.getLast())),
                        metricName));
            }
        }

        // some much better than selected
        final var muchBetterNodes = outliers.getMuchBetterNodes();
        if (!muchBetterNodes.isEmpty()) {
            if (muchBetterNodes.size() == 1) {
                explanations.add(String.format(
                        "One move (%s) is significantly better (%.2f%% better) than the selected one according to the %s metric.",
                        moveToString(muchBetterNodes.getLast().getMoveFromParent()),
                        scoreToProbability(evalFunction.apply(muchBetterNodes.getLast()))
                                - scoreToProbability(evalFunction.apply(selectedNode)),
                        metricName));
            } else {
                explanations.add(String.format(
                        "%d moves are significantly better (at least %.2f%% better) than the selected one according to the %s metric.",
                        muchBetterNodes.size(),
                        scoreToProbability(evalFunction.apply(muchBetterNodes.getLast()))
                                - scoreToProbability(evalFunction.apply(selectedNode)),
                        metricName));
            }
        }

        return String.join(" ", explanations);
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

        final String nodeStringBase = String.format("{move: %s, visits: %d", moveToString(move), node.getVisitCount());

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
