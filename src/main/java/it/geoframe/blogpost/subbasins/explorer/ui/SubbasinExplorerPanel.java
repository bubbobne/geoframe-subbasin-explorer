package it.geoframe.blogpost.subbasins.explorer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.util.ArrayList;
import java.util.List;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.style.FeatureTypeStyle;
import org.geotools.api.style.Fill;
import org.geotools.api.style.LineSymbolizer;
import org.geotools.api.style.Rule;
import org.geotools.api.style.Stroke;
import org.geotools.api.style.Style;
import org.geotools.api.style.StyleFactory;
import org.geotools.api.style.Symbolizer;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.styling.StyleBuilder;
import org.geotools.swing.JMapPane;
import org.geotools.swing.action.InfoAction;
import org.geotools.swing.action.PanAction;
import org.geotools.swing.action.ResetAction;
import org.geotools.swing.action.ZoomInAction;
import org.geotools.swing.action.ZoomOutAction;
import org.geotools.swing.event.MapMouseAdapter;
import org.geotools.swing.event.MapMouseEvent;
import org.geotools.swing.tool.PanTool;
import org.geotools.swing.tool.ScrollWheelTool;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;

import it.geoframe.blogpost.subbasins.explorer.services.ExplorerConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfigStore;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectValidator;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

/**
 *
 * @author Daniele Andreis
 */
public final class SubbasinExplorerPanel extends JPanel {
	private static final Color STREAM_GAUGE_COLOR = new Color(235, 137, 52);
	private static final Color DEFAULT_COLOR = new Color(201, 203, 208);
	private static final Color LAKE_COLOR = new Color(76, 120, 168);
	private static final Color HIGHLIGHT_FILL_COLOR = new Color(255, 240, 120);
	private static final Color HIGHLIGHT_STROKE_COLOR = new Color(189, 25, 45);

	private final ProjectConfig config;
	private final JTextArea infoArea = new JTextArea();
	private final JLabel statusLabel = new JLabel(" ");
	private final FilterFactory ff = CommonFactoryFinder.getFilterFactory();
	private FeatureLayer subbasinLayer;
	private String selectedFeatureId;
	private String selectedSubbasinId;
	private TimeseriesWindow timeseriesWindow;

	private DataStore dataStore;
	private SimpleFeatureSource subbasinSource;
	private SimpleFeatureSource networkSource;
	private JMapPane mapPane;

	public SubbasinExplorerPanel(ProjectConfig config) {
		this.config = config;
		buildUi();
		SwingUtilities.invokeLater(this::loadMapLayers);
	}

	private void buildUi() {
		setLayout(new BorderLayout());

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setResizeWeight(0.72);
		splitPane.setContinuousLayout(true);

		JPanel mapPanel = new JPanel(new BorderLayout());
		mapPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
		mapPane = new JMapPane();
		JToolBar tb = new JToolBar();
		tb.setFloatable(false);
		tb.add(new ZoomInAction(mapPane));
		tb.add(new ZoomOutAction(mapPane));
		tb.add(new PanAction(mapPane));
		tb.add(new ResetAction(mapPane));
		tb.add(new InfoAction(mapPane));
		Action infoAction = new InfoAction(mapPane);
		tb.add(new AbstractAction("Info") {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				infoAction.actionPerformed(e);
				clearSelection();
			}
		});

