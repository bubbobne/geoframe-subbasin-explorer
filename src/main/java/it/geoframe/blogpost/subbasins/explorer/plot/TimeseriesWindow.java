package it.geoframe.blogpost.subbasins.explorer.plot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
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
	private final JComboBox<String> simulationTableCombo;
	private final JComboBox<String> basinCombo;
	private final JComboBox<String> streamGaugeCombo;
	private final JList<String> seriesList;
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
		JFreeChart chart = ChartFactory.createTimeSeriesChart("Timeseries", "Tempo", "Valore", dataset, true, true,
				false);
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
		simulationTableCombo = new JComboBox<>();
		basinCombo = new JComboBox<>();
		streamGaugeCombo = new JComboBox<>();
		seriesList = new JList<>(new DefaultListModel<>());
		seriesList.setVisibleRowCount(6);
		seriesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		controlsPanel.add(new JLabel("Tabella da aggiungere:"), gbc);
		gbc.gridy++;
		controlsPanel.add(simulationTableCombo, gbc);
		gbc.gridy++;
		controlsPanel.add(new JLabel("Sottobacino:"), gbc);
		gbc.gridy++;
		controlsPanel.add(basinCombo, gbc);
		gbc.gridy++;
		JButton addSimulationButton = new JButton("Carica simulazione");
		addSimulationButton.addActionListener(e -> addSelectedSeriesFromSimulationCombo());
		controlsPanel.add(addSimulationButton, gbc);
		gbc.gridy++;
		controlsPanel.add(new JLabel("Stream gauge:"), gbc);
		gbc.gridy++;
		controlsPanel.add(streamGaugeCombo, gbc);
		gbc.gridy++;
		JButton addGaugeButton = new JButton("Carica stream gauge");
		addGaugeButton.addActionListener(e -> addSelectedSeriesFromGaugeCombo());
		controlsPanel.add(addGaugeButton, gbc);
		gbc.gridy++;
		controlsPanel.add(new JLabel("Linee nel grafico:"), gbc);
		gbc.gridy++;
		controlsPanel.add(new JScrollPane(seriesList), gbc);
		gbc.gridy++;
		JButton removeSelectedLineButton = new JButton("Cancella linea selezionata");
		removeSelectedLineButton.addActionListener(e -> removeSelectedSeries());
		controlsPanel.add(removeSelectedLineButton, gbc);

		statusArea = new JTextArea();
		statusArea.setEditable(false);
		statusArea.setLineWrap(true);
		statusArea.setWrapStyleWord(true);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsPanel, new ChartPanel(chart));
		splitPane.setResizeWeight(0.3);
		dialog.add(splitPane, BorderLayout.CENTER);
		dialog.add(new JScrollPane(statusArea), BorderLayout.SOUTH);
		dialog.setSize(new Dimension(1180, 680));
		dialog.setLocationRelativeTo(parent);
	}

	public void showForSelection(String subbasinId, String firstTable, String type) {
		this.activeType = type == null ? "discharge" : type;
		dataset.removeAllSeries();
		baseSeriesKey = null;
		reloadSeriesList();
		reloadCombos();
		if (subbasinId != null) {
			basinCombo.setSelectedItem(subbasinId);
		}
		if (firstTable != null) {
			simulationTableCombo.setSelectedItem(firstTable);
		}
		dialog.setTitle("Vista " + activeType);
		dialog.setVisible(true);
		addSelectedSeriesFromSimulationCombo();
	}

	private void reloadCombos() {
		simulationTableCombo.removeAllItems();
		for (String table : filterSimulationTables(tableSupplier.get())) {
			simulationTableCombo.addItem(table);
		}
		basinCombo.removeAllItems();
		for (String id : basinSupplier.get()) {
			basinCombo.addItem(id);
		}
		streamGaugeCombo.removeAllItems();
		if (config.mode() == ProjectMode.GEOPACKAGE && streamGaugeSelectionSupplier.getAsBoolean()) {
			for (String table : filterStreamGaugeTables(tableSupplier.get())) {
				streamGaugeCombo.addItem(table);
			}
		}
	}

	private List<String> filterSimulationTables(List<String> sourceTables) {
		if (sourceTables == null || sourceTables.isEmpty()) {
			return List.of();
		}
		List<String> filtered = new ArrayList<>();
		for (String table : sourceTables) {
			if (table != null && containsAny(table, "discharge", "sim", activeType)) {
				filtered.add(table);
			}
		}
		return filtered.isEmpty() ? sourceTables : filtered;
	}

	private List<String> filterStreamGaugeTables(List<String> sourceTables) {
		if (sourceTables == null || sourceTables.isEmpty()) {
			return List.of();
		}
		List<String> filtered = new ArrayList<>();
		for (String table : sourceTables) {
			if (table == null) {
				continue;
			}
			Set<String> cols = loader.listColumnNamesFromAnyInput(config, table);
			boolean hasTs = hasColumn(cols, "ts", "timestamp", "time", "date");
			boolean hasValue = hasColumn(cols, "value", "obs", "measured");
			boolean hasSimColumn = hasColumn(cols, "sim", "simulated", "simulation", "discharge_sim");
			if (hasTs && hasValue && !hasSimColumn) {
				filtered.add(table);
			}
		}
		return filtered;
	}

	private boolean hasColumn(Set<String> columns, String... names) {
		if (columns == null || columns.isEmpty()) {
			return false;
		}
		for (String name : names) {
			for (String column : columns) {
				if (column.equalsIgnoreCase(name)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean containsAny(String table, String... tokens) {
		if (table == null || tokens == null) {
			return false;
		}
		String normalized = table.toLowerCase(Locale.ROOT);
		for (String token : tokens) {
			if (token != null && !token.isBlank() && normalized.contains(token.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private void addSelectedSeriesFromSimulationCombo() {
		String table = (String) simulationTableCombo.getSelectedItem();
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
		reloadSeriesList();
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

	private void reloadSeriesList() {
		DefaultListModel<String> model = new DefaultListModel<>();
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			model.addElement(dataset.getSeries(i).getKey().toString());
		}
		seriesList.setModel(model);
	}

	private void removeSelectedSeries() {
		if (dataset.getSeriesCount() == 0) {
			statusArea.setText("Non ci sono linee da eliminare.");
			return;
		}
		int selectedIndex = seriesList.getSelectedIndex();
		if (selectedIndex < 0 || selectedIndex >= dataset.getSeriesCount()) {
			statusArea.setText("Seleziona una linea dalla lista e premi cancella.");
			return;
		}
		if (selectedIndex == 0) {
			statusArea.setText("La portata base non può essere eliminata.");
			return;
		}
		String removedKey = dataset.getSeries(selectedIndex).getKey().toString();
		dataset.removeSeries(selectedIndex);
		applySeriesStyles();
		reloadSeriesList();
		statusArea.setText("Linea eliminata: " + removedKey);
	}
}
