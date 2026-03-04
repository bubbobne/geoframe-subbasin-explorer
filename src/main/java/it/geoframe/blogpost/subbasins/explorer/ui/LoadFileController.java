package it.geoframe.blogpost.subbasins.explorer.ui;

import javax.swing.*;

import it.geoframe.blogpost.subbasins.explorer.services.ExplorerConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfigStore;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectValidator;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class LoadFileController {

	public interface Navigator {
		void goHome(ProjectConfig cfg);
	}

	private final LoadFileView view;
	private final Navigator navigator;

	private Path geopackagePath;
	private Path sqlitePath;
	private Path legacyRootPath;
	private ProjectMode mode = ProjectMode.GEOPACKAGE;

	public LoadFileController(LoadFileView view, Navigator navigator) {
		this.view = view;
		this.navigator = navigator;
		wire();
		preloadIfPresent();
		applyLegacyDefaultsIfEmpty();
		revalidateAndUpdateUI();
	}

	private void wire() {
		view.geopackageModeButton().addActionListener(e -> {
			mode = ProjectMode.GEOPACKAGE;
			view.setGeopackageEnabled(true);
			view.setLegacyEnabled(false);
			revalidateAndUpdateUI();
		});

		view.legacyModeButton().addActionListener(e -> {
			mode = ProjectMode.LEGACY_FOLDER;
			view.setGeopackageEnabled(false);
			view.setLegacyEnabled(true);
			revalidateAndUpdateUI();
		});

		view.browseGeopackageButton().addActionListener(e -> {
			Path p = chooseFile(I18n.tr("chooser.selectGeopackage"), "gpkg");
			if (p != null) {
				geopackagePath = p;
				view.setGeopackagePath(p.toString());
				revalidateAndUpdateUI();
			}
		});

		view.browseSqliteButton().addActionListener(e -> {
			Path p = chooseFile(I18n.tr("chooser.selectSqlite"), "sqlite", "db");
			if (p != null) {
				sqlitePath = p;
				view.setSqlitePath(p.toString());
				revalidateAndUpdateUI();
			}
		});
		view.browseLegacyRootButton().addActionListener(e -> {
			Path p = chooseDirectory(I18n.tr("chooser.selectLegacy"));
			if (p != null) {
				legacyRootPath = p;
				view.setLegacyRootPath(p.toString());
				revalidateAndUpdateUI();
			}
		});

		view.legacyShpIdFieldInput().getDocument()
				.addDocumentListener(new SimpleDocumentListener(this::revalidateAndUpdateUI));
		view.legacyCsvIdFieldInput().getDocument()
				.addDocumentListener(new SimpleDocumentListener(this::revalidateAndUpdateUI));
		view.legacySubbasinsShpInput().getDocument()
				.addDocumentListener(new SimpleDocumentListener(this::revalidateAndUpdateUI));
		view.legacyNetworkShpInput().getDocument()
				.addDocumentListener(new SimpleDocumentListener(this::revalidateAndUpdateUI));
		view.legacySubbasinsCsvInput().getDocument()
				.addDocumentListener(new SimpleDocumentListener(this::revalidateAndUpdateUI));
		view.legacyTopologyCsvInput().getDocument()
				.addDocumentListener(new SimpleDocumentListener(this::revalidateAndUpdateUI));
		view.legacyTimeseriesPrefixesInput().getDocument()
				.addDocumentListener(new SimpleDocumentListener(this::revalidateAndUpdateUI));

		view.continueButton().addActionListener(e -> {
			ProjectConfig cfg = currentConfig();
			var result = ProjectValidator.validate(cfg);
			if (!result.ok()) {
				showResult(result);
				return;
			}
			ProjectConfigStore.save(cfg);
			navigator.goHome(cfg);
		});
	}

	private void preloadIfPresent() {
		ProjectConfigStore.load().ifPresent(cfg -> {
			this.mode = cfg.mode();
			if (cfg.mode() == ProjectMode.GEOPACKAGE) {
				this.geopackagePath = cfg.geopackagePath();
				this.sqlitePath = cfg.sqlitePath();
				view.setGeopackagePath(geopackagePath.toString());
				view.setSqlitePath(sqlitePath.toString());
				view.geopackageModeButton().setSelected(true);
			} else {
				this.legacyRootPath = cfg.legacyRootPath();
				view.setLegacyRootPath(legacyRootPath.toString());
				view.setLegacyShpIdField(cfg.legacyShpIdField());
				view.setLegacyCsvIdField(cfg.legacyCsvIdColumn());
				view.setLegacySubbasinsShp(cfg.legacySubbasinsShpName());
				view.setLegacyNetworkShp(cfg.legacyNetworkShpName());
				view.setLegacySubbasinsCsv(cfg.legacySubbasinsCsvName());
				view.setLegacyTopologyCsv(cfg.legacyTopologyCsvName());
				view.setLegacyTimeseriesPrefixes(String.join(",", cfg.legacyTimeseriesPrefixes()));
				view.legacyModeButton().setSelected(true);
			}
			view.setGeopackageEnabled(cfg.mode() == ProjectMode.GEOPACKAGE);
			view.setLegacyEnabled(cfg.mode() == ProjectMode.LEGACY_FOLDER);

			view.appendLogLine(I18n.tr("load.log.loadedLastProject"));
		});
	}

	private void applyLegacyDefaultsIfEmpty() {
		if (isBlank(view.legacySubbasinsShp())) {
			view.setLegacySubbasinsShp(ExplorerConfig.legacySubbasinsShapefile());
		}
		if (isBlank(view.legacyNetworkShp())) {
			view.setLegacyNetworkShp(ExplorerConfig.legacyNetworkShapefile());
		}
		if (isBlank(view.legacySubbasinsCsv())) {
			view.setLegacySubbasinsCsv(ExplorerConfig.legacySubbasinsCsv());
		}
		if (isBlank(view.legacyTopologyCsv())) {
			view.setLegacyTopologyCsv(ExplorerConfig.legacyTopologyCsv());
		}
		if (isBlank(view.legacyTimeseriesPrefixes())) {
			view.setLegacyTimeseriesPrefixes(String.join(",", ExplorerConfig.legacyTimeseriesPrefixes()));
		}
	}

	private void revalidateAndUpdateUI() {
		if (mode == ProjectMode.GEOPACKAGE) {
			if (geopackagePath == null || sqlitePath == null) {
				view.setContinueEnabled(false);
				view.setLogText(I18n.tr("load.log.selectGeopackage"));
				return;
			}
		} else {
			if (legacyRootPath == null) {
				view.setContinueEnabled(false);
				view.setLogText(I18n.tr("load.log.selectLegacy"));
				return;

			}
		}
		ProjectConfig cfg = currentConfig();
		var result = ProjectValidator.validate(cfg);

		showResult(result);
		view.setContinueEnabled(result.ok());
	}

	private void showResult(ProjectValidator.ValidationResult result) {
		StringBuilder sb = new StringBuilder();
		if (result.ok())
			sb.append(I18n.tr("load.log.validationOk")).append("\n\n");

		if (!result.info().isEmpty()) {
			sb.append(I18n.tr("load.log.info")).append("\n");
			for (String w : result.info())
				sb.append(" - ").append(w).append("\n");
			sb.append("\n");
		}

		if (!result.warnings().isEmpty()) {
			sb.append(I18n.tr("load.log.warnings")).append("\n");
			for (String w : result.warnings())
				sb.append(" - ").append(w).append("\n");
			sb.append("\n");
		}
		if (!result.errors().isEmpty()) {
			sb.append(I18n.tr("load.log.errors")).append("\n");
			for (String er : result.errors())
				sb.append(" - ").append(er).append("\n");
		}
		view.setLogText(sb.toString());
	}

	private Path chooseFile(String title, String... extensions) {
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle(title);
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);

		if (extensions != null && extensions.length > 0) {
			fc.setAcceptAllFileFilterUsed(true);
			fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
					String.join(", ", extensions).toUpperCase() + " " + I18n.tr("chooser.filesSuffix"), extensions));
		}

		int res = fc.showOpenDialog(view);
		if (res == JFileChooser.APPROVE_OPTION) {
			return fc.getSelectedFile().toPath();
		}
		return null;
	}

	private Path chooseDirectory(String title) {
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle(title);
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int res = fc.showOpenDialog(view);
		if (res == JFileChooser.APPROVE_OPTION) {
			return fc.getSelectedFile().toPath();
		}
		return null;
	}

	private ProjectConfig currentConfig() {
		if (mode == ProjectMode.GEOPACKAGE) {
			return ProjectConfig.geopackage(geopackagePath, sqlitePath);
		}
		return ProjectConfig.legacyFolder(legacyRootPath, Objects.toString(view.legacyShpIdField(), ""),
				Objects.toString(view.legacyCsvIdField(), ""), Objects.toString(view.legacySubbasinsShp(), ""),
				Objects.toString(view.legacyNetworkShp(), ""), Objects.toString(view.legacySubbasinsCsv(), ""),
				Objects.toString(view.legacyTopologyCsv(), ""), parseCsvList(view.legacyTimeseriesPrefixes()));
	}

	private List<String> parseCsvList(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static final class SimpleDocumentListener implements javax.swing.event.DocumentListener {
		private final Runnable onChange;

		private SimpleDocumentListener(Runnable onChange) {
			this.onChange = onChange;
		}

		@Override
		public void insertUpdate(javax.swing.event.DocumentEvent e) {
			onChange.run();
		}

		@Override
		public void removeUpdate(javax.swing.event.DocumentEvent e) {
			onChange.run();
		}

		@Override
		public void changedUpdate(javax.swing.event.DocumentEvent e) {
			onChange.run();
		}
	}

}
