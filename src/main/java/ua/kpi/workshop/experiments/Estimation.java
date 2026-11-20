package ua.kpi.workshop.experiments;

import static ua.kpi.workshop.experiments.Output.f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import ua.kpi.workshop.model.WorkshopParameters;
import ua.kpi.workshop.model.WorkshopResult;

public final class Estimation {

    private static final double EPSILON_LOAD = 0.005;
    private static final double EPSILON_WASTE = 0.0002;

    private record Response(String name, ToDoubleFunction<WorkshopResult> value, int digits) {
    }

    private Estimation() {
    }

    public static void runReplicationStudy() {
        int pilot = 20;
        WorkshopResult[] results = Replicator.run(ExperimentSettings.baseParameters(),
                ExperimentSettings.TIME_MODELING, ExperimentSettings.WARM_UP, pilot);
        double[] load = Replicator.select(results, WorkshopResult::loadSecondary2);
        double[] waste = Replicator.select(results, WorkshopResult::wasteProbability);

        try (Output output = new Output("replications.txt")) {
            output.line("КІЛЬКІСТЬ ПОВТОРНИХ ПРОГОНІВ (пробна серія з " + pilot + " прогонів, T_mod = "
                    + f(ExperimentSettings.TIME_MODELING, 0) + " хв, T_0 = " + f(ExperimentSettings.WARM_UP, 0) + " хв)");
            output.table(new String[]{"Відгук", "Середнє", "σ", "ε", "n = 20·σ²/ε²"}, List.of(
                    new String[]{"Завантаження В2", f(Stat.mean(load), 5), f(Stat.stdDev(load), 5), "0.005",
                            String.valueOf(Stat.requiredReplications(Stat.stdDev(load), EPSILON_LOAD, 0.95))},
                    new String[]{"Ймовірність відходів", f(Stat.mean(waste), 6), f(Stat.stdDev(waste), 6), "0.0002",
                            String.valueOf(Stat.requiredReplications(Stat.stdDev(waste), EPSILON_WASTE, 0.95))}));
            output.line();
            output.line("Прийнята кількість прогонів: " + ExperimentSettings.REPLICATIONS);
        }
    }

    public static void runEstimates() {
        WorkshopResult[] results = Replicator.run(ExperimentSettings.baseParameters());
        List<Response> responses = List.of(
                new Response("Завантаження верстата В2", WorkshopResult::loadSecondary2, 4),
                new Response("Ймовірність появи відходів", WorkshopResult::wasteProbability, 5),
                new Response("Завантаження верстата В1", WorkshopResult::loadSecondary1, 4),
                new Response("Середня кількість деталей у накопичувачі", WorkshopResult::meanBuffer, 1),
                new Response("Середній час очікування в накопичувачі, хв", WorkshopResult::meanBufferWait, 0),
                new Response("Завантаження верстата П1", WorkshopResult::loadPrimary1, 4),
                new Response("Завантаження верстата П2", WorkshopResult::loadPrimary2, 4),
                new Response("Середня черга первинної обробки", WorkshopResult::meanQueuePrimary, 3),
                new Response("Середня черга повторної обробки", WorkshopResult::meanQueueRework, 4),
                new Response("Частка деталей, оброблених П1", WorkshopResult::shareFirstPass1, 4),
                new Response("Кількість деталей, що надійшли", r -> r.arrived(), 0),
                new Response("Кількість відходів", r -> r.waste(), 1));

        try (Output output = new Output("estimates.txt")) {
            output.line("ОЦІНКИ ВИХІДНИХ ХАРАКТЕРИСТИК (" + ExperimentSettings.REPLICATIONS + " прогонів, T_mod = "
                    + f(ExperimentSettings.TIME_MODELING, 0) + " хв, T_0 = " + f(ExperimentSettings.WARM_UP, 0) + " хв, поріг h = 3)");
            List<String[]> rows = new ArrayList<>();
            for (Response response : responses) {
                double[] values = Replicator.select(results, response.value());
                int spreadDigits = response.digits() == 0 ? 1 : response.digits();
                rows.add(new String[]{response.name(), f(Stat.mean(values), response.digits()),
                        f(Stat.stdDev(values), spreadDigits), "± " + f(Stat.confidenceHalfWidth(values), spreadDigits)});
            }
            output.table(new String[]{"Характеристика", "Середнє", "σ", "Довірчий інтервал (0.95)"}, rows);

            WorkshopParameters p = ExperimentSettings.baseParameters();
            double share1 = Replicator.average(results, WorkshopResult::shareFirstPass1);
            double wasteTheory = (share1 * p.defect1() + (1 - share1) * p.defect2()) * p.defect2();
            double balance = p.secondaryMean() / p.arrivalMean() * (1 - wasteTheory);
            output.line();
            output.line("Аналітична оцінка ймовірності відходів (s1·q1 + (1-s1)·q2)·q2 = " + f(wasteTheory, 5));
            output.line("Баланс потоку: ρ_В1 + ρ_В2 = (t_В / t_надх)·(1 - P_відх) = " + f(balance, 4)
                    + "; отримано " + f(Replicator.average(results, WorkshopResult::loadSecondaryTotal), 4));
        }
    }
}
