package mcts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.Game;
import other.move.Move;
import other.RankUtils;
import other.context.Context;
import main.collections.FastArrayList;

public class Node
{
    private final Node parent;
    private final Move moveFromParent;
    private final Context context;
    private final Game game;

    private int visitCount = 0;
    /** For every player, sum of utilities / scores backpropagated through this node */
	private final double[] scoreSums;
    private final List<Node> children = new ArrayList<Node>();
    private final FastArrayList<Move> unexpandedMoves;

    public Node(final Node parent, final Move moveFromParent, final Context context)
    {
        this.parent = parent;
        this.moveFromParent = moveFromParent;
        this.context = context;
        this.game = context.game();
        this.scoreSums = new double[game.players().count() + 1];

        // For simplicity, we just take ALL legal moves.
        // This means we do not support simultaneous-move games.
        unexpandedMoves = new FastArrayList<Move>(game.moves(context).moves());
    }

    public boolean isExpanded()
    {
        return unexpandedMoves.isEmpty();
    }

    public Node select() 
    {
        Node bestChild = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        final double twoParentLog = 2.0 * Math.log(Math.max(1, this.visitCount));
        final int currentPlayerID = this.context.state().mover();
        int numBestFound = 0;

        for(final var childNode : this.children)
        {
            final double exploit = childNode.scoreSums[currentPlayerID] / childNode.visitCount;
            final double explore = Math.sqrt(twoParentLog / childNode.visitCount);
            final double ucb1Value = exploit + explore;

            if(ucb1Value > bestValue)
            {
                bestValue = ucb1Value;
                bestChild = childNode;
                numBestFound = 1;
            }
            else if
            (
                ucb1Value == bestValue &&
                ThreadLocalRandom.current().nextInt() % ++numBestFound == 0
            )
            {
                bestChild = childNode;
            }
        }
        return bestChild;
    }

    public void expand()
    {
        if(this.isExpanded() || this.context.trial().over())
            return;
        
        final var move = this.unexpandedMoves.remove(
            ThreadLocalRandom.current().nextInt(this.unexpandedMoves.size()));

        final Context context = new Context(this.context);

        context.game().apply(context, move);

        var newNode = new Node(this, move, context);
        
        var utilities = newNode.simulate();
        propagate(newNode, utilities);

        this.children.add(newNode);
    }

    private double[] simulate()
    {
        Context tempContext = this.context;
        if(!tempContext.trial().over())
        {
            tempContext = new Context(this.context);
            this.game.playout(tempContext, null, -1.0, null, 0, -1, ThreadLocalRandom.current());
        }

        return RankUtils.utilities(tempContext);
    }

    private static void propagate(Node node, final double[] utilities)
    {
        // if(this.parent == null) return;
        while(node != null)
        {
            node.visitCount++;
            for(var p = 1; p <= node.game.players().count(); p++)
            {
                node.scoreSums[p] += utilities[p];
            }
            node = node.parent;
        }
    }

    public Move selectFinalMove()
    {
        Node bestChild = null;
        int bestVisitCount = Integer.MIN_VALUE;
        int numBestFound = 0;

        for(final var childNode : this.children)
        {
            if(childNode.visitCount > bestVisitCount)
            {
                bestVisitCount = childNode.visitCount;
                bestChild = childNode;
                numBestFound = 1;
            }
            else if
            (
                childNode.visitCount == bestVisitCount &&
                ThreadLocalRandom.current().nextInt() % ++numBestFound == 0
            )
            {
                bestChild = childNode;
            }
        }
        return bestChild.moveFromParent;
    }

}