package ua.kpi.workshop.experiments;

import static ua.kpi.workshop.experiments.Output.f;

import java.util.ArrayList;
import java.util.List;
import ua.kpi.workshop.model.WorkshopParameters;
import ua.kpi.workshop.model.WorkshopResult;

public final class Proposals {

    private record Variant(String name, WorkshopParameters parameters) {
    }

    private Proposals() {
    }

    public static void run() {
        WorkshopParameters base = WorkshopParameters.defaults();
        int steady = ExperimentSettings.STEADY_BUFFER;
        List<Variant> variants = List.of(
                new Variant("0. Базовий варіант (h = 3, t_В = 100)", base.withInitialBuffer(steady)),
                new Variant("1. Поріг h = 1", base.withThreshold(1).withInitialBuffer(steady)),
                new Variant("2. Поріг h = 0 (В2 працює нарівні з В1)", base.withThreshold(0).withInitialBuffer(steady)),
                new Variant("3. t_В = 90 хв, h = 3", base.withSecondaryMean(90)),
                new Variant("4. t_В = 90 хв, h = 1", base.withSecondaryMean(90).withThreshold(1)),
                new Variant("5. t_В = 80 хв, h = 3", base.withSecondaryMean(80)),
                new Variant("6. t_В = 80 хв, h = 1", base.withSecondaryMean(80).withThreshold(1)),
                new Variant("7. Додатковий верстат, h = 3", base.withReserveMachines(2)),
                new Variant("8. Додатковий верстат, h = 1", base.withReserveMachines(2).withThreshold(1)));

        try (Output output = new Output("proposals.txt")) {
            output.line("ПРОБНІ ЗАПУСКИ ПРОПОЗИЦІЙ");
            output.line(ExperimentSettings.REPLICATIONS + " прогонів на варіант, T_mod = " + f(ExperimentSettings.TIME_MODELING, 0)
                    + " хв, T_0 = " + f(ExperimentSettings.WARM_UP, 0) + " хв");
            output.line("ρ_В2 для варіантів 7-8 - середнє завантаження одного з двох верстатів, що підключаються за умовою");
            output.line();
            List<String[]> rows = new ArrayList<>();
            for (Variant variant : variants) {
                WorkshopResult[] results = Replicator.run(variant.parameters());
                double[] load2 = Replicator.select(results, WorkshopResult::loadSecondary2);
                double[] buffer = Replicator.select(results, WorkshopResult::meanBuffer);
                double[] wait = Replicator.select(results, WorkshopResult::meanBufferWait);
                rows.add(new String[]{
                        variant.name(),
                        f(Replicator.average(results, WorkshopResult::loadSecondary1), 4),
                        f(Stat.mean(load2), 4) + " ± " + f(Stat.confidenceHalfWidth(load2), 4),
                        f(Stat.mean(buffer), 2) + " ± " + f(Stat.confidenceHalfWidth(buffer), 2),
                        f(Stat.mean(wait), 0) + " ± " + f(Stat.confidenceHalfWidth(wait), 0),
                        f(Replicator.average(results, WorkshopResult::maxBuffer), 0),
                        f(Replicator.average(results, WorkshopResult::wasteProbability), 5)});
            }
            output.table(new String[]{"Варіант", "ρ_В1", "ρ_В2", "L_нак, деталей", "W_нак, хв", "L_max", "P_відх"}, rows);
        }
    }
}
