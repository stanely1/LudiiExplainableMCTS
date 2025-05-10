package main;

import app.StartDesktopApp;
import mcts.ExplainableMcts;
import mcts.policies.playout.IPlayoutPolicy;
// import mcts.policies.playout.NST;
import mcts.policies.playout.MAST;
import mcts.policies.selection.GraveSelectionPolicy;
import mcts.policies.selection.ISelectionPolicy;
import mcts.policies.selection.MostVisitedSelectionPolicy;
import pns.ProofNumberSearch;
import utils.AIRegistry;

public class LaunchLudii {
    public static void main(final String[] args) {
        final boolean useScoreBounds = true;

        final double graveBias = 1e-6;
        final int graveRef = 100;
        final double eps = 0.1;
        final int maxNGramLength = 3;

        final ISelectionPolicy selectionPolicy = new GraveSelectionPolicy(graveBias, graveRef);
        final ISelectionPolicy finalMoveSelectionPolicy = new MostVisitedSelectionPolicy();
        final IPlayoutPolicy playoutPolicy = new MAST(eps);
        // final IPlayoutPolicy playoutPolicy = new NST(maxNGramLength, eps);

        if (!AIRegistry.registerAI(
                "Explainable MCTS",
                () -> {
                    return new ExplainableMcts(
                            selectionPolicy, finalMoveSelectionPolicy, playoutPolicy, useScoreBounds);
                },
                (game) -> {
                    return true;
                })) System.err.println("WARNING! Failed to register AI because one with that name already existed!");

        if (!AIRegistry.registerAI(
                "Proof-Number Search",
                () -> {
                    return new ProofNumberSearch();
                },
                (game) -> {
                    return true;
                })) System.err.println("WARNING! Failed to register AI because one with that name already existed!");

        StartDesktopApp.main(new String[0]);
    }
}
