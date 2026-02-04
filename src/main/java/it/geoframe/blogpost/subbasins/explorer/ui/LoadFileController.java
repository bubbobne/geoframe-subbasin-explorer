package it.geoframe.blogpost.subbasins.explorer.ui;



import javax.swing.*;

import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfigStore;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectValidator;

import java.nio.file.Path;

public final class LoadFileController {

    public interface Navigator {
        void goHome(ProjectConfig cfg);
    }

    private final LoadFileView view;
    private final Navigator navigator;

    private Path geopackagePath;
    private Path sqlitePath;

    public LoadFileController(LoadFileView view, Navigator navigator) {
        this.view = view;
        this.navigator = navigator;
        wire();
        preloadIfPresent();
        revalidateAndUpdateUI();
    }

    private void wire() {
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

        view.continueButton().addActionListener(e -> {
            ProjectConfig cfg = new ProjectConfig(geopackagePath, sqlitePath);
            var result = ProjectValidator.validate(cfg);
            if (!result.ok()) {
                // dovrebbe essere già disabilitato, ma doppio check
                showResult(result);
                return;
            }
            ProjectConfigStore.save(geopackagePath, sqlitePath);
            navigator.goHome(cfg);
        });
    }

    private void preloadIfPresent() {
        ProjectConfigStore.load().ifPresent(cfg -> {
            this.geopackagePath = cfg.geopackagePath();
            this.sqlitePath = cfg.sqlitePath();
            view.setGeopackagePath(geopackagePath.toString());
            view.setSqlitePath(sqlitePath.toString());
            view.appendLogLine("Loaded last project from preferences.");
        });
    }

    private void revalidateAndUpdateUI() {
        if (geopackagePath == null || sqlitePath == null) {
            view.setContinueEnabled(false);
            view.setLogText("Select a GeoPackage and a SQLite file.");
            return;
        }

        ProjectConfig cfg = new ProjectConfig(geopackagePath, sqlitePath);
        var result = ProjectValidator.validate(cfg);

        showResult(result);
        view.setContinueEnabled(result.ok());
    }

    private void showResult(ProjectValidator.ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        if (result.ok()) sb.append("✅ Validation OK\n\n");
        
        
        if (!result.info().isEmpty()) {
            sb.append("Info:\n");
            for (String w : result.info()) sb.append(" - ").append(w).append("\n");
            sb.append("\n");
        }
        
        if (!result.warnings().isEmpty()) {
            sb.append("Warnings:\n");
            for (String w : result.warnings()) sb.append(" - ").append(w).append("\n");
            sb.append("\n");
        }
        if (!result.errors().isEmpty()) {
            sb.append("Errors:\n");
            for (String er : result.errors()) sb.append(" - ").append(er).append("\n");
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
                    String.join(", ", extensions).toUpperCase() + " files", extensions
            ));
        }

        int res = fc.showOpenDialog(view);
        if (res == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile().toPath();
        }
        return null;
    }
}
