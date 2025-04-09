package main;

import mcts.ExplainableMcts;

import app.StartDesktopApp;
import utils.AIRegistry;

public class LaunchLudii
{
	public static void main(final String[] args)
	{
		if (!AIRegistry.registerAI("Explainable MCTS", () -> {return new ExplainableMcts();}, (game) -> {return true;}))
			System.err.println("WARNING! Failed to register AI because one with that name already existed!");

		StartDesktopApp.main(new String[0]);
	}
}
