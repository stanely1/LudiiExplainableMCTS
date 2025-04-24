package main;

import app.StartDesktopApp;
import mcts.ExplainableMcts;
import mcts.policies.selection.ISelectionPolicy;
import mcts.policies.selection.MostVisitedSelectionPolicy;
import mcts.policies.selection.RaveSelectionPolicy;
import mcts.policies.selection.UCB1SelectionPolicy;
import utils.AIRegistry;

public class LaunchLudii {
    public static void main(final String[] args) {
        final boolean useScoreBounds = true;
        final boolean useAMAF = true;
        final ISelectionPolicy selectionPolicy = new RaveSelectionPolicy();
        final ISelectionPolicy finalMoveSelectionPolicy = new MostVisitedSelectionPolicy();

        if (!AIRegistry.registerAI(
                "Explainable MCTS",
                () -> {
                    return new ExplainableMcts(selectionPolicy, finalMoveSelectionPolicy, useScoreBounds, useAMAF);
                },
                (game) -> {
                    return true;
                }))
            System.err.println("WARNING! Failed to register AI because one with that name already existed!");

        StartDesktopApp.main(new String[0]);
    }
}
