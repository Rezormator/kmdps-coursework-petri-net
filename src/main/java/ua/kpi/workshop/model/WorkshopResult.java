package ua.kpi.workshop.model;

public record WorkshopResult(
        double observedTime,
        double loadSecondary2,
        double loadSecondary1,
        double wasteProbability,
        double meanBuffer,
        int maxBuffer,
        double meanBufferWait,
        double loadPrimary1,
        double loadPrimary2,
        double meanQueuePrimary,
        double meanQueueRework,
        long arrived,
        long waste,
        long reworked,
        long finished1,
        long finished2,
        double shareFirstPass1) {

    public double loadSecondaryTotal() {
        return loadSecondary1 + loadSecondary2;
    }
}
