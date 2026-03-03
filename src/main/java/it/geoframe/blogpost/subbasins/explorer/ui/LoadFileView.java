package it.geoframe.blogpost.subbasins.explorer.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * @author Daniele Andreis
 */
public class LoadFileView extends JPanel {

	private final JLabel m_titleLabel = new JLabel("Open project");

	private final JLabel m_modeLabel = new JLabel("Project type");
	private final JRadioButton m_geopackageMode = new JRadioButton("GeoPackage + SQLite");
	private final JRadioButton m_legacyMode = new JRadioButton("Legacy folder");

	private final JLabel m_geopLabel = new JLabel("GeoPackage (must include: subbasin, topologi, simulation*)");
	private final JTextField m_geopPathField = new JTextField();

	private final JLabel m_inputLabel = new JLabel("SQLite input (must include table: measurements)");
	private final JTextField m_sqlitePathField = new JTextField();

	private final JLabel m_legacyRootLabel = new JLabel("Legacy root folder");
	private final JTextField m_legacyRootField = new JTextField();

	private final JLabel m_legacyShpIdLabel = new JLabel("Subbasin ID field name in shapefile");
	private final JTextField m_legacyShpIdField = new JTextField();

	private final JLabel m_legacyCsvIdLabel = new JLabel("Subbasin ID column name in CSV");
	private final JTextField m_legacyCsvIdField = new JTextField();

	private final JLabel m_legacySubbasinShpLabel = new JLabel("Subbasins shapefile name");
	private final JTextField m_legacySubbasinShpField = new JTextField();

	private final JLabel m_legacyNetworkShpLabel = new JLabel("Network shapefile name");
	private final JTextField m_legacyNetworkShpField = new JTextField();

	private final JLabel m_legacySubbasinsCsvLabel = new JLabel("Subbasins CSV name");
	private final JTextField m_legacySubbasinsCsvField = new JTextField();

	private final JLabel m_legacyTopologyCsvLabel = new JLabel("Topology CSV name (optional)");
	private final JTextField m_legacyTopologyCsvField = new JTextField();

	private final JLabel m_legacyPrefixesLabel = new JLabel("Timeseries prefixes (comma-separated)");
	private final JTextField m_legacyPrefixesField = new JTextField();

	private final JTextArea m_checkFileText = new JTextArea();

	private final JButton m_browseGeopButton = new JButton("Browse…");
	private final JButton m_browseSqliteButton = new JButton("Browse…");
	private final JButton m_browseLegacyRootButton = new JButton("Browse…");

	private final JButton m_continueButton = new JButton("Continue");

	public LoadFileView() {
		buildUi();
		postInit();
	}

	private void postInit() {
		m_titleLabel.setFont(m_titleLabel.getFont().deriveFont(Font.BOLD, 20f));

		setupPathField(m_geopPathField);
		setupPathField(m_sqlitePathField);
		setupPathField(m_legacyRootField);
		setupInputField(m_legacyShpIdField);
		setupInputField(m_legacyCsvIdField);
		setupInputField(m_legacySubbasinShpField);
		setupInputField(m_legacyNetworkShpField);
		setupInputField(m_legacySubbasinsCsvField);
		setupInputField(m_legacyTopologyCsvField);
		setupInputField(m_legacyPrefixesField);

		ButtonGroup group = new ButtonGroup();
		group.add(m_geopackageMode);
		group.add(m_legacyMode);
		m_geopackageMode.setSelected(true);
		m_modeLabel.setFont(m_modeLabel.getFont().deriveFont(Font.BOLD));

		m_checkFileText.setEditable(false);
		m_checkFileText.setLineWrap(true);
		m_checkFileText.setWrapStyleWord(true);
		m_checkFileText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		m_continueButton.setEnabled(false);
		setGeopackageEnabled(true);
		setLegacyEnabled(false);

		setLogText("Select a GeoPackage and a SQLite file.");
		setGeopackagePath(null);
		setSqlitePath(null);
		setLegacyRootPath(null);
		setLegacyShpIdField("");
		setLegacyCsvIdField("");
	}

