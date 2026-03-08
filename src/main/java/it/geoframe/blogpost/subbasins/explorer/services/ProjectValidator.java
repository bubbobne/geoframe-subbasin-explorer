package it.geoframe.blogpost.subbasins.explorer.services;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import java.io.BufferedReader;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
/**
 * @author Daniele Andreis
 */
public final class ProjectValidator {

	private ProjectValidator() {
	}

	public static ValidationResult validate(ProjectConfig cfg) {
		List<String> info = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		// 1) config + file existence
		if (cfg == null) {
			errors.add("Config is null.");
			return new ValidationResult(false, info, errors, warnings);
		}
		if (cfg.mode() == ProjectMode.GEOPACKAGE) {
			checkFileExists(cfg.geopackagePath(), "GeoPackage", info, errors);
			checkFileExists(cfg.sqlitePath(), "SQLite", info, errors);
			if (!errors.isEmpty())
				return new ValidationResult(false, info, errors, warnings);

			// 2) SQLite checks
			validateSqlite(cfg.sqlitePath(), info, errors, warnings);

			// 3) GeoPackage checks via JDBC (most deterministic)
			validateGeoPackageSqliteSide(cfg.geopackagePath(), info, errors, warnings);

			// 4) GeoTools open (sanity check dependencies)
			validateGeoToolsOpen(cfg.geopackagePath(), info, errors, warnings);
		} else {
			validateLegacyFolder(cfg, info, errors, warnings);
		}
		boolean ok = errors.isEmpty();
		if (ok) {
			info.add("✅ Project validation OK. You can continue.");
		} else {
			info.add("❌ Project validation FAILED. Fix errors before continuing.");
		}

		return new ValidationResult(ok, info, errors, warnings);
	}

	private static void checkFileExists(Path p, String label, List<String> info, List<String> errors) {
		if (p == null) {
			errors.add(label + " path is null.");
			return;
		}
		if (!Files.exists(p)) {
			errors.add(label + " file does not exist: " + p);
			return;
		}
		if (!Files.isRegularFile(p)) {
			errors.add(label + " is not a file: " + p);
			return;
		}
		if (!Files.isReadable(p)) {
			errors.add(label + " is not readable: " + p);
			return;
		}
		info.add("✅ " + label + " file found: " + p.getFileName());
	}

