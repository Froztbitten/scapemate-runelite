# ScapeMate RuneLite plugin

Companion plugin for [scapemate.net](https://scapemate.net). While enabled and
paired, it sends your worn equipment and combat levels to the site so the DPS
calculator fills itself in as you play.

## What it sends

Only while **Send my data to scapemate.net** is ticked *and* the plugin is
paired:

- your display name
- your worn equipment item IDs
- your combat levels: Attack, Strength, Defence, Ranged, Magic, Hitpoints, Prayer

Nothing else. No chat, no location, no inventory, no bank. scapemate.net is a
third-party site, not run by Jagex or RuneLite.

## Setup

1. Sign in at <https://scapemate.net/connect> and generate a pairing code.
2. Paste it into the plugin's **Pairing code** setting in RuneLite.
3. Tick **Send my data to scapemate.net**.

The code is single use and expires after ten minutes. It is exchanged for a
token and then cleared from your settings. Revoke access any time from the
Connect page.

## Building

```bash
./gradlew build
```

Requires JDK 11 (the Plugin Hub build target). The Gradle toolchain will fetch
one if your default JDK is newer.

## Running it in RuneLite

```bash
./gradlew shadowJar
```

Then launch RuneLite with the plugin on the classpath, or use the RuneLite
developer tools' "sideload" path. See
<https://github.com/runelite/plugin-hub#testing-your-plugin>.
