package it.geoframe.blogpost.subbasins.explorer.ui;


import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfigStore;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.style.FeatureTypeStyle;
import org.geotools.api.style.Fill;
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
import org.geotools.swing.event.MapMouseAdapter;
import org.geotools.swing.event.MapMouseEvent;
import org.locationtech.jts.geom.Envelope;
import org.geotools.api.data.Query;


import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
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
 *
 *@author Daniele Andreis
 */
public final class SubbasinExplorerPanel extends JPanel {
    private static final Color STREAM_GAUGE_COLOR = new Color(76, 120, 168);
    private static final Color LAKE_COLOR = new Color(84, 180, 112);
    private static final Color DEFAULT_COLOR = new Color(201, 203, 208);
    private static final Color NETWORK_COLOR = new Color(52, 93, 166);

    private final ProjectConfig config;
    private final JTextArea infoArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");
    private final FilterFactory ff = CommonFactoryFinder.getFilterFactory();

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
        gbc.insets = new Insets(0, 0, 6, 0);

        JLabel title = new JLabel("Subbasin info");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        header.add(title, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        header.add(statusLabel, gbc);

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
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        return scrollPane;
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
            mapContent.setTitle("Subbasins");
            mapContent.addLayer(new FeatureLayer(subbasinSource, buildSubbasinStyle(subbasinSource.getSchema())));
            networkSource = loadNetworkSource();
            if (networkSource != null) {
                mapContent.addLayer(new FeatureLayer(networkSource, buildNetworkStyle(networkSource.getSchema())));
            }

            mapPane.setMapContent(mapContent);
            mapPane.reset();
            mapPane.addMouseListener(new MapMouseAdapter() {
                @Override
                public void onMouseClicked(MapMouseEvent ev) {
                    handleMapClick(ev);
                }
            });
            statusLabel.setText("Clicca su un sottobacino per vedere le informazioni.");
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

        ProjectConfigStore.load().ifPresent(cfg -> {
  
    				  params.put("database", cfg.legacyRootPath().toString());
    		});
          
        
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
        ProjectConfigStore.load().ifPresent(cfg -> {
		
				  params.put("database", cfg.geopackagePath().toString());
		});
      

        dataStore = DataStoreFinder.getDataStore(params);
        if (dataStore == null) {
            return Optional.empty();
        }

        String[] typeNames = dataStore.getTypeNames();
        String target = Arrays.stream(typeNames)
                .filter(name -> name.equalsIgnoreCase("basin"))
                .findFirst()
                .orElseGet(() -> Arrays.stream(typeNames)
                        .filter(name -> name.toLowerCase(Locale.ROOT).contains("basin"))
                        .findFirst()
                        .orElse(null));

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
        String target = Arrays.stream(typeNames)
                .filter(name -> name.equalsIgnoreCase("network"))
                .findFirst()
                .orElseGet(() -> Arrays.stream(typeNames)
                        .filter(name -> name.toLowerCase(Locale.ROOT).contains("network"))
                        .findFirst()
                        .orElse(null));
        if (target == null) {
            return null;
        }
        return dataStore.getFeatureSource(target);
    }

    private Style buildSubbasinStyle(SimpleFeatureType schema) {
        StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory();
        StyleBuilder styleBuilder = new StyleBuilder();

        String geomName = schema.getGeometryDescriptor().getLocalName();
        Expression geom = ff.property(geomName);

        Rule streamGaugeRule = createRule(styleBuilder, STREAM_GAUGE_COLOR, geom);
        streamGaugeRule.setFilter(buildStreamGaugeFilter(schema));

        Rule lakeRule = createRule(styleBuilder, LAKE_COLOR, geom);
        lakeRule.setFilter(buildLakeFilter(schema));

        Rule defaultRule = createRule(styleBuilder, DEFAULT_COLOR, geom);
        defaultRule.setElseFilter(true);

        FeatureTypeStyle fts = styleFactory.createFeatureTypeStyle(
                new Rule[]{streamGaugeRule, lakeRule, defaultRule}
        );

        Style style = (Style) styleFactory.createStyle();
        ((org.geotools.api.style.Style) style).featureTypeStyles().add(fts);
        return style;
    }

    private Style buildNetworkStyle(SimpleFeatureType schema) {
        StyleBuilder styleBuilder = new StyleBuilder();
        String geomName = schema.getGeometryDescriptor().getLocalName();
        Expression geom = ff.property(geomName);
        Stroke stroke = styleBuilder.createStroke(NETWORK_COLOR, 1.4f);
        Rule rule = styleBuilder.createRule(styleBuilder.createLineSymbolizer(stroke));
        Style style = styleBuilder.createStyle();
        FeatureTypeStyle ft = styleBuilder.createFeatureTypeStyle(geomName, rule);
        style.featureTypeStyles().add(ft);
        return style;
    }

    private Rule createRule(StyleBuilder styleBuilder, Color fillColor, Expression geom) {
        Stroke stroke = styleBuilder.createStroke(new Color(75, 75, 75), 0.6f);
        Fill fill = styleBuilder.createFill(fillColor, 0.65f);
        Rule rule = styleBuilder.createRule(styleBuilder.createPolygonSymbolizer(stroke, fill));
        return rule;
    }

    private Filter buildStreamGaugeFilter(SimpleFeatureType schema) {
        String field = resolveStreamGaugeField(schema);
        if (field == null) {
            return Filter.EXCLUDE;
        }
        Expression prop = ff.property(field);
        //Filter notNull = ff.isNotNull(prop);
        Filter notEmpty = ff.notEqual(prop, ff.literal(""), false);
        return  notEmpty;
    }

    private Filter buildLakeFilter(SimpleFeatureType schema) {
        if (schema.getDescriptor("isLake") == null) {
            return Filter.EXCLUDE;
        }
        Expression prop = ff.property("isLake");
        Filter isTrue = ff.equal(prop, ff.literal(true), false);
        Filter isOne = ff.equal(prop, ff.literal(1), false);
        Filter isTrueString = ff.equal(prop, ff.literal("true"), false);
        return ff.or(ff.or(isTrue, isOne), isTrueString);
    }

    private String resolveStreamGaugeField(SimpleFeatureType schema) {
        if (schema.getDescriptor("streamGauge") != null) {
            return "streamGauge";
        }
        if (schema.getDescriptor("isStreamGauge") != null) {
            return "isStreamGauge";
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
        Envelope env = new Envelope(x - toleranceX, x + toleranceX, y - toleranceY, y + toleranceY);
        double minx = x - toleranceX;
        double maxx = x + toleranceX;
        double miny = y - toleranceY;
        double maxy = y + toleranceY;
        Filter filter = ff.bbox(ff.property(subbasinSource.getSchema().getGeometryDescriptor().getLocalName()), minx, miny, maxx, maxy, null);

        try {
            SimpleFeatureCollection collection = subbasinSource.getFeatures(new Query(subbasinSource.getName().getLocalPart(), filter));
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
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(feature.getID()).append("\n\n");
        for (int i = 0; i < feature.getAttributeCount(); i++) {
            String name = feature.getFeatureType().getDescriptor(i).getLocalName();
            Object value = feature.getAttribute(i);
            sb.append(name).append(": ").append(value == null ? "" : value).append("\n");
        }
        infoArea.setText(sb.toString());
        infoArea.setCaretPosition(0);
    }
}
