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
    private final Node parent;
    private final Move moveFromParent;
    private final Context context;
    private final Game game;

    private int visitCount = 0;

    /** For every player, sum of utilities / scores backpropagated through this node */
    private final double[] scoreSums;

    private final List<Node> children = new ArrayList<Node>();
    private final FastArrayList<Move> unexpandedMoves;

    public Node(final Node parent, final Move moveFromParent, final Context context) {
        this.parent = parent;
        this.moveFromParent = moveFromParent;
        this.context = context;
        this.game = context.game();
        this.scoreSums = new double[game.players().count() + 1];

        // For simplicity, we just take ALL legal moves.
        // This means we do not support simultaneous-move games.
        unexpandedMoves = new FastArrayList<Move>(game.moves(context).moves());
    }

    /** State getters
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

    public double[] getScoreSums() {
        return scoreSums;
    }

    public List<Node> getChildren() {
        return children;
    }

    public FastArrayList<Move> getUnexpandedMoves() {
        return unexpandedMoves;
    }

    /** MCTS logic */
    public boolean isExpanded() {
        return unexpandedMoves.isEmpty();
    }

    public boolean isTerminal() {
        return context.trial().over();
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
        if (this.isExpanded() || this.isTerminal()) {
            return this;
        }

        final var move = this.unexpandedMoves.remove(ThreadLocalRandom.current().nextInt(this.unexpandedMoves.size()));

        final Context context = new Context(this.context);

        context.game().apply(context, move);

        var newNode = new Node(this, move, context);
        this.children.add(newNode);

        return newNode;
    }

    public double[] simulate() {
        Context tempContext = this.context;
        if (!isTerminal()) {
            tempContext = new Context(this.context);
            this.game.playout(tempContext, null, -1.0, null, 0, -1, ThreadLocalRandom.current());
        }

        return RankUtils.utilities(tempContext);
    }

    public static void propagate(Node node, final double[] utilities) {
        while (node != null) {
            node.visitCount++;
            for (var p = 1; p <= node.game.players().count(); p++) {
                node.scoreSums[p] += utilities[p];
            }
            node = node.parent;
        }
    }
}
