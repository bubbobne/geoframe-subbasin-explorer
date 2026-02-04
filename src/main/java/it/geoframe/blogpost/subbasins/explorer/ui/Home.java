package it.geoframe.blogpost.subbasins.explorer.ui;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * Home temporanea: placeholder finché non colleghi l'Open Project
 * (GeoPackage+SQLite). Qui poi metterai: "Open project", "Recent projects",
 * ecc.
 */
public final class Home extends JFrame {
	private void setAppIconFromClasspath(String path) {
		try {
			URL url = getClass().getClassLoader().getResource(path);
			if (url == null) {
				// niente icona, ma non fallire
				return;
			}
			Image img = new ImageIcon(url).getImage();
			setIconImage(img);
		} catch (Exception ignored) {
		}
	}

	public Home(String version) {
		super("GEOframe Subbasins Explorer — Home");

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		setLayout(new BorderLayout());
		setAppIconFromClasspath("images/geoframe.png");
		JLabel title = new JLabel("GEOframe Subbasins Explorer", SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

		JLabel subtitle = new JLabel("Home (temporary) — version " + version, SwingConstants.CENTER);

		JPanel center = new JPanel(new GridLayout(3, 1, 0, 10));
		center.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
		center.add(title);
		center.add(subtitle);

		JButton openProject = new JButton("Open project (GeoPackage + SQLite) — TODO");
		openProject.setEnabled(false);
		center.add(openProject);

		add(center, BorderLayout.CENTER);

		setSize(1100, 700);
		setLocationRelativeTo(null);
		setResizable(true);
	}
}