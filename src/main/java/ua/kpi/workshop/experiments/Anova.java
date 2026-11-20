package ua.kpi.workshop.experiments;

public final class Anova {

    public record Result(
            double[] levelMeans,
            double grandMean,
            double sumFactor,
            double sumResidual,
            int dofFactor,
            int dofResidual,
            double varianceFactor,
            double varianceResidual,
            double f,
            double fCritical) {

        public boolean significant() {
            return f > fCritical;
        }
    }

    private Anova() {
    }

    public static Result oneFactor(double[][] samples, double alpha) {
        int q = samples.length;
        int p = samples[0].length;
        for (double[] sample : samples) {
            if (sample.length != p) {
                throw new IllegalArgumentException("Кількість прогонів на всіх рівнях має бути однаковою");
            }
        }
        double[] levelMeans = new double[q];
        for (int j = 0; j < q; j++) {
            levelMeans[j] = Stat.mean(samples[j]);
        }
        double grandMean = Stat.mean(levelMeans);

        double sumFactor = 0;
        for (double levelMean : levelMeans) {
            sumFactor += (levelMean - grandMean) * (levelMean - grandMean);
        }
        sumFactor *= p;

        double sumResidual = 0;
        for (int j = 0; j < q; j++) {
            for (double y : samples[j]) {
                sumResidual += (y - levelMeans[j]) * (y - levelMeans[j]);
            }
        }

        int dofFactor = q - 1;
        int dofResidual = q * (p - 1);
        double varianceFactor = sumFactor / dofFactor;
        double varianceResidual = sumResidual / dofResidual;
        return new Result(levelMeans, grandMean, sumFactor, sumResidual, dofFactor, dofResidual,
                varianceFactor, varianceResidual, varianceFactor / varianceResidual,
                Stat.fisherCritical(dofFactor, dofResidual, alpha));
    }
}
