package main;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import mcts.ExplainableMcts;
import mcts.policies.backpropagation.*;
import mcts.policies.playout.*;
import mcts.policies.selection.*;

public class ExplainableMctsFactory {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses the given JSON string and returns a configured ExplainableMcts instance.
     *
     * @param json JSON configuration string
     * @return ExplainableMcts instance
     * @throws IOException if JSON parsing fails
     */
    public static ExplainableMcts fromJson(final String json) throws IOException {
        Config cfg = MAPPER.readValue(json, Config.class);
        ISelectionPolicy selectionPolicy = createSelectionPolicy(cfg);
        ISelectionPolicy finalMoveSelection = createFinalMoveSelectionPolicy(cfg);
        IPlayoutPolicy playoutPolicy = createPlayoutPolicy(cfg);

        return new ExplainableMcts(selectionPolicy, finalMoveSelection, playoutPolicy, cfg.useScoreBounds, cfg.usePNS);
    }

    private static ISelectionPolicy createSelectionPolicy(Config cfg) {
        switch (cfg.selectionPolicy.toLowerCase()) {
            case "grave":
                return new GraveSelectionPolicy(cfg.graveBias, cfg.graveRef);
            case "robust_child":
                return new MostVisitedSelectionPolicy();
            case "ucb1":
                return new UCB1SelectionPolicy();
            case "mostvisited":
                return new MostVisitedSelectionPolicy();
            default:
                throw new IllegalArgumentException("Unknown selection policy: " + cfg.selectionPolicy);
        }
    }

    private static ISelectionPolicy createFinalMoveSelectionPolicy(Config cfg) {
        switch (cfg.finalMoveSelectionPolicy.toLowerCase()) {
            case "grave":
                return new GraveSelectionPolicy(cfg.graveBias, cfg.graveRef);
            case "robust_child":
                return new MostVisitedSelectionPolicy();
            case "ucb1":
                return new UCB1SelectionPolicy();
            case "mostvisited":
                return new MostVisitedSelectionPolicy();
            default:
                throw new IllegalArgumentException("Unknown final selection policy: " + cfg.selectionPolicy);
        }
    }

    private static IPlayoutPolicy createPlayoutPolicy(Config cfg) {
        String policy = cfg.playoutPolicy.toLowerCase();
        switch (policy) {
            case "uniform":
                return new UniformPlayoutPolicy();

                // case "epsilongreedy": {
                //     IPlayoutPolicy base;
                //     switch (cfg.wrappedPlayoutPolicy.toLowerCase()) {
                //         case "uniform":
                //             base = new UniformPlayoutPolicy();
                //             break;
                //         case "mast":
                //             base = new MAST(cfg.eps);
                //             break;
                //         case "nst":
                //             base = new NST(cfg.maxNGramLength, cfg.eps);
                //             break;
                //         default:
                //             throw new IllegalArgumentException("Unknown wrapped playout policy: " +
                // cfg.wrappedPlayoutPolicy);
                //     }
                //     if (!(base instanceof PlayoutMoveSelector))
                //         throw new IllegalArgumentException("Wrapped policy must extend PlayoutMoveSelector");
                //     return new EpsilonGreedyWrapper((PlayoutMoveSelector) base, cfg.eps);
                // }

            case "mast":
                return new MAST(cfg.eps);

                // case "nst":
                //     return new NST(cfg.maxNGramLength, cfg.eps);

            default:
                throw new IllegalArgumentException("Unknown playout policy: " + cfg.playoutPolicy);
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

        @JsonProperty("graveBias")
        public double graveBias;

        @JsonProperty("graveRef")
        public int graveRef;

        @JsonProperty("playoutPolicy")
        public String playoutPolicy;

        @JsonProperty("eps")
        public double eps;

        @JsonProperty("maxNGramLength")
        public int maxNGramLength;
    }
}
