package it.geoframe.blogpost.subbasins.explorer.ui;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * The splash screen for rainfall/runoff output viewer
 * 
 * @author Daniele Andreis
 */
public class SplashScreen extends JPanel {

	private final JLabel m_titleLabel = new JLabel();
	private final JLabel m_logoLabel = new JLabel();
	private final JLabel m_versionLabel = new JLabel();
	private final JLabel m_statusLabel = new JLabel();
	private final JProgressBar m_progressBar = new JProgressBar(0, 100);
	private final JLabel m_authorLabel = new JLabel();
	private final JLabel m_licenseLabel = new JLabel();

	public SplashScreen(String author, String license, String version) {
		initializePanel();
		setTitle("GEOframe Subbasins Explorer");
		setVersion(version);
		setStatus("Starting…");
		setProgress(0);
		setAuthor(author);
		setLicense(license);
	}

	// ---------- Public API (Controller-friendly) ----------

	public void setTitle(String title) {
		m_titleLabel.setText(Objects.requireNonNullElse(title, ""));
	}

	public void setVersion(String version) {
		m_versionLabel.setText(Objects.requireNonNullElse(version, ""));
	}

	public void setStatus(String status) {
		m_statusLabel.setText(Objects.requireNonNullElse(status, ""));
	}

	/** 0..100 */
	public void setProgress(int percent) {
		int p = Math.max(0, Math.min(100, percent));
		m_progressBar.setValue(p);
		m_progressBar.setStringPainted(true);
		m_progressBar.setString(p + "%");
	}

	public void setAuthor(String author) {
		m_authorLabel.setText(Objects.requireNonNullElse(author, ""));
	}

	public void setLicense(String license) {
		m_licenseLabel.setText(Objects.requireNonNullElse(license, ""));
	}

	// ---------- UI building ----------

	protected void initializePanel() {
		setLayout(new BorderLayout());
		add(createPanel(), BorderLayout.CENTER);
		setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		setBackground(Color.WHITE);
	}

	private JPanel createPanel() {
		JPanel p = new JPanel();
		p.setBackground(Color.WHITE);

		// Responsive layout: no huge PX constants
		FormLayout layout = new FormLayout("pref:grow", // single column, grows
				"pref, 10dlu, pref, 8dlu, pref, 6dlu, pref, 10dlu, pref, 6dlu, pref");
		p.setLayout(layout);
		CellConstraints cc = new CellConstraints();

		// Title
		m_titleLabel.setName("titleLabel");
		m_titleLabel.setFont(m_titleLabel.getFont().deriveFont(Font.BOLD, 26f));
		m_titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p.add(m_titleLabel, cc.xy(1, 1, "center, default"));

		// Logo
		m_logoLabel.setName("logoLabel");
		m_logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		m_logoLabel.setIcon(loadImageOrNull("images/geoframe.png"));
		p.add(m_logoLabel, cc.xy(1, 3, "center, default"));

		// Version
		m_versionLabel.setName("versionLabel");
		m_versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
		m_versionLabel.setForeground(new Color(60, 60, 60));
		p.add(m_versionLabel, cc.xy(1, 5, "center, default"));

		// Status
		m_statusLabel.setName("statusLabel");
		m_statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p.add(m_statusLabel, cc.xy(1, 7, "center, default"));

		// Progress
		m_progressBar.setName("progressBar");
		m_progressBar.setBorderPainted(false);
		p.add(m_progressBar, cc.xy(1, 9, "fill, default"));

		// Author + license
		JPanel bottom = new JPanel(new GridLayout(2, 1));
		bottom.setOpaque(false);

		m_authorLabel.setName("authorLabel");
		m_authorLabel.setHorizontalAlignment(SwingConstants.CENTER);
		bottom.add(m_authorLabel);

		m_licenseLabel.setName("licenseLabel");
		m_licenseLabel.setHorizontalAlignment(SwingConstants.CENTER);
		m_licenseLabel.setForeground(new Color(90, 90, 90));
		bottom.add(m_licenseLabel);

		p.add(bottom, cc.xy(1, 11, "fill, default"));

		return p;
	}

	private ImageIcon loadImageOrNull(String pathOnClasspath) {
		try {
			var url = getClass().getClassLoader().getResource(pathOnClasspath);
			if (url == null) {
				// Non esplodere: splash senza logo è meglio di crash
				return null;
			}
			return new ImageIcon(url);
		} catch (Exception e) {
			return null;
		}
	}

	// ---------- Optional: quick manual preview ----------
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("SplashScreen preview");
			frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

			SplashScreen splash = new SplashScreen("Daniele Andreis", "1.0", "GPL3");
			splash.setStatus("Loading UI…");
			splash.setProgress(35);

			frame.setContentPane(splash);
			frame.pack();
			frame.setSize(new Dimension(640, 420));
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}

	public void disposeWindowAncestor() {
		var w = SwingUtilities.getWindowAncestor(this);
		if (w != null) {
			w.setVisible(false);
			w.dispose();
		}
	}

}
