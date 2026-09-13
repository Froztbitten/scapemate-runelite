package net.scapemate.plugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Development launcher. Starts RuneLite with this plugin loaded as if it had
 * come from the Plugin Hub, so it can be tested before submission.
 *
 * Run it with {@code ./gradlew run}.
 */
public class ScapeMatePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ScapeMatePlugin.class);
		RuneLite.main(args);
	}
}
