package main;

import app.StartDesktopApp;
import mcts.ExplainableMcts;
import mcts.policies.selection.MostVisitedSelectionPolicy;
import mcts.policies.selection.UCB1SelectionPolicy;
import utils.AIRegistry;

public class LaunchLudii {
    public static void main(final String[] args) {
        final boolean useScoreBounds = true;

        if (!AIRegistry.registerAI(
                "Explainable MCTS",
                () -> {
                    return new ExplainableMcts(
                            new UCB1SelectionPolicy(), new MostVisitedSelectionPolicy(), useScoreBounds);
                },
                (game) -> {
                    return true;
                })) System.err.println("WARNING! Failed to register AI because one with that name already existed!");

        StartDesktopApp.main(new String[0]);
    }
}
