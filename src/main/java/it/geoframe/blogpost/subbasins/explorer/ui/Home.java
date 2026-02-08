package it.geoframe.blogpost.subbasins.explorer.ui;

import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;

/**
 * Home temporanea: placeholder finché non colleghi l'Open Project
 * (GeoPackage+SQLite). Qui poi metterai: "Open project", "Recent projects",
 * ecc.
 */
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class Home extends JPanel {



	public Home(ProjectConfig config, String version) {
		super(new BorderLayout());
		SubbasinExplorerPanel explorerPanel = new SubbasinExplorerPanel(config);
		add(explorerPanel, BorderLayout.CENTER);
	}
}
