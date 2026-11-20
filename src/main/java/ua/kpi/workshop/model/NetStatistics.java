package ua.kpi.workshop.model;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;
import ua.stetsenkoinna.petriobj.FiringPhase;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.SimulationStatisticCollector;

public final class NetStatistics implements SimulationStatisticCollector {

    public interface FiringObserver {
        void onFiring(double time, FiringPhase phase, PetriT transition);
    }

    private final PetriP[] places;
    private final PetriT[] transitions;
    private final Map<PetriT, Integer> transitionIndex = new IdentityHashMap<>();
    private final double warmUp;
    private final double end;

    private final int[] marks;
    private final int[] buffers;
    private final double[] markIntegral;
    private final double[] bufferIntegral;
    private final int[] markMax;
    private final long[] exits;
    private double lastTime;

    private double[] checkpoints = new double[0];
    private int nextCheckpoint;
    private DoubleConsumer checkpointHandler = time -> { };
    private FiringObserver firingObserver;

    public NetStatistics(PetriNet net, double warmUp, double end) {
        this.places = net.getListP();
        this.transitions = net.getListT();
        this.warmUp = warmUp;
        this.end = end;
        for (int i = 0; i < transitions.length; i++) {
            transitionIndex.put(transitions[i], i);
        }
        marks = new int[places.length];
        buffers = new int[transitions.length];
        markIntegral = new double[places.length];
        bufferIntegral = new double[transitions.length];
        markMax = new int[places.length];
        exits = new long[transitions.length];
        takeSnapshot();
        for (int i = 0; i < places.length; i++) {
            markMax[i] = marks[i];
        }
    }

    public void setCheckpoints(double[] times, DoubleConsumer handler) {
        this.checkpoints = times.clone();
        this.checkpointHandler = handler;
        this.nextCheckpoint = 0;
    }

    public void setFiringObserver(FiringObserver observer) {
        this.firingObserver = observer;
    }

    @Override
    public boolean shouldCollect(double currentTime) {
        return true;
    }

    @Override
    public void onTimeStep(double currentTime, PetriNet net, int petriObjId) {
        advanceTo(currentTime);
        takeSnapshot();
    }

    @Override
    public void flush(double currentTime) {
    }

    @Override
    public void onFiringPhase(double time, FiringPhase phase, PetriT transition, Iterable<PetriSim> objects) {
        if (phase == FiringPhase.AFTER_ACT_OUT && time >= warmUp && time <= end) {
            Integer index = transitionIndex.get(transition);
            if (index != null) {
                exits[index]++;
            }
        }
        if (firingObserver != null && (phase == FiringPhase.AFTER_ACT_IN || phase == FiringPhase.AFTER_ACT_OUT)) {
            firingObserver.onFiring(time, phase, transition);
        }
    }

    @Override
    public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        advanceTo(end);
    }

    @Override
    public void shutdown() {
    }

    public double observedTime() {
        return Math.max(Math.min(lastTime, end) - warmUp, 0);
    }

    public double meanMark(PetriP place) {
        return markIntegral[indexOf(place)] / observedTime();
    }

    public double meanBuffer(PetriT transition) {
        return bufferIntegral[transitionIndex.get(transition)] / observedTime();
    }

    public double markIntegral(PetriP place) {
        return markIntegral[indexOf(place)];
    }

    public double bufferIntegral(PetriT transition) {
        return bufferIntegral[transitionIndex.get(transition)];
    }

    public int maxMark(PetriP place) {
        return markMax[indexOf(place)];
    }

    public long exits(PetriT transition) {
        return exits[transitionIndex.get(transition)];
    }

    private void advanceTo(double time) {
        double limit = Math.min(time, end);
        while (nextCheckpoint < checkpoints.length && checkpoints[nextCheckpoint] <= limit) {
            integrateTo(checkpoints[nextCheckpoint]);
            checkpointHandler.accept(checkpoints[nextCheckpoint]);
            nextCheckpoint++;
        }
        integrateTo(limit);
    }

    private void integrateTo(double time) {
        if (time <= lastTime) {
            return;
        }
        double from = Math.max(lastTime, warmUp);
        if (time > from) {
            double delta = time - from;
            for (int i = 0; i < marks.length; i++) {
                markIntegral[i] += marks[i] * delta;
                if (marks[i] > markMax[i]) {
                    markMax[i] = marks[i];
                }
            }
            for (int i = 0; i < buffers.length; i++) {
                bufferIntegral[i] += buffers[i] * delta;
            }
        }
        lastTime = time;
    }

    private void takeSnapshot() {
        for (int i = 0; i < places.length; i++) {
            marks[i] = places[i].getMark();
        }
        for (int i = 0; i < transitions.length; i++) {
            buffers[i] = transitions[i].getBuffer();
        }
    }

    private int indexOf(PetriP place) {
        for (int i = 0; i < places.length; i++) {
            if (places[i] == place) {
                return i;
            }
        }
        throw new IllegalArgumentException("Позиція не належить мережі: " + place.getName());
    }
}