	private static void setupInputField(JTextField tf) {
		tf.setEditable(true);
		tf.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
				new EmptyBorder(8, 10, 8, 10)));
		tf.setBackground(UIManager.getColor("TextField.background"));
	}

	private static void setupPathField(JTextField tf) {
		tf.setEditable(false);
		tf.setFocusable(false);
		tf.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
				new EmptyBorder(8, 10, 8, 10)));
		tf.setBackground(UIManager.getColor("TextField.background"));
	}

	public JButton browseGeopackageButton() {
		return m_browseGeopButton;
	}

	public JButton browseSqliteButton() {
		return m_browseSqliteButton;
	}

	public JButton continueButton() {
		return m_continueButton;
	}

	public JButton browseLegacyRootButton() {
		return m_browseLegacyRootButton;
	}

	public JRadioButton geopackageModeButton() {
		return m_geopackageMode;
	}

	public JRadioButton legacyModeButton() {
		return m_legacyMode;
	}

	public void setTitle(String t) {
		m_titleLabel.setText(t);
	}

	public void setGeopackagePath(String path) {
		m_geopPathField.setText(path == null ? "(not selected)" : path);
		m_geopPathField.setToolTipText(path);
	}

	public void setSqlitePath(String path) {
		m_sqlitePathField.setText(path == null ? "(not selected)" : path);
		m_sqlitePathField.setToolTipText(path);
	}

	public void setLogText(String text) {
		m_checkFileText.setText(text == null ? "" : text);
		m_checkFileText.setCaretPosition(m_checkFileText.getDocument().getLength());
	}

	public void appendLogLine(String line) {
		m_checkFileText.append((line == null ? "" : line) + "");
		m_checkFileText.setCaretPosition(m_checkFileText.getDocument().getLength());
	}

	public void setContinueEnabled(boolean enabled) {
		m_continueButton.setEnabled(enabled);
	}

	public void setLegacyRootPath(String path) {
		m_legacyRootField.setText(path == null ? "(not selected)" : path);
		m_legacyRootField.setToolTipText(path);
	}

	public void setLegacyShpIdField(String value) {
		m_legacyShpIdField.setText(value == null ? "" : value);
	}

	public void setLegacyCsvIdField(String value) {
		m_legacyCsvIdField.setText(value == null ? "" : value);
	}

	public void setLegacySubbasinsShp(String value) {
		m_legacySubbasinShpField.setText(value == null ? "" : value);
	}

	public void setLegacyNetworkShp(String value) {
		m_legacyNetworkShpField.setText(value == null ? "" : value);
	}

	public void setLegacySubbasinsCsv(String value) {
		m_legacySubbasinsCsvField.setText(value == null ? "" : value);
	}

	public void setLegacyTopologyCsv(String value) {
		m_legacyTopologyCsvField.setText(value == null ? "" : value);
	}

	public void setLegacyTimeseriesPrefixes(String value) {
		m_legacyPrefixesField.setText(value == null ? "" : value);
	}

	public String legacyShpIdField() {
		return m_legacyShpIdField.getText();
	}

	public String legacyCsvIdField() {
		return m_legacyCsvIdField.getText();
	}

	public String legacySubbasinsShp() {
		return m_legacySubbasinShpField.getText();
	}

	public String legacyNetworkShp() {
		return m_legacyNetworkShpField.getText();
	}

	public String legacySubbasinsCsv() {
		return m_legacySubbasinsCsvField.getText();
	}

	public String legacyTopologyCsv() {
		return m_legacyTopologyCsvField.getText();
	}

	public String legacyTimeseriesPrefixes() {
		return m_legacyPrefixesField.getText();
	}

	public JTextField legacyShpIdFieldInput() {
		return m_legacyShpIdField;
	}

	public JTextField legacyCsvIdFieldInput() {
		return m_legacyCsvIdField;
	}

	public JTextField legacySubbasinsShpInput() {
		return m_legacySubbasinShpField;
	}

	public JTextField legacyNetworkShpInput() {
		return m_legacyNetworkShpField;
	}

	public JTextField legacySubbasinsCsvInput() {
		return m_legacySubbasinsCsvField;
	}

	public JTextField legacyTopologyCsvInput() {
		return m_legacyTopologyCsvField;
	}

	public JTextField legacyTimeseriesPrefixesInput() {
		return m_legacyPrefixesField;
	}

	public void setGeopackageEnabled(boolean enabled) {
		m_geopPathField.setEnabled(enabled);
		m_sqlitePathField.setEnabled(enabled);
		m_browseGeopButton.setEnabled(enabled);
		m_browseSqliteButton.setEnabled(enabled);
	}

	public void setLegacyEnabled(boolean enabled) {
		m_legacyRootField.setEnabled(enabled);
		m_legacyShpIdField.setEnabled(enabled);
		m_legacyCsvIdField.setEnabled(enabled);
		m_legacySubbasinShpField.setEnabled(enabled);
		m_legacyNetworkShpField.setEnabled(enabled);
		m_legacySubbasinsCsvField.setEnabled(enabled);
		m_legacyTopologyCsvField.setEnabled(enabled);
		m_legacyPrefixesField.setEnabled(enabled);
		m_browseLegacyRootButton.setEnabled(enabled);
	}

	private void buildUi() {
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(18, 18, 18, 18));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(m_titleLabel, BorderLayout.WEST);

		content.add(titleRow);
		content.add(Box.createVerticalStrut(16));
		JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		modeRow.setOpaque(false);
		modeRow.add(m_modeLabel);
		modeRow.add(m_geopackageMode);
		modeRow.add(m_legacyMode);
		content.add(modeRow);
		content.add(Box.createVerticalStrut(16));

		content.add(sectionRow(m_geopLabel, m_geopPathField, m_browseGeopButton));
		content.add(Box.createVerticalStrut(12));
		content.add(sectionRow(m_inputLabel, m_sqlitePathField, m_browseSqliteButton));
		content.add(Box.createVerticalStrut(16));

		content.add(sectionRow(m_legacyRootLabel, m_legacyRootField, m_browseLegacyRootButton));
		content.add(Box.createVerticalStrut(10));
		content.add(sectionRow(m_legacyShpIdLabel, m_legacyShpIdField, null));
		content.add(Box.createVerticalStrut(10));
		content.add(sectionRow(m_legacyCsvIdLabel, m_legacyCsvIdField, null));
		content.add(Box.createVerticalStrut(10));
		content.add(sectionRow(m_legacySubbasinShpLabel, m_legacySubbasinShpField, null));
		content.add(Box.createVerticalStrut(10));
		content.add(sectionRow(m_legacyNetworkShpLabel, m_legacyNetworkShpField, null));
		content.add(Box.createVerticalStrut(10));
		content.add(sectionRow(m_legacySubbasinsCsvLabel, m_legacySubbasinsCsvField, null));
		content.add(Box.createVerticalStrut(10));
		content.add(sectionRow(m_legacyTopologyCsvLabel, m_legacyTopologyCsvField, null));
		content.add(Box.createVerticalStrut(10));
		content.add(sectionRow(m_legacyPrefixesLabel, m_legacyPrefixesField, null));
		content.add(Box.createVerticalStrut(14));

		JLabel logLabel = new JLabel("Checks / log");
		logLabel.setFont(logLabel.getFont().deriveFont(Font.BOLD));
		content.add(logLabel);
		content.add(Box.createVerticalStrut(6));

		JScrollPane logScroll = new JScrollPane(m_checkFileText);
		logScroll.setPreferredSize(new Dimension(900, 360));
		logScroll.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
				BorderFactory.createEmptyBorder()));
		content.add(logScroll);

		content.add(Box.createVerticalStrut(14));

		JPanel actions = new JPanel(new BorderLayout());
		actions.setOpaque(false);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		right.setOpaque(false);
		right.add(m_continueButton);

		actions.add(right, BorderLayout.EAST);
		content.add(actions);

		add(content, BorderLayout.CENTER);
	}

	private JPanel sectionRow(JLabel label, JTextField pathField, JButton browseButton) {
		JPanel section = new JPanel();
		section.setOpaque(false);
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

		label.setFont(label.getFont().deriveFont(Font.BOLD));
		section.add(label);
		section.add(Box.createVerticalStrut(6));

		JPanel row = new JPanel(new BorderLayout(10, 0));
		row.setOpaque(false);
		row.add(pathField, BorderLayout.CENTER);

		if (browseButton != null) {
			browseButton.setPreferredSize(new Dimension(110, pathField.getPreferredSize().height));
			row.add(browseButton, BorderLayout.EAST);
		}

		section.add(row);
		return section;
	}
}
