package ua.kpi.workshop.experiments;

import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.distribution.TDistribution;

public final class Stat {

    private Stat() {
    }

    public static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    public static double stdDev(double[] values) {
        if (values.length < 2) {
            return 0;
        }
        double mean = mean(values);
        double sum = 0;
        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    public static double studentQuantile(int degreesOfFreedom, double confidence) {
        return new TDistribution(degreesOfFreedom).inverseCumulativeProbability(1 - (1 - confidence) / 2);
    }

    public static double confidenceHalfWidth(double[] values) {
        int n = values.length;
        return studentQuantile(n - 1, 0.95) * stdDev(values) / Math.sqrt(n);
    }

    public static double fisherCritical(int dofNumerator, int dofDenominator, double alpha) {
        return new FDistribution(dofNumerator, dofDenominator).inverseCumulativeProbability(1 - alpha);
    }

    public static int requiredReplications(double sigma, double epsilon, double confidence) {
        double n = sigma * sigma / (epsilon * epsilon * (1 - confidence));
        return Math.max(1, (int) Math.ceil(n));
    }
}
