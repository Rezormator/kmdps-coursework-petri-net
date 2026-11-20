package ua.kpi.workshop.experiments;

import ua.kpi.workshop.model.WorkshopParameters;

public final class ExperimentSettings {

    public static final double TIME_MODELING = 12_000_000;
    public static final double WARM_UP = 2_000_000;
    public static final int REPLICATIONS = 40;
    public static final int STEADY_BUFFER = 200;

    private ExperimentSettings() {
    }

    public static WorkshopParameters baseParameters() {
        return WorkshopParameters.defaults().withInitialBuffer(STEADY_BUFFER);
    }
}