		tb.add(new AbstractAction("Home") {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				try {
					ReferencedEnvelope env = subbasinSource != null ? subbasinSource.getBounds() : null;
					if (env != null) {
						mapPane.setDisplayArea(env);
					}
				} catch (IOException ex) {
					statusLabel.setText("Impossibile calcolare bounds: " + ex.getMessage());
				}
			}
		});
		mapPanel.add(tb, BorderLayout.NORTH);

		mapPane.setBackground(Color.WHITE);
		mapPanel.add(mapPane, BorderLayout.CENTER);

		JPanel infoPanel = new JPanel(new BorderLayout());
		infoPanel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));
		infoPanel.add(buildInfoHeader(), BorderLayout.NORTH);
		infoPanel.add(buildInfoArea(), BorderLayout.CENTER);

		splitPane.setLeftComponent(mapPanel);
		splitPane.setRightComponent(infoPanel);

		add(splitPane, BorderLayout.CENTER);
	}

	private JPanel buildInfoHeader() {
		JPanel header = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(0, 0, 6, 0);

		JLabel title = new JLabel("Subbasin info");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		header.add(title, gbc);

		gbc.gridy = 1;
		gbc.insets = new Insets(0, 0, 6, 0);
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
		header.add(statusLabel, gbc);

		gbc.gridy = 2;
		gbc.insets = new Insets(0, 0, 0, 0);
		JButton openChartsButton = new JButton("Apri vista grafici");
		openChartsButton.addActionListener(e -> openChartsPlaceholderView());
		header.add(openChartsButton, gbc);

		return header;
	}

	private JScrollPane buildInfoArea() {
		infoArea.setEditable(false);
		infoArea.setLineWrap(true);
		infoArea.setWrapStyleWord(true);
		infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		infoArea.setText("Seleziona un sottobacino per vedere i dettagli.");

		JScrollPane scrollPane = new JScrollPane(infoArea);
		scrollPane.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
				BorderFactory.createEmptyBorder(4, 4, 4, 4)));
		return scrollPane;
	}

	private void openChartsPlaceholderView() {
		if (selectedSubbasinId == null || selectedSubbasinId.isBlank()) {
			statusLabel.setText("Seleziona prima un sottobacino.");
			return;
		}
		new ChartSetupDialog().showDialog();
	}

	private String[] loadSimulationTableNames() {
		if (config == null || config.mode() != ProjectMode.GEOPACKAGE || config.geopackagePath() == null) {
			return new String[0];
		}
		try {
			List<String> simulationTables = ProjectValidator.listSimulationDischargeTables(config.geopackagePath(),
					ExplorerConfig.geopackageSimulationPrefix(), 500);
			return simulationTables.toArray(String[]::new);
		} catch (SQLException e) {
			statusLabel.setText("Errore lettura tabelle simulazione: " + e.getMessage());
			return new String[0];
		}
	}

	private List<String> loadAllTableNamesFromInputs() {
		List<String> out = new ArrayList<>();
		if (config == null) {
			return out;
		}
		out.addAll(listTableNamesFromDb(config.geopackagePath()));
		out.addAll(listTableNamesFromDb(config.sqlitePath()));
		return out;
	}

	private List<String> listTableNamesFromDb(java.nio.file.Path dbPath) {
		if (dbPath == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		String sql = "SELECT name FROM sqlite_master WHERE type IN ('table','view') ORDER BY name";
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
				PreparedStatement ps = c.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				out.add(rs.getString(1));
			}
		} catch (SQLException e) {
			statusLabel.setText("Errore lettura tabelle da " + dbPath.getFileName() + ": " + e.getMessage());
		}
		return out;
	}

	private List<String> loadBasinIds() {
		List<String> out = new ArrayList<>();
		if (subbasinSource == null) {
			return out;
		}
		java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>();
		try (SimpleFeatureIterator it = subbasinSource.getFeatures().features()) {
			while (it.hasNext()) {
				SimpleFeature f = it.next();
				String id = extractSubbasinId(f);
				if (id != null && !id.isBlank()) {
					uniq.add(id);
				}
			}
		} catch (IOException e) {
			statusLabel.setText("Errore lettura ID sottobacini: " + e.getMessage());
		}
		out.addAll(uniq);
		return out;
	}

	private void loadMapLayers() {
		try {
			Optional<SimpleFeatureSource> source = loadSubbasinSource();
			if (source.isEmpty()) {
				if (config.mode() == ProjectMode.LEGACY_FOLDER) {
					statusLabel.setText("Layer legacy non trovato.");
				} else {
					statusLabel.setText("Tabella subbasin non trovata nel GeoPackage.");
				}
				return;
			}

			subbasinSource = source.get();
			MapContent mapContent = new MapContent();
			subbasinLayer = new FeatureLayer(subbasinSource,
					buildSubbasinStyle(subbasinSource.getSchema(), null));

			mapContent.addLayer(subbasinLayer);

			networkSource = loadNetworkSource();

			if (networkSource != null) {
				mapContent.addLayer(new FeatureLayer(networkSource, buildNetworkStyle(networkSource.getSchema())));
			}

			mapPane.setCursorTool(new PanTool()); // pan con mouse
			mapPane.addMouseListener(new ScrollWheelTool(mapPane));
			mapPane.setMapContent(mapContent);
			mapPane.reset();
			mapPane.addMouseListener(new MapMouseAdapter() {
				@Override
				public void onMouseClicked(MapMouseEvent ev) {
					handleMapClick(ev);
				}
			});
			statusLabel.setText(networkSource == null
					? "Clicca su un sottobacino per vedere le informazioni. (layer network non trovato)"
					: "Clicca su un sottobacino per vedere le informazioni.");
		} catch (Exception e) {
			statusLabel.setText("Errore nel caricamento della mappa.");
			infoArea.setText("Dettagli errore: " + e.getMessage());
		}
	}

	private Optional<SimpleFeatureSource> loadSubbasinSource() throws IOException {
		if (config == null) {
			return Optional.empty();
		}
		if (config.mode() == ProjectMode.LEGACY_FOLDER) {
			return loadLegacySource();
		}
		return loadGeoPackageSource();
	}

	private Optional<SimpleFeatureSource> loadLegacySource() throws IOException {
		Map<String, Object> params = new HashMap<>();
		ProjectConfigStore.load().ifPresent(cfg -> params.put("database", cfg.legacyRootPath().toString()));

		dataStore = DataStoreFinder.getDataStore(params);
		if (dataStore == null) {
			return Optional.empty();
		}
		String[] typeNames = dataStore.getTypeNames();
		if (typeNames == null || typeNames.length == 0) {
			return Optional.empty();
		}
		return Optional.of(dataStore.getFeatureSource(typeNames[0]));
	}

	private Optional<SimpleFeatureSource> loadGeoPackageSource() throws IOException {
		Map<String, Object> params = new HashMap<>();
		params.put("dbtype", "geopkg");
		ProjectConfigStore.load().ifPresent(cfg -> params.put("database", cfg.geopackagePath().toString()));

		dataStore = DataStoreFinder.getDataStore(params);
		if (dataStore == null) {
			return Optional.empty();
		}

		String[] typeNames = dataStore.getTypeNames();
		String basinTable = ExplorerConfig.geopackageBasinTable();
		String target = findTypeName(typeNames, basinTable);
		if (target == null) {
			return Optional.empty();
		}

		return Optional.of(dataStore.getFeatureSource(target));
	}

	private SimpleFeatureSource loadNetworkSource() throws IOException {
		if (dataStore == null || config == null || config.mode() != ProjectMode.GEOPACKAGE) {
			return null;
		}
		String[] typeNames = dataStore.getTypeNames();
		String networkTable = ExplorerConfig.geopackageNetworkTable();
		String target = findTypeName(typeNames, networkTable);
		if (target == null) {
			return null;
		}

		SimpleFeatureSource source = dataStore.getFeatureSource(target);
		if (hasLinearGeometry(source.getSchema())) {
			return source;
		}

		for (String typeName : typeNames) {
			SimpleFeatureSource candidate = dataStore.getFeatureSource(typeName);
			if (typeName.toLowerCase(Locale.ROOT).contains(networkTable.toLowerCase(Locale.ROOT))
					&& hasLinearGeometry(candidate.getSchema())) {
				return candidate;
			}
		}
		return source;
	}

	private String findTypeName(String[] typeNames, String expected) {
		if (typeNames == null || expected == null || expected.isBlank()) {
			return null;
		}
		Optional<String> exact = Arrays.stream(typeNames).filter(name -> name.equalsIgnoreCase(expected)).findFirst();
		if (exact.isPresent()) {
			return exact.get();
		}
		Optional<String> startsWith = Arrays.stream(typeNames)
				.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(expected.toLowerCase(Locale.ROOT)))
				.findFirst();
		if (startsWith.isPresent()) {
			return startsWith.get();
		}
		return Arrays.stream(typeNames)
				.filter(name -> name.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))).findFirst()
				.orElse(null);
	}

	private boolean hasLinearGeometry(SimpleFeatureType schema) {
		Class<?> binding = schema.getGeometryDescriptor().getType().getBinding();
		return LineString.class.isAssignableFrom(binding) || MultiLineString.class.isAssignableFrom(binding);
	}

	private Style buildSubbasinStyle(SimpleFeatureType schema, String selectedId) {
		StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory();
		StyleBuilder styleBuilder = new StyleBuilder();

		String geomName = schema.getGeometryDescriptor().getLocalName();

		Rule selectedRule = createPolygonRule(styleBuilder, HIGHLIGHT_FILL_COLOR, geomName);
		selectedRule.setFilter(buildSelectedFeatureFilter(selectedId));
		selectedRule.symbolizers().clear();
		selectedRule.symbolizers().add(createHighlightedSymbolizer(styleBuilder, geomName));

		Rule lakeRule = createPolygonRule(styleBuilder, LAKE_COLOR, geomName);
		lakeRule.setFilter(buildLakeFilter(schema));

		Rule streamGaugeRule = createPolygonRule(styleBuilder, STREAM_GAUGE_COLOR, geomName);
		streamGaugeRule.setFilter(buildStreamGaugeFilter(schema));

		Rule defaultRule = createPolygonRule(styleBuilder, DEFAULT_COLOR, geomName);
		defaultRule.setElseFilter(true);
		FeatureTypeStyle fts = styleFactory
				.createFeatureTypeStyle(new Rule[] { selectedRule, lakeRule, streamGaugeRule, defaultRule });

		Style style = styleFactory.createStyle();
		style.featureTypeStyles().add(fts);
		return style;
	}

	private Symbolizer createHighlightedSymbolizer(StyleBuilder styleBuilder, String geomName) {
		Stroke thickStroke = styleBuilder.createStroke(HIGHLIGHT_STROKE_COLOR, 4.2f);
		Fill fill = styleBuilder.createFill(HIGHLIGHT_FILL_COLOR, 0.8f);
		return styleBuilder.createPolygonSymbolizer(thickStroke, fill, geomName);
	}

	private Filter buildSelectedFeatureFilter(String selectedId) {
		if (selectedId == null || selectedId.isBlank()) {
			return Filter.EXCLUDE;
		}
		return ff.id(Collections.singleton(ff.featureId(selectedId)));
	}

	private Rule createPolygonRule(StyleBuilder sb, Color fillColor, String geomName) {
		Stroke stroke = sb.createStroke(new Color(75, 75, 75), 1.0f);
		Fill fill = sb.createFill(fillColor, 1.0f);
		return sb.createRule(sb.createPolygonSymbolizer(stroke, fill, geomName));
	}

	private Style buildNetworkStyle(SimpleFeatureType schema) {
		StyleBuilder sb = new StyleBuilder();

		Stroke stroke = sb.createStroke(LAKE_COLOR, 0.7f); // colore + spessore
		LineSymbolizer lineSym = sb.createLineSymbolizer(stroke);

		Style style = sb.createStyle(lineSym);

		return style;
	}

	private Filter buildStreamGaugeFilter(SimpleFeatureType schema) {
		String field = resolveStreamGaugeField(schema);
		if (field == null) {
			return Filter.EXCLUDE;
		}
		Expression prop = ff.property(field);
		return ff.notEqual(prop, ff.literal(""), false);
	}

	private Filter buildLakeFilter(SimpleFeatureType schema) {
		String field = findAttributeIgnoreCase(schema, "islake", "is_lake", "isLake");
		if (field == null) {
			return Filter.EXCLUDE;
		}
		Expression prop = ff.property(field);
		Expression asString = ff.function("strToUpperCase", ff.function("strTrim", prop));
		Filter isTrue = ff.equal(prop, ff.literal(true), false);
		Filter isOneNumber = ff.equal(prop, ff.literal(1), false);
		Filter isOneDouble = ff.equal(prop, ff.literal(1.0d), false);
		Filter greaterThanZero = ff.greater(prop, ff.literal(0));
		Filter isTrueString = ff.equal(asString, ff.literal("TRUE"), false);
		Filter isY = ff.equal(asString, ff.literal("Y"), false);
		Filter isYes = ff.equal(asString, ff.literal("YES"), false);
		Filter isT = ff.equal(asString, ff.literal("T"), false);
		Filter isOneString = ff.equal(prop, ff.literal("1"), false);
		return ff.or(ff.or(ff.or(isTrue, ff.or(isOneNumber, isOneDouble)), ff.or(greaterThanZero, isOneString)),
				ff.or(ff.or(isTrueString, isY), ff.or(isYes, isT)));

	}

	private String resolveStreamGaugeField(SimpleFeatureType schema) {
		return findAttributeIgnoreCase(schema, "streamGauge", "isStreamGauge", "stream_gauge", "is_stream_gauge");
	}

	private String findAttributeIgnoreCase(SimpleFeatureType schema, String... candidates) {
		if (schema == null || candidates == null) {
			return null;
		}
		for (String candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			AttributeDescriptor descriptor = schema.getDescriptor(candidate);
			if (descriptor != null) {
				return descriptor.getLocalName();
			}
			for (AttributeDescriptor attributeDescriptor : schema.getAttributeDescriptors()) {
				String attributeName = attributeDescriptor.getLocalName();
				if (attributeName != null && attributeName.equalsIgnoreCase(candidate)) {
					return attributeName;
				}
			}
		}
		return null;
	}

	
	private void clearSelection() {
		selectedFeatureId = null;
		refreshSubbasinStyle();
	}

	
	private void handleMapClick(MapMouseEvent ev) {
		if (subbasinSource == null) {
			return;
		}

		ReferencedEnvelope displayArea = mapPane.getDisplayArea();
		if (displayArea == null) {
			return;
		}

		double toleranceX = displayArea.getWidth() / Math.max(1, mapPane.getWidth()) * 6;
		double toleranceY = displayArea.getHeight() / Math.max(1, mapPane.getHeight()) * 6;
		double x = ev.getWorldPos().x;
		double y = ev.getWorldPos().y;
		double minx = x - toleranceX;
		double maxx = x + toleranceX;
		double miny = y - toleranceY;
		double maxy = y + toleranceY;

		Filter filter = ff.bbox(ff.property(subbasinSource.getSchema().getGeometryDescriptor().getLocalName()), minx,
				miny, maxx, maxy, null);

		try {
			SimpleFeatureCollection collection = subbasinSource
					.getFeatures(new Query(subbasinSource.getName().getLocalPart(), filter));
			try (SimpleFeatureIterator iterator = collection.features()) {
				if (iterator.hasNext()) {
					updateInfo(iterator.next());
				} else {
					infoArea.setText("Nessun sottobacino trovato in questo punto.");
					clearSelection();
				}
			}
		} catch (IOException e) {
			infoArea.setText("Errore nella selezione: " + e.getMessage());
		}
	}

	private void updateInfo(SimpleFeature feature) {
		selectedFeatureId = feature.getID();
		selectedSubbasinId = extractSubbasinId(feature);
		refreshSubbasinStyle();

		StringBuilder sb = new StringBuilder();
		sb.append("ID: ").append(feature.getID()).append("\n\n");
		for (int i = 1; i < feature.getAttributeCount(); i++) {
			String name = feature.getFeatureType().getDescriptor(i).getLocalName();
			Object value = feature.getAttribute(i);
			sb.append(name).append(": ").append(value == null ? "" : value).append("\n");
		}
		infoArea.setText(sb.toString());
		infoArea.setCaretPosition(0);
	}

	private String extractSubbasinId(SimpleFeature feature) {
		Object value = feature.getAttribute("basin_id");
		if (value == null) {
			value = feature.getAttribute("basinid");
		}
		if (value == null) {
			value = feature.getAttribute("id");
		}
		if (value == null) {
			value = feature.getAttribute("ID");
		}
		if (value == null) {
			String fid = feature.getID();
			if (fid == null) {
				return null;
			}
			int dot = fid.lastIndexOf('.');
			return dot >= 0 ? fid.substring(dot + 1) : fid;
		}
		return String.valueOf(value);
	}

	private boolean isSelectedSubbasinStreamGauge() {
		if (subbasinSource == null || selectedFeatureId == null) {
			return false;
		}
		String streamField = resolveStreamGaugeField(subbasinSource.getSchema());
		if (streamField == null) {
			return false;
		}
		try {
			SimpleFeatureCollection collection = subbasinSource.getFeatures();
			try (SimpleFeatureIterator it = collection.features()) {
				while (it.hasNext()) {
					SimpleFeature f = it.next();
					if (selectedFeatureId.equals(f.getID())) {
						Object v = f.getAttribute(streamField);
						if (v == null) return false;
						String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
						return !s.isBlank() && !s.equals("false") && !s.equals("0") && !s.equals("no") && !s.equals("n");
					}
				}
			}
		} catch (IOException ignored) {
		}
		return false;
	}

	private final class ChartSetupDialog {
		private final JDialog dialog = new JDialog();
		private final JComboBox<String> simulationCombo = new JComboBox<>();
		private final JComboBox<String> typeCombo = new JComboBox<>(new String[] { "discharge", "state", "fluxes" });

		private ChartSetupDialog() {
			dialog.setModal(false);
			dialog.setTitle("Selezione grafico");
			dialog.setLayout(new BorderLayout(8, 8));
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			JPanel panel = new JPanel(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.insets = new Insets(6, 6, 6, 6);
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1;
			if (config.mode() == ProjectMode.GEOPACKAGE) {
				panel.add(new JLabel("Simulazione da plottare:"), gbc);
				gbc.gridy++;
				for (String t : loadSimulationTableNames()) {
					simulationCombo.addItem(t);
				}
				panel.add(simulationCombo, gbc);
				gbc.gridy++;
			}
			panel.add(new JLabel("Tipo grafico:"), gbc);
			gbc.gridy++;
			panel.add(typeCombo, gbc);
			gbc.gridy++;
			JButton nextButton = new JButton("Avanti");
			nextButton.addActionListener(e -> {
				dialog.dispose();
				if (timeseriesWindow == null) {
					timeseriesWindow = new TimeseriesWindow();
				}
				timeseriesWindow.showForSelection(selectedSubbasinId, (String) simulationCombo.getSelectedItem(),
						(String) typeCombo.getSelectedItem());
			});
			panel.add(nextButton, gbc);
			dialog.add(panel, BorderLayout.CENTER);
			dialog.setSize(new Dimension(420, 260));
			dialog.setLocationRelativeTo(SubbasinExplorerPanel.this);
		}

		private void showDialog() {
			dialog.setVisible(true);
		}
	}

	private final class TimeseriesWindow {
		private final JDialog dialog;
		private final JComboBox<String> tableCombo;
		private final JComboBox<String> basinCombo;
		private final JComboBox<String> streamGaugeCombo;
		private final JTextArea statusArea;
		private final TimeSeriesCollection dataset;
		private final DefaultListModel<String> loadedSeriesModel;
		private final JList<String> loadedSeriesList;
		private final java.util.Map<String, TimeSeries> loadedSeries = new java.util.LinkedHashMap<>();
		private JFreeChart chart;
		private String activeSubbasinId;
		private String activeType;
		private String baseSeriesKey;

		private TimeseriesWindow() {
			dialog = new JDialog();
			dialog.setModal(false);
			dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
			dialog.setTitle("Vista grafico");
			dialog.setLayout(new BorderLayout(8, 8));
			dataset = new TimeSeriesCollection();
			chart = ChartFactory.createTimeSeriesChart("Timeseries", "Tempo", "Valore", dataset, true, true, false);

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
			controlsPanel.add(new JLabel("Tabella simulazione:"), gbc);
			gbc.gridy++;
			controlsPanel.add(tableCombo, gbc);
			gbc.gridy++;
			JButton addSimulationButton = new JButton("Carica simulazione");
			addSimulationButton.addActionListener(e -> addSimulationSeries());
			controlsPanel.add(addSimulationButton, gbc);
			gbc.gridy++;
			controlsPanel.add(new JLabel("Sottobacino:"), gbc);
			gbc.gridy++;
			controlsPanel.add(basinCombo, gbc);
			gbc.gridy++;
			JButton addBasinButton = new JButton("Carica sottobacino");
			addBasinButton.addActionListener(e -> addSimulationSeries());
			controlsPanel.add(addBasinButton, gbc);
			gbc.gridy++;
			if (config.mode() == ProjectMode.GEOPACKAGE) {
				controlsPanel.add(new JLabel("Tabella stream gauge:"), gbc);
				gbc.gridy++;
				controlsPanel.add(streamGaugeCombo, gbc);
				gbc.gridy++;
				JButton addGaugeButton = new JButton("Carica stream gauge");
				addGaugeButton.addActionListener(e -> addStreamGaugeSeries());
				controlsPanel.add(addGaugeButton, gbc);
				gbc.gridy++;
			}
			controlsPanel.add(new JLabel("Serie caricate:"), gbc);
			gbc.gridy++;
			loadedSeriesModel = new DefaultListModel<>();
			loadedSeriesList = new JList<>(loadedSeriesModel);
			loadedSeriesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			controlsPanel.add(new JScrollPane(loadedSeriesList), gbc);
			gbc.gridy++;
			JButton removeButton = new JButton("Rimuovi serie selezionata");
			removeButton.addActionListener(e -> removeSelectedSeries());
			controlsPanel.add(removeButton, gbc);
			gbc.gridy++;

			statusArea = new JTextArea();
			statusArea.setEditable(false);
			statusArea.setLineWrap(true);
			statusArea.setWrapStyleWord(true);
			JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsPanel, new ChartPanel(chart));
			splitPane.setResizeWeight(0.30);
			dialog.add(splitPane, BorderLayout.CENTER);
			dialog.add(new JScrollPane(statusArea), BorderLayout.SOUTH);
			dialog.setSize(new Dimension(1240, 700));
			dialog.setLocationRelativeTo(SubbasinExplorerPanel.this);
		}

		private void showForSelection(String subbasinId, String firstTable, String type) {
			this.activeSubbasinId = subbasinId;
			this.activeType = type == null ? "discharge" : type;
			dataset.removeAllSeries();
			loadedSeries.clear();
			loadedSeriesModel.clear();
			baseSeriesKey = null;
			reloadCombos();
			if (subbasinId != null) basinCombo.setSelectedItem(subbasinId);
			if (firstTable != null) tableCombo.setSelectedItem(firstTable);
			dialog.setTitle("Vista " + activeType);
			dialog.setVisible(true);
			addBaseSeries();
		}

		private void reloadCombos() {
			tableCombo.removeAllItems();
			for (String table : loadAllTableNamesFromInputs()) tableCombo.addItem(table);
			basinCombo.removeAllItems();
			for (String id : loadBasinIds()) basinCombo.addItem(id);
			streamGaugeCombo.removeAllItems();
			if (config.mode() == ProjectMode.GEOPACKAGE && isSelectedSubbasinStreamGauge()) {
				for (String table : loadAllTableNamesFromInputs()) streamGaugeCombo.addItem(table);
			}
		}

		private void addBaseSeries() {
			String table = (String) tableCombo.getSelectedItem();
			String basinId = activeSubbasinId != null ? activeSubbasinId : (String) basinCombo.getSelectedItem();
			if (table == null || basinId == null) {
				statusArea.setText("Serie iniziale non caricata: selezione incompleta.");
				return;
			}
			String key = key(table, basinId, "base");
			TimeSeries series = new TimeSeries(table + " | basin " + basinId + " [iniziale]");
			int count = fillSeriesFromAnyInput(table, basinId, series);
			if (count <= 0) {
				statusArea.setText("Serie iniziale non trovata in input.");
				return;
			}
			baseSeriesKey = key;
			addSeriesInternal(key, series, true);
			statusArea.setText("Serie iniziale caricata (blu): " + table + " | basin " + basinId + " | punti: " + count);
		}

		private void addSimulationSeries() {
			String table = (String) tableCombo.getSelectedItem();
			String basinId = (String) basinCombo.getSelectedItem();
			if (table == null || basinId == null) {
				statusArea.setText("Seleziona simulazione e sottobacino.");
				return;
			}
			String key = key(table, basinId, "sim");
			if (loadedSeries.containsKey(key)) {
				statusArea.setText("Serie già presente: " + table + " | basin " + basinId);
				return;
			}
			TimeSeries series = new TimeSeries(table + " | basin " + basinId);
			int count = fillSeriesFromAnyInput(table, basinId, series);
			if (count <= 0) {
				statusArea.setText("Nessun dato trovato per: " + table + " | basin " + basinId);
				return;
			}
			addSeriesInternal(key, series, false);
			statusArea.setText("Aggiunta serie simulazione: " + table + " | basin " + basinId + " | punti: " + count);
		}

		private void addStreamGaugeSeries() {
			if (!(config.mode() == ProjectMode.GEOPACKAGE && isSelectedSubbasinStreamGauge())) {
				statusArea.setText("Il sottobacino selezionato non è stream gauge.");
				return;
			}
			String table = (String) streamGaugeCombo.getSelectedItem();
			String basinId = (String) basinCombo.getSelectedItem();
			if (table == null || basinId == null) {
				statusArea.setText("Seleziona tabella stream gauge e sottobacino.");
				return;
			}
			String key = key(table, basinId, "gauge");
			if (loadedSeries.containsKey(key)) {
				statusArea.setText("Serie stream gauge già presente.");
				return;
			}
			TimeSeries series = new TimeSeries("stream gauge | " + table + " | basin " + basinId);
			int count = fillSeriesFromAnyInput(table, basinId, series);
			if (count <= 0) {
				statusArea.setText("Nessun dato stream gauge trovato.");
				return;
			}
			addSeriesInternal(key, series, false);
			statusArea.setText("Aggiunta serie stream gauge: " + table + " | basin " + basinId + " | punti: " + count);
		}

		private void addSeriesInternal(String key, TimeSeries series, boolean isBase) {
			loadedSeries.put(key, series);
			dataset.addSeries(series);
			loadedSeriesModel.addElement(key + (isBase ? " (base, non rimovibile)" : ""));
			restyleSeries();
		}

		private void removeSelectedSeries() {
			String selected = loadedSeriesList.getSelectedValue();
			if (selected == null) {
				statusArea.setText("Seleziona una serie da rimuovere.");
				return;
			}
			String key = selected.replace(" (base, non rimovibile)", "");
			if (baseSeriesKey != null && baseSeriesKey.equals(key)) {
				statusArea.setText("La serie iniziale (blu) non può essere rimossa.");
				return;
			}
			TimeSeries ts = loadedSeries.remove(key);
			if (ts != null) {
				dataset.removeSeries(ts);
				loadedSeriesModel.removeElement(selected);
				restyleSeries();
				statusArea.setText("Serie rimossa: " + key);
			}
		}

		private void restyleSeries() {
			XYPlot plot = chart.getXYPlot();
			XYLineAndShapeRenderer renderer;
			if (plot.getRenderer() instanceof XYLineAndShapeRenderer) {
				renderer = (XYLineAndShapeRenderer) plot.getRenderer();
			} else {
				renderer = new XYLineAndShapeRenderer(true, false);
				plot.setRenderer(renderer);
			}
			for (int i = 0; i < dataset.getSeriesCount(); i++) {
				Comparable<?> name = dataset.getSeriesKey(i);
				if (name != null && baseSeriesKey != null && name.toString().contains("[iniziale]")) {
					renderer.setSeriesPaint(i, Color.BLUE);
				} else {
					renderer.setSeriesPaint(i, null);
				}
			}
		}

		private String key(String table, String basinId, String type) {
			return type + " | " + table + " | basin " + basinId;
		}

		private int fillSeriesFromAnyInput(String table, String basinId, TimeSeries series) {
			int count = fillSeriesFromDb(config.geopackagePath(), table, basinId, series);
			if (count > 0) return count;
			return fillSeriesFromDb(config.sqlitePath(), table, basinId, series);
		}

		private int fillSeriesFromDb(java.nio.file.Path dbPath, String table, String basinId, TimeSeries series) {
			if (dbPath == null) return 0;
			String safeTable = table.replace("\"", "\"\"");
			String sql = "SELECT ts, value FROM \"" + safeTable + "\" WHERE CAST(basin_id AS TEXT)=? ORDER BY ts";
			try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
					PreparedStatement ps = c.prepareStatement(sql)) {
				ps.setString(1, basinId);
				int count = 0;
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						long ts = rs.getLong("ts");
						double value = rs.getDouble("value");
						if (!rs.wasNull()) {
							series.addOrUpdate(new Millisecond(new java.util.Date(ts)), value);
							count++;
						}
					}
				}
				return count;
			} catch (SQLException ex) {
				return 0;
			}
		}
	}

	private void refreshSubbasinStyle() {
		if (subbasinLayer == null || subbasinSource == null) {
			return;
		}
		subbasinLayer.setStyle(buildSubbasinStyle(subbasinSource.getSchema(), selectedFeatureId));
		mapPane.repaint();
	}

}
