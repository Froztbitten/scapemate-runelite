package net.scapemate.plugin;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Minimal sidebar panel for enabling the equipped melee loadout. */
class ScapeMatePanel extends PluginPanel
{
	private final JButton meleeButton = new JButton("Set equipped as melee loadout");

	interface Actions
	{
		void setLoadout(String combatStyle);
	}

	ScapeMatePanel(Actions actions)
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		meleeButton.setFocusPainted(false);
		meleeButton.addActionListener(e -> actions.setLoadout("melee"));
		add(meleeButton, BorderLayout.NORTH);
		setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));
	}

	void setBusy(boolean busy)
	{
		SwingUtilities.invokeLater(() -> meleeButton.setEnabled(!busy));
	}
}
