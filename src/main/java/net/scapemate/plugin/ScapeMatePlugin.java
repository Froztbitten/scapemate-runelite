package net.scapemate.plugin;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "ScapeMate",
	description = "Send your gear and levels to scapemate.net's DPS calculator",
	tags = {"dps", "calculator", "gear", "loadout", "companion"}
)
public class ScapeMatePlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(ScapeMatePlugin.class);

	/** Combat skills are all the DPS calculator uses; nothing else is read. */
	private static final Skill[] TRACKED_SKILLS = {
		Skill.ATTACK,
		Skill.STRENGTH,
		Skill.DEFENCE,
		Skill.RANGED,
		Skill.MAGIC,
		Skill.HITPOINTS,
		Skill.PRAYER,
	};

	/** The server rejects anything faster; batch bursts of equipment changes. */
	private static final long MIN_SYNC_INTERVAL_MS = 2500;

	@Inject
	private Client client;

	@Inject
	private ScapeMateConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ScapeMateClient api;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClientThread clientThread;

	private NavigationButton navButton;
	private ScapeMatePanel panel;

	private long lastSyncAt;
	private long lastSuccessfulSyncAt;
	private String lastPayloadDigest;

	@Provides
	ScapeMateConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ScapeMateConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new ScapeMatePanel(this::pushLoadout);

		navButton = NavigationButton.builder()
			.tooltip("ScapeMate")
			.icon(ImageUtil.loadImageResource(ScapeMatePlugin.class, "icon.png"))
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		refreshPanel();
		redeemPairingCodeIfPresent();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		lastPayloadDigest = null;
	}

	/**
	 * Sends the worn equipment to the site as the named loadout. Reports back
	 * to the panel either way: this one is user-initiated, so silence would be
	 * indistinguishable from the button not working.
	 */
	private void pushLoadout(String combatStyle)
	{
		String token = config.pluginToken();
		if (token == null || token.isEmpty())
		{
			report("Not paired yet. Paste a code from scapemate.net/connect.", true);
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			report("Log in first.", true);
			return;
		}

		panel.setBusy(true);
		clientThread.invoke(() -> sendLoadout(combatStyle, token));
	}

	private void sendLoadout(String combatStyle, String token)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			panel.setBusy(false);
			report("Log in first.", true);
			return;
		}

		ScapeMateClient.LoadoutSnapshot snapshot = buildSnapshot();
		if (snapshot.equipment.isEmpty())
		{
			panel.setBusy(false);
			report("You are not wearing anything.", true);
			return;
		}

		report("Sending...", false);

		api.setLoadout(config.apiBaseUrl(), token, combatStyle, snapshot,
			new ScapeMateClient.ResultCallback()
			{
				@Override
				public void onSuccess()
				{
					panel.setBusy(false);
					report("Saved " + snapshot.equipment.size()
						+ " items as your " + combatStyle + " loadout.", false);
				}

				@Override
				public void onError(String message)
				{
					panel.setBusy(false);
					report(message, true);
				}
			});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		refreshPanel();
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Force the next sync through: this is a different character or session.
			lastPayloadDigest = null;
			redeemPairingCodeIfPresent();
		}
	}

	/**
	 * The pairing code is typed into the settings panel while the plugin is
	 * already running, so start-up and login are both too early to notice it.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ScapeMateConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		refreshPanel();
		if ("pairingCode".equals(event.getKey()))
		{
			redeemPairingCodeIfPresent();
		}
		else if ("testConnection".equals(event.getKey()) && config.testConnection())
		{
			// RuneLite config has no button type, so these are checkboxes that
			// run the action and untick themselves.
			configManager.setConfiguration(ScapeMateConfig.GROUP, "testConnection", false);
			testConnection();
		}
		else if ("syncNow".equals(event.getKey()) && config.syncNow())
		{
			configManager.setConfiguration(ScapeMateConfig.GROUP, "syncNow", false);
			forceSync();
		}
		else if ("syncEnabled".equals(event.getKey()) && config.syncEnabled())
		{
			// Turning sync on should push straight away rather than waiting
			// for the player to happen to change gear.
			lastPayloadDigest = null;
			maybeSync();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.WORN)
		{
			maybeSync();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		for (Skill skill : TRACKED_SKILLS)
		{
			if (skill == event.getSkill())
			{
				maybeSync();
				return;
			}
		}
	}

	/**
	 * Trades the pairing code for a token, then clears the code from config so
	 * a spent code is not left sitting in the settings panel.
	 */
	private void redeemPairingCodeIfPresent()
	{
		String code = config.pairingCode();
		if (code == null || code.trim().isEmpty())
		{
			return;
		}

		api.redeem(config.apiBaseUrl(), code.trim(), new ScapeMateClient.RedeemCallback()
		{
			@Override
			public void onToken(String token)
			{
				configManager.setConfiguration(ScapeMateConfig.GROUP, "pluginToken", token);
				configManager.setConfiguration(ScapeMateConfig.GROUP, "pairingCode", "");
				log.info("ScapeMate: paired successfully");
				if (panel != null)
				{
					refreshPanel();
					report(config.syncEnabled()
						? "Paired. Sending your gear..."
						: "Paired. Now tick \"Send my data to scapemate.net\" in settings.", false);
				}
				lastPayloadDigest = null;
				// Use the token we were just handed: reading it back through
				// the config proxy can race with the write above.
				pushSnapshot(token, true);
			}

			@Override
			public void onError(String message)
			{
				log.warn("ScapeMate: pairing failed - {}", message);
				if (panel != null)
				{
					refreshPanel();
					report(message, true);
				}
			}
		});
	}

	/**
	 * Mirrors status into the chat box as well as the panel. The action
	 * controls now live in settings, so the side panel may not be open.
	 */
	private void report(String message, boolean error)
	{
		if (panel != null)
		{
			panel.setStatus(message, error);
		}

		if (error)
		{
			log.warn("ScapeMate: {}", message);
		}
		else
		{
			log.info("ScapeMate: {}", message);
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		chatMessageManager.queue(QueuedMessage.builder()
			.type(net.runelite.api.ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("[ScapeMate] ")
				.append(ChatColorType.NORMAL)
				.append(message)
				.build())
			.build());
	}

	/** Redraws the checklist from the current config and game state. */
	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}
		panel.setState(
			hasToken(),
			config.syncEnabled(),
			client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null,
			lastSuccessfulSyncAt);
	}

	/**
	 * Verifies the token against the server without needing game state, so a
	 * broken link can be told apart from a sync that had nothing to send.
	 */
	private void testConnection()
	{
		refreshPanel();

		if (!hasToken())
		{
			report("Not paired. Paste a code from scapemate.net/connect.", true);
			return;
		}

		panel.setBusy(true);
		report("Contacting " + config.apiBaseUrl() + "...", false);

		api.ping(config.apiBaseUrl(), config.pluginToken(),
			new ScapeMateClient.ResultCallback()
			{
				@Override
				public void onSuccess()
				{
					panel.setBusy(false);
					report("Connection OK. The server accepted this token.", false);
				}

				@Override
				public void onError(String message)
				{
					panel.setBusy(false);
					report(message, true);
				}
			});
	}

	private boolean hasToken()
	{
		String token = config.pluginToken();
		return token != null && !token.isEmpty();
	}

	/**
	 * Pushes the current gear and levels regardless of the change detection,
	 * so the player can confirm the link is working without swapping gear.
	 */
	private void forceSync()
	{
		if (!hasToken())
		{
			report("Not paired yet. Paste a code from scapemate.net/connect.", true);
			return;
		}

		// The data disclosure lives on that toggle, so nothing leaves the
		// client until it is ticked - a button press is not a substitute.
		if (!config.syncEnabled())
		{
			report("Tick \"Send my data to scapemate.net\" in settings first.", true);
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			report("Log in first.", true);
			return;
		}

		report("Syncing...", false);
		// Bypasses the change detection: the point is to confirm the link.
		lastPayloadDigest = null;
		pushSnapshot(config.pluginToken(), true);
	}

	/** Sends the current loadout, unless it is unchanged or too soon. */
	private void maybeSync()
	{
		if (!config.syncEnabled())
		{
			return;
		}

		String token = config.pluginToken();
		if (token == null || token.isEmpty())
		{
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}

		ScapeMateClient.LoadoutSnapshot snapshot = buildSnapshot();
		String digest = String.valueOf(snapshot.levels) + snapshot.equipment.size()
			+ equipmentDigest(snapshot.equipment);

		// Equipment and stat events fire in bursts; only send real changes.
		if (digest.equals(lastPayloadDigest))
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastSyncAt < MIN_SYNC_INTERVAL_MS)
		{
			return;
		}

		lastSyncAt = now;
		lastPayloadDigest = digest;
		pushSnapshot(token, false);
	}

	/**
	 * The single place a snapshot is sent. `report` drives whether the panel is
	 * updated: the automatic sync stays quiet, but anything the player asked
	 * for has to say what happened.
	 */
	private void pushSnapshot(String token, boolean report)
	{
		if (!config.syncEnabled() || token == null || token.isEmpty())
		{
			return;
		}

		// getItemContainer and getLocalPlayer are only valid on the game thread.
		// Called from a config change or a button they can return null, which
		// is indistinguishable from being logged out.
		clientThread.invoke(() -> sendSnapshot(token, report));
	}

	private void sendSnapshot(String token, boolean report)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			if (report)
			{
				report("Log in and it will sync.", true);
			}
			return;
		}

		ScapeMateClient.LoadoutSnapshot snapshot = buildSnapshot();
		lastSyncAt = System.currentTimeMillis();

		api.sync(config.apiBaseUrl(), token, snapshot,
			new ScapeMateClient.ResultCallback()
			{
				@Override
				public void onSuccess()
				{
					lastSuccessfulSyncAt = System.currentTimeMillis();
					refreshPanel();
					if (report)
					{
						report("Synced " + snapshot.equipment.size()
							+ " items and " + snapshot.levels.size() + " levels.", false);
					}
				}

				@Override
				public void onError(String message)
				{
					// Always surface this. A silent failure here is exactly
					// what made pairing look like it did nothing.
					report(message, true);
				}
			});
	}

	private ScapeMateClient.LoadoutSnapshot buildSnapshot()
	{
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Skill skill : TRACKED_SKILLS)
		{
			levels.put(skill.getName(), client.getRealSkillLevel(skill));
		}

		List<ScapeMateClient.EquippedItem> equipment = new ArrayList<>();
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
			{
				Item item = worn.getItem(slot.getSlotIdx());
				if (item != null && item.getId() > 0)
				{
					equipment.add(new ScapeMateClient.EquippedItem(slot.name(), item.getId()));
				}
			}
		}

		String name = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
		return new ScapeMateClient.LoadoutSnapshot(name, levels, equipment);
	}

	private static String equipmentDigest(List<ScapeMateClient.EquippedItem> equipment)
	{
		StringBuilder sb = new StringBuilder();
		for (ScapeMateClient.EquippedItem item : equipment)
		{
			sb.append(item.slot).append(':').append(item.itemId).append(',');
		}
		return sb.toString();
	}
}
