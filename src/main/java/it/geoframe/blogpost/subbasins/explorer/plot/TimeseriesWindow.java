package it.geoframe.blogpost.subbasins.explorer.plot;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer2;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;
import org.jfree.data.time.TimeTableXYDataset;

import it.geoframe.blogpost.subbasins.explorer.io.TimeseriesLoader;
import it.geoframe.blogpost.subbasins.explorer.services.ExplorerConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;

public final class TimeseriesWindow {
	private static final String STREAM_GAUGE_PREFIX = "stream gauge";
	private static final String DATE_FMT = "yyyy-MM-dd";
	private record StatePoint(long timestamp, double sweDelta, double aetDelta, double canopyDelta, double rootzoneDelta,
			double runoffDelta, double groundDelta) {
	}

	private final ProjectConfig config;
	private final TimeseriesLoader loader;
	private final Supplier<List<String>> tableSupplier;
	private final Supplier<List<String>> basinSupplier;
	private final BooleanSupplier streamGaugeSelectionSupplier;
	private final JDialog dialog;
	private final JComboBox<String> simulationTableCombo;
	private final JComboBox<String> basinCombo;
	private final JComboBox<String> streamGaugeCombo;
	private final JComboBox<String> stateAggregationCombo;
	private final JList<String> seriesList;
	private final JTextArea statusArea;
	private final TimeSeriesCollection dataset;
	private String activeType;
	private String baseSeriesKey;
	private final XYLineAndShapeRenderer renderer;
	private final StackedXYAreaRenderer2 stackedRenderer;
	private final XYPlot plot;
	private final CardLayout modeControlsLayout;
	private final JPanel modeControlsContainer;

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
		plot = chart.getXYPlot();
		renderer = new XYLineAndShapeRenderer(true, false);
		stackedRenderer = new StackedXYAreaRenderer2();
		plot.setRenderer(renderer);
		plot.setDomainPannable(true);
		plot.setRangePannable(true);

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
		stateAggregationCombo = new JComboBox<>(ExplorerConfig.stateAggregationOptions());
		stateAggregationCombo.setSelectedItem(ExplorerConfig.stateAggregationDefault());
		stateAggregationCombo.addActionListener(e -> {
			if ("state".equalsIgnoreCase(activeType)) {
				addSelectedSeriesFromSimulationCombo();
			}
		});
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

		modeControlsLayout = new CardLayout();
		modeControlsContainer = new JPanel(modeControlsLayout);
		modeControlsContainer.add(buildDischargeControls(), "discharge");
		modeControlsContainer.add(buildStateControls(), "state");
		modeControlsContainer.add(buildFluxesControls(), "fluxes");
		controlsPanel.add(modeControlsContainer, gbc);
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