	private static void validateSqlite(Path sqlitePath, List<String> info, List<String> errors, List<String> warnings) {
		info.add("— Checking SQLite input…");
		String measurementTable = ExplorerConfig.sqliteMeasurementTable();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath)) {
			info.add("✅ SQLite opened successfully.");

			if (!tableExists(c, measurementTable)) {
				errors.add("SQLite: missing table '" + measurementTable + "'.");
				return;
			}
			info.add("✅ SQLite table found: " + measurementTable);

			Set<String> cols = tableColumns(c, measurementTable);
			requireColumns("SQLite.measurements", cols, Set.of("ts", "basin_id", "value"), info, errors);

			// optional sanity warnings
			if (!cols.contains("timestep"))
				warnings.add("SQLite.measurements: 'timestep' column missing? (should be required)");
		} catch (SQLException e) {
			errors.add("SQLite: cannot open/read DB: " + e.getMessage());
		}
	}

	private static void validateGeoPackageSqliteSide(Path geopkgPath, List<String> info, List<String> errors,
			List<String> warnings) {
		info.add("— Checking GeoPackage content (SQLite side)…");
		String basinTable = ExplorerConfig.geopackageBasinTable();
		String networkTable = ExplorerConfig.geopackageNetworkTable();
		String topologyPrefix = ExplorerConfig.geopackageTopologyPrefix();
		String simulationPrefix = ExplorerConfig.geopackageSimulationPrefix();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + geopkgPath)) {
			info.add("✅ GeoPackage opened as SQLite successfully.");

			// Required layers/tables (as per your spec)
			requireTable(c, basinTable, "GeoPackage", info, errors);
			requireTable(c, networkTable, "GeoPackage", info, errors);

			boolean hasTopology = tableExists(c, topologyPrefix);
			if (hasTopology || anyTableStartsWith(c, topologyPrefix)) {
				info.add("✅ GeoPackage table found: " + topologyPrefix);
			} else {
				errors.add("GeoPackage: missing table with prefix '" + topologyPrefix + "'.");
			}

			// at least one table containing simulation+discharge (eg
			// sim*_simulation_discharge)
			List<String> sims = listSimulationDischargeTables(c, simulationPrefix, 10);
			boolean hasSimulation = !sims.isEmpty();
			if (hasSimulation) {
				info.add("✅ GeoPackage simulation tables detected: " + sims + (sims.size() == 10 ? " …" : ""));
			} else {
				errors.add("GeoPackage: missing at least one table containing '" + simulationPrefix
						+ "' and 'discharge'.");
			}

			// Optional: basic gpkg sanity
			if (!tableExists(c, "gpkg_contents")) {
				warnings.add("GeoPackage: 'gpkg_contents' not found. File may not be a valid GeoPackage.");
			} else {
				info.add("✅ GeoPackage core table found: gpkg_contents");
			}

		} catch (SQLException e) {
			errors.add("GeoPackage: cannot open/read file: " + e.getMessage());
		}
	}

	private static void validateGeoToolsOpen(Path geopkgPath, List<String> info, List<String> errors,
			List<String> warnings) {
		info.add("— Checking GeoTools DataStore open…");
		Map<String, Object> params = new HashMap<>();
		params.put("dbtype", "geopkg");
		params.put("database", geopkgPath.toFile());

		DataStore ds = null;
		try {
			ds = DataStoreFinder.getDataStore(params);
			if (ds == null) {
				errors.add("GeoTools: DataStoreFinder returned null for GeoPackage. Check GeoTools dependencies.");
				return;
			}
			info.add("✅ GeoTools opened GeoPackage via DataStore.");

			String[] typeNames = ds.getTypeNames();
			if (typeNames == null || typeNames.length == 0) {
				warnings.add("GeoTools: opened GeoPackage but found no feature types.");
			} else {
				info.add("✅ GeoTools feature types: " + Arrays.toString(typeNames));
			}
		} catch (Exception e) {
			errors.add("GeoTools: cannot open GeoPackage: " + e.getMessage());
		} finally {
			if (ds != null) {
				try {
					ds.dispose();
				} catch (Exception ignored) {
				}
			}
		}
	}

	// --- helpers ---
	private static void requireTable(Connection c, String name, String label, List<String> info, List<String> errors)
			throws SQLException {
		if (!tableExists(c, name)) {
			errors.add(label + ": missing layer/table '" + name + "'.");
		} else {
			info.add("✅ " + label + " table found: " + name);
		}
	}

	private static boolean tableExists(Connection c, String tableName) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"SELECT name FROM sqlite_master WHERE type IN ('table','view') AND lower(name)=lower(?)")) {
			ps.setString(1, tableName);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static boolean anyTableStartsWith(Connection c, String prefix) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"SELECT name FROM sqlite_master WHERE type IN ('table','view') AND lower(name) LIKE lower(?)")) {
			ps.setString(1, prefix + "%");
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}


	public static List<String> listSimulationDischargeTables(Path sqliteDbPath, String simulationPrefix, int limit)
			throws SQLException {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
			return listSimulationDischargeTables(c, simulationPrefix, limit);
		}
	}

	public static List<String> listSimulationTables(Path sqliteDbPath, String simulationPrefix, int limit)
			throws SQLException {
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
			List<String> out = new ArrayList<>();
			try (PreparedStatement ps = c.prepareStatement(
					"SELECT name FROM sqlite_master WHERE type IN ('table','view') AND lower(name) LIKE lower(?) ORDER BY name LIMIT ?")) {
				ps.setString(1, simulationPrefix + "%");
				ps.setInt(2, limit);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						out.add(rs.getString(1));
					}
				}
			}
			return out;
		}
	}

	private static List<String> listSimulationDischargeTables(Connection c, String simulationPrefix, int limit)
			throws SQLException {
		List<String> out = new ArrayList<>();
		try (PreparedStatement ps = c.prepareStatement(
				"SELECT name FROM sqlite_master " + "WHERE type IN ('table','view') " + "AND lower(name) LIKE lower(?) "
						+ "AND instr(lower(name), 'discharge') > 0 " + "ORDER BY name LIMIT ?")) {
			ps.setString(1, simulationPrefix + "%");
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(rs.getString(1));
				}
			}
		}
		return out;
	}

	private static Set<String> tableColumns(Connection c, String tableName) throws SQLException {
		Set<String> cols = new HashSet<>();
		try (Statement st = c.createStatement();
				ResultSet rs = st.executeQuery("PRAGMA table_info('" + tableName.replace("'", "''") + "')")) {
			while (rs.next()) {
				String name = rs.getString("name");
				if (name != null)
					cols.add(name.toLowerCase(Locale.ROOT));
			}
		}
		return cols;
	}

	private static void requireColumns(String label, Set<String> actual, Set<String> required, List<String> info,
			List<String> errors) {
		boolean ok = true;
		for (String r : required) {
			if (!actual.contains(r.toLowerCase(Locale.ROOT))) {
				errors.add(label + ": missing column '" + r + "'. Found: " + actual);
				ok = false;
			}
		}
		if (ok)
			info.add("✅ " + label + " columns OK: " + required);
	}

	public record ValidationResult(boolean ok, List<String> info, List<String> errors, List<String> warnings) {
	}

	private static void checkDirectoryExists(Path p, String label, List<String> info, List<String> errors) {
		if (p == null) {
			errors.add(label + " path is null.");
			return;
		}
		if (!Files.exists(p)) {
			errors.add(label + " folder does not exist: " + p);
			return;
		}
		if (!Files.isDirectory(p)) {
			errors.add(label + " is not a folder: " + p);
			return;
		}
		if (!Files.isReadable(p)) {
			errors.add(label + " is not readable: " + p);
			return;
		}
		info.add("✅ " + label + " folder found: " + p.getFileName());
	}

	private static void validateLegacyFolder(ProjectConfig cfg, List<String> info, List<String> errors,
			List<String> warnings) {
		info.add("— Checking legacy folder input…");
		checkDirectoryExists(cfg.legacyRootPath(), "Legacy root", info, errors);
		if (!errors.isEmpty())
			return;

		Path root = cfg.legacyRootPath();
		String subbasinsShpName = defaultIfBlank(cfg.legacySubbasinsShpName(),
				ExplorerConfig.legacySubbasinsShapefile());
		String networkShpName = defaultIfBlank(cfg.legacyNetworkShpName(), ExplorerConfig.legacyNetworkShapefile());
		String subbasinsCsvName = defaultIfBlank(cfg.legacySubbasinsCsvName(), ExplorerConfig.legacySubbasinsCsv());
		String topologyCsvName = defaultIfBlank(cfg.legacyTopologyCsvName(), ExplorerConfig.legacyTopologyCsv());

		Path subbasinShp = root.resolve(subbasinsShpName);
		Path networkShp = root.resolve(networkShpName);
		Path subbasinsCsv = root.resolve(subbasinsCsvName);
		Path topologyCsv = root.resolve(topologyCsvName);

		checkFileExists(subbasinShp, "Legacy subbasin shapefile", info, errors);
		checkFileExists(networkShp, "Legacy network shapefile", info, errors);
		checkFileExists(subbasinsCsv, "Legacy subbasins CSV", info, errors);
		if (Files.exists(topologyCsv)) {
			checkFileExists(topologyCsv, "Legacy topology CSV", info, errors);
		} else {
			warnings.add("Legacy: topology CSV not found (optional): " + topologyCsvName);
		}

		if (!errors.isEmpty())
			return;

		if (cfg.legacyShpIdField() == null || cfg.legacyShpIdField().isBlank()) {
			errors.add("Legacy: missing subbasin ID field name for shapefile.");
		} else {
			info.add("✅ Legacy shapefile ID field set: " + cfg.legacyShpIdField());
			Path dbf = toDbfPath(subbasinShp);
			if (!Files.exists(dbf)) {
				warnings.add("Legacy: '" + dbf.getFileName() + "' not found, cannot verify shapefile fields.");
			}
		}

		if (cfg.legacyCsvIdColumn() == null || cfg.legacyCsvIdColumn().isBlank()) {
			errors.add("Legacy: missing subbasin ID column name for CSV.");
		} else {
			validateCsvHasColumn(subbasinsCsv, cfg.legacyCsvIdColumn(), info, errors, warnings);
		}

		List<Path> subfolders = listSubbasinFolders(root);
		if (subfolders.isEmpty()) {
			warnings.add("Legacy: no subbasin folders found inside root.");
		} else {
			info.add("✅ Legacy subbasin folders found: " + subfolders.size());
			List<String> prefixes = cfg.legacyTimeseriesPrefixes() == null || cfg.legacyTimeseriesPrefixes().isEmpty()
					? List.of(ExplorerConfig.legacyTimeseriesPrefixes())
					: cfg.legacyTimeseriesPrefixes();
			validateLegacyTimeseriesFiles(subfolders, prefixes, info, warnings);
		}
	}

	private static void validateCsvHasColumn(Path csvPath, String columnName, List<String> info, List<String> errors,
			List<String> warnings) {
		info.add("— Checking legacy CSV columns…");
		try {
			List<String> cols = readCsvHeaderColumns(csvPath);
			if (cols.isEmpty()) {
				errors.add("Legacy CSV: header row is empty.");
				return;
			}
			boolean found = cols.stream().map(String::trim).anyMatch(c -> c.equalsIgnoreCase(columnName.trim()));
			if (found) {
				info.add("✅ Legacy CSV contains column: " + columnName);
			} else {
				errors.add("Legacy CSV: missing column '" + columnName + "'. Found: " + cols);
			}
		} catch (IOException e) {
			warnings.add("Legacy CSV: cannot read header row: " + e.getMessage());
		}
	}

	private static char sniffDelimiter(String header) {
		long commas = header.chars().filter(ch -> ch == ',').count();
		long semicolons = header.chars().filter(ch -> ch == ';').count();
		return semicolons > commas ? ';' : ',';
	}

	private static List<Path> listSubbasinFolders(Path root) {
		try (var stream = Files.list(root)) {
			return stream.filter(Files::isDirectory).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static void validateLegacyTimeseriesFiles(List<Path> subfolders, List<String> prefixes, List<String> info,
			List<String> warnings) {
		int checked = 0;
		int matched = 0;
		int parseable = 0;
		for (Path folder : subfolders) {
			String id = folder.getFileName().toString();
			if (id.isBlank()) {
				continue;
			}
			checked++;
			boolean hasMatch = false;
			for (String prefix : prefixes) {
				if (prefix == null || prefix.isBlank()) {
					continue;
				}
				if (Files.exists(folder.resolve(prefix.trim() + id + ".csv"))) {
					hasMatch = true;
					break;
				}
			}
			if (hasMatch) {
				matched++;
				if (hasParseableLegacyTimeseries(folder, id, prefixes)) {
					parseable++;
				}
			}
		}
		if (checked == 0) {
			return;
		}
		if (matched == 0) {
			warnings.add("Legacy: no timeseries file matched '<prefix><subbasinId>.csv' in subbasin folders.");
		} else {
			info.add("✅ Legacy timeseries naming check passed for " + matched + "/" + checked + " folders.");
			if (parseable == 0) {
				warnings.add(
						"Legacy: matched timeseries files found, but none has parseable header columns (expected @H,timestamp,value_1 or standard CSV header).");
			} else {
				info.add("✅ Legacy timeseries CSV header check passed for " + parseable + " folder(s).");
			}
		}
	}

	private static boolean hasParseableLegacyTimeseries(Path folder, String id, List<String> prefixes) {
		for (String prefix : prefixes) {
			if (prefix == null || prefix.isBlank()) {
				continue;
			}
			Path candidate = folder.resolve(prefix.trim() + id + ".csv");
			if (!Files.exists(candidate)) {
				continue;
			}
			try {
				List<String> cols = readOMSCsvHeaderColumns(candidate);
				if (!cols.isEmpty()) {
					return true;
				}
			} catch (IOException ignored) {
				// continue with next candidate
			}
		}
		return false;
	}

	private static List<String> readOMSCsvHeaderColumns(Path csvPath) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				if (trimmed.startsWith("@H")) {
					char delimiter = sniffDelimiter(trimmed);
					String[] cols = trimmed.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)));
					List<String> out = new ArrayList<>();
					for (int i = 1; i < cols.length; i++) {
						String col = cols[i].trim();
						if (!col.isEmpty()) {
							out.add(col);
						}
					}
					return out;
				}
				if (!trimmed.startsWith("@") && !trimmed.regionMatches(true, 0, "Created", 0, 7)
						&& !trimmed.regionMatches(true, 0, "Author", 0, 6)
						&& !trimmed.regionMatches(true, 0, "ID", 0, 2) && !trimmed.regionMatches(true, 0, "Type", 0, 4)
						&& !trimmed.regionMatches(true, 0, "Format", 0, 6)) {
					char delimiter = sniffDelimiter(trimmed);
					return Arrays.stream(trimmed.split(java.util.regex.Pattern.quote(String.valueOf(delimiter))))
							.map(String::trim).filter(s -> !s.isEmpty()).toList();
				}
			}
		}
		return List.of();
	}

	private static List<String> readCsvHeaderColumns(Path csvPath) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
			String line;
			String delimiter = ";";
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}

				return Arrays.stream(trimmed.split(java.util.regex.Pattern.quote(String.valueOf(delimiter))))
						.map(String::trim).filter(s -> !s.isEmpty()).toList();
			}
		}

		return List.of();
	}

	private static Path toDbfPath(Path shapefile) {
		String filename = shapefile.getFileName().toString();
		int dot = filename.lastIndexOf('.');
		String base = dot > 0 ? filename.substring(0, dot) : filename;
		return shapefile.getParent().resolve(base + ".dbf");
	}

	private static String defaultIfBlank(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

}
