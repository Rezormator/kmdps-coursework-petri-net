package ua.kpi.workshop.experiments;

import static ua.kpi.workshop.experiments.Output.f;
import static ua.kpi.workshop.experiments.ThresholdExperiment.e;

import java.util.ArrayList;
import java.util.List;
import org.knowm.xchart.style.Styler;
import ua.kpi.workshop.model.WorkshopParameters;
import ua.kpi.workshop.model.WorkshopResult;

public final class FactorialExperiment {

    private static final int THRESHOLD_LOW = 3;
    private static final int THRESHOLD_HIGH = 1;
    private static final double SECONDARY_LOW = 100;
    private static final double SECONDARY_HIGH = 80;

    private FactorialExperiment() {
    }

    public static void run() {
        int p = ExperimentSettings.REPLICATIONS;
        int[][] plan = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        double[][] y = new double[plan.length][];
        double[] means = new double[plan.length];
        for (int i = 0; i < plan.length; i++) {
            y[i] = Replicator.select(Replicator.run(point(plan[i][0], plan[i][1])), WorkshopResult::loadSecondary2);
            means[i] = Stat.mean(y[i]);
        }

        try (Output output = new Output("factorial.txt")) {
            output.line("ФАКТОРНИЙ ЕКСПЕРИМЕНТ 2²: відгук - завантаження верстата В2");
            output.line("Фактор A (x1): поріг h, рівні " + THRESHOLD_LOW + " (-1) та " + THRESHOLD_HIGH + " (+1)");
            output.line("Фактор B (x2): час вторинної обробки t_В, рівні " + f(SECONDARY_LOW, 0) + " хв (-1) та " + f(SECONDARY_HIGH, 0) + " хв (+1)");
            output.line(p + " прогонів у кожній точці плану, T_mod = " + f(ExperimentSettings.TIME_MODELING, 0)
                    + " хв, T_0 = " + f(ExperimentSettings.WARM_UP, 0) + " хв");
            output.line();
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < plan.length; i++) {
                rows.add(new String[]{String.valueOf(i + 1), sign(plan[i][0]), sign(plan[i][1]),
                        String.valueOf(plan[i][0] < 0 ? THRESHOLD_LOW : THRESHOLD_HIGH),
                        f(plan[i][1] < 0 ? SECONDARY_LOW : SECONDARY_HIGH, 0),
                        f(means[i], 4), f(Stat.stdDev(y[i]), 4)});
            }
            output.table(new String[]{"№", "x1", "x2", "h", "t_В, хв", "ρ_В2 середнє", "σ"}, rows);

            double b0 = 0;
            double b1 = 0;
            double b2 = 0;
            double b12 = 0;
            for (int i = 0; i < plan.length; i++) {
                b0 += means[i];
                b1 += plan[i][0] * means[i];
                b2 += plan[i][1] * means[i];
                b12 += plan[i][0] * plan[i][1] * means[i];
            }
            b0 /= plan.length;
            b1 /= plan.length;
            b2 /= plan.length;
            b12 /= plan.length;

            int total = plan.length * p;
            double sumResidual = 0;
            for (int i = 0; i < plan.length; i++) {
                for (double value : y[i]) {
                    sumResidual += (value - means[i]) * (value - means[i]);
                }
            }
            int dofResidual = total - plan.length;
            double varianceResidual = sumResidual / dofResidual;
            double critical = Stat.fisherCritical(1, dofResidual, 0.05);

            output.line();
            output.line("Дисперсійний аналіз:");
            output.table(new String[]{"Джерело", "Сума квадратів", "Ступені вільності", "Дисперсія", "F", "Вплив"}, List.of(
                    effectRow("фактор A (поріг)", total * b1 * b1, varianceResidual, critical),
                    effectRow("фактор B (час t_В)", total * b2 * b2, varianceResidual, critical),
                    effectRow("взаємодія AB", total * b12 * b12, varianceResidual, critical),
                    new String[]{"залишок", e(sumResidual), String.valueOf(dofResidual), e(varianceResidual), "", ""}));
            output.line("F_кр(0.05; 1; " + dofResidual + ") = " + f(critical, 3));
            output.line();
            output.line("Рівняння регресії в нормованих факторах:");
            output.line("  ρ_В2 = " + f(b0, 4) + " " + signed(b1) + "·x1 " + signed(b2) + "·x2 " + signed(b12) + "·x1·x2");
            output.line("  x1 = (2 - h) / 1,  x2 = (90 - t_В) / 10");

            responseTable(output);
        }
    }

    private static void responseTable(Output output) {
        int[] thresholds = {3, 2, 1};
        double[] times = {100, 90, 80};
        List<Charts.Series> series = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        double[] x = {3, 2, 1};
        for (double time : times) {
            double[] values = new double[thresholds.length];
            String[] row = new String[thresholds.length + 1];
            row[0] = f(time, 0);
            for (int j = 0; j < thresholds.length; j++) {
                WorkshopParameters parameters = WorkshopParameters.defaults().withThreshold(thresholds[j])
                        .withSecondaryMean(time).withInitialBuffer(time >= 100 ? ExperimentSettings.STEADY_BUFFER : 0);
                values[j] = Replicator.average(Replicator.run(parameters), WorkshopResult::loadSecondary2);
                row[j + 1] = f(values[j], 4);
            }
            rows.add(row);
            series.add(new Charts.Series("t_В = " + f(time, 0) + " хв", x, values));
        }
        output.line();
        output.line("Завантаження верстата В2 залежно від порогу h та часу вторинної обробки t_В:");
        output.table(new String[]{"t_В, хв", "h = 3", "h = 2", "h = 1"}, rows);
        Charts.lines("factorial_response.png", "Поріг підключення h, деталей", "Завантаження верстата В2",
                series, 0.40, 1.02, true, Styler.LegendPosition.InsideSW);
    }

    private static WorkshopParameters point(int x1, int x2) {
        double secondary = x2 < 0 ? SECONDARY_LOW : SECONDARY_HIGH;
        return WorkshopParameters.defaults()
                .withThreshold(x1 < 0 ? THRESHOLD_LOW : THRESHOLD_HIGH)
                .withSecondaryMean(secondary)
                .withInitialBuffer(secondary >= 100 ? ExperimentSettings.STEADY_BUFFER : 0);
    }

    private static String[] effectRow(String name, double sum, double varianceResidual, double critical) {
        double fValue = sum / varianceResidual;
        return new String[]{name, e(sum), "1", e(sum), f(fValue, 1), fValue > critical ? "значущий" : "не значущий"};
    }

    private static String sign(int value) {
        return value > 0 ? "+1" : "-1";
    }

    private static String signed(double value) {
        return (value >= 0 ? "+" : "-") + f(Math.abs(value), 4);
    }
}
