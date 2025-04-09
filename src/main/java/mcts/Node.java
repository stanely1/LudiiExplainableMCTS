package mcts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.Game;
import other.move.Move;
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

    public Node select() 
    {

    }

    


}