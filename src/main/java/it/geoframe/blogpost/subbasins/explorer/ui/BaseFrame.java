package it.geoframe.blogpost.subbasins.explorer.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Image;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import it.geoframe.blogpost.subbasins.explorer.controller.LoadProjectCardController;
import it.geoframe.blogpost.subbasins.explorer.controller.Navigator;
import it.geoframe.blogpost.subbasins.explorer.i18n.Messanger;
import it.geoframe.blogpost.subbasins.explorer.services.ProjectConfig;
import it.geoframe.blogpost.subbasins.explorer.ui.basinmap.SubbasinMapPanel;
import it.geoframe.blogpost.subbasins.explorer.ui.fileloader.LoadProjectCardPanel;

/**
 * This is the base frame of the app. It loads the main application panels.
 *
 * @author Daniele Andreis
 */
public final class BaseFrame extends JFrame implements Navigator {

    private static final String CARD_LOAD = "LOAD";
    private static final String CARD_BASIN_MAP = "BASIN_MAP";

    private final LoadProjectCardPanel loadFileView = new LoadProjectCardPanel();

    private final CardLayout rootLayout = new CardLayout();
    private final JPanel root = new JPanel(rootLayout);

    public BaseFrame(String version, Image appIcon) {
        super(Messanger.tr("app.title"));

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        if (appIcon != null) {
            setIconImage(appIcon);
        }

        root.add(loadFileView, CARD_LOAD);

        add(root, BorderLayout.CENTER);

        new LoadProjectCardController(loadFileView, this);

        setSize(1100, 700);
        setLocationRelativeTo(null);
        setResizable(true);

        rootLayout.show(root, CARD_LOAD);
    }

    @Override
    public void goBasinMap(ProjectConfig cfg) {
        SubbasinMapPanel explorerPanel = new SubbasinMapPanel(cfg);
        root.add(explorerPanel, CARD_BASIN_MAP);
        rootLayout.show(root, CARD_BASIN_MAP);

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        revalidate();
        repaint();
    }

    @Override
    public void goSubbasinTable(ProjectConfig cfg) {
        // TODO Auto-generated method stub
    }

    @Override
    public void goDataBaseDetails(ProjectConfig cfg) {
        // TODO Auto-generated method stub
    }

    @Override
    public void goMetadataEditor(ProjectConfig cfg) {
        // TODO Auto-generated method stub
    }
}
