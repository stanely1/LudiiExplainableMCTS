package mcts.policies.backpropagation;

import java.util.ArrayList;
import java.util.List;

public final class BackpropagationFlags {
    public static final int SCORE_BOUNDS = 0x1;
    public static final int AMAF_STATS = (0x1 << 1);
    public static final int GLOBAL_ACTION_STATS = (0x1 << 2);
    public static final int GLOBAL_NGRAM_ACTION_STATS = (0x1 << 3);
    public static final int PROOF_DISPROOF_NUMBERS = (0x1 << 4);

    public static String flagsToString(final int flags) {
        List<String> activatedFlags = new ArrayList<>();

        if ((flags & SCORE_BOUNDS) != 0) {
            activatedFlags.add("score bounds");
        }

        if ((flags & AMAF_STATS) != 0) {
            activatedFlags.add("AMAF");
        }

        if ((flags & GLOBAL_ACTION_STATS) != 0) {
            activatedFlags.add("global action stats");
        }

        if ((flags & GLOBAL_NGRAM_ACTION_STATS) != 0) {
            activatedFlags.add("global N-Gram stats");
        }

        if ((flags & PROOF_DISPROOF_NUMBERS) != 0) {
            activatedFlags.add("PNS numbers");
        }

        return String.join(", ", activatedFlags);
    }
}
