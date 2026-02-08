package it.geoframe.blogpost.subbasins.explorer.ui;

import javax.swing.*;

import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;

import java.awt.*;

/**
 * @author Daniele Andreis
 */

public final class MainFrame extends JFrame implements LoadFileController.Navigator {

	private final CardLayout cards = new CardLayout();
	private final JPanel root = new JPanel(cards);

	private final LoadFileView loadFileView = new LoadFileView();

	public MainFrame(String version, Image appIcon) {
		super("GEOframe Subbasins Explorer");

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());

		if (appIcon != null)
			setIconImage(appIcon);

		root.add(loadFileView, "LOAD");
		add(root, BorderLayout.CENTER);

		// Controller (naviga verso Home)
		new LoadFileController(loadFileView, this);

		setSize(1100, 700);
		setLocationRelativeTo(null);
		setResizable(true);
	}

	@Override
	public void goHome(ProjectConfig cfg) {
		Home home = new Home(cfg, "0.1.0-SNAPSHOT");
		setContentPane(home);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
		revalidate();
		repaint();
	}
}