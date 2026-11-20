package ua.kpi.workshop.experiments;

import static ua.kpi.workshop.experiments.Output.f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ua.kpi.workshop.model.WorkshopParameters;
import ua.kpi.workshop.model.WorkshopResult;

public final class ThresholdExperiment {

    private static final int[] LEVELS = {3, 2, 1};

    private ThresholdExperiment() {
    }

    public static void runSteady() {
        try (Output output = new Output("threshold_steady.txt")) {
            output.line("ОДНОФАКТОРНИЙ ЕКСПЕРИМЕНТ: вплив порогу підключення h на завантаження В2 (сталий режим)");
            output.line(ExperimentSettings.REPLICATIONS + " прогонів на рівень, T_mod = " + f(ExperimentSettings.TIME_MODELING, 0)
                    + " хв, T_0 = " + f(ExperimentSettings.WARM_UP, 0) + " хв");
            output.line();
            WorkshopResult[][] results = new WorkshopResult[LEVELS.length][];
            for (int j = 0; j < LEVELS.length; j++) {
                results[j] = Replicator.run(ExperimentSettings.baseParameters().withThreshold(LEVELS[j]));
            }
            report(output, results);
        }
    }

    public static void runStartup() {
        double time = 25_000;
        int runs = 400;
        try (Output output = new Output("threshold_startup.txt")) {
            output.line("ОДНОФАКТОРНИЙ ЕКСПЕРИМЕНТ: вплив порогу підключення h на завантаження В2 у період пуску");
            output.line(runs + " прогонів на рівень, інтервал [0; " + f(time, 0) + "] хв, порожній початковий стан");
            output.line();
            WorkshopResult[][] results = new WorkshopResult[LEVELS.length][];
            for (int j = 0; j < LEVELS.length; j++) {
                results[j] = Replicator.run(WorkshopParameters.defaults().withThreshold(LEVELS[j]), time, 0, runs);
            }
            report(output, results);
        }
    }

    private static void report(Output output, WorkshopResult[][] results) {
        double[][] load = new double[LEVELS.length][];
        List<String[]> rows = new ArrayList<>();
        for (int j = 0; j < LEVELS.length; j++) {
            load[j] = Replicator.select(results[j], WorkshopResult::loadSecondary2);
            rows.add(new String[]{
                    String.valueOf(LEVELS[j]),
                    f(Stat.mean(load[j]), 4),
                    f(Stat.stdDev(load[j]), 4),
                    "± " + f(Stat.confidenceHalfWidth(load[j]), 4),
                    f(Replicator.average(results[j], WorkshopResult::loadSecondary1), 4),
                    f(Replicator.average(results[j], WorkshopResult::loadSecondaryTotal), 4),
                    f(Replicator.average(results[j], WorkshopResult::meanBuffer), 1),
                    f(Replicator.average(results[j], WorkshopResult::meanBufferWait), 0)});
        }
        output.table(new String[]{"Поріг h", "ρ_В2 середнє", "σ", "Довірчий інтервал", "ρ_В1", "ρ_В1+ρ_В2", "L_нак", "W_нак, хв"}, rows);

        Anova.Result anova = Anova.oneFactor(load, 0.05);
        output.line();
        output.line("Дисперсійний аналіз:");
        output.table(new String[]{"Джерело", "Сума квадратів", "Ступені вільності", "Дисперсія"}, List.of(
                new String[]{"фактор (поріг h)", e(anova.sumFactor()), String.valueOf(anova.dofFactor()), e(anova.varianceFactor())},
                new String[]{"залишок", e(anova.sumResidual()), String.valueOf(anova.dofResidual()), e(anova.varianceResidual())}));
        output.line("F = " + f(anova.f(), 3) + "; F_кр(0.05; " + anova.dofFactor() + "; " + anova.dofResidual() + ") = " + f(anova.fCritical(), 3));
        output.line(anova.significant()
                ? "Висновок: F > F_кр, вплив порогу на завантаження В2 статистично значущий."
                : "Висновок: F < F_кр, вплив порогу на завантаження В2 статистично не значущий.");
    }

    static String e(double value) {
        return String.format(Locale.ROOT, "%.4E", value);
    }
}
