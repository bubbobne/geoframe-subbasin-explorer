package it.geoframe.blogpost.subbasins.explorer.services;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
/**
 * @author Daniele Andreis
 */
public final class ProjectConfigStore {
	private static final Preferences P = Preferences.userRoot().node("it/geoframe/subbasins-explorer");

	private ProjectConfigStore() {
	}

	public static void save(ProjectConfig cfg) {
		P.put("mode", cfg.mode().name());
		if (cfg.mode() == ProjectMode.GEOPACKAGE) {
			P.put("geopackagePath", cfg.geopackagePath().toString());
			P.put("sqlitePath", cfg.sqlitePath().toString());
			P.remove("legacyRootPath");
			P.remove("legacyShpIdField");
			P.remove("legacyCsvIdColumn");
			P.remove("legacySubbasinsShpName");
			P.remove("legacyNetworkShpName");
			P.remove("legacySubbasinsCsvName");
			P.remove("legacyTopologyCsvName");
			P.remove("legacyTimeseriesPrefixes");
		} else {
			P.put("legacyRootPath", cfg.legacyRootPath().toString());
			P.put("legacyShpIdField", cfg.legacyShpIdField());
			P.put("legacyCsvIdColumn", cfg.legacyCsvIdColumn());
			P.put("legacySubbasinsShpName", orDefault(cfg.legacySubbasinsShpName(), ExplorerConfig.legacySubbasinsShapefile()));
			P.put("legacyNetworkShpName", orDefault(cfg.legacyNetworkShpName(), ExplorerConfig.legacyNetworkShapefile()));
			P.put("legacySubbasinsCsvName", orDefault(cfg.legacySubbasinsCsvName(), ExplorerConfig.legacySubbasinsCsv()));
			P.put("legacyTopologyCsvName", orDefault(cfg.legacyTopologyCsvName(), ExplorerConfig.legacyTopologyCsv()));
			P.put("legacyTimeseriesPrefixes", String.join(",", cfg.legacyTimeseriesPrefixes()));
			P.remove("geopackagePath");
			P.remove("sqlitePath");
		}
	}

	public static void save(Path geopackage, Path sqlite) {
		save(ProjectConfig.geopackage(geopackage, sqlite));
	}

	public static Optional<ProjectConfig> load() {
		String mode = P.get("mode", null);
		if (mode == null) {
			String g = P.get("geopackagePath", null);
			String s = P.get("sqlitePath", null);
			if (g == null || s == null)
				return Optional.empty();
			return Optional.of(ProjectConfig.geopackage(Path.of(g), Path.of(s)));
		}
		ProjectMode projectMode = ProjectMode.valueOf(mode);
		if (projectMode == ProjectMode.GEOPACKAGE) {
			String g = P.get("geopackagePath", null);
			String s = P.get("sqlitePath", null);
			if (g == null || s == null)
				return Optional.empty();
			return Optional.of(ProjectConfig.geopackage(Path.of(g), Path.of(s)));
		}
		String root = P.get("legacyRootPath", null);
		String shpId = P.get("legacyShpIdField", null);
		String csvId = P.get("legacyCsvIdColumn", null);
		if (root == null || shpId == null || csvId == null)
			return Optional.empty();
		String prefixes = P.get("legacyTimeseriesPrefixes", String.join(",", ExplorerConfig.legacyTimeseriesPrefixes()));
		List<String> prefixList = Arrays.stream(prefixes.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
		return Optional.of(ProjectConfig.legacyFolder(Path.of(root), shpId, csvId,
				P.get("legacySubbasinsShpName", ExplorerConfig.legacySubbasinsShapefile()),
				P.get("legacyNetworkShpName", ExplorerConfig.legacyNetworkShapefile()),
				P.get("legacySubbasinsCsvName", ExplorerConfig.legacySubbasinsCsv()),
				P.get("legacyTopologyCsvName", ExplorerConfig.legacyTopologyCsv()), prefixList));

	}

	public static void clear() {
		P.remove("geopackagePath");
		P.remove("sqlitePath");
		P.remove("legacyRootPath");
		P.remove("legacyShpIdField");
		P.remove("legacyCsvIdColumn");
		P.remove("legacySubbasinsShpName");
		P.remove("legacyNetworkShpName");
		P.remove("legacySubbasinsCsvName");
		P.remove("legacyTopologyCsvName");
		P.remove("legacyTimeseriesPrefixes");
		P.remove("mode");

	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
