package it.geoframe.blogpost.subbasins.explorer.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Application configuration loaded from properties file.
 *
 * Lookup order:
 * 1) explicit path via -Dgeoframe.explorer.config=/path/to/explorer.properties
 * 2) ~/.geoframe-subbasins-explorer/explorer.properties
 * 3) classpath resource explorer.properties
 */
public final class ExplorerConfig {

	private static final String CONFIG_FILE = "explorer.properties";
	private static final String EXTERNAL_CONFIG_PROPERTY = "geoframe.explorer.config";
	private static final Path USER_CONFIG_PATH = Paths.get(System.getProperty("user.home"),
			".geoframe-subbasins-explorer", CONFIG_FILE);
	private static final Properties PROPS = loadProperties();

	private ExplorerConfig() {
	}

	public static String sqliteMeasurementTable() {
		return get("tables.sqlite.measurement", "measurement");
	}

	public static String geopackageBasinTable() {
		return get("tables.geopackage.basin", "basin");
	}

	public static String geopackageNetworkTable() {
		return get("tables.geopackage.network", "network");
	}

	public static String geopackageTopologyPrefix() {
		return get("tables.geopackage.topology.prefix", "topology");
	}

	public static String geopackageSimulationPrefix() {
		return get("tables.geopackage.simulation.prefix", "sim");
	}

	public static String timeseriesTimestampColumn() {
		return get("tables.timeseries.columns.timestamp", "ts");
	}

	public static String timeseriesValueColumn() {
		return get("tables.timeseries.columns.value", "value");
	}


	public static String[] stateAggregationOptions() {
		String configured = get("charts.state.aggregation.options", "1h,12h,24h,settimana,mese,anno");
		String[] parts = configured.split(",");
		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].trim();
		}
		return parts;
	}

	public static String stateAggregationDefault() {
		return get("charts.state.aggregation.default", "mese");
	}

	public static String[] timeseriesBasinIdCandidates() {
		String configured = get("tables.timeseries.columns.basin-id.candidates", "basin_id,basinid,id");
		return configured.split(",");
	}


	public static String chartOption(String key, String defaultValue) {
		return get(key, defaultValue);
	}

	private static String get(String key, String defaultValue) {
		String v = PROPS.getProperty(key);
		return (v == null || v.isBlank()) ? defaultValue : v.trim();
	}

	private static Properties loadProperties() {
		Properties p = new Properties();
		Path externalConfigPath = externalConfigPath();
		if (loadFromPath(p, externalConfigPath)) {
			return p;
		}

		if (loadFromPath(p, USER_CONFIG_PATH)) {
			return p;
		}

		loadFromClasspath(p);
		return p;
	}

	private static Path externalConfigPath() {
		String configuredPath = System.getProperty(EXTERNAL_CONFIG_PROPERTY);
		if (configuredPath == null || configuredPath.isBlank()) {
			return null;
		}
		return Paths.get(configuredPath.trim());
	}

	private static boolean loadFromPath(Properties p, Path path) {
		if (path == null || !Files.exists(path) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
			return false;
		}
		try (InputStream in = Files.newInputStream(path)) {
			p.load(in);
			return true;
		} catch (IOException ignored) {
			return false;
		}
	}

	private static void loadFromClasspath(Properties p) {
		try (InputStream in = ExplorerConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
			if (in != null) {
				p.load(in);
			}
		} catch (IOException ignored) {
			// Keep defaults if configuration file is unavailable.
		}
	}
}
