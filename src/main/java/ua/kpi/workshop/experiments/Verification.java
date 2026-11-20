package ua.kpi.workshop.experiments;

import static ua.kpi.workshop.experiments.Output.f;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import ua.kpi.workshop.model.NetStatistics;
import ua.kpi.workshop.model.WorkshopModel;
import ua.kpi.workshop.model.WorkshopParameters;
import ua.kpi.workshop.model.WorkshopResult;
import ua.stetsenkoinna.petriobj.FiringPhase;
import ua.stetsenkoinna.petriobj.PetriT;

public final class Verification {

    private static final double TIME_MODELING = 2_000_000;
    private static final int QUEUE_RUNS = 10;

    private record Scenario(String name, WorkshopParameters parameters) {
    }

    private record Check(String name, double expected, double actual, double tolerance) {
        boolean passed() {
            return Math.abs(expected - actual) <= tolerance;
        }
    }

    private record Event(double time, FiringPhase phase, String transition, String marking) {
    }

    private Verification() {
    }

    public static void printProtocol() {
        WorkshopModel model = new WorkshopModel(WorkshopParameters.defaults());
        List<Event> events = new ArrayList<>();
        model.simulate(200_000, 0, statistics -> statistics.setFiringObserver(
                (time, phase, transition) -> events.add(new Event(time, phase, transition.getName(), marking(model)))));

        try (Output output = new Output("protocol.txt")) {
            output.line("ПРОТОКОЛ ПОДІЙ (параметри за завданням, час у хвилинах)");
            output.line("Формат: момент часу, подія (вхід маркерів у перехід / вихід маркерів з переходу),");
            output.line("перехід, маркірування основних позицій після події:");
            output.line("ЧП - черга первинної обробки, П1/П2 - верстат вільний (1) чи зайнятий (0),");
            output.line("ЧПовт - черга повторної обробки, Нак - накопичувач, В1/В2 - верстат вільний (1) чи зайнятий (0),");
            output.line("Відх - кількість відходів.");

            output.line();
            output.line("Фрагмент 1. Початок роботи моделі");
            print(output, events, 0, 21);

            int connect = first(events, 0, WorkshopModel.SECONDARY2, FiringPhase.AFTER_ACT_IN);
            output.line();
            output.line("Фрагмент 2. Перше підключення верстата В2 (у накопичувачі більше трьох деталей)");
            print(output, events, connect - 9, connect + 1);

            int waste = first(events, 0, WorkshopModel.BAD_REWORK, FiringPhase.AFTER_ACT_OUT);
            int reworkStart = last(events, waste, WorkshopModel.REWORK, FiringPhase.AFTER_ACT_IN);
            int defect = Math.max(last(events, reworkStart, WorkshopModel.BAD1, FiringPhase.AFTER_ACT_OUT),
                    last(events, reworkStart, WorkshopModel.BAD2, FiringPhase.AFTER_ACT_OUT));
            output.line();
            output.line("Фрагмент 3. Брак, повторна обробка на П2 та поява відходів");
            print(output, events, defect - 2, waste);
        }
    }

    private static String marking(WorkshopModel model) {
        return "ЧП=" + model.place(WorkshopModel.QUEUE_PRIMARY).getMark()
                + " П1=" + model.place(WorkshopModel.PRIMARY1_FREE).getMark()
                + " П2=" + model.place(WorkshopModel.PRIMARY2_FREE).getMark()
                + " ЧПовт=" + model.place(WorkshopModel.QUEUE_REWORK).getMark()
                + " Нак=" + model.place(WorkshopModel.BUFFER).getMark()
                + " В1=" + model.place(WorkshopModel.SECONDARY1_FREE).getMark()
                + " В2=" + model.place(WorkshopModel.SECONDARY2_FREE).getMark()
                + " Відх=" + model.place(WorkshopModel.WASTE).getMark();
    }

