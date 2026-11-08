# CustomTools — Paper 26.2

A starter Paper plugin for Minecraft Java Edition 26.2. It adds:
- A tagged custom 3x3 pickaxe.
- Custom-branded diamond axe and shovel items (their block/tool behaviour is vanilla).
- `/customtools give <player> <pickaxe|axe|shovel|bundle>`.
- Offline Tebex delivery queue: if the player is offline but has joined before, items are delivered on their next join.
- OP-only in-game admin commands and configurable names/lore.
- No crafting recipes are registered; tools can only be obtained through admin commands or Tebex console delivery.

## Important behaviour

The 3x3 pickaxe breaks the 8 surrounding blocks in a plane based on the direction the player is facing. It uses the normal player block-break path so other plugins can still listen to/cancel those block breaks. It may still interact differently with some protection/mining plugins; test on a private test server first.

The axe and shovel are currently custom-named and tagged diamond tools. They do not have extra abilities yet.

## Build on an iPad

## Build on an iPad (automatic build)

The ZIP includes a GitHub Actions workflow. It builds the plugin in the cloud using Java 25, so you do not need to install Java, Gradle or use a terminal on your iPad.

1. Create a **private** GitHub repository.
2. Upload the contents of this ZIP to the repository, keeping the folders in place.
3. Open the repository's **Actions** tab. If GitHub asks, enable workflows.
4. The workflow named **Build CustomTools** runs after upload. You can also choose it and tap **Run workflow**.
5. Open the completed run, scroll to **Artifacts**, and download `CustomTools-Paper-26.2`.
6. Inside the downloaded artifact is `CustomTools-1.0.0.jar`.

Paper 26.2's official setup uses the `26.2.build.+` API dependency and Java 25.

## Install on your Paper server

1. Make a backup of the server.
2. Upload `CustomTools-1.0.0.jar` into the server's `plugins` folder using your host's file manager.
3. Restart the server fully (do not use `/reload`).
4. Check the console for `CustomTools enabled`.
5. Give yourself a test tool: `/customtools give YOURNAME bundle`.

## Tebex setup

Keep the official Tebex Minecraft plugin installed and connected. This plugin does not process payments itself; Tebex runs its commands after a purchase.

Create packages in euros in your Tebex panel, then add these as **console** commands:

- 3x3 Pickaxe: `customtools give {username} pickaxe`
- Custom Axe: `customtools give {username} axe`
- Custom Shovel: `customtools give {username} shovel`
- Tool Bundle: `customtools give {username} bundle`

Tebex documents `{username}` as a universal command variable. Test delivery with Tebex's manual payment/test feature before publishing packages. Never put your Tebex secret key in this plugin or share it publicly.

## Commands and access

There are **no custom crafting recipes**. Players cannot craft these tools.

In-game, only OP players can use:
- `/customtools give <player> <pickaxe|axe|shovel|bundle>`
- `/customtools reload`

The server console can also run these commands, which is required for Tebex console delivery. Non-OP players cannot run the commands.

## Configuration

Edit `plugins/CustomTools/config.yml`, then run `/customtools reload`.
- Change tool names and lore.
- Set `mining.same-block-only: true` if you only want the 3x3 pickaxe to mine blocks matching the centre block.

## Compatibility

Built for Paper 26.2 and Java 25. This is source code; it is not a precompiled JAR. Compile it first, then upload the JAR to the server.
