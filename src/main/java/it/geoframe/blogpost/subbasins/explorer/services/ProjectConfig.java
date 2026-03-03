package it.geoframe.blogpost.subbasins.explorer.services;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Save project data based on the type of project.
 *
 * @author Daniele Andreis
 */
public record ProjectConfig(ProjectMode mode, Path geopackagePath, Path sqlitePath, Path legacyRootPath,
		String legacyShpIdField, String legacyCsvIdColumn, String legacySubbasinsShpName, String legacyNetworkShpName,
		String legacySubbasinsCsvName, String legacyTopologyCsvName, List<String> legacyTimeseriesPrefixes) {

	public ProjectConfig {
		Objects.requireNonNull(mode, "mode");
	}

	public static ProjectConfig geopackage(Path geopackagePath, Path sqlitePath) {
		return new ProjectConfig(ProjectMode.GEOPACKAGE, geopackagePath, sqlitePath, null, null, null, null, null,
				null, null, List.of());
	}

	public static ProjectConfig legacyFolder(Path legacyRootPath, String legacyShpIdField, String legacyCsvIdColumn,
			String legacySubbasinsShpName, String legacyNetworkShpName, String legacySubbasinsCsvName,
			String legacyTopologyCsvName, List<String> legacyTimeseriesPrefixes) {
		return new ProjectConfig(ProjectMode.LEGACY_FOLDER, null, null, legacyRootPath, legacyShpIdField,
				legacyCsvIdColumn, legacySubbasinsShpName, legacyNetworkShpName, legacySubbasinsCsvName,
				legacyTopologyCsvName, legacyTimeseriesPrefixes == null ? List.of() : List.copyOf(legacyTimeseriesPrefixes));
	}
}
