package ua.kpi.workshop;

import static ua.kpi.workshop.experiments.Output.f;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import ua.kpi.workshop.experiments.Estimation;
import ua.kpi.workshop.experiments.FactorialExperiment;
import ua.kpi.workshop.experiments.Output;
import ua.kpi.workshop.experiments.Proposals;
import ua.kpi.workshop.experiments.ThresholdExperiment;
import ua.kpi.workshop.experiments.TransientStudy;
import ua.kpi.workshop.experiments.Verification;
import ua.kpi.workshop.model.NetStatistics;
import ua.kpi.workshop.model.WorkshopModel;
import ua.kpi.workshop.model.WorkshopParameters;
import ua.kpi.workshop.model.WorkshopResult;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

public final class Main {

    private record Command(String key, String title, Runnable action) {
    }

    private static final List<Command> COMMANDS = List.of(
            new Command("run", "Один прогін моделі з параметрами за завданням", Main::runOnce),
            new Command("net", "Опис мережі Петрі (позиції, переходи, дуги)", Main::printNet),
            new Command("protocol", "Протокол подій (верифікація)", Verification::printProtocol),
            new Command("verify", "Таблиця верифікації", Verification::run),
            new Command("transient", "Дослідження перехідного періоду", TransientStudy::run),
            new Command("replications", "Визначення кількості прогонів", Estimation::runReplicationStudy),
            new Command("estimate", "Оцінки вихідних характеристик", Estimation::runEstimates),
            new Command("threshold", "Вплив порогу підключення В2: сталий режим", ThresholdExperiment::runSteady),
            new Command("startup", "Вплив порогу підключення В2: період пуску", ThresholdExperiment::runStartup),
            new Command("factorial", "Факторний експеримент 2² (поріг, час вторинної обробки)", FactorialExperiment::run),
            new Command("proposals", "Пробні запуски пропозицій", Proposals::run));

