package it.geoframe.blogpost.subbasins.explorer.plot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import it.geoframe.blogpost.subbasins.explorer.io.TimeseriesLoader;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;

public final class TimeseriesWindow {
	private final ProjectConfig config;
	private final TimeseriesLoader loader;
	private final Supplier<List<String>> tableSupplier;
	private final Supplier<List<String>> basinSupplier;
	private final BooleanSupplier streamGaugeSelectionSupplier;
	private final JDialog dialog;
	private final JComboBox<String> tableCombo;
	private final JComboBox<String> basinCombo;
	private final JComboBox<String> streamGaugeCombo;
	private final JTextArea statusArea;
	private final TimeSeriesCollection dataset;
	private String activeType;
	private String baseSeriesKey;
	private final XYLineAndShapeRenderer renderer;

	public TimeseriesWindow(Component parent, ProjectConfig config, TimeseriesLoader loader,
			Supplier<List<String>> tableSupplier, Supplier<List<String>> basinSupplier,
			BooleanSupplier streamGaugeSelectionSupplier) {
		this.config = config;
		this.loader = loader;
		this.tableSupplier = tableSupplier;
		this.basinSupplier = basinSupplier;
		this.streamGaugeSelectionSupplier = streamGaugeSelectionSupplier;
		dialog = new JDialog();
		dialog.setModal(false);
		dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		dialog.setTitle("Vista grafico");
		dialog.setLayout(new BorderLayout(8, 8));
		dataset = new TimeSeriesCollection();
		JFreeChart chart = ChartFactory.createTimeSeriesChart("Timeseries", "Tempo", "Valore", dataset, true, true, false);
		XYPlot plot = chart.getXYPlot();
		renderer = new XYLineAndShapeRenderer(true, false);
		plot.setRenderer(renderer);
		JPanel controlsPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		tableCombo = new JComboBox<>();
		basinCombo = new JComboBox<>();
		streamGaugeCombo = new JComboBox<>();
		controlsPanel.add(new JLabel("Tabella da aggiungere:"), gbc);
		gbc.gridy++;
		controlsPanel.add(tableCombo, gbc);
		gbc.gridy++;
		controlsPanel.add(new JLabel("Sottobacino:"), gbc);
		gbc.gridy++;
		controlsPanel.add(basinCombo, gbc);
		gbc.gridy++;
		if (config.mode() == ProjectMode.GEOPACKAGE) {
			controlsPanel.add(new JLabel("Stream gauge (se presente):"), gbc);
			gbc.gridy++;
			controlsPanel.add(streamGaugeCombo, gbc);
			gbc.gridy++;
		}
		JButton addSimulationButton = new JButton("Carica simulazione");
		addSimulationButton.addActionListener(e -> addSelectedSeriesFromMainCombo());
		controlsPanel.add(addSimulationButton, gbc);
		gbc.gridy++;
		JButton addGaugeButton = new JButton("Carica stream gauge");
		addGaugeButton.addActionListener(e -> addSelectedSeriesFromGaugeCombo());
		controlsPanel.add(addGaugeButton, gbc);
		gbc.gridy++;
		JButton clearExtraLinesButton = new JButton("Tieni solo portata base");
		clearExtraLinesButton.addActionListener(e -> keepOnlyBaseSeries());
		controlsPanel.add(clearExtraLinesButton, gbc);
		statusArea = new JTextArea();
		statusArea.setEditable(false);
		statusArea.setLineWrap(true);
		statusArea.setWrapStyleWord(true);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsPanel, new ChartPanel(chart));
		splitPane.setResizeWeight(0.28);
		dialog.add(splitPane, BorderLayout.CENTER);
		dialog.add(new JScrollPane(statusArea), BorderLayout.SOUTH);
		dialog.setSize(new Dimension(1180, 680));
		dialog.setLocationRelativeTo(parent);
	}

	public void showForSelection(String subbasinId, String firstTable, String type) {
		this.activeType = type == null ? "discharge" : type;
		dataset.removeAllSeries();
		baseSeriesKey = null;
		reloadCombos();
		if (subbasinId != null) {
			basinCombo.setSelectedItem(subbasinId);
		}
		if (firstTable != null) {
			tableCombo.setSelectedItem(firstTable);
		}
		dialog.setTitle("Vista " + activeType);
		dialog.setVisible(true);
		addSelectedSeries();
	}

	private void reloadCombos() {
		tableCombo.removeAllItems();
		for (String table : filterTablesByActiveType(tableSupplier.get())) {
			tableCombo.addItem(table);
		}
		basinCombo.removeAllItems();
		for (String id : basinSupplier.get()) {
			basinCombo.addItem(id);
		}
		streamGaugeCombo.removeAllItems();
		if (config.mode() == ProjectMode.GEOPACKAGE && streamGaugeSelectionSupplier.getAsBoolean()) {
			for (String table : tableSupplier.get()) {
				streamGaugeCombo.addItem(table);
			}
		}
	}

	private List<String> filterTablesByActiveType(List<String> sourceTables) {
		if (sourceTables == null || sourceTables.isEmpty()) {
			return List.of();
		}
		if (activeType == null || activeType.isBlank()) {
			return sourceTables;
		}
		String normalized = activeType.toLowerCase(Locale.ROOT);
		List<String> filtered = new ArrayList<>();
		for (String table : sourceTables) {
			if (table != null && table.toLowerCase(Locale.ROOT).contains(normalized)) {
				filtered.add(table);
			}
		}
		return filtered.isEmpty() ? sourceTables : filtered;
	}

	private void addSelectedSeriesFromMainCombo() {
		String table = (String) tableCombo.getSelectedItem();
		addSeries(table, false);
	}

	private void addSelectedSeriesFromGaugeCombo() {
		if (config.mode() != ProjectMode.GEOPACKAGE || !streamGaugeSelectionSupplier.getAsBoolean()) {
			statusArea.setText("Stream gauge non disponibile per la selezione corrente.");
			return;
		}
		String table = (String) streamGaugeCombo.getSelectedItem();
		addSeries(table, true);
	}

	private void addSelectedSeries() {
		addSelectedSeriesFromMainCombo();
	}

	private void addSeries(String table, boolean isGaugeSeries) {
		String basinId = (String) basinCombo.getSelectedItem();
		if (table == null || basinId == null) {
			statusArea.setText("Seleziona tabella e sottobacino.");
			return;
		}
		String labelPrefix = isGaugeSeries ? "stream gauge" : table;
		TimeSeries series = new TimeSeries(labelPrefix + " | basin " + basinId);
		int count = loader.fillSeriesFromAnyInput(config, table, basinId, series);
		if (count <= 0) {
			statusArea.setText("Nessun dato trovato per tabella " + table + " e basin " + basinId + ".");
			return;
		}
		dataset.addSeries(series);
		applySeriesStyles();
		statusArea.setText("Aggiunta serie: " + table + "\nBasin ID: " + basinId + "\nPunti: " + count);
	}

	private void applySeriesStyles() {
		if (dataset.getSeriesCount() == 0) {
			return;
		}
		if (baseSeriesKey == null) {
			baseSeriesKey = dataset.getSeries(0).getKey().toString();
		}
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			String key = dataset.getSeries(i).getKey().toString();
			renderer.setSeriesPaint(i, key.equals(baseSeriesKey) ? Color.BLUE : Color.DARK_GRAY);
		}
	}

	private void keepOnlyBaseSeries() {
		if (baseSeriesKey == null || dataset.getSeriesCount() <= 1) {
			statusArea.setText("Non ci sono linee aggiuntive da rimuovere.");
			return;
		}
		for (int i = dataset.getSeriesCount() - 1; i >= 0; i--) {
			String key = dataset.getSeries(i).getKey().toString();
			if (!key.equals(baseSeriesKey)) {
				dataset.removeSeries(i);
			}
		}
		applySeriesStyles();
		statusArea.setText("Rimosse le linee aggiuntive. Tenuta solo la portata base.");
	}
}
