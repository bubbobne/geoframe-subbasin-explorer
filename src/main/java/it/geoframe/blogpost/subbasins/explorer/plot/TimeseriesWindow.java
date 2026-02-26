package it.geoframe.blogpost.subbasins.explorer.plot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
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
		JButton addButton = new JButton("Carica");
		addButton.addActionListener(e -> addSelectedSeries());
		controlsPanel.add(addButton, gbc);
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
		for (String table : tableSupplier.get()) {
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

	private void addSelectedSeries() {
		String table = (String) tableCombo.getSelectedItem();
		String basinId = (String) basinCombo.getSelectedItem();
		if (table == null || basinId == null) {
			statusArea.setText("Seleziona tabella e sottobacino.");
			return;
		}
		TimeSeries series = new TimeSeries(table + " | basin " + basinId);
		int count = loader.fillSeriesFromAnyInput(config, table, basinId, series);
		if (count > 0) {
			dataset.addSeries(series);
			statusArea.setText("Aggiunta serie: " + table + "\nBasin ID: " + basinId + "\nPunti: " + count);
			return;
		}
		if (config.mode() == ProjectMode.GEOPACKAGE && streamGaugeSelectionSupplier.getAsBoolean()) {
			String sgTable = (String) streamGaugeCombo.getSelectedItem();
			if (sgTable != null) {
				TimeSeries sgSeries = new TimeSeries("stream gauge | " + sgTable + " | basin " + basinId);
				int sgCount = loader.fillSeriesFromAnyInput(config, sgTable, basinId, sgSeries);
				if (sgCount > 0) {
					dataset.addSeries(sgSeries);
					statusArea.append("\nAggiunta serie stream gauge: " + sgTable + " (" + sgCount + " punti)");
				}
			}
		}
	}
}
