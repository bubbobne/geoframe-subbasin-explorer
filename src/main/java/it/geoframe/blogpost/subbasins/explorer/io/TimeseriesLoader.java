package it.geoframe.blogpost.subbasins.explorer.io;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import it.geoframe.blogpost.subbasins.explorer.io.TimeseriesRepository.TableColumnDetail;

import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;

import it.geoframe.blogpost.subbasins.explorer.services.ExplorerConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;

public final class TimeseriesLoader {
	public record TimeValueRow(long timestamp, Map<String, Double> values) {
	}

	private final TimeseriesRepository repository;

	public TimeseriesLoader(TimeseriesRepository repository) {
		this.repository = repository;
	}

	public List<String> listAllTableNames(ProjectConfig config) {
		if (config == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		out.addAll(repository.listTables(config.geopackagePath()));
		out.addAll(repository.listTables(config.sqlitePath()));
		return out;
	}

	public int fillSeriesFromAnyInput(ProjectConfig config, String table, String basinId, TimeSeries series) {
		int count = fillSeriesFromDb(config.geopackagePath(), table, basinId, series);
		if (count > 0) {
			return count;
		}
		return fillSeriesFromDb(config.sqlitePath(), table, basinId, series);
	}

	public Set<String> listColumnNamesFromAnyInput(ProjectConfig config, String table) {
		if (config == null || table == null || table.isBlank()) {
			return Set.of();
		}
		Set<String> out = new LinkedHashSet<>();
		out.addAll(repository.listColumnNames(config.geopackagePath(), table));
		out.addAll(repository.listColumnNames(config.sqlitePath(), table));
		return out;
	}


	public List<TableColumnDetail> listTableDetailsFromAnyInput(ProjectConfig config, String table) {
		if (config == null || table == null || table.isBlank()) {
			return List.of();
		}
		List<TableColumnDetail> gpkgDetails = repository.listTableDetails(config.geopackagePath(), table);
		if (!gpkgDetails.isEmpty()) {
			return gpkgDetails;
		}
		return repository.listTableDetails(config.sqlitePath(), table);
	}

	public List<TimeValueRow> loadRowsFromAnyInput(ProjectConfig config, String table, String basinId,
			String... valueColumns) {
		List<TimeValueRow> rows = loadRowsFromDb(config.geopackagePath(), table, basinId, valueColumns);
		if (!rows.isEmpty()) {
			return rows;
		}
		return loadRowsFromDb(config.sqlitePath(), table, basinId, valueColumns);
	}

	private int fillSeriesFromDb(Path dbPath, String table, String basinId, TimeSeries series) {
		if (dbPath == null || table == null || basinId == null) {
			return 0;
		}

		Set<String> columns = repository.listColumnNames(dbPath, table);
		Optional<String> basinColumn = repository.findFirstColumnIgnoreCase(columns,
				ExplorerConfig.timeseriesBasinIdCandidates());
		Optional<String> tsColumn = repository.findFirstColumnIgnoreCase(columns,
				new String[] { ExplorerConfig.timeseriesTimestampColumn(), "timestamp", "date", "time" });
		Optional<String> valueColumn = repository.findFirstColumnIgnoreCase(columns,
				new String[] { ExplorerConfig.timeseriesValueColumn(), "simulated", "obs", "q" });
		if (basinColumn.isEmpty() || tsColumn.isEmpty() || valueColumn.isEmpty()) {
			return 0;
		}

		String safeTable = table.replace("\"", "\"\"");
		String sql = "SELECT \"" + tsColumn.get() + "\", \"" + valueColumn.get() + "\" FROM \"" + safeTable
				+ "\" WHERE \"" + basinColumn.get() + "\"=? ORDER BY \"" + tsColumn.get() + "\"";
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
				PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, basinId);
			int count = 0;
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					long ts = rs.getLong(1);
					double value = rs.getDouble(2);
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

	private List<TimeValueRow> loadRowsFromDb(Path dbPath, String table, String basinId, String... valueColumns) {
		if (dbPath == null || table == null || basinId == null || valueColumns == null || valueColumns.length == 0) {
			return List.of();
		}
		Set<String> columns = repository.listColumnNames(dbPath, table);
		Optional<String> basinColumn = repository.findFirstColumnIgnoreCase(columns,
				ExplorerConfig.timeseriesBasinIdCandidates());
		Optional<String> tsColumn = repository.findFirstColumnIgnoreCase(columns,
				new String[] { ExplorerConfig.timeseriesTimestampColumn(), "timestamp", "date", "time" });
		if (basinColumn.isEmpty() || tsColumn.isEmpty()) {
			return List.of();
		}
		Map<String, String> resolvedColumns = new LinkedHashMap<>();
		for (String col : valueColumns) {
			Optional<String> resolved = repository.findFirstColumnIgnoreCase(columns, new String[] { col });
			if (resolved.isEmpty()) {
				return List.of();
			}
			resolvedColumns.put(col, resolved.get());
		}

		StringBuilder select = new StringBuilder("SELECT \"").append(tsColumn.get()).append("\"");
		for (String actual : resolvedColumns.values()) {
			select.append(", \"").append(actual).append("\"");
		}
		String safeTable = table.replace("\"", "\"\"");
		String sql = select + " FROM \"" + safeTable + "\" WHERE \"" + basinColumn.get() + "\"=? ORDER BY \""
				+ tsColumn.get() + "\"";
		List<TimeValueRow> out = new ArrayList<>();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
				PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, basinId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Map<String, Double> vals = new LinkedHashMap<>();
					for (int i = 0; i < valueColumns.length; i++) {
						double v = rs.getDouble(i + 2);
						vals.put(valueColumns[i], rs.wasNull() ? Double.NaN : v);
					}
					out.add(new TimeValueRow(rs.getLong(1), vals));
				}
			}
		} catch (SQLException ex) {
			return List.of();
		}
		return out;
	}
}
