package it.geoframe.blogpost.subbasins.explorer.io;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository dedicated to time-series table discovery and extraction.
 * 
 * @author Daniele Andreis
 *
 */
public final class TimeseriesRepository {
	public record TableColumnDetail(int ordinalPosition, String name, String type, boolean notNull,
			String defaultValue, boolean primaryKey) {
	}

	public List<String> listTables(Path dbPath) {
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
		} catch (SQLException ignored) {
			return List.of();
		}
		return out;
	}

	public Set<String> listColumnNames(Path dbPath, String tableName) {
		if (dbPath == null || tableName == null || tableName.isBlank()) {
			return Set.of();
		}
		String safeTable = tableName.replace("\"", "\"\"");
		String sql = "SELECT * FROM \"" + safeTable + "\" LIMIT 1";
		Set<String> out = new LinkedHashSet<>();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
				PreparedStatement ps = c.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			ResultSetMetaData md = rs.getMetaData();
			for (int i = 1; i <= md.getColumnCount(); i++) {
				String columnName = md.getColumnName(i);
				if (columnName != null) {
					out.add(columnName);
				}
			}
		} catch (SQLException ignored) {
			return Set.of();
		}
		return out;
	}

	public Optional<String> findFirstColumnIgnoreCase(Set<String> columns, String[] candidates) {
		if (columns == null || columns.isEmpty() || candidates == null) {
			return Optional.empty();
		}
		for (String candidate : candidates) {
			if (candidate == null || candidate.isBlank()) {
				continue;
			}
			String trimmed = candidate.trim();
			for (String column : columns) {
				if (column.equalsIgnoreCase(trimmed)) {
					return Optional.of(column);
				}
			}
		}
		return Optional.empty();
	}

	public List<TableColumnDetail> listTableDetails(Path dbPath, String tableName) {
		if (dbPath == null || tableName == null || tableName.isBlank()) {
			return List.of();
		}
		String safeTable = tableName.replace("\"", "\"\"");
		String sql = "PRAGMA table_info(\"" + safeTable + "\")";
		List<TableColumnDetail> out = new ArrayList<>();
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
				PreparedStatement ps = c.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				out.add(new TableColumnDetail(rs.getInt("cid") + 1, rs.getString("name"), rs.getString("type"),
						rs.getInt("notnull") == 1, rs.getString("dflt_value"), rs.getInt("pk") == 1));
			}
		} catch (SQLException ignored) {
			return List.of();
		}
		return out;
	}
}
