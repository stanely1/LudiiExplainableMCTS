package mcts.explanations.outliers;

public final class OutliersThresholds {
    private final double epsilon;
    private final double slightDiff;
    private final double lowAbsVal;
    private final double highAbsVal;

    private OutliersThresholds(double epsilon, double slightDiff, double lowAbsVal, double highAbsVal) {
        this.epsilon = epsilon;
        this.slightDiff = slightDiff;
        this.lowAbsVal = lowAbsVal;
        this.highAbsVal = highAbsVal;
    }

    public static OutliersThresholds defaultThresholds() {
        return new OutliersThresholds(1e-8, 0.1, 0.1, 0.63);
    }

    // ---------------------------------------------------------------------------------------------------------
    public double getEpsilon() {
        return epsilon;
    }

    public double getSlightDiff() {
        return slightDiff;
    }

    public double getLowAbsVal() {
        return lowAbsVal;
    }

    public double getHighAbsVal() {
        return highAbsVal;
    }
}
