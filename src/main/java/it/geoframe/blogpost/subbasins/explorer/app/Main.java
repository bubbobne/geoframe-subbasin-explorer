package it.geoframe.blogpost.subbasins.explorer.app;

import java.awt.Dimension;
import java.awt.Image;
import java.net.URL;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import it.geoframe.blogpost.subbasins.explorer.ui.MainFrame;
import it.geoframe.blogpost.subbasins.explorer.ui.SplashScreen;

public final class Main {

	private Main() {
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			setSystemLookAndFeelQuietly();

			final String version = readVersionOrFallback("0.1.0-SNAPSHOT");
			final String author = "by Daniele Andreis";
			final String license = "GPL-3.0-only";

			final SplashScreen splash = new SplashScreen(version, author, license);
			splash.setVisible(true);
			final JWindow splashWindow = new JWindow();
			splashWindow.setContentPane(splash);
			splashWindow.pack();
			splashWindow.setSize(new Dimension(840, 620));
			splashWindow.setLocationRelativeTo(null);
			splashWindow.setAlwaysOnTop(true);
			splashWindow.setVisible(true);
			new SwingWorker<Void, String>() {

				@Override
				protected Void doInBackground() throws Exception {
					publish("Starting…");
					publish("Loading UI resources…");
					splash.setProgress(40);
					Thread.sleep(1500); // placeholder (rimuovi quando hai init reali)
					publish("Initializing GeoTools…");
					Thread.sleep(2000);
					splash.setProgress(80);
					publish("Ready.");
					Thread.sleep(100);

					return null;
				}

				@Override
				protected void process(List<String> chunks) {
					splash.setStatus(chunks.get(chunks.size() - 1));
				}

				@Override
				protected void done() {
					splash.setVisible(false);
					splash.disposeWindowAncestor();

					// Home temporanea
					Image appIcon = loadIconOrNull("images/geoframe.png");
					MainFrame frame = new MainFrame(version, appIcon);
					frame.setVisible(true);
				}

			}.execute();
		});
	}

	private static Image loadIconOrNull(String path) {
		try {
			URL url = Main.class.getClassLoader().getResource(path);
			if (url == null)
				return null;
			return new ImageIcon(url).getImage();
		} catch (Exception e) {
			return null;
		}
	}

	private static void setSystemLookAndFeelQuietly() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
		}
	}

	private static String readVersionOrFallback(String fallback) {
		Package p = Main.class.getPackage();
		String v = (p != null) ? p.getImplementationVersion() : null;
		return (v != null && !v.isBlank()) ? v : fallback;
	}
}