package net.scapemate.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Talks to the ScapeMate backend. Every call is asynchronous: RuneLite's game
 * thread must never block on network IO.
 */
@Singleton
class ScapeMateClient
{
	private static final Logger log = LoggerFactory.getLogger(ScapeMateClient.class);

	private static final MediaType JSON = MediaType.parse("application/json");

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	ScapeMateClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	interface RedeemCallback
	{
		void onToken(String token);

		void onError(String message);
	}

	/** Exchanges a one-time pairing code for a long-lived plugin token. */
	void redeem(String baseUrl, String code, RedeemCallback callback)
	{
		JsonObject body = new JsonObject();
		body.addProperty("code", code);

		Request request = new Request.Builder()
			.url(baseUrl + "/plugin/redeem")
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				callback.onError("Could not reach scapemate.net: " + e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					String payload = res.body() == null ? "" : res.body().string();
					JsonObject parsed = gson.fromJson(payload, JsonObject.class);

					if (!res.isSuccessful() || parsed == null || !parsed.has("token"))
					{
						String error = parsed != null && parsed.has("error")
							? parsed.get("error").getAsString()
							: "Pairing failed (" + res.code() + ")";
						callback.onError(error);
						return;
					}

					callback.onToken(parsed.get("token").getAsString());
				}
				catch (Exception e)
				{
					callback.onError("Unexpected response: " + e.getMessage());
				}
			}
		});
	}

	/**
	 * Pushes the current loadout. Failures are logged rather than surfaced: a
	 * sync runs on every equipment change and must not nag the player.
	 */
	void sync(String baseUrl, String token, LoadoutSnapshot snapshot)
	{
		Request request = new Request.Builder()
			.url(baseUrl + "/plugin/sync")
			.header("Authorization", "Bearer " + token)
			.post(RequestBody.create(JSON, gson.toJson(snapshot)))
			.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("ScapeMate sync failed", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response res = response)
				{
					if (!res.isSuccessful())
					{
						log.debug("ScapeMate sync rejected: {}", res.code());
					}
				}
			}
		});
	}

	/** Wire format for {@code POST /api/plugin/sync}. */
	static class LoadoutSnapshot
	{
		String playerName;
		Map<String, Integer> levels;
		java.util.List<EquippedItem> equipment;

		LoadoutSnapshot(String playerName, Map<String, Integer> levels, java.util.List<EquippedItem> equipment)
		{
			this.playerName = playerName;
			this.levels = levels;
			this.equipment = equipment;
		}
	}

	static class EquippedItem
	{
		String slot;
		int itemId;

		EquippedItem(String slot, int itemId)
		{
			this.slot = slot;
			this.itemId = itemId;
		}
	}
}