		ChartPanel chartPanel = new ChartPanel(chart);
		chartPanel.setMouseWheelEnabled(true);
		chartPanel.setMouseZoomable(true, false);
		JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanel, consoleScroll);
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
		showModeControls();
		if (subbasinId != null) {
			basinCombo.setSelectedItem(subbasinId);
		}
		if (firstTable != null) {
			simulationTableCombo.setSelectedItem(firstTable);
		}
		plot.setDataset(dataset);
		plot.setRenderer(renderer);
		dialog.setTitle("Vista " + activeType);
		dialog.setVisible(true);
		appendLog("Aperta vista " + activeType + " per sottobacino " + String.valueOf(subbasinId) + ".");
		addSelectedSeriesFromSimulationCombo();
	}

	private JPanel buildDischargeControls() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 0, 2, 0);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel.add(new JLabel("Stream gauge:"), gbc);
		gbc.gridy++;
		panel.add(streamGaugeCombo, gbc);
		gbc.gridy++;
		JButton addGaugeButton = new JButton("Carica stream gauge");
		addGaugeButton.addActionListener(e -> addSelectedSeriesFromGaugeCombo());
		panel.add(addGaugeButton, gbc);
		gbc.gridy++;
		JButton metricsButton = new JButton("Calcola metriche (KGE, NSE, NSElog)");
		metricsButton.addActionListener(e -> showMetricsPopup());
		panel.add(metricsButton, gbc);
		return panel;
	}

	private JPanel buildStateControls() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 0, 2, 0);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel.add(new JLabel("Aggregazione stati:"), gbc);
		gbc.gridy++;
		panel.add(stateAggregationCombo, gbc);
		return panel;
	}

	private JPanel buildFluxesControls() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JLabel("Fluxes: serie fisse senza controlli aggiuntivi."), BorderLayout.CENTER);
		return panel;
	}

	private void showModeControls() {
		String mode = activeType == null ? "discharge" : activeType.toLowerCase(Locale.ROOT);
		if (!"discharge".equals(mode) && !"state".equals(mode) && !"fluxes".equals(mode)) {
			mode = "discharge";
		}
		modeControlsLayout.show(modeControlsContainer, mode);
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
			if (table == null) {
				continue;
			}
			String lower = table.toLowerCase(Locale.ROOT);
			boolean isDischarge = lower.contains("discharge");
			if ("discharge".equalsIgnoreCase(activeType) && isDischarge) {
				filtered.add(table);
			}
			if (("state".equalsIgnoreCase(activeType) || "fluxes".equalsIgnoreCase(activeType)) && lower.contains("sim")
					&& !isDischarge) {
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

	private void addSelectedSeriesFromSimulationCombo() {
		String table = (String) simulationTableCombo.getSelectedItem();
		if ("fluxes".equalsIgnoreCase(activeType)) {
			addFluxesSeries(table);
			return;
		}
		if ("state".equalsIgnoreCase(activeType)) {
			addStateSeries(table);
			return;
		}
		addSeries(table, false);
	}

	private void addFluxesSeries(String table) {
		String basinId = (String) basinCombo.getSelectedItem();
		if (table == null || basinId == null) {
			appendLog("Seleziona tabella e sottobacino.");
			return;
		}
		dataset.removeAllSeries();
		baseSeriesKey = null;
		List<TimeseriesLoader.TimeValueRow> rows = loader.loadRowsFromAnyInput(config, table, basinId,
				cfg("charts.fluxes.columns.melting_discharge", "melting_discharge"),
				cfg("charts.fluxes.columns.canopy_throughfall", "canopy_throughfall"),
				cfg("charts.fluxes.columns.canopy_aet", "canopy_aet"),
				cfg("charts.fluxes.columns.rootzone_aet", "rootzone_aet"),
				cfg("charts.fluxes.columns.root_zone_recharge", "root_zone_recharge"),
				cfg("charts.fluxes.columns.ground_discharge", "ground_discharge"),
				cfg("charts.fluxes.columns.runoff_discharge", "runoff_discharge"),
				cfg("charts.fluxes.columns.rootzone_quick", "rootzone_quick"));
		if (rows.isEmpty()) {
			appendLog("Nessun dato fluxes trovato in " + table + " per basin " + basinId + ".");
			return;
		}
		String meltCol = cfg("charts.fluxes.columns.melting_discharge", "melting_discharge");
		String throughCol = cfg("charts.fluxes.columns.canopy_throughfall", "canopy_throughfall");
		String canopyAetCol = cfg("charts.fluxes.columns.canopy_aet", "canopy_aet");
		String rootAetCol = cfg("charts.fluxes.columns.rootzone_aet", "rootzone_aet");
		String rechargeCol = cfg("charts.fluxes.columns.root_zone_recharge", "root_zone_recharge");
		String groundCol = cfg("charts.fluxes.columns.ground_discharge", "ground_discharge");
		String runoffCol = cfg("charts.fluxes.columns.runoff_discharge", "runoff_discharge");
		String quickCol = cfg("charts.fluxes.columns.rootzone_quick", "rootzone_quick");
		addLineSeries(rows, meltCol, cfg("charts.fluxes.labels.melting_discharge", "melting_discharg"),
				cfgColor("charts.fluxes.colors.melting_discharge", "#75C4FF"));
		addLineSeries(rows, throughCol, cfg("charts.fluxes.labels.canopy_throughfall", "canopy_throughfall"),
				cfgColor("charts.fluxes.colors.canopy_throughfall", "#22C55E"));
		addSummedLineSeries(rows, cfg("charts.fluxes.labels.aet_sum", "canopy_aet + rootzone_aet"),
				new String[] { canopyAetCol, rootAetCol }, cfgColor("charts.fluxes.colors.aet_sum", "#F97316"));
		addLineSeries(rows, rechargeCol, cfg("charts.fluxes.labels.root_zone_recharge", "root_zone_recharge"),
				cfgColor("charts.fluxes.colors.root_zone_recharge", "#784820"));
		addLineSeries(rows, groundCol, cfg("charts.fluxes.labels.ground_discharge", "ground_discharge"),
				cfgColor("charts.fluxes.colors.ground_discharge", "#808080"));
		addLineSeries(rows, runoffCol, cfg("charts.fluxes.labels.runoff_discharge", "runoff_discharge"),
				cfgColor("charts.fluxes.colors.runoff_discharge", "#0000FF"));
		addLineSeries(rows, quickCol, cfg("charts.fluxes.labels.rootzone_quick", "rootzone_quick"),
				cfgColor("charts.fluxes.colors.rootzone_quick", "#4F46E5"));
		reloadSeriesList();
		appendLog("Caricate serie fluxes da " + table + " | basin " + basinId + " | punti: " + rows.size());
	}

	private void addStateSeries(String table) {
		String basinId = (String) basinCombo.getSelectedItem();
		if (table == null || basinId == null) {
			appendLog("Seleziona tabella e sottobacino.");
			return;
		}
		List<TimeseriesLoader.TimeValueRow> rows = loader.loadRowsFromAnyInput(config, table, basinId,
				cfg("charts.state.columns.swe", "swe"),
				cfg("charts.state.columns.rootzone_aet", "rootzone_aet"),
				cfg("charts.state.columns.canopy_aet", "canopy_aet"),
				cfg("charts.state.columns.canopy_final", "canopy_final"),
				cfg("charts.state.columns.canopy_initial", "canopy_initial"),
				cfg("charts.state.columns.rootzone_final", "rootzone_final"),
				cfg("charts.state.columns.rootzone_initial", "rootzone_initial"),
				cfg("charts.state.columns.runoff_final", "runoff_final"),
				cfg("charts.state.columns.runoff_initial", "runoff_initial"),
				cfg("charts.state.columns.ground_final", "ground_final"),
				cfg("charts.state.columns.ground_initial", "ground_initial"));
		if (rows.isEmpty()) {
			appendLog("Nessun dato state trovato in " + table + " per basin " + basinId + ".");
			return;
		}
		List<StatePoint> deltas = computeStateDeltas(rows);
		List<StatePoint> aggregated = aggregateStatePoints(deltas, (String) stateAggregationCombo.getSelectedItem());
		TimeTableXYDataset stateDataset = new TimeTableXYDataset();
		for (StatePoint row : aggregated) {
			Date date = new Date(row.timestamp());
			stateDataset.add(new Millisecond(date), row.sweDelta(), cfg("charts.state.labels.swe", "swe"));
			stateDataset.add(new Millisecond(date), row.aetDelta(), cfg("charts.state.labels.aet_sum", "rootzone_aet + canopy_aet"));
			stateDataset.add(new Millisecond(date), row.canopyDelta(), cfg("charts.state.labels.canopy_delta", "canopy_final - canopy_initial"));
			stateDataset.add(new Millisecond(date), row.rootzoneDelta(), cfg("charts.state.labels.rootzone_delta", "rootzone_final - rootzone_initial"));
			stateDataset.add(new Millisecond(date), row.runoffDelta(), cfg("charts.state.labels.runoff_delta", "runoff_final - runoff_initial"));
			stateDataset.add(new Millisecond(date), row.groundDelta(), cfg("charts.state.labels.ground_delta", "ground_final - ground_initial"));
		}
		plot.setDataset(stateDataset);
		plot.setRenderer(stackedRenderer);
		stackedRenderer.setSeriesPaint(0, cfgColor("charts.state.colors.swe", "#808080"));
		stackedRenderer.setSeriesPaint(1, cfgColor("charts.state.colors.aet_sum", "#F97316"));
		stackedRenderer.setSeriesPaint(2, cfgColor("charts.state.colors.canopy_delta", "#22C55E"));
		stackedRenderer.setSeriesPaint(3, cfgColor("charts.state.colors.rootzone_delta", "#784820"));
		stackedRenderer.setSeriesPaint(4, cfgColor("charts.state.colors.runoff_delta", "#0000FF"));
		stackedRenderer.setSeriesPaint(5, cfgColor("charts.state.colors.ground_delta", "#7D7D7D"));
		dataset.removeAllSeries();
		reloadSeriesList();
		appendLog("Caricate serie state impilate da " + table + " | basin " + basinId + " | aggregazione: "
				+ stateAggregationCombo.getSelectedItem() + " | punti: " + aggregated.size());
	}

	private List<StatePoint> computeStateDeltas(List<TimeseriesLoader.TimeValueRow> rows) {
		List<StatePoint> out = new ArrayList<>();
		double previousSwe = Double.NaN;
		String sweCol = cfg("charts.state.columns.swe", "swe");
		String rootzoneAetCol = cfg("charts.state.columns.rootzone_aet", "rootzone_aet");
		String canopyAetCol = cfg("charts.state.columns.canopy_aet", "canopy_aet");
		String canopyFinalCol = cfg("charts.state.columns.canopy_final", "canopy_final");
		String canopyInitialCol = cfg("charts.state.columns.canopy_initial", "canopy_initial");
		String rootzoneFinalCol = cfg("charts.state.columns.rootzone_final", "rootzone_final");
		String rootzoneInitialCol = cfg("charts.state.columns.rootzone_initial", "rootzone_initial");
		String runoffFinalCol = cfg("charts.state.columns.runoff_final", "runoff_final");
		String runoffInitialCol = cfg("charts.state.columns.runoff_initial", "runoff_initial");
		String groundFinalCol = cfg("charts.state.columns.ground_final", "ground_final");
		String groundInitialCol = cfg("charts.state.columns.ground_initial", "ground_initial");
		for (TimeseriesLoader.TimeValueRow row : rows) {
			double swe = value(row, sweCol);
			double sweDelta = Double.isFinite(previousSwe) && Double.isFinite(swe) ? swe - previousSwe : 0d;
			if (Double.isFinite(swe)) {
				previousSwe = swe;
			}
			out.add(new StatePoint(row.timestamp(), sweDelta, value(row, rootzoneAetCol) + value(row, canopyAetCol),
					value(row, canopyFinalCol) - value(row, canopyInitialCol),
					value(row, rootzoneFinalCol) - value(row, rootzoneInitialCol),
					value(row, runoffFinalCol) - value(row, runoffInitialCol),
					value(row, groundFinalCol) - value(row, groundInitialCol)));
		}
		return out;
	}

	private List<StatePoint> aggregateStatePoints(List<StatePoint> points, String aggregation) {
		if (points.isEmpty()) {
			return List.of();
		}
		Map<Long, StatePoint> aggregated = new LinkedHashMap<>();
		for (StatePoint p : points) {
			long keyTs = bucketStart(p.timestamp(), aggregation);
			StatePoint current = aggregated.get(keyTs);
			if (current == null) {
				aggregated.put(keyTs, new StatePoint(keyTs, p.sweDelta(), p.aetDelta(), p.canopyDelta(), p.rootzoneDelta(),
						p.runoffDelta(), p.groundDelta()));
			} else {
				aggregated.put(keyTs,
						new StatePoint(keyTs, current.sweDelta() + p.sweDelta(), current.aetDelta() + p.aetDelta(),
								current.canopyDelta() + p.canopyDelta(), current.rootzoneDelta() + p.rootzoneDelta(),
								current.runoffDelta() + p.runoffDelta(), current.groundDelta() + p.groundDelta()));
			}
		}
		return new ArrayList<>(aggregated.values());
	}

	private long bucketStart(long ts, String aggregation) {
		Instant instant = Instant.ofEpochMilli(ts);
		if ("1h".equalsIgnoreCase(aggregation)) {
			return (ts / 3_600_000L) * 3_600_000L;
		}
		if ("12h".equalsIgnoreCase(aggregation)) {
			return (ts / 43_200_000L) * 43_200_000L;
		}
		if ("24h".equalsIgnoreCase(aggregation)) {
			return (ts / 86_400_000L) * 86_400_000L;
		}
		LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
		if ("settimana".equalsIgnoreCase(aggregation)) {
			LocalDate monday = date.with(java.time.DayOfWeek.MONDAY);
			return monday.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
		}
		if ("anno".equalsIgnoreCase(aggregation)) {
			LocalDate firstYearDay = date.with(TemporalAdjusters.firstDayOfYear());
			return firstYearDay.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
		}
		LocalDate firstMonthDay = date.with(TemporalAdjusters.firstDayOfMonth());
		return firstMonthDay.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
	}


	private String cfg(String key, String defaultValue) {
		return ExplorerConfig.chartOption(key, defaultValue);
	}

	private Color cfgColor(String key, String defaultHex) {
		String raw = cfg(key, defaultHex);
		try {
			return Color.decode(raw.startsWith("#") ? raw : ("#" + raw));
		} catch (NumberFormatException ex) {
			return Color.decode(defaultHex);
		}
	}


	private void addLineSeries(List<TimeseriesLoader.TimeValueRow> rows, String key, String label, Color color) {
		TimeSeries series = new TimeSeries(label);
		for (TimeseriesLoader.TimeValueRow row : rows) {
			double v = value(row, key);
			if (Double.isFinite(v)) {
				series.addOrUpdate(new Millisecond(new Date(row.timestamp())), v);
			}
		}
		dataset.addSeries(series);
		renderer.setSeriesPaint(dataset.getSeriesCount() - 1, color);
	}

	private void addSummedLineSeries(List<TimeseriesLoader.TimeValueRow> rows, String label, String[] keys, Color color) {
		TimeSeries series = new TimeSeries(label);
		for (TimeseriesLoader.TimeValueRow row : rows) {
			double sum = 0d;
			boolean valid = true;
			for (String key : keys) {
				double v = value(row, key);
				if (!Double.isFinite(v)) {
					valid = false;
					break;
				}
				sum += v;
			}
			if (valid) {
				series.addOrUpdate(new Millisecond(new Date(row.timestamp())), sum);
			}
		}
		dataset.addSeries(series);
		renderer.setSeriesPaint(dataset.getSeriesCount() - 1, color);
	}

	private double value(TimeseriesLoader.TimeValueRow row, String key) {
		if (row == null || row.values() == null) {
			return Double.NaN;
		}
		Double v = row.values().get(key);
		return v == null ? Double.NaN : v.doubleValue();
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
