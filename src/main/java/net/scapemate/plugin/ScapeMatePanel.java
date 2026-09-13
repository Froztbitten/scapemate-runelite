package net.scapemate.plugin;

import java.awt.BorderLayout;
import java.awt.Color;
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
 * Sidebar panel.
 *
 * The checklist exists because every precondition for syncing used to fail
 * silently, which made a working link and a broken one look identical. Each
 * row says whether it is satisfied, so the reason nothing is being sent is
 * always visible rather than inferred.
 */
class ScapeMatePanel extends PluginPanel
{
	private final JLabel pairedRow = new JLabel();
	private final JLabel sendingRow = new JLabel();
	private final JLabel loggedInRow = new JLabel();
	private final JLabel lastSyncRow = new JLabel();
	private final JLabel status = new JLabel(" ");

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

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("ScapeMate");
		title.setFont(title.getFont().deriveFont(16f));
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(title);

		JPanel checklist = new JPanel(new GridLayout(0, 1, 0, 2));
		checklist.setBackground(ColorScheme.DARK_GRAY_COLOR);
		checklist.setAlignmentX(Component.LEFT_ALIGNMENT);
		checklist.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
		for (JLabel row : new JLabel[]{pairedRow, sendingRow, loggedInRow, lastSyncRow})
		{
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			checklist.add(row);
		}
		content.add(checklist);

		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		meleeButton.setFocusPainted(false);
		meleeButton.addActionListener(e -> actions.setLoadout("melee"));
		buttons.add(meleeButton);
		content.add(buttons);

		JLabel hint = new JLabel(
			"<html><body style='width:170px'>Test connection and Sync now live in "
				+ "this plugin's settings.</body></html>");
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setAlignmentX(Component.LEFT_ALIGNMENT);
		hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		content.add(hint);

		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		status.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		content.add(status);

		add(content, BorderLayout.NORTH);
		setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));

		setState(false, false, false, 0L);
	}

	/** Every caller may be on a network thread, so all of this hops to Swing. */
	void setState(boolean paired, boolean sending, boolean loggedIn, long lastSyncAt)
	{
		SwingUtilities.invokeLater(() ->
		{
			mark(pairedRow, paired, "Paired with scapemate.net", "Not paired - paste a code below");
			mark(sendingRow, sending, "Sending is on", "Sending is off - tick it in settings");
			mark(loggedInRow, loggedIn, "Logged in", "Not logged in");

			if (lastSyncAt > 0)
			{
				long secondsAgo = (System.currentTimeMillis() - lastSyncAt) / 1000;
				mark(lastSyncRow, true, "Last sync " + describe(secondsAgo), "");
			}
			else
			{
				mark(lastSyncRow, false, "", "Nothing synced yet");
			}
		});
	}

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

	private static void mark(JLabel row, boolean ok, String okText, String failText)
	{
		row.setText((ok ? "✓  " : "✗  ") + (ok ? okText : failText));
		row.setForeground(ok
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.LIGHT_GRAY_COLOR);
	}

	private static String describe(long secondsAgo)
	{
		if (secondsAgo < 60)
		{
			return secondsAgo + "s ago";
		}
		if (secondsAgo < 3600)
		{
			return (secondsAgo / 60) + "m ago";
		}
		return (secondsAgo / 3600) + "h ago";
	}
}