    private Main() {
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length > 0) {
            execute(args[0]);
            return;
        }
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.println();
            System.out.println("Імітаційна модель обробної ділянки цеху (мережа Петрі, PetriObjModelPaint)");
            for (int i = 0; i < COMMANDS.size(); i++) {
                System.out.printf("  %2d. %s%n", i + 1, COMMANDS.get(i).title());
            }
            System.out.println("  12. Усі дослідження підряд");
            System.out.println("   0. Вихід");
            System.out.print("Оберіть режим: ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String input = scanner.nextLine().trim();
            if (input.equals("0")) {
                return;
            }
            try {
                int number = Integer.parseInt(input);
                if (number >= 1 && number <= COMMANDS.size()) {
                    execute(COMMANDS.get(number - 1).key());
                } else if (number == 12) {
                    execute("all");
                } else {
                    System.out.println("Невідомий режим");
                }
            } catch (NumberFormatException e) {
                System.out.println("Невідомий режим");
            }
        }
    }

    private static void execute(String key) {
        if (key.equals("all")) {
            for (Command command : COMMANDS) {
                execute(command.key());
            }
            return;
        }
        Command command = COMMANDS.stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
        if (command == null) {
            List<String> keys = new ArrayList<>(List.of("all"));
            COMMANDS.forEach(c -> keys.add(c.key()));
            System.out.println("Невідомий режим '" + key + "'. Доступні: " + String.join(", ", keys));
            return;
        }
        System.out.println();
        System.out.println("=== " + command.title() + " ===");
        long start = System.nanoTime();
        command.action().run();
        System.out.println("(виконано за " + f((System.nanoTime() - start) / 1e9, 1) + " с; результати збережено в "
                + Output.resultsDirectory() + ")");
    }

    private static void runOnce() {
        double timeModeling = 1_000_000;
        WorkshopModel model = new WorkshopModel(WorkshopParameters.defaults());
        NetStatistics statistics = model.simulate(timeModeling, 0);
        WorkshopResult result = model.collect(statistics);

        try (Output output = new Output("single_run.txt")) {
            output.line("Час моделювання: " + f(timeModeling, 0) + " хв");
            output.line();
            output.line("Позиції мережі Петрі:");
            List<String[]> places = new ArrayList<>();
            for (PetriP place : model.net().getListP()) {
                places.add(new String[]{place.getName(), String.valueOf(place.getMark()),
                        f(statistics.meanMark(place), 4), String.valueOf(statistics.maxMark(place))});
            }
            output.table(new String[]{"Позиція", "Маркерів у кінці", "Середнє", "Максимум"}, places);
            output.line();
            output.line("Переходи мережі Петрі:");
            List<String[]> transitions = new ArrayList<>();
            for (PetriT transition : model.net().getListT()) {
                transitions.add(new String[]{transition.getName(), String.valueOf(statistics.exits(transition)),
                        f(statistics.meanBuffer(transition), 4)});
            }
            output.table(new String[]{"Перехід", "Кількість виходів", "Середня кількість активних каналів"}, transitions);
            output.line();
            output.line("Вихідні характеристики моделі:");
            output.line("  завантаження верстата В2:            " + f(result.loadSecondary2(), 4));
            output.line("  ймовірність появи відходів:          " + f(result.wasteProbability(), 5)
                    + " (" + result.waste() + " з " + result.arrived() + " деталей)");
            output.line("  завантаження верстата В1:            " + f(result.loadSecondary1(), 4));
            output.line("  середня кількість у накопичувачі:    " + f(result.meanBuffer(), 2) + " (максимум " + result.maxBuffer() + ")");
            output.line("  завантаження верстатів П1 / П2:      " + f(result.loadPrimary1(), 4) + " / " + f(result.loadPrimary2(), 4));
            output.line("  оброблено верстатами В1 / В2:        " + result.finished1() + " / " + result.finished2());
        }
    }

    private static void printNet() {
        WorkshopModel model = new WorkshopModel(WorkshopParameters.defaults());
        try (Output output = new Output("petri_net.txt")) {
            output.line("Позиції:");
            List<String[]> places = new ArrayList<>();
            for (PetriP place : model.net().getListP()) {
                places.add(new String[]{place.getName(), String.valueOf(place.getMark())});
            }
            output.table(new String[]{"Позиція", "Початкове маркірування"}, places);
            output.line();
            output.line("Переходи:");
            List<String[]> rows = new ArrayList<>();
            for (PetriT transition : model.net().getListT()) {
                StringBuilder inputs = new StringBuilder();
                for (ArcIn arc : model.net().getArcIn()) {
                    if (arc.getNameT().equals(transition.getName())) {
                        inputs.append(inputs.isEmpty() ? "" : ", ").append(arc.getNameP());
                        if (arc.getIsInf()) {
                            inputs.append(" (інф., k=").append(arc.getQuantity()).append(")");
                        } else if (arc.getQuantity() > 1) {
                            inputs.append(" (k=").append(arc.getQuantity()).append(")");
                        }
                    }
                }
                StringBuilder outputs = new StringBuilder();
                for (ArcOut arc : model.net().getArcOut()) {
                    if (arc.getNameT().equals(transition.getName())) {
                        outputs.append(outputs.isEmpty() ? "" : ", ").append(arc.getNameP());
                    }
                }
                String delay = transition.getDistribution() == null
                        ? f(transition.getParameter(), 1)
                        : transition.getDistribution() + "(" + f(transition.getParameter(), 1) + ")";
                rows.add(new String[]{transition.getName(), delay, String.valueOf(transition.getPriority()),
                        f(transition.getProbability(), 2), inputs.toString(), outputs.toString()});
            }
            output.table(new String[]{"Перехід", "Затримка", "Пріоритет", "Ймовірність", "Вхідні позиції", "Вихідні позиції"}, rows);
        }
    }
}
