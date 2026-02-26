package it.geoframe.blogpost.subbasins.explorer.plot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import it.geoframe.blogpost.subbasins.explorer.model.ChartRequest;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectMode;

public final class ChartSetupDialog {

	private final JDialog dialog = new JDialog();
	private final JComboBox<String> simulationCombo = new JComboBox<>();
	private final JComboBox<String> typeCombo = new JComboBox<>(new String[] { "discharge", "state", "fluxes" });

	public ChartSetupDialog(Component parent, ProjectMode mode, String[] simulationTables, Consumer<ChartRequest> onConfirm) {
		dialog.setModal(false);
		dialog.setTitle("Selezione grafico");
		dialog.setLayout(new BorderLayout(8, 8));
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		if (mode == ProjectMode.GEOPACKAGE) {
			panel.add(new JLabel("Simulazione da plottare:"), gbc);
			gbc.gridy++;
			for (String t : simulationTables) {
				simulationCombo.addItem(t);
			}
			panel.add(simulationCombo, gbc);
			gbc.gridy++;
		}
		panel.add(new JLabel("Tipo grafico:"), gbc);
		gbc.gridy++;
		panel.add(typeCombo, gbc);
		gbc.gridy++;
		JButton nextButton = new JButton("Avanti");
		nextButton.addActionListener(e -> {
			dialog.dispose();
			onConfirm.accept(new ChartRequest((String) simulationCombo.getSelectedItem(), (String) typeCombo.getSelectedItem()));
		});
		panel.add(nextButton, gbc);
		dialog.add(panel, BorderLayout.CENTER);
		dialog.setSize(new Dimension(420, 260));
		dialog.setLocationRelativeTo(parent);
	}

	public void showDialog() {
		dialog.setVisible(true);
	}
}
