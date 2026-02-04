package it.geoframe.blogpost.subbasins.explorer.ui;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoadFileView extends JPanel {

    private final JLabel m_titleLabel = new JLabel("Open project");

    private final JLabel m_geopLabel = new JLabel("GeoPackage (must include: subbasin, topologi, simulation*)");
    private final JTextField m_geopPathField = new JTextField();

    private final JLabel m_inputLabel = new JLabel("SQLite input (must include table: measurements)");
    private final JTextField m_sqlitePathField = new JTextField();

    private final JTextArea m_checkFileText = new JTextArea();

    private final JButton m_browseGeopButton = new JButton("Browse…");
    private final JButton m_browseSqliteButton = new JButton("Browse…");
    private final JButton m_continueButton = new JButton("Continue");

    public LoadFileView() {
        buildUi();
        postInit();
    }

    private void postInit() {
        // Title
        m_titleLabel.setFont(m_titleLabel.getFont().deriveFont(Font.BOLD, 20f));

        // Path fields
        setupPathField(m_geopPathField);
        setupPathField(m_sqlitePathField);

        // Log
        m_checkFileText.setEditable(false);
        m_checkFileText.setLineWrap(true);
        m_checkFileText.setWrapStyleWord(true);
        m_checkFileText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        // Continue disabled until valid
        m_continueButton.setEnabled(false);

        // Default text
        setLogText("Select a GeoPackage and a SQLite file.");
        setGeopackagePath(null);
        setSqlitePath(null);
    }

    private static void setupPathField(JTextField tf) {
        tf.setEditable(false);
        tf.setFocusable(false);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
                new EmptyBorder(8, 10, 8, 10)
        ));
        tf.setBackground(UIManager.getColor("TextField.background"));
    }

    // --- API for controller ---
    public JButton browseGeopackageButton() { return m_browseGeopButton; }
    public JButton browseSqliteButton() { return m_browseSqliteButton; }
    public JButton continueButton() { return m_continueButton; }

    public void setTitle(String t) { m_titleLabel.setText(t); }

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
        m_checkFileText.append((line == null ? "" : line) + "\n");
        m_checkFileText.setCaretPosition(m_checkFileText.getDocument().getLength());
    }

    public void setContinueEnabled(boolean enabled) {
        m_continueButton.setEnabled(enabled);
    }

    // --- UI layout (single column) ---
    private void buildUi() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        // Title row
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(m_titleLabel, BorderLayout.WEST);

        content.add(titleRow);
        content.add(Box.createVerticalStrut(16));

        // GeoPackage section
        content.add(sectionRow(m_geopLabel, m_geopPathField, m_browseGeopButton));
        content.add(Box.createVerticalStrut(12));

        // SQLite section
        content.add(sectionRow(m_inputLabel, m_sqlitePathField, m_browseSqliteButton));
        content.add(Box.createVerticalStrut(16));

        // Log area
        JLabel logLabel = new JLabel("Checks / log");
        logLabel.setFont(logLabel.getFont().deriveFont(Font.BOLD));
        content.add(logLabel);
        content.add(Box.createVerticalStrut(6));

        JScrollPane logScroll = new JScrollPane(m_checkFileText);
        logScroll.setPreferredSize(new Dimension(900, 360));
        logScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
                BorderFactory.createEmptyBorder()
        ));
        content.add(logScroll);

        content.add(Box.createVerticalStrut(14));

        // Bottom actions
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);

        // Right-aligned continue button
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

        browseButton.setPreferredSize(new Dimension(110, pathField.getPreferredSize().height));
        row.add(browseButton, BorderLayout.EAST);

        section.add(row);
        return section;
    }
}


