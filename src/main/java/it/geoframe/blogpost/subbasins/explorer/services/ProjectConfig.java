package it.geoframe.blogpost.subbasins.explorer.services;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Save project data based on the type of project
 * 
 * @author Daniele Andreis
 */
public record ProjectConfig(ProjectMode mode, Path geopackagePath, Path sqlitePath, Path legacyRootPath,
		String legacyShpIdField, String legacyCsvIdColumn) {

	public ProjectConfig {
		Objects.requireNonNull(mode, "mode");
	}

	public static ProjectConfig geopackage(Path geopackagePath, Path sqlitePath) {
		return new ProjectConfig(ProjectMode.GEOPACKAGE, geopackagePath, sqlitePath, null, null, null);
	}

	public static ProjectConfig legacyFolder(Path legacyRootPath, String legacyShpIdField, String legacyCsvIdColumn) {
		return new ProjectConfig(ProjectMode.LEGACY_FOLDER, null, null, legacyRootPath, legacyShpIdField,
				legacyCsvIdColumn);
	}
}
