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

	private NavigationButton navButton;
	private ScapeMatePanel panel;

	private long lastSyncAt;
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
			panel.setStatus("Not paired yet. Add a code from scapemate.net/connect.", true);
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			panel.setStatus("Log in first.", true);
			return;
		}

		ScapeMateClient.LoadoutSnapshot snapshot = buildSnapshot();
		if (snapshot.equipment.isEmpty())
		{
			panel.setStatus("You are not wearing anything.", true);
			return;
		}

		panel.setBusy(true);
		panel.setStatus("Sending...", false);

		api.setLoadout(config.apiBaseUrl(), token, combatStyle, snapshot,
			new ScapeMateClient.ResultCallback()
			{
				@Override
				public void onSuccess()
				{
					panel.setBusy(false);
					panel.setStatus("Saved " + snapshot.equipment.size()
						+ " items as your " + combatStyle + " loadout.", false);
				}

				@Override
				public void onError(String message)
				{
					panel.setBusy(false);
					panel.setStatus(message, true);
				}
			});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Force the next sync through: this is a different character or session.
			lastPayloadDigest = null;
			redeemPairingCodeIfPresent();
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
				lastPayloadDigest = null;
				maybeSync();
			}

			@Override
			public void onError(String message)
			{
				log.warn("ScapeMate: pairing failed - {}", message);
			}
		});
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
		api.sync(config.apiBaseUrl(), token, snapshot);
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
