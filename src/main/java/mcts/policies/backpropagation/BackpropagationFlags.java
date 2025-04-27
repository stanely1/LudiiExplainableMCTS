package mcts.policies.backpropagation;

public final class BackpropagationFlags {
    public static final int SCORE_BOUNDS = 0x1;
    public static final int AMAF_STATS = (0x1 << 1);
    // for MAST / NST
    public static final int GLOBAL_ACTION_STATS = (0x1 << 2);
}
