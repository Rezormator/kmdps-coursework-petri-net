package ua.kpi.workshop.model;

public record WorkshopParameters(
        double arrivalMean,
        double primary1Mean,
        double primary2Mean,
        double defect1,
        double defect2,
        double secondaryMean,
        int threshold,
        int reserveMachines,
        int initialBuffer,
        boolean deterministic) {

    public static final int NEVER = 1_000_000;

    public static WorkshopParameters defaults() {
        return new WorkshopParameters(50, 40, 60, 0.04, 0.08, 100, 3, 1, 0, false);
    }

    public WorkshopParameters withArrivalMean(double value) {
        return new WorkshopParameters(value, primary1Mean, primary2Mean, defect1, defect2, secondaryMean,
                threshold, reserveMachines, initialBuffer, deterministic);
    }

    public WorkshopParameters withPrimaryMeans(double first, double second) {
        return new WorkshopParameters(arrivalMean, first, second, defect1, defect2, secondaryMean,
                threshold, reserveMachines, initialBuffer, deterministic);
    }

    public WorkshopParameters withDefects(double first, double second) {
        return new WorkshopParameters(arrivalMean, primary1Mean, primary2Mean, first, second, secondaryMean,
                threshold, reserveMachines, initialBuffer, deterministic);
    }

    public WorkshopParameters withSecondaryMean(double value) {
        return new WorkshopParameters(arrivalMean, primary1Mean, primary2Mean, defect1, defect2, value,
                threshold, reserveMachines, initialBuffer, deterministic);
    }

    public WorkshopParameters withThreshold(int value) {
        return new WorkshopParameters(arrivalMean, primary1Mean, primary2Mean, defect1, defect2, secondaryMean,
                value, reserveMachines, initialBuffer, deterministic);
    }

    public WorkshopParameters withReserveMachines(int value) {
        return new WorkshopParameters(arrivalMean, primary1Mean, primary2Mean, defect1, defect2, secondaryMean,
                threshold, value, initialBuffer, deterministic);
    }

    public WorkshopParameters withInitialBuffer(int value) {
        return new WorkshopParameters(arrivalMean, primary1Mean, primary2Mean, defect1, defect2, secondaryMean,
                threshold, reserveMachines, value, deterministic);
    }

    public WorkshopParameters withDeterministic(boolean value) {
        return new WorkshopParameters(arrivalMean, primary1Mean, primary2Mean, defect1, defect2, secondaryMean,
                threshold, reserveMachines, initialBuffer, value);
    }
}
