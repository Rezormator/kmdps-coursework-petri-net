package ua.kpi.workshop.experiments;

import static ua.kpi.workshop.experiments.Output.f;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.knowm.xchart.style.Styler;
import ua.kpi.workshop.model.NetStatistics;
import ua.kpi.workshop.model.WorkshopModel;
import ua.kpi.workshop.model.WorkshopParameters;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

public final class TransientStudy {

    private static final int TRIAL_RUNS = 4;
    private static final double STEP = 100_000;
    private static final double HORIZON = 20_000_000;
    private static final double EPSILON_LOAD = 0.01;
    private static final double EPSILON_WASTE = 0.0004;

    private record Trajectory(double[] load, double[] waste, double[] buffer) {
    }

    private TransientStudy() {
    }

    public static void run() {
        try (Output output = new Output("transient.txt")) {
            trialObservations(output);
            output.line();
            ensembleWindows(output);
        }
    }

    private static void trialObservations(Output output) {
        int points = (int) (HORIZON / STEP);
        double[] checkpoints = IntStream.rangeClosed(1, points).mapToDouble(k -> k * STEP).toArray();
        Trajectory[] runs = IntStream.range(0, TRIAL_RUNS).parallel()
                .mapToObj(run -> trajectory(checkpoints))
                .toArray(Trajectory[]::new);

        double[][] load = new double[TRIAL_RUNS][];
        double[][] waste = new double[TRIAL_RUNS][];
        double[][] buffer = new double[TRIAL_RUNS][];
        for (int i = 0; i < TRIAL_RUNS; i++) {
            load[i] = runs[i].load();
            waste[i] = runs[i].waste();
            buffer[i] = runs[i].buffer();
        }

        output.line("ПРОБНІ СПОСТЕРЕЖЕННЯ (" + TRIAL_RUNS + " прогони, порожній початковий стан, горизонт " + f(HORIZON, 0) + " хв)");
        output.line("Середні значення відгуків на інтервалі [0; t]:");
        List<String[]> rows = new ArrayList<>();
        for (double t : new double[]{0.1, 0.5, 1, 2, 3, 5, 7, 10, 15, 20}) {
            int k = (int) Math.round(t * 1e6 / STEP) - 1;
            List<String> row = new ArrayList<>();
            row.add(f(t, 1));
            for (double[] series : load) {
                row.add(f(series[k], 4));
            }
            row.add(f(spread(load, k), 4));
            for (double[] series : buffer) {
                row.add(f(series[k], 1));
            }
            rows.add(row.toArray(String[]::new));
        }
        List<String> header = new ArrayList<>(List.of("t, млн хв"));
        for (int i = 1; i <= TRIAL_RUNS; i++) {
            header.add("ρ_В2 №" + i);
        }
        header.add("розмах");
        for (int i = 1; i <= TRIAL_RUNS; i++) {
            header.add("L_нак №" + i);
        }
        output.table(header.toArray(String[]::new), rows);

        output.line();
        output.line("Завантаження В2: розбіжність між прогонами менша за " + EPSILON_LOAD + " починаючи з T = " + describe(stableTime(load, EPSILON_LOAD)));
        output.line("Ймовірність відходів: розбіжність менша за " + f(EPSILON_WASTE, 4) + " починаючи з T = " + describe(stableTime(waste, EPSILON_WASTE)));
        StringBuilder ends = new StringBuilder();
        for (double[] series : buffer) {
            ends.append(ends.isEmpty() ? "" : "; ").append(f(series[points - 1], 1));
        }
        output.line("Середня кількість деталей у накопичувачі в кінці горизонту: " + ends
                + " (розмах " + f(spread(buffer, points - 1), 1) + ") - не стабілізується");

        double[] time = IntStream.rangeClosed(1, points).mapToDouble(k -> k * STEP / 1e6).toArray();
        List<Charts.Series> loadSeries = new ArrayList<>();
        List<Charts.Series> bufferSeries = new ArrayList<>();
        for (int i = 0; i < TRIAL_RUNS; i++) {
            loadSeries.add(new Charts.Series("прогін " + (i + 1), time, load[i]));
            bufferSeries.add(new Charts.Series("прогін " + (i + 1), time, buffer[i]));
        }
        Charts.lines("transient_load.png", "Час моделювання t, млн хв", "Середнє завантаження верстата В2 на [0; t]",
                loadSeries, 0.94, 1.0, false, Styler.LegendPosition.InsideSE);
        Charts.lines("transient_buffer.png", "Час моделювання t, млн хв", "Середня кількість деталей у накопичувачі на [0; t]",
                bufferSeries, null, null, false, Styler.LegendPosition.InsideNW);
    }

