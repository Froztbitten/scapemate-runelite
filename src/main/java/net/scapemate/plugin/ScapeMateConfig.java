package net.scapemate.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ScapeMateConfig.GROUP)
public interface ScapeMateConfig extends Config
{
	String GROUP = "scapemate";

	@ConfigSection(
		name = "Account link",
		description = "Pair this client with your scapemate.net account",
		position = 0
	)
	String linkSection = "link";

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Send my data to scapemate.net",
		description =
			"WARNING: this sends data to scapemate.net, a third-party server not run by Jagex or RuneLite. " +
			"While enabled it sends your display name, your worn equipment item IDs, and your combat skill " +
			"levels (Attack, Strength, Defence, Ranged, Magic, Hitpoints, Prayer), so the site's DPS " +
			"calculator can fill itself in. Nothing else is sent: no chat, no location, no inventory, no " +
			"bank. Data is sent only while this is ticked and the plugin is paired.",
		section = linkSection,
		position = 1
	)
	default boolean syncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pairingCode",
		name = "Pairing code",
		description =
			"Paste the code from scapemate.net/connect. It is exchanged for a token once, then cleared.",
		section = linkSection,
		position = 2
	)
	default String pairingCode()
	{
		return "";
	}

	@ConfigItem(
		keyName = "pluginToken",
		name = "",
		description = "",
		section = linkSection,
		hidden = true
	)
	default String pluginToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "testConnection",
		name = "Test connection",
		description =
			"Tick to check that this client can reach scapemate.net and that its "
			+ "pairing is still valid. Reports in the chat box, then unticks itself.",
		section = linkSection,
		position = 4
	)
	default boolean testConnection()
	{
		return false;
	}

	@ConfigItem(
		keyName = "syncNow",
		name = "Sync gear and levels now",
		description =
			"Tick to send your worn equipment and combat levels immediately, rather "
			+ "than waiting for them to change. Unticks itself when done.",
		section = linkSection,
		position = 5
	)
	default boolean syncNow()
	{
		return false;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "syncIntervalMinutes",
		name = "Minutes between syncs",
		description =
			"How long changes are gathered up before one update is sent. Lower is "
			+ "closer to live, but every update is a request against scapemate.net's "
			+ "hosting bill, so the default is deliberately relaxed.",
		section = linkSection,
		position = 6
	)
	default int syncIntervalMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "API base URL",
		description = "Only change this if you are running your own ScapeMate backend.",
		section = linkSection,
		position = 3
	)
	default String apiBaseUrl()
	{
		return "https://api-zwkgmxtwca-uc.a.run.app/api";
	}
}