    private static int first(List<Event> events, int from, String transition, FiringPhase phase) {
        for (int i = from; i < events.size(); i++) {
            if (events.get(i).transition().equals(transition) && events.get(i).phase() == phase) {
                return i;
            }
        }
        return events.size() - 1;
    }

    private static int last(List<Event> events, int before, String transition, FiringPhase phase) {
        for (int i = before; i >= 0; i--) {
            if (events.get(i).transition().equals(transition) && events.get(i).phase() == phase) {
                return i;
            }
        }
        return 0;
    }

    private static void print(Output output, List<Event> events, int from, int to) {
        for (int i = Math.max(from, 0); i <= Math.min(to, events.size() - 1); i++) {
            Event e = events.get(i);
            String kind = e.phase() == FiringPhase.AFTER_ACT_IN ? "вхід " : "вихід";
            output.line(String.format(java.util.Locale.ROOT, "%10.3f  %s  %-23s | %s", e.time(), kind, e.transition(), e.marking()));
        }
    }

    public static void run() {
        int never = WorkshopParameters.NEVER;
        WorkshopParameters base = WorkshopParameters.defaults();
        WorkshopParameters instant = base.withPrimaryMeans(0.001, 0.001).withDefects(0, 0);
        List<Scenario> scenarios = List.of(
                new Scenario("базовий (за завданням)", base),
                new Scenario("без браку", base.withArrivalMean(60).withDefects(0, 0)),
                new Scenario("суцільний брак", base.withArrivalMean(200).withDefects(1, 1)),
                new Scenario("В2 не підключається", base.withSecondaryMean(40).withThreshold(never)),
                new Scenario("аналог M/M/1", instant.withSecondaryMean(40).withThreshold(never)),
                new Scenario("аналог M/M/2", instant.withSecondaryMean(60).withThreshold(0)),
                new Scenario("детермінований, t_В = 40", base.withDefects(0, 0).withSecondaryMean(40).withDeterministic(true)),
                new Scenario("детермінований, t_В = 100", base.withDefects(0, 0).withDeterministic(true)),
                new Scenario("t_В = 80, поріг 3", base.withSecondaryMean(80).withThreshold(3)),
                new Scenario("t_В = 80, поріг 2", base.withSecondaryMean(80).withThreshold(2)),
                new Scenario("t_В = 80, поріг 1", base.withSecondaryMean(80).withThreshold(1)),
                new Scenario("рідкі надходження", base.withArrivalMean(100)));

        WorkshopResult[] r = IntStream.range(0, scenarios.size()).parallel()
                .mapToObj(i -> WorkshopModel.run(scenarios.get(i).parameters(), TIME_MODELING, 0))
                .toArray(WorkshopResult[]::new);

        try (Output output = new Output("verification.txt")) {
            output.line("ВЕРИФІКАЦІЯ МОДЕЛІ (час моделювання " + f(TIME_MODELING, 0) + " хв, перехідний період не відкидається)");
            output.line();
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < scenarios.size(); i++) {
                WorkshopParameters p = scenarios.get(i).parameters();
                rows.add(new String[]{
                        String.valueOf(i + 1), scenarios.get(i).name(),
                        number(p.arrivalMean()), number(p.primary1Mean()), number(p.primary2Mean()),
                        number(p.defect1()), number(p.defect2()), number(p.secondaryMean()),
                        p.threshold() == never ? "∞" : String.valueOf(p.threshold()),
                        f(r[i].loadPrimary1(), 4), f(r[i].loadPrimary2(), 4), f(r[i].wasteProbability(), 5),
                        f(r[i].loadSecondary1(), 4), f(r[i].loadSecondary2(), 4),
                        f(r[i].meanBuffer(), 3), String.valueOf(r[i].maxBuffer())});
            }
            output.table(new String[]{"№", "Сценарій", "t_надх", "t_П1", "t_П2", "q1", "q2", "t_В", "h",
                    "ρ_П1", "ρ_П2", "P_відх", "ρ_В1", "ρ_В2", "L_нак", "L_max"}, rows);

            List<Check> checks = new ArrayList<>();
            double share1 = r[0].shareFirstPass1();
            checks.add(new Check("1: баланс вторинної обробки ρ_В1+ρ_В2 = λ(1-P)·t_В", balance(r, scenarios, 0), r[0].loadSecondaryTotal(), tolerance(r, scenarios, 0)));
            checks.add(new Check("1: P_відх = (s1·q1 + (1-s1)·q2)·q2", (share1 * base.defect1() + (1 - share1) * base.defect2()) * base.defect2(), r[0].wasteProbability(), 0.0005));
            checks.add(new Check("1: ρ_П1 = λ·s1·t_П1", lambda(r, 0) * share1 * base.primary1Mean(), r[0].loadPrimary1(), 0.01));
            checks.add(new Check("2: без браку P_відх = 0", 0, r[1].wasteProbability(), 0));
            checks.add(new Check("2: баланс ρ_В1+ρ_В2 = λ·t_В", balance(r, scenarios, 1), r[1].loadSecondaryTotal(), tolerance(r, scenarios, 1)));
            checks.add(new Check("3: суцільний брак P_відх = 1", 1, r[2].wasteProbability(), 0.002));
            checks.add(new Check("3: вторинна обробка простоює, ρ_В1+ρ_В2 = 0", 0, r[2].loadSecondaryTotal(), 0));
            checks.add(new Check("4: В2 не підключається, ρ_В2 = 0", 0, r[3].loadSecondary2(), 0));
            checks.add(new Check("4: ρ_В1 = λ(1-P)·t_В", balance(r, scenarios, 3), r[3].loadSecondary1(), tolerance(r, scenarios, 3)));
            double rho = r[4].loadSecondary1();
            double[] queue1 = Replicator.select(Replicator.run(scenarios.get(4).parameters(), TIME_MODELING, 0, QUEUE_RUNS), WorkshopResult::meanBuffer);
            double[] queue2 = Replicator.select(Replicator.run(scenarios.get(5).parameters(), TIME_MODELING, 0, QUEUE_RUNS), WorkshopResult::meanBuffer);
            checks.add(new Check("5: M/M/1 ρ_В1 = t_В/t_надх = 0.8", 0.8, rho, tolerance(r, scenarios, 4)));
            checks.add(new Check("5: M/M/1 L_нак = ρ²/(1-ρ) = 3.2 (" + QUEUE_RUNS + " прогонів)", 3.2, Stat.mean(queue1), queueTolerance(queue1)));
            checks.add(new Check("6: M/M/2 ρ_В1+ρ_В2 = 1.2", 1.2, r[5].loadSecondaryTotal(), tolerance(r, scenarios, 5)));
            checks.add(new Check("6: M/M/2 L_нак = 0.675 (" + QUEUE_RUNS + " прогонів)", 0.675, Stat.mean(queue2), queueTolerance(queue2)));
            checks.add(new Check("7: детермінований ρ_П1 = 0.8", 0.8, r[6].loadPrimary1(), 0.001));
            checks.add(new Check("7: детермінований ρ_В1 = 0.8", 0.8, r[6].loadSecondary1(), 0.001));
            checks.add(new Check("7: детермінований ρ_В2 = 0", 0, r[6].loadSecondary2(), 0));
            checks.add(new Check("7: детермінований L_нак = 0", 0, r[6].meanBuffer(), 0));
            checks.add(new Check("8: детермінований ρ_В2 -> 1", 1, r[7].loadSecondary2(), 0.001));
            checks.add(new Check("8: детермінований L_нак -> h = 3", 3, r[7].meanBuffer(), 0.01));
            checks.add(new Check("9: поріг 3, баланс ρ_В1+ρ_В2 = λ(1-P)·t_В", balance(r, scenarios, 8), r[8].loadSecondaryTotal(), tolerance(r, scenarios, 8)));
            checks.add(new Check("10: поріг 2, баланс ρ_В1+ρ_В2 = λ(1-P)·t_В", balance(r, scenarios, 9), r[9].loadSecondaryTotal(), tolerance(r, scenarios, 9)));
            checks.add(new Check("11: поріг 1, баланс ρ_В1+ρ_В2 = λ(1-P)·t_В", balance(r, scenarios, 10), r[10].loadSecondaryTotal(), tolerance(r, scenarios, 10)));
            checks.add(new Check("9-11: ρ_В2 зростає зі зменшенням порогу (1 = так)", 1,
                    r[8].loadSecondary2() < r[9].loadSecondary2() && r[9].loadSecondary2() < r[10].loadSecondary2() ? 1 : 0, 0));
            checks.add(new Check("9-11: L_нак спадає зі зменшенням порогу (1 = так)", 1,
                    r[8].meanBuffer() > r[9].meanBuffer() && r[9].meanBuffer() > r[10].meanBuffer() ? 1 : 0, 0));
            checks.add(new Check("12: баланс ρ_В1+ρ_В2 = λ(1-P)·t_В", balance(r, scenarios, 11), r[11].loadSecondaryTotal(), tolerance(r, scenarios, 11)));

            output.line();
            output.line("Перевірка очікуваних значень:");
            List<String[]> checkRows = new ArrayList<>();
            for (Check c : checks) {
                checkRows.add(new String[]{c.name(), f(c.expected(), 4), f(c.actual(), 4),
                        f(Math.abs(c.expected() - c.actual()), 4), f(c.tolerance(), 4), c.passed() ? "так" : "НІ"});
            }
            output.table(new String[]{"Сценарій: перевірка", "Очікуване", "Отримане", "Відхилення", "Допуск", "Виконано"}, checkRows);
            output.line();
            output.line("Пройдено перевірок: " + checks.stream().filter(Check::passed).count() + " з " + checks.size());

            WorkshopModel model = new WorkshopModel(base);
            NetStatistics statistics = model.simulate(TIME_MODELING, 0);
            int inside = model.place(WorkshopModel.QUEUE_PRIMARY).getMark() + model.place("ПісляП1").getMark()
                    + model.place("ПісляП2").getMark() + model.place(WorkshopModel.QUEUE_REWORK).getMark()
                    + model.place("ПісляПовт").getMark() + model.place(WorkshopModel.BUFFER).getMark();
            for (String name : new String[]{WorkshopModel.PRIMARY1, WorkshopModel.PRIMARY2, WorkshopModel.REWORK,
                    WorkshopModel.SECONDARY1, WorkshopModel.SECONDARY2}) {
                PetriT transition = model.transition(name);
                inside += transition.getBuffer();
            }
            long arrived = statistics.exits(model.transition(WorkshopModel.ARRIVAL));
            long done1 = statistics.exits(model.transition(WorkshopModel.SECONDARY1));
            long done2 = statistics.exits(model.transition(WorkshopModel.SECONDARY2));
            long waste = statistics.exits(model.transition(WorkshopModel.BAD_REWORK));
            output.line();
            output.line("Баланс деталей (базовий сценарій):");
            output.line("  надійшло " + arrived + " = оброблено В1 " + done1 + " + оброблено В2 " + done2
                    + " + відходи " + waste + " + у системі " + inside + " = " + (done1 + done2 + waste + inside));
        }
    }

    private static double queueTolerance(double[] values) {
        return 3 * Stat.stdDev(values) / Math.sqrt(values.length);
    }

    private static double lambda(WorkshopResult[] r, int i) {
        return r[i].arrived() / r[i].observedTime();
    }

    private static double balance(WorkshopResult[] r, List<Scenario> scenarios, int i) {
        return lambda(r, i) * (1 - r[i].wasteProbability()) * scenarios.get(i).parameters().secondaryMean();
    }

    private static double tolerance(WorkshopResult[] r, List<Scenario> scenarios, int i) {
        return 3 * scenarios.get(i).parameters().secondaryMean() * Math.sqrt(r[i].arrived()) / r[i].observedTime();
    }

    private static String number(double value) {
        return new java.text.DecimalFormat("0.###", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT)).format(value);
    }
}
