package ua.kpi.workshop.experiments;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

public final class Charts {

    public record Series(String legend, double[] x, double[] y) {
    }

    private static final int WIDTH = 1000;
    private static final int HEIGHT = 560;

    private Charts() {
    }

    public static void lines(String fileName, String xLabel, String yLabel, List<Series> series,
                             Double yMin, Double yMax, boolean markers, Styler.LegendPosition legend) {
        XYChart chart = new XYChartBuilder().width(WIDTH).height(HEIGHT).xAxisTitle(xLabel).yAxisTitle(yLabel).build();
        style(chart, legend);
        if (yMin != null && yMax != null) {
            chart.getStyler().setYAxisMin(yMin);
            chart.getStyler().setYAxisMax(yMax);
        }
        for (Series item : series) {
            XYSeries line = chart.addSeries(item.legend(), item.x(), item.y());
            line.setMarker(markers ? SeriesMarkers.CIRCLE : SeriesMarkers.NONE);
            line.setLineStyle(new BasicStroke(2f));
        }
        save(chart, fileName);
    }

    private static void style(XYChart chart, Styler.LegendPosition legend) {
        Styler styler = chart.getStyler();
        styler.setChartBackgroundColor(Color.WHITE);
        styler.setPlotBackgroundColor(Color.WHITE);
        styler.setLegendPosition(legend);
        styler.setLegendFont(new Font("Arial", Font.PLAIN, 16));
        styler.setChartTitleVisible(false);
        chart.getStyler().setAxisTitleFont(new Font("Arial", Font.BOLD, 18));
        chart.getStyler().setAxisTickLabelsFont(new Font("Arial", Font.PLAIN, 15));
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setDecimalPattern("#0.###");
    }

    private static void save(XYChart chart, String fileName) {
        try {
            BitmapEncoder.saveBitmapWithDPI(chart, Output.pathFor(fileName).toString(), BitmapEncoder.BitmapFormat.PNG, 150);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
