package ua.kpi.workshop.experiments;

import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import ua.kpi.workshop.model.WorkshopModel;
import ua.kpi.workshop.model.WorkshopParameters;
import ua.kpi.workshop.model.WorkshopResult;

public final class Replicator {

    private Replicator() {
    }

    public static WorkshopResult[] run(WorkshopParameters parameters, double timeModeling, double warmUp, int count) {
        return IntStream.range(0, count)
                .parallel()
                .mapToObj(i -> WorkshopModel.run(parameters, timeModeling, warmUp))
                .toArray(WorkshopResult[]::new);
    }

    public static WorkshopResult[] run(WorkshopParameters parameters) {
        return run(parameters, ExperimentSettings.TIME_MODELING, ExperimentSettings.WARM_UP, ExperimentSettings.REPLICATIONS);
    }

    public static double[] select(WorkshopResult[] results, ToDoubleFunction<WorkshopResult> response) {
        double[] values = new double[results.length];
        for (int i = 0; i < results.length; i++) {
            values[i] = response.applyAsDouble(results[i]);
        }
        return values;
    }

    public static double average(WorkshopResult[] results, ToDoubleFunction<WorkshopResult> response) {
        return Stat.mean(select(results, response));
    }
}