    private static Trajectory trajectory(double[] checkpoints) {
        WorkshopModel model = new WorkshopModel(WorkshopParameters.defaults());
        PetriT secondary2 = model.transition(WorkshopModel.SECONDARY2);
        PetriT arrival = model.transition(WorkshopModel.ARRIVAL);
        PetriT waste = model.transition(WorkshopModel.BAD_REWORK);
        PetriP buffer = model.place(WorkshopModel.BUFFER);
        double[] load = new double[checkpoints.length];
        double[] wasteShare = new double[checkpoints.length];
        double[] bufferMean = new double[checkpoints.length];
        NetStatistics[] holder = new NetStatistics[1];
        int[] index = {0};
        model.simulate(checkpoints[checkpoints.length - 1], 0, statistics -> {
            holder[0] = statistics;
            statistics.setCheckpoints(checkpoints, t -> {
                int k = index[0]++;
                load[k] = holder[0].bufferIntegral(secondary2) / t;
                bufferMean[k] = holder[0].markIntegral(buffer) / t;
                long arrived = holder[0].exits(arrival);
                wasteShare[k] = arrived > 0 ? (double) holder[0].exits(waste) / arrived : 0;
            });
        });
        return new Trajectory(load, wasteShare, bufferMean);
    }

    private static void ensembleWindows(Output output) {
        int runs = 200;
        double window = 250_000;
        int windows = 20;
        double[] empty = windowMeans(WorkshopParameters.defaults(), runs, window, windows);
        double[] steady = windowMeans(ExperimentSettings.baseParameters(), runs, window, windows);
        double[] time = IntStream.rangeClosed(1, windows).mapToDouble(k -> k * window / 1e6).toArray();

        output.line("ЗМІЩЕННЯ ЧЕРЕЗ ПОЧАТКОВИЙ СТАН (вікна по " + f(window, 0) + " хв, усереднення за " + runs + " прогонами)");
        List<String[]> rows = new ArrayList<>();
        for (int k = 0; k < windows; k++) {
            rows.add(new String[]{f(time[k], 2), f(empty[k], 4), f(steady[k], 4)});
        }
        output.table(new String[]{"Кінець вікна, млн хв", "ρ_В2, порожній початковий стан",
                "ρ_В2, у накопичувачі " + ExperimentSettings.STEADY_BUFFER + " деталей"}, rows);

        Charts.lines("transient_windows.png", "Час моделювання, млн хв", "Завантаження верстата В2 у вікні часу",
                List.of(new Charts.Series("порожній початковий стан", time, empty),
                        new Charts.Series("початково " + ExperimentSettings.STEADY_BUFFER + " деталей у накопичувачі", time, steady)),
                0.90, 1.0, true, Styler.LegendPosition.InsideSE);
    }

    private static double[] windowMeans(WorkshopParameters parameters, int runs, double window, int windows) {
        double[] checkpoints = IntStream.rangeClosed(1, windows).mapToDouble(k -> k * window).toArray();
        double[][] all = IntStream.range(0, runs).parallel().mapToObj(run -> {
            WorkshopModel model = new WorkshopModel(parameters);
            PetriT secondary2 = model.transition(WorkshopModel.SECONDARY2);
            double[] values = new double[windows];
            double[] previous = {0};
            int[] index = {0};
            NetStatistics[] holder = new NetStatistics[1];
            model.simulate(checkpoints[windows - 1], 0, statistics -> {
                holder[0] = statistics;
                statistics.setCheckpoints(checkpoints, t -> {
                    double integral = holder[0].bufferIntegral(secondary2);
                    values[index[0]++] = (integral - previous[0]) / window;
                    previous[0] = integral;
                });
            });
            return values;
        }).toArray(double[][]::new);

        double[] means = new double[windows];
        for (int k = 0; k < windows; k++) {
            double sum = 0;
            for (double[] values : all) {
                sum += values[k];
            }
            means[k] = sum / runs;
        }
        return means;
    }

    private static double spread(double[][] series, int index) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double[] s : series) {
            min = Math.min(min, s[index]);
            max = Math.max(max, s[index]);
        }
        return max - min;
    }

    private static double stableTime(double[][] series, double epsilon) {
        int points = series[0].length;
        int stableFrom = points;
        for (int k = points - 1; k >= 0; k--) {
            if (spread(series, k) >= epsilon) {
                break;
            }
            stableFrom = k;
        }
        return stableFrom == points ? Double.NaN : (stableFrom + 1) * STEP;
    }

    private static String describe(double time) {
        return Double.isNaN(time) ? "не досягнуто на горизонті спостереження" : f(time, 0) + " хв";
    }
}
