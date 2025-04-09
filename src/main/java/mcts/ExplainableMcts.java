package mcts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.Game;
import main.collections.FastArrayList;
import other.AI;
import other.RankUtils;
import other.context.Context;
import other.move.Move;

import mcts.Node;

public class ExplainableMcts extends AI
{
    //-------------------------------------------------------------------------
	protected int player = -1;

	Node x = new Node();

	protected String analysisReport = "Explainable Mcts thinking..."+x.name;



	//-------------------------------------------------------------------------

    public ExplainableMcts()
    {
        this.friendlyName = "ExplainableMcts";
    }

	@Override
    public Move selectAction
    (
		final Game game,
		final Context context,
		final double maxSeconds,
		final int maxIterations,
		final int maxDepth
    )
    {
        var legalMoves = game.moves(context).moves();
        return legalMoves.get(0);
    }

	@Override
	public void initAI(final Game game, final int playerID)
	{
		this.player = playerID;
	}

    @Override
	public boolean supportsGame(final Game game)
	{
		if (game.isStochasticGame())
			return false;

		if (!game.isAlternatingMoveGame())
			return false;

		return true;
	}

    @Override
	public String generateAnalysisReport()
	{
		return analysisReport;
	}
}