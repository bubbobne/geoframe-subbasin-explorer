package it.geoframe.blogpost.subbasins.explorer.services;

import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

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
		} else {
			P.put("legacyRootPath", cfg.legacyRootPath().toString());
			P.put("legacyShpIdField", cfg.legacyShpIdField());
			P.put("legacyCsvIdColumn", cfg.legacyCsvIdColumn());
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
		return Optional.of(ProjectConfig.legacyFolder(Path.of(root), shpId, csvId));

	}

	public static void clear() {
		P.remove("geopackagePath");
		P.remove("sqlitePath");
		P.remove("legacyRootPath");
		P.remove("legacyShpIdField");
		P.remove("legacyCsvIdColumn");
		P.remove("mode");

	}
}
