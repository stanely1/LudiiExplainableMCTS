package main;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import mcts.ExplainableMcts;
import mcts.policies.playout.*;
import mcts.policies.selection.*;

public class ExplainableMctsFactory {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ExplainableMcts fromJson(final String json) throws IOException {
        Config cfg = MAPPER.readValue(json, Config.class);
        ISelectionPolicy selectionPolicy = createSelectionPolicy(cfg);
        ISelectionPolicy finalMoveSelection = createFinalMoveSelectionPolicy(cfg);
        IPlayoutPolicy playoutPolicy = createPlayoutPolicy(cfg);

        return new ExplainableMcts(selectionPolicy, finalMoveSelection, playoutPolicy, cfg.useScoreBounds, cfg.usePNS);
    }

    private static ISelectionPolicy createSelectionPolicy(Config cfg) {
        switch (cfg.selectionPolicy.toLowerCase()) {
            case "grave" -> {
                return new GraveSelectionPolicy(cfg.graveBias, cfg.graveRef);
            }
            case "robustchild" -> {
                return new MostVisitedSelectionPolicy();
            }
            case "mostvisited" -> {
                return new MostVisitedSelectionPolicy();
            }
            case "ucb1" -> {
                return new UCB1SelectionPolicy();
            }
            default -> {
                System.err.println("WARNING: unknown selection policy: " + cfg.selectionPolicy);
                return new UCB1SelectionPolicy();
            }
        }
    }

    private static ISelectionPolicy createFinalMoveSelectionPolicy(Config cfg) {
        switch (cfg.finalMoveSelectionPolicy.toLowerCase()) {
            case "grave" -> {
                return new GraveSelectionPolicy(cfg.graveBias, cfg.graveRef);
            }
            case "robustchild" -> {
                return new MostVisitedSelectionPolicy();
            }
            case "mostvisited" -> {
                return new MostVisitedSelectionPolicy();
            }
            case "ucb1" -> {
                return new UCB1SelectionPolicy();
            }
            default -> {
                System.err.println("WARNING: unknown final move selection policy: " + cfg.finalMoveSelectionPolicy);
                return new MostVisitedSelectionPolicy();
            }
        }
    }

    private static IPlayoutPolicy createPlayoutPolicy(Config cfg) {
        String policy = cfg.playoutPolicy.toLowerCase();
        switch (policy) {
            case "uniform" -> {
                return new UniformPlayoutPolicy();
            }
            case "mast" -> {
                return new MAST(cfg.epsilon);
            }
            case "nst" -> {
                return new NST(cfg.maxNGramLength, cfg.epsilon);
            }
            default -> {
                System.err.println("WARNING: unknown playout policy: " + cfg.playoutPolicy);
                return new UniformPlayoutPolicy();
            }
        }
    }

    private static class Config {
        @JsonProperty("useScoreBounds")
        public boolean useScoreBounds;

        @JsonProperty("usePNS")
        public boolean usePNS;

        @JsonProperty("selectionPolicy")
        public String selectionPolicy;

        @JsonProperty("finalMoveSelectionPolicy")
        public String finalMoveSelectionPolicy;

        @JsonProperty("playoutPolicy")
        public String playoutPolicy;

        @JsonProperty("graveBias")
        public double graveBias;

        @JsonProperty("graveRef")
        public int graveRef;

        @JsonProperty("epsilon")
        public double epsilon;

        @JsonProperty("maxNGramLength")
        public int maxNGramLength;
    }
}
