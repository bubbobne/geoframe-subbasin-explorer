package it.geoframe.blogpost.subbasins.explorer.plot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;

import it.geoframe.blogpost.subbasins.explorer.io.TimeseriesLoader;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;

public final class TimeseriesWindow {
	private static final String STREAM_GAUGE_PREFIX = "stream gauge";
	private static final String DATE_FMT = "yyyy-MM-dd";

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
		JButton metricsButton = new JButton("Calcola metriche (KGE, NSE, NSElog)");
		metricsButton.addActionListener(e -> showMetricsPopup());
		controlsPanel.add(metricsButton, gbc);
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
		statusArea.setBackground(new Color(15, 18, 22));
		statusArea.setForeground(new Color(134, 239, 172));
		statusArea.setCaretColor(new Color(134, 239, 172));
		statusArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		JScrollPane consoleScroll = new JScrollPane(statusArea);
		consoleScroll.setPreferredSize(new Dimension(1000, 220));

		JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new ChartPanel(chart), consoleScroll);
		rightSplit.setResizeWeight(0.68);
		rightSplit.setContinuousLayout(true);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsPanel, rightSplit);
		splitPane.setResizeWeight(0.27);
		dialog.add(splitPane, BorderLayout.CENTER);
		dialog.setSize(new Dimension(1240, 760));
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
		appendLog("Aperta vista portate per sottobacino " + String.valueOf(subbasinId) + ".");
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
			appendLog("Stream gauge non disponibile per la selezione corrente.");
			return;
		}
		String table = (String) streamGaugeCombo.getSelectedItem();
		addSeries(table, true);
	}

	private void addSeries(String table, boolean isGaugeSeries) {
		String basinId = (String) basinCombo.getSelectedItem();
		if (table == null || basinId == null) {
			appendLog("Seleziona tabella e sottobacino.");
			return;
		}
		String labelPrefix = isGaugeSeries ? STREAM_GAUGE_PREFIX : table;
		TimeSeries series = new TimeSeries(labelPrefix + " | basin " + basinId);
		int count = loader.fillSeriesFromAnyInput(config, table, basinId, series);
		if (count <= 0) {
			appendLog("Nessun dato trovato per tabella " + table + " e basin " + basinId + ".");
			return;
		}
		dataset.addSeries(series);
		applySeriesStyles();
		reloadSeriesList();
		appendLog("Aggiunta serie: " + table + " | basin " + basinId + " | punti: " + count);
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
			appendLog("Non ci sono linee da eliminare.");
			return;
		}
		int selectedIndex = seriesList.getSelectedIndex();
		if (selectedIndex < 0 || selectedIndex >= dataset.getSeriesCount()) {
			appendLog("Seleziona una linea dalla lista e premi cancella.");
			return;
		}
		if (selectedIndex == 0) {
			appendLog("La portata base non può essere eliminata.");
			return;
		}
		String removedKey = dataset.getSeries(selectedIndex).getKey().toString();
		dataset.removeSeries(selectedIndex);
		applySeriesStyles();
		reloadSeriesList();
		appendLog("Linea eliminata: " + removedKey);
	}

	private void showMetricsPopup() {
		List<TimeSeries> simulationSeries = getSimulationSeries();
		List<TimeSeries> gaugeSeries = getGaugeSeries();
		if (simulationSeries.isEmpty()) {
			appendLog("Metriche: caricare almeno una serie simulata.");
			return;
		}
		if (gaugeSeries.isEmpty()) {
			appendLog("Metriche: caricare almeno una serie stream gauge.");
			return;
		}

		JComboBox<String> simCombo = new JComboBox<>(seriesKeys(simulationSeries));
		JComboBox<String> gaugeCombo = new JComboBox<>(seriesKeys(gaugeSeries));
		JTextField fromField = new JTextField();
		JTextField toField = new JTextField();
		long[] range = computeDateRange();
		SimpleDateFormat fmt = buildDateFormatter();
		if (range[0] > 0) {
			fromField.setText(fmt.format(new Date(range[0])));
		}
		if (range[1] > 0) {
			toField.setText(fmt.format(new Date(range[1])));
		}

		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel.add(new JLabel("Serie simulata:"), gbc);
		gbc.gridy++;
		panel.add(simCombo, gbc);
		gbc.gridy++;
		panel.add(new JLabel("Stream gauge:"), gbc);
		gbc.gridy++;
		panel.add(gaugeCombo, gbc);
		gbc.gridy++;
		panel.add(new JLabel("Data inizio (yyyy-MM-dd, opzionale):"), gbc);
		gbc.gridy++;
		panel.add(fromField, gbc);
		gbc.gridy++;
		panel.add(new JLabel("Data fine (yyyy-MM-dd, opzionale):"), gbc);
		gbc.gridy++;
		panel.add(toField, gbc);

		int choice = JOptionPane.showConfirmDialog(dialog, panel, "Calcolo metriche", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (choice != JOptionPane.OK_OPTION) {
			return;
		}

		Long from = parseDateOrNull(fromField.getText());
		Long to = parseDateOrNull(toField.getText());
		if (from == null && !fromField.getText().isBlank()) {
			appendLog("Formato data inizio non valido. Usa yyyy-MM-dd.");
			return;
		}
		if (to == null && !toField.getText().isBlank()) {
			appendLog("Formato data fine non valido. Usa yyyy-MM-dd.");
			return;
		}
		if (from != null && to != null && from > to) {
			appendLog("Intervallo non valido: data inizio > data fine.");
			return;
		}

		TimeSeries sim = findSeriesByKey((String) simCombo.getSelectedItem());
		TimeSeries gauge = findSeriesByKey((String) gaugeCombo.getSelectedItem());
		if (sim == null || gauge == null) {
			appendLog("Impossibile trovare le serie selezionate per il calcolo metriche.");
			return;
		}

		double[] metrics = computeMetrics(sim, gauge, from, to);
		if (Double.isNaN(metrics[0])) {
			appendLog("Metriche non calcolabili: servono dati in comune nel periodo selezionato.");
			return;
		}
		appendLog(String.format(Locale.ROOT,
				"Metriche [%s vs %s] -> KGE=%.4f, NSE=%.4f, NSElog=%.4f", sim.getKey(), gauge.getKey(), metrics[0],
				metrics[1], metrics[2]));
	}

	private double[] computeMetrics(TimeSeries simulated, TimeSeries observed, Long from, Long to) {
		List<Double> simValues = new ArrayList<>();
		List<Double> obsValues = new ArrayList<>();
		for (int i = 0; i < observed.getItemCount(); i++) {
			TimeSeriesDataItem obsItem = observed.getDataItem(i);
			long t = obsItem.getPeriod().getStart().getTime();
			if (from != null && t < from) {
				continue;
			}
			if (to != null && t > to) {
				continue;
			}
			Number simN = simulated.getValue(obsItem.getPeriod());
			if (simN == null || obsItem.getValue() == null) {
				continue;
			}
			double sim = simN.doubleValue();
			double obs = obsItem.getValue().doubleValue();
			if (Double.isFinite(sim) && Double.isFinite(obs)) {
				simValues.add(sim);
				obsValues.add(obs);
			}
		}
		if (simValues.size() < 2) {
			return new double[] { Double.NaN, Double.NaN, Double.NaN };
		}

		double meanObs = mean(obsValues);
		double meanSim = mean(simValues);
		double nseNum = 0d;
		double nseDen = 0d;
		for (int i = 0; i < simValues.size(); i++) {
			double s = simValues.get(i);
			double o = obsValues.get(i);
			nseNum += (s - o) * (s - o);
			nseDen += (o - meanObs) * (o - meanObs);
		}
		double nse = nseDen == 0d ? Double.NaN : 1d - (nseNum / nseDen);

		List<Double> simLog = new ArrayList<>();
		List<Double> obsLog = new ArrayList<>();
		for (int i = 0; i < simValues.size(); i++) {
			double s = simValues.get(i);
			double o = obsValues.get(i);
			if (s > 0d && o > 0d) {
				simLog.add(Math.log(s));
				obsLog.add(Math.log(o));
			}
		}
		double nseLog = Double.NaN;
		if (simLog.size() >= 2) {
			double meanObsLog = mean(obsLog);
			double num = 0d;
			double den = 0d;
			for (int i = 0; i < simLog.size(); i++) {
				double s = simLog.get(i);
				double o = obsLog.get(i);
				num += (s - o) * (s - o);
				den += (o - meanObsLog) * (o - meanObsLog);
			}
			nseLog = den == 0d ? Double.NaN : 1d - (num / den);
		}

		double r = correlation(simValues, obsValues);
		double stdSim = stddev(simValues, meanSim);
		double stdObs = stddev(obsValues, meanObs);
		double alpha = stdObs == 0d ? Double.NaN : stdSim / stdObs;
		double beta = meanObs == 0d ? Double.NaN : meanSim / meanObs;
		double kge = Double.isNaN(r) || Double.isNaN(alpha) || Double.isNaN(beta) ? Double.NaN
				: 1d - Math.sqrt((r - 1d) * (r - 1d) + (alpha - 1d) * (alpha - 1d) + (beta - 1d) * (beta - 1d));

		return new double[] { kge, nse, nseLog };
	}

	private double mean(List<Double> values) {
		double sum = 0d;
		for (double v : values) {
			sum += v;
		}
		return sum / values.size();
	}

	private double stddev(List<Double> values, double mean) {
		if (values.size() < 2) {
			return Double.NaN;
		}
		double sum = 0d;
		for (double v : values) {
			double d = v - mean;
			sum += d * d;
		}
		return Math.sqrt(sum / (values.size() - 1));
	}

	private double correlation(List<Double> x, List<Double> y) {
		if (x.size() != y.size() || x.size() < 2) {
			return Double.NaN;
		}
		double meanX = mean(x);
		double meanY = mean(y);
		double num = 0d;
		double denX = 0d;
		double denY = 0d;
		for (int i = 0; i < x.size(); i++) {
			double dx = x.get(i) - meanX;
			double dy = y.get(i) - meanY;
			num += dx * dy;
			denX += dx * dx;
			denY += dy * dy;
		}
		if (denX == 0d || denY == 0d) {
			return Double.NaN;
		}
		return num / Math.sqrt(denX * denY);
	}

	private List<TimeSeries> getSimulationSeries() {
		List<TimeSeries> out = new ArrayList<>();
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			TimeSeries s = dataset.getSeries(i);
			if (!isGaugeSeries(s)) {
				out.add(s);
			}
		}
		return out;
	}

	private List<TimeSeries> getGaugeSeries() {
		List<TimeSeries> out = new ArrayList<>();
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			TimeSeries s = dataset.getSeries(i);
			if (isGaugeSeries(s)) {
				out.add(s);
			}
		}
		return out;
	}

	private boolean isGaugeSeries(TimeSeries series) {
		return series != null && series.getKey().toString().toLowerCase(Locale.ROOT).startsWith(STREAM_GAUGE_PREFIX);
	}

	private String[] seriesKeys(List<TimeSeries> series) {
		String[] out = new String[series.size()];
		for (int i = 0; i < series.size(); i++) {
			out[i] = series.get(i).getKey().toString();
		}
		return out;
	}

	private TimeSeries findSeriesByKey(String key) {
		if (key == null) {
			return null;
		}
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			TimeSeries s = dataset.getSeries(i);
			if (key.equals(s.getKey().toString())) {
				return s;
			}
		}
		return null;
	}

	private Long parseDateOrNull(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			SimpleDateFormat fmt = buildDateFormatter();
			Date date = fmt.parse(text.trim());
			return date == null ? null : date.getTime();
		} catch (ParseException e) {
			return null;
		}
	}

	private SimpleDateFormat buildDateFormatter() {
		SimpleDateFormat fmt = new SimpleDateFormat(DATE_FMT, Locale.ROOT);
		fmt.setLenient(false);
		fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
		return fmt;
	}

	private long[] computeDateRange() {
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			TimeSeries s = dataset.getSeries(i);
			if (s.getItemCount() == 0) {
				continue;
			}
			long sMin = s.getDataItem(0).getPeriod().getStart().getTime();
			long sMax = s.getDataItem(s.getItemCount() - 1).getPeriod().getStart().getTime();
			min = Math.min(min, sMin);
			max = Math.max(max, sMax);
		}
		if (min == Long.MAX_VALUE || max == Long.MIN_VALUE) {
			return new long[] { -1, -1 };
		}
		return new long[] { min, max };
	}

	private void appendLog(String message) {
		statusArea.append("$ " + message + "\n");
		statusArea.setCaretPosition(statusArea.getDocument().getLength());
	}
}
