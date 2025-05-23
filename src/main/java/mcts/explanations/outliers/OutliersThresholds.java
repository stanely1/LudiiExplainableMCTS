package mcts.explanations.outliers;

public final class OutliersThresholds {
    private final double epsilon;
    private final double relativeDelta;
    private final double absoluteNeutral;
    private final double absoluteGood;

    private OutliersThresholds(double epsilon, double relativeDelta, double absoluteNeutral, double absoluteGood) {
        this.epsilon = epsilon;
        this.relativeDelta = relativeDelta;
        this.absoluteNeutral = absoluteNeutral;
        this.absoluteGood = absoluteGood;
    }

    public static OutliersThresholds defaultThresholds() {
        return new OutliersThresholds(1e-8, 0.1, 0.1, 0.63);
    }

    // ---------------------------------------------------------------------------------------------------------
    public double getEpsilon() {
        return epsilon;
    }

    public double getRelativeDelta() {
        return relativeDelta;
    }

    public double getAbsoluteNeutral() {
        return absoluteNeutral;
    }

    public double getAbsoluteGood() {
        return absoluteGood;
    }
}
