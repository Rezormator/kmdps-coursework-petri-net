package ua.kpi.workshop.model;

import java.util.ArrayList;
import ua.kpi.workshop.net.WorkshopNet;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;

public final class WorkshopModel {

    public static final String GENERATOR = "Генератор";
    public static final String QUEUE_PRIMARY = "ЧергаП";
    public static final String PRIMARY1_FREE = "П1вільний";
    public static final String PRIMARY2_FREE = "П2вільний";
    public static final String QUEUE_REWORK = "ЧергаПовт";
    public static final String BUFFER = "Накопичувач";
    public static final String SECONDARY1_FREE = "В1вільний";
    public static final String SECONDARY2_FREE = "В2вільний";
    public static final String WASTE = "Відходи";

    public static final String ARRIVAL = "Надходження";
    public static final String PRIMARY1 = "Обробка на П1";
    public static final String PRIMARY2 = "Обробка на П2";
    public static final String GOOD1 = "П1: якісна";
    public static final String BAD1 = "П1: брак";
    public static final String GOOD2 = "П2: якісна";
    public static final String BAD2 = "П2: брак";
    public static final String REWORK = "Повторна обробка на П2";
    public static final String GOOD_REWORK = "Повт: якісна";
    public static final String BAD_REWORK = "Повт: відходи";
    public static final String SECONDARY1 = "Обробка на В1";
    public static final String SECONDARY2 = "Обробка на В2";

    private static final Object CONSTRUCTION_LOCK = new Object();

    private final WorkshopParameters parameters;
    private final PetriNet net;

    public WorkshopModel(WorkshopParameters parameters) {
        this.parameters = parameters;
        synchronized (CONSTRUCTION_LOCK) {
            try {
                net = WorkshopNet.CreateNetWorkshop();
            } catch (ExceptionInvalidNetStructure | ExceptionInvalidTimeDelay e) {
                throw new IllegalStateException("Не вдалося побудувати мережу Петрі", e);
            }
        }
        apply(parameters);
    }

    public PetriNet net() {
        return net;
    }

    public WorkshopParameters parameters() {
        return parameters;
    }

    public PetriP place(String name) {
        for (PetriP place : net.getListP()) {
            if (place.getName().equals(name)) {
                return place;
            }
        }
        throw new IllegalArgumentException("У мережі немає позиції " + name);
    }

    public PetriT transition(String name) {
        for (PetriT transition : net.getListT()) {
            if (transition.getName().equals(name)) {
                return transition;
            }
        }
        throw new IllegalArgumentException("У мережі немає переходу " + name);
    }

    public NetStatistics simulate(double timeModeling, double warmUp) {
        return simulate(timeModeling, warmUp, null);
    }

    public NetStatistics simulate(double timeModeling, double warmUp, java.util.function.Consumer<NetStatistics> setup) {
        NetStatistics statistics = new NetStatistics(net, warmUp, timeModeling);
        if (setup != null) {
            setup.accept(statistics);
        }
        ArrayList<PetriSim> objects = new ArrayList<>();
        objects.add(new PetriSim(net));
        PetriObjModel model = new PetriObjModel(objects);
        model.setIsProtokol(false);
        model.setIsStatistics(true);
        model.setStatisticCollector(statistics);
        model.go(timeModeling);
        return statistics;
    }

    public WorkshopResult run(double timeModeling, double warmUp) {
        return collect(simulate(timeModeling, warmUp));
    }

    public static WorkshopResult run(WorkshopParameters parameters, double timeModeling, double warmUp) {
        return new WorkshopModel(parameters).run(timeModeling, warmUp);
    }

    public WorkshopResult collect(NetStatistics s) {
        long arrived = s.exits(transition(ARRIVAL));
        long waste = s.exits(transition(BAD_REWORK));
        long finished1 = s.exits(transition(SECONDARY1));
        long finished2 = s.exits(transition(SECONDARY2));
        long firstPass1 = s.exits(transition(PRIMARY1));
        long firstPass2 = s.exits(transition(PRIMARY2));
        PetriP buffer = place(BUFFER);
        return new WorkshopResult(
                s.observedTime(),
                s.meanBuffer(transition(SECONDARY2)) / parameters.reserveMachines(),
                s.meanBuffer(transition(SECONDARY1)),
                arrived > 0 ? (double) waste / arrived : 0,
                s.meanMark(buffer),
                s.maxMark(buffer),
                finished1 + finished2 > 0 ? s.markIntegral(buffer) / (finished1 + finished2) : 0,
                s.meanBuffer(transition(PRIMARY1)),
                s.meanBuffer(transition(PRIMARY2)) + s.meanBuffer(transition(REWORK)),
                s.meanMark(place(QUEUE_PRIMARY)),
                s.meanMark(place(QUEUE_REWORK)),
                arrived,
                waste,
                s.exits(transition(REWORK)),
                finished1,
                finished2,
                firstPass1 + firstPass2 > 0 ? (double) firstPass1 / (firstPass1 + firstPass2) : 0);
    }

    private void apply(WorkshopParameters p) {
        setDelay(ARRIVAL, p.arrivalMean());
        setDelay(PRIMARY1, p.primary1Mean());
        setDelay(PRIMARY2, p.primary2Mean());
        setDelay(REWORK, p.primary2Mean());
        setDelay(SECONDARY1, p.secondaryMean());
        setDelay(SECONDARY2, p.secondaryMean());
        setOutcome(GOOD1, BAD1, p.defect1());
        setOutcome(GOOD2, BAD2, p.defect2());
        setOutcome(GOOD_REWORK, BAD_REWORK, p.defect2());
        place(SECONDARY2_FREE).setMark(p.reserveMachines());
        place(BUFFER).setMark(p.initialBuffer());
        setConnectionCondition(p.threshold() + 1);
    }

    private void setDelay(String name, double mean) {
        PetriT transition = transition(name);
        transition.setDistribution(parameters.deterministic() ? null : "exp", mean);
        transition.setParamDeviation(0.0);
    }

    private void setOutcome(String good, String bad, double defect) {
        transition(good).setProbability(1 - defect);
        transition(bad).setProbability(defect);
    }

    private void setConnectionCondition(int quantity) {
        PetriT secondary2 = transition(SECONDARY2);
        boolean found = false;
        for (ArcIn arc : net.getArcIn()) {
            if (arc.getIsInf() && arc.getNameT().equals(SECONDARY2) && arc.getNameP().equals(BUFFER)) {
                arc.setQuantity(quantity);
                found = true;
            }
        }
        if (!found) {
            throw new IllegalStateException("У мережі немає інформаційної дуги «" + BUFFER + "» → «" + SECONDARY2 + "»");
        }
        try {
            secondary2.createInP(net.getArcIn());
        } catch (ExceptionInvalidTimeDelay e) {
            throw new IllegalStateException(e);
        }
    }
}
