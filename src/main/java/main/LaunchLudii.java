package main;

import app.StartDesktopApp;
import mcts.ExplainableMcts;
import mcts.policies.playout.IPlayoutPolicy;
import mcts.policies.playout.MAST;
import mcts.policies.selection.GraveSelectionPolicy;
import mcts.policies.selection.ISelectionPolicy;
import mcts.policies.selection.MostVisitedSelectionPolicy;
import utils.AIRegistry;

public class LaunchLudii {
    public static void main(final String[] args) {
        final boolean useScoreBounds = true;
        final double graveBias = 1e-6;
        final int graveRef = 100;
        final ISelectionPolicy selectionPolicy = new GraveSelectionPolicy(graveBias, graveRef);

        final ISelectionPolicy finalMoveSelectionPolicy = new MostVisitedSelectionPolicy();
        final IPlayoutPolicy playoutPolicy = new MAST();

        if (!AIRegistry.registerAI(
                "Explainable MCTS",
                () -> {
                    return new ExplainableMcts(
                            selectionPolicy, finalMoveSelectionPolicy, playoutPolicy, useScoreBounds);
                },
                (game) -> {
                    return true;
                })) System.err.println("WARNING! Failed to register AI because one with that name already existed!");

        StartDesktopApp.main(new String[0]);
    }
}
