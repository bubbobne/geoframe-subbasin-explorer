package it.geoframe.blogpost.subbasins.explorer.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

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

	public int fillSeriesFromAnyInput(ProjectConfig config, String table, String basinId, TimeSeries series,
			boolean isGaugeSeries) {
		int count = fillSeriesFromDb(config.geopackagePath(), table, basinId, series, isGaugeSeries);
		if (count > 0) {
			return count;
		}
		return fillSeriesFromDb(config.sqlitePath(), table, basinId, series, isGaugeSeries);
	}

	public int fillSeriesFromLegacyFolder(ProjectConfig config, String basinId, String prefix, TimeSeries series) {
		if (config == null || config.legacyRootPath() == null || basinId == null || basinId.isBlank() || prefix == null
				|| prefix.isBlank()) {
			return 0;
		}
		Path csv = config.legacyRootPath().resolve(basinId).resolve(prefix.trim() + basinId + ".csv");
		if (!Files.exists(csv) || !Files.isReadable(csv)) {
			return 0;
		}
		return fillSeriesFromLegacyCsv(csv, series);
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

	private int fillSeriesFromDb(Path dbPath, String table, String basinId, TimeSeries series, boolean isGaugeSerie) {
		if (dbPath == null || table == null || (basinId == null && !isGaugeSerie)) {
			return 0;
		}

		Set<String> columns = repository.listColumnNames(dbPath, table);
		Optional<String> basinColumn = repository.findFirstColumnIgnoreCase(columns,
				ExplorerConfig.timeseriesBasinIdCandidates());
		Optional<String> tsColumn = repository.findFirstColumnIgnoreCase(columns,
				new String[] { ExplorerConfig.timeseriesTimestampColumn(), "ts", "timestamp", "date", "time" });
		Optional<String> valueColumn = repository.findFirstColumnIgnoreCase(columns,
				new String[] { ExplorerConfig.timeseriesValueColumn(), "value", "simulated", "obs", "q" });
		if ((!isGaugeSerie && basinColumn.isEmpty()) || tsColumn.isEmpty() || valueColumn.isEmpty()) {
			return 0;
		}

		String safeTable = table.replace("\"", "\"\"");
		String sql = "SELECT \"" + tsColumn.get() + "\", \"" + valueColumn.get() + "\" FROM \"" + safeTable + "\"";
		if (!isGaugeSerie) {
			sql = sql + " WHERE \"" + basinColumn.get() + "\"=?";
		}
		sql = sql + " ORDER BY \"" + tsColumn.get() + "\"";
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
				PreparedStatement ps = c.prepareStatement(sql)) {
			if (!isGaugeSerie) {
				ps.setString(1, basinId);
			}
			int count = 0;
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					long ts = rs.getLong(1);
					double value = rs.getDouble(2);
					if (value == -9999.0) {
						value = Double.NaN;
					}
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

	private int fillSeriesFromLegacyCsv(Path csvPath, TimeSeries series) {
		try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
			LegacyCsvHeader header = LegacyCsvHeader.read(reader);
			if (header == null) {
				return 0;
			}
			int count = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("@")) {
					continue;
				}
				String[] parts = splitLine(line, header.delimiter());
				if (parts.length <= Math.max(header.timestampIndex(), header.valueIndex())) {
					continue;
				}
				String tsText = parts[header.timestampIndex()].trim();
				String valueText = parts[header.valueIndex()].trim();
				if (tsText.isEmpty() || valueText.isEmpty()) {
					continue;
				}
				Long ts = parseTimestamp(tsText, header.dateFormat());
				if (ts == null) {
					continue;
				}
				double value;
				try {
					value = Double.parseDouble(valueText);
				} catch (NumberFormatException ex) {
					continue;
				}
				series.addOrUpdate(new Millisecond(new Date(ts)), value);
				count++;
			}
			return count;
		} catch (IOException e) {
			return 0;
		}
	}

	private static Long parseTimestamp(String value, String format) {
		List<String> formats = new ArrayList<>();
		if (format != null && !format.isBlank()) {
			formats.add(format.trim());
		}
		formats.add("yyyy-MM-dd HH:mm:ss");
		formats.add("yyyy-MM-dd HH:mm");
		formats.add("yyyy-MM-dd");
		for (String f : formats) {
			SimpleDateFormat sdf = new SimpleDateFormat(f, Locale.ROOT);
			sdf.setLenient(false);
			sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			try {
				return sdf.parse(value).getTime();
			} catch (ParseException ignored) {
			}
		}
		return null;
	}

	private static String[] splitLine(String line, char delimiter) {
		return line.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1);
	}

	private record LegacyCsvHeader(char delimiter, int timestampIndex, int valueIndex, String dateFormat) {
		static LegacyCsvHeader read(BufferedReader reader) throws IOException {
			String line;
			String[] header = null;
			String format = null;
			char delimiter = ',';
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				if (trimmed.startsWith("@H")) {
					delimiter = sniffDelimiter(trimmed);
					String[] cols = splitLine(trimmed, delimiter);
					header = new String[Math.max(0, cols.length - 1)];
					for (int i = 1; i < cols.length; i++) {
						header[i - 1] = cols[i].trim();
					}
					continue;
				}
				if (trimmed.regionMatches(true, 0, "Format", 0, 6)) {
					delimiter = sniffDelimiter(trimmed);
					String[] parts = splitLine(trimmed, delimiter);
					if (parts.length > 1) {
						format = parts[1].trim();
					}
					continue;
				}
				if (!trimmed.startsWith("@")) {
					delimiter = sniffDelimiter(trimmed);
					header = splitLine(trimmed, delimiter);
					for (int i = 0; i < header.length; i++) {
						header[i] = header[i].trim();
					}
					break;
				}
			}
			if (header == null || header.length == 0) {
				return null;
			}
			int tsIdx = indexOfIgnoreCase(header, "timestamp", "ts", "date", "time");
			int valueIdx = indexOfIgnoreCase(header, "value_1", "value", "q", "simulated", "obs");
			if (tsIdx < 0 || valueIdx < 0) {
				return null;
			}
			return new LegacyCsvHeader(delimiter, tsIdx, valueIdx, format);
		}

		private static int indexOfIgnoreCase(String[] values, String... candidates) {
			for (int i = 0; i < values.length; i++) {
				for (String c : candidates) {
					if (values[i] != null && values[i].equalsIgnoreCase(c)) {
						return i;
					}
				}
			}
			return -1;
		}

		private static char sniffDelimiter(String header) {
			long commas = header.chars().filter(ch -> ch == ',').count();
			long semicolons = header.chars().filter(ch -> ch == ';').count();
			return semicolons > commas ? ';' : ',';
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
