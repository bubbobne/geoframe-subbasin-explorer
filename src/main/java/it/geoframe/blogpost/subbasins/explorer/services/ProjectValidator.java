package it.geoframe.blogpost.subbasins.explorer.services;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import java.io.BufferedReader;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

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
		}else {
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
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath)) {
			info.add("✅ SQLite opened successfully.");

			if (!tableExists(c, "measurement")) {
				errors.add("SQLite: missing table 'measurement'.");
				return;
			}
			info.add("✅ SQLite table found: measurement");

			Set<String> cols = tableColumns(c, "measurement");
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
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + geopkgPath)) {
			info.add("✅ GeoPackage opened as SQLite successfully.");

			// Required layers/tables (as per your spec)
			requireTable(c, "basin", "GeoPackage", info, errors);
			requireTable(c, "network", "GeoPackage", info, errors);

			// "topologi" vs "topology": you mentioned "topologi" earlier; handle both
			boolean hasTopology = tableExists(c, "topology");
			if (hasTopology || anyTableStartsWith(c, "topology")) {
				info.add("✅ GeoPackage table found: " + (hasTopology ? "topology" : "topologi"));
			} else {
				errors.add("GeoPackage: missing table 'topology' (or 'topologi').");
			}

			// at least one table starting with "simulation"
			boolean hasSimulation = anyTableStartsWith(c, "sim");
			if (hasSimulation) {
				List<String> sims = listTablesStartingWith(c, "sim", 10);
				info.add("✅ GeoPackage simulation tables detected: " + sims + (sims.size() == 10 ? " …" : ""));
			} else {
				errors.add("GeoPackage: missing at least one table starting with 'simulation*'.");
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

	private static List<String> listTablesStartingWith(Connection c, String prefix, int limit) throws SQLException {
		List<String> out = new ArrayList<>();
		try (PreparedStatement ps = c.prepareStatement(
				"SELECT name FROM sqlite_master WHERE type IN ('table','view') AND lower(name) LIKE lower(?) ORDER BY name LIMIT ?")) {
			ps.setString(1, prefix + "%");
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next())
					out.add(rs.getString(1));
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

	    private static void validateLegacyFolder(ProjectConfig cfg, List<String> info, List<String> errors, List<String> warnings) {
	        info.add("— Checking legacy folder input…");
	        checkDirectoryExists(cfg.legacyRootPath(), "Legacy root", info, errors);
	        if (!errors.isEmpty()) return;

	        Path root = cfg.legacyRootPath();
	        Path subbasinShp = root.resolve("subbasin_complete.shp");
	        Path networkShp = root.resolve("network_complete.shp");
	        Path networkAlt = root.resolve("network_compete.shp");
	        Path subbasinsCsv = root.resolve("subbasins.csv");

	        checkFileExists(subbasinShp, "Legacy subbasin shapefile", info, errors);
	        if (!Files.exists(networkShp) && Files.exists(networkAlt)) {
	            networkShp = networkAlt;
	            warnings.add("Legacy network shapefile name 'network_compete.shp' detected (expected 'network_complete.shp').");
	        }
	        checkFileExists(networkShp, "Legacy network shapefile", info, errors);
	        checkFileExists(subbasinsCsv, "Legacy subbasins CSV", info, errors);

	        if (!errors.isEmpty()) return;

	        if (cfg.legacyShpIdField() == null || cfg.legacyShpIdField().isBlank()) {
	            errors.add("Legacy: missing subbasin ID field name for shapefile.");
	        } else {
	            info.add("✅ Legacy shapefile ID field set: " + cfg.legacyShpIdField());
	            Path dbf = root.resolve("subbasin_complete.dbf");
	            if (!Files.exists(dbf)) {
	                warnings.add("Legacy: 'subbasin_complete.dbf' not found, cannot verify shapefile fields.");
	            }
	        }

	        if (cfg.legacyCsvIdColumn() == null || cfg.legacyCsvIdColumn().isBlank()) {
	            errors.add("Legacy: missing subbasin ID column name for CSV.");
	        } else {
	            validateCsvHasColumn(subbasinsCsv, cfg.legacyCsvIdColumn(), info, errors, warnings);
	        }

	        boolean hasSubfolders = hasSubbasinFolders(root);
	        if (!hasSubfolders) {
	            warnings.add("Legacy: no subbasin folders found inside root.");
	        }
	    }

	    private static void validateCsvHasColumn(Path csvPath, String columnName, List<String> info,
	                                             List<String> errors, List<String> warnings) {
	        info.add("— Checking legacy CSV columns…");
	        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
	            String header = reader.readLine();
	            if (header == null || header.isBlank()) {
	                errors.add("Legacy CSV: header row is empty.");
	                return;
	            }
	            char delimiter = sniffDelimiter(header);
	            String[] cols = header.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)));
	            boolean found = Arrays.stream(cols)
	                    .map(String::trim)
	                    .anyMatch(c -> c.equalsIgnoreCase(columnName.trim()));
	            if (found) {
	                info.add("✅ Legacy CSV contains column: " + columnName);
	            } else {
	                errors.add("Legacy CSV: missing column '" + columnName + "'. Found: " + Arrays.toString(cols));
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

	    private static boolean hasSubbasinFolders(Path root) {
	        try {
	            return Files.list(root).anyMatch(Files::isDirectory);
	        } catch (IOException e) {
	            return false;
	        }
	    }

	
	
	
}
