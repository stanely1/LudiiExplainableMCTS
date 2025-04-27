package mcts.policies.backpropagation;

import java.util.ArrayList;
import java.util.List;

public final class BackpropagationFlags {
    public static final int SCORE_BOUNDS = 0x1;
    public static final int AMAF_STATS = (0x1 << 1);
    // for MAST / NST
    public static final int GLOBAL_ACTION_STATS = (0x1 << 2);

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

        return String.join(", ", activatedFlags);
    }
}
