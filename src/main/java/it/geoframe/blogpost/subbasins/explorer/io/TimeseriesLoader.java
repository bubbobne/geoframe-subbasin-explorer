package it.geoframe.blogpost.subbasins.explorer.io;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;

import it.geoframe.blogpost.subbasins.explorer.services.ExplorerConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;

public final class TimeseriesLoader {

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
}
