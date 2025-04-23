package mcts;

import game.Game;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import main.collections.FastArrayList;
import mcts.policies.selection.ISelectionPolicy;
import other.RankUtils;
import other.context.Context;
import other.move.Move;

public class Node {
    private static final double WIN_SCORE = 1.0;
    private static final double LOSS_SCORE = -WIN_SCORE;

    private Node parent;
    private Move moveFromParent;

    private final Context context;
    private final Game game;

    private int visitCount = 0;

    /**
     * For every player, sum of utilities / scores backpropagated through this node
     */
    private final double[] scoreSums;

    /** Scores from range [-1, 1], 1 means win, -1 loss */
    private final double[] pessimisticScores;

    private final double[] optimisticScores;

    /** ALL MOVES AS FIRST */
    private final double[] scoreAMAF;

    private int visitCountAMAF = 0;

    private final List<Node> children = new ArrayList<>();
    private final FastArrayList<Move> unexpandedMoves;

    public Node(final Node parent, final Move moveFromParent, final Context context) {
        this.parent = parent;
        this.moveFromParent = moveFromParent;
        this.context = context;
        this.game = context.game();

        final var playerCount = game.players().count();
        this.scoreSums = new double[playerCount + 1];
        this.scoreAMAF = new double[playerCount + 1];

        // if (useScoreBounds)
        this.pessimisticScores = new double[playerCount + 1];
        this.optimisticScores = new double[playerCount + 1];

        for (var i = 1; i <= playerCount; i++) {
            this.pessimisticScores[i] = LOSS_SCORE;
            this.optimisticScores[i] = WIN_SCORE;
        }

        // For simplicity, we just take ALL legal moves.
        // This means we do not support simultaneous-move games.
        this.unexpandedMoves = new FastArrayList<>(game.moves(context).moves());
    }

    /**
     * State getters
     * TODO: use @Getter annotation for less code
     */
    public Node getParent() {
        return parent;
    }

    public Move getMoveFromParent() {
        return moveFromParent;
    }

    public Context getContext() {
        return context;
    }

    public Game getGame() {
        return game;
    }

    public int getVisitCount() {
        return visitCount;
    }

    public int getVisitCountAMAF() {
        return visitCountAMAF;
    }

    public double getScoreSum(final int player) {
        return scoreSums[player];
    }

    public double getScoreAMAF(final int player) {
        return scoreAMAF[player];
    }

    public double getPessimisticScore(final int player) {
        return pessimisticScores[player];
    }

    public double getOptimisticScore(final int player) {
        return optimisticScores[player];
    }

    public List<Node> getChildren() {
        return children;
    }

    public FastArrayList<Move> getUnexpandedMoves() {
        return unexpandedMoves;
    }

    public Node getChildByMove(final Move move) {
        for (final var childNode : this.children) {
            if (childNode.moveFromParent.equals(move)) {
                return childNode;
            }
        }
        return null;
    }

    public int getPlayer() {
        return context.state().mover();
    }

    /** MCTS logic */
    public void detachFromParent() {
        this.parent = null;
        this.moveFromParent = null;
    }

    public boolean isExpanded() {
        return unexpandedMoves.isEmpty();
    }

    public boolean isTerminal() {
        return context.trial().over();
    }

    public boolean isSolved(final int player) {
        return getPessimisticScore(player) == getOptimisticScore(player);
    }

    public boolean isWin(final int player) {
        return getPessimisticScore(player) == WIN_SCORE;
    }

    public boolean isLoss(final int player) {
        return getOptimisticScore(player) == LOSS_SCORE;
    }

    public Node select(final ISelectionPolicy selectionPolicy) {
        Node bestChild = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        int numBestFound = 0;

        for (final var childNode : this.children) {
            final double childValue = selectionPolicy.getNodeValue(childNode);

            if (childValue > bestValue) {
                bestValue = childValue;
                bestChild = childNode;
                numBestFound = 1;
            } else if (childValue == bestValue && ThreadLocalRandom.current().nextInt() % ++numBestFound == 0) {
                bestChild = childNode;
            }
        }
        return bestChild;
    }

    public Node expand() {
        if (this.isExpanded() || this.isTerminal() || this.isSolved(this.getPlayer())) {
            return this;
        }

        final var move = this.unexpandedMoves.remove(ThreadLocalRandom.current().nextInt(this.unexpandedMoves.size()));

        final Context newContext = new Context(this.context);

        newContext.game().apply(newContext, move);

        var newNode = new Node(this, newContext.trial().lastMove(), newContext);
        this.children.add(newNode);

        return newNode;
    }

    public record SimulationResult(Context context, double[] utilities) {}

    public SimulationResult simulate() {
        Context tempContext = this.context;

        if (this.isSolved(this.getPlayer())) {
            return new SimulationResult(tempContext, pessimisticScores);
        }

        if (!isTerminal()) {
            tempContext = new Context(this.context);
            this.game.playout(tempContext, null, -1.0, null, 0, -1, ThreadLocalRandom.current());
        }

        return new SimulationResult(tempContext, RankUtils.utilities(tempContext));
    }

    public void propagate(final double[] utilities, final boolean useScoreBounds) {
        if (useScoreBounds && this.isTerminal()) {
            propagateScoreBounds(utilities);
        }

        Node node = this;
        while (node != null) {
            node.visitCount++;
            for (var p = 1; p <= this.game.players().count(); p++) {
                node.scoreSums[p] += utilities[p];
            }
            node = node.parent;
        }
    }

    private void propagateScoreBounds(final double[] utilities) {
        final var playerCount = this.game.players().count();

        for (var p = 1; p <= playerCount; p++) {
            this.pessimisticScores[p] = utilities[p];
            this.optimisticScores[p] = utilities[p];
        }

        Node node = this.parent;
        while (node != null) {
            for (var p = 1; p <= this.game.players().count(); p++) {
                final var player = p;

                if (player == node.getPlayer()) {
                    node.pessimisticScores[player] = node.children.stream()
                            .mapToDouble(child -> child.getPessimisticScore(player))
                            .max()
                            .orElse(LOSS_SCORE);
                } else if (node.isExpanded()) {
                    node.pessimisticScores[player] = node.children.stream()
                            .mapToDouble(child -> child.getPessimisticScore(player))
                            .min()
                            .orElse(LOSS_SCORE);
                } else {
                    node.pessimisticScores[player] = LOSS_SCORE;
                }

                if (node.isExpanded()) {
                    node.optimisticScores[player] = node.children.stream()
                            .mapToDouble(child -> child.getOptimisticScore(player))
                            .max()
                            .orElse(WIN_SCORE);
                } else {
                    node.optimisticScores[player] = WIN_SCORE;
                }
            }
            node = node.parent;
        }
    }
}
