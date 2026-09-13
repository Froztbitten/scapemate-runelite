package net.scapemate.plugin;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel. Copying gear into a loadout is a button rather than part of
 * the automatic sync, so the plugin never overwrites a loadout the player
 * built by hand without being asked.
 */
class ScapeMatePanel extends PluginPanel
{
	private final JLabel status = new JLabel(" ");
	private final JButton meleeButton = new JButton("Set equipped as current melee loadout");

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

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("ScapeMate");
		title.setFont(title.getFont().deriveFont(16f));
		title.setForeground(java.awt.Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(title);

		JLabel blurb = new JLabel(
			"<html><body style='width:170px'>Copy what you are wearing into a "
				+ "loadout on scapemate.net.</body></html>");
		blurb.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		blurb.setAlignmentX(Component.LEFT_ALIGNMENT);
		blurb.setBorder(BorderFactory.createEmptyBorder(6, 0, 10, 0));
		content.add(blurb);

		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

		meleeButton.setFocusPainted(false);
		meleeButton.addActionListener(e -> actions.setLoadout("melee"));
		buttons.add(meleeButton);
		content.add(buttons);

		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		status.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		content.add(status);

		add(content, BorderLayout.NORTH);
		setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));
	}

	/** Safe to call from a network callback; hops to the Swing thread itself. */
	void setStatus(String message, boolean error)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText("<html><body style='width:170px'>" + message + "</body></html>");
			status.setForeground(error
				? ColorScheme.PROGRESS_ERROR_COLOR
				: ColorScheme.PROGRESS_COMPLETE_COLOR);
		});
	}

	void setBusy(boolean busy)
	{
		SwingUtilities.invokeLater(() -> meleeButton.setEnabled(!busy));
	}
}
