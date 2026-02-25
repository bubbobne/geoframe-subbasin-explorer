package it.geoframe.blogpost.subbasins.explorer.ui;

import it.geoframe.blogpost.subbasins.explorer.services.ExplorerConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfigStore;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.feature.type.AttributeDescriptor;
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
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.StyleBuilder;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.JMapPane;
import org.geotools.swing.action.InfoAction;
import org.geotools.swing.action.PanAction;
import org.geotools.swing.action.ResetAction;
import org.geotools.swing.action.ZoomInAction;
import org.geotools.swing.action.ZoomOutAction;
import org.geotools.swing.event.MapMouseAdapter;
import org.geotools.swing.event.MapMouseEvent;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.geotools.swing.tool.PanTool;
import org.geotools.swing.tool.ScrollWheelTool;
import java.util.Collections;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
		JDialog dialog = new JDialog();
		dialog.setModal(false);
		dialog.setTitle("Vista grafici (placeholder)");
		dialog.setLayout(new BorderLayout(8, 8));

		JPanel controlsPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;

		gbc.gridx = 0;
		gbc.gridy = 0;
		controlsPanel.add(new JLabel("Dominio:"), gbc);
		gbc.gridy++;
		controlsPanel.add(new JComboBox<>(new String[] { "Snow", "Canopy", "Root zone", "Groundwater" }), gbc);

		gbc.gridy++;
		controlsPanel.add(new JLabel("Variabile:"), gbc);
		gbc.gridy++;
		controlsPanel.add(new JComboBox<>(new String[] { "Portata", "Stato", "Flusso" }), gbc);

		gbc.gridy++;
		controlsPanel.add(new JLabel("Aggregazione:"), gbc);
		gbc.gridy++;
		controlsPanel.add(new JComboBox<>(new String[] { "Oraria", "Giornaliera", "Mensile" }), gbc);

		JTextArea placeholder = new JTextArea();
		placeholder.setEditable(false);
		placeholder.setLineWrap(true);
		placeholder.setWrapStyleWord(true);
		placeholder.setText("Questa vista multi-panel per i grafici non è ancora implementata.\n"
				+ "Qui verranno mostrati i grafici selezionati dai menu a tendina.");

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsPanel, new JScrollPane(placeholder));
		splitPane.setResizeWeight(0.35);

		dialog.add(splitPane, BorderLayout.CENTER);
		dialog.setSize(new Dimension(760, 420));
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
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
			Layer subbasinLayer = new FeatureLayer(subbasinSource,
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

		Rule selectedRule = createPolygonRule(styleBuilder, DEFAULT_COLOR, geomName);
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
				.createFeatureTypeStyle(new Rule[] { lakeRule, streamGaugeRule, defaultRule, selectedRule });

		Style style = styleFactory.createStyle();
		style.featureTypeStyles().add(fts);
		return style;
	}

	private Symbolizer createHighlightedSymbolizer(StyleBuilder styleBuilder, String geomName) {
		Stroke thickStroke = styleBuilder.createStroke(HIGHLIGHT_STROKE_COLOR, 4.2f);
		Fill fill = styleBuilder.createFill(HIGHLIGHT_FILL_COLOR, 2f);
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
				}
			}
		} catch (IOException e) {
			infoArea.setText("Errore nella selezione: " + e.getMessage());
		}
	}

	private void updateInfo(SimpleFeature feature) {
		selectedFeatureId = feature.getID();
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

	private void refreshSubbasinStyle() {
		if (subbasinLayer == null || subbasinSource == null) {
			return;
		}
		subbasinLayer.setStyle(buildSubbasinStyle(subbasinSource.getSchema(), selectedFeatureId));
		mapPane.repaint();
	}

}
