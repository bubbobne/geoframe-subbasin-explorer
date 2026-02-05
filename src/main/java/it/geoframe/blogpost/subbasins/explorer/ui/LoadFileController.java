package it.geoframe.blogpost.subbasins.explorer.ui;

import javax.swing.*;

import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfigStore;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectValidator;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;
import java.nio.file.Path;
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
			Path p = chooseFile("Select GeoPackage", "gpkg");
			if (p != null) {
				geopackagePath = p;
				view.setGeopackagePath(p.toString());
				revalidateAndUpdateUI();
			}
		});

		view.browseSqliteButton().addActionListener(e -> {
			Path p = chooseFile("Select SQLite", "sqlite", "db");
			if (p != null) {
				sqlitePath = p;
				view.setSqlitePath(p.toString());
				revalidateAndUpdateUI();
			}
		});
		view.browseLegacyRootButton().addActionListener(e -> {
			Path p = chooseDirectory("Select legacy folder");
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

		view.continueButton().addActionListener(e -> {
			ProjectConfig cfg = currentConfig();
			var result = ProjectValidator.validate(cfg);
			if (!result.ok()) {
				// dovrebbe essere già disabilitato, ma doppio check
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
				view.legacyModeButton().setSelected(true);
			}
			view.setGeopackageEnabled(cfg.mode() == ProjectMode.GEOPACKAGE);
			view.setLegacyEnabled(cfg.mode() == ProjectMode.LEGACY_FOLDER);

			view.appendLogLine("Loaded last project from preferences.");
		});
	}

	private void revalidateAndUpdateUI() {
		if (mode == ProjectMode.GEOPACKAGE) {
			if (geopackagePath == null || sqlitePath == null) {
				view.setContinueEnabled(false);
				view.setLogText("Select a GeoPackage and a SQLite file.");
				return;
			}
		} else {
			if (legacyRootPath == null) {
				view.setContinueEnabled(false);
				view.setLogText("Select the legacy folder and fill the ID fields.");
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
			sb.append("✅ Validation OK\n\n");

		if (!result.info().isEmpty()) {
			sb.append("Info:\n");
			for (String w : result.info())
				sb.append(" - ").append(w).append("\n");
			sb.append("\n");
		}

		if (!result.warnings().isEmpty()) {
			sb.append("Warnings:\n");
			for (String w : result.warnings())
				sb.append(" - ").append(w).append("\n");
			sb.append("\n");
		}
		if (!result.errors().isEmpty()) {
			sb.append("Errors:\n");
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
					String.join(", ", extensions).toUpperCase() + " files", extensions));
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
	        return ProjectConfig.legacyFolder(legacyRootPath,
	                Objects.toString(view.legacyShpIdField(), ""),
	                Objects.toString(view.legacyCsvIdField(), ""));
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
