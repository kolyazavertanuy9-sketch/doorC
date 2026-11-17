# DoorC Launcher

DoorC Launcher is a Windows desktop application with a custom dark interface, DoorC branding,
and a stable Windows installer identity, so future releases upgrade the installed launcher
rather than creating a second app.
The first-run setup asks where game data should be stored; the launcher installer separately
lets the user choose the application install folder. Both regular Minecraft instances and the
DoorC profile are kept under the selected game-data folder and use separate instance directories.

## Run

From the project root:

```text
gradle :doorc-launcher:run
```

The launcher loads official Minecraft release and snapshot/legacy catalogs and shows
available Fabric, Forge, NeoForge, and Quilt versions. Standard installations go to
`<selected-game-folder>\instances\standard\<instance-name>`. DoorC is installed separately
to `<selected-game-folder>\instances\DoorC-1.21.11`; that instance uses Fabric and includes
the built `wvDoorClient` and `WVlib` jars.

## Mods, maps and texture packs

Open **MODS & MORE**, choose an installed profile, then search Modrinth or CurseForge.
Modrinth supports mods and resource packs here; its public API does not expose a maps/worlds
project type, so maps are searched on CurseForge. CurseForge requires a developer API key
from its [Studios console](https://console.curseforge.com/). Configure it as the
`CURSEFORGE_API_KEY` environment variable on the Windows account that runs the launcher,
then restart the launcher. Do not commit or share the key.

The browser filters by the selected profile's Minecraft version and mod loader. It installs
mods into that profile's `mods` folder, texture packs into `resourcepacks`, and safely extracts
world ZIP files into `saves`. Required mod dependencies are not installed automatically; add
them separately through the same browser. CurseForge projects that do not permit third-party
downloads are shown but cannot be installed.

The launcher supports offline profiles and Ely.by accounts. Offline profiles use the
chosen nickname and its standard offline UUID; the nickname is saved locally. They work
in single-player and on servers that allow offline profiles, but do not authenticate with
Microsoft or Ely.by online servers.

Ely.by sign-in supports two-factor authentication. Credentials are sent to Ely.by over
HTTPS and are not saved; the access token stays in memory for the current launcher session.
When launching an Ely.by account, the launcher downloads the official authlib-injector
release and verifies its GitHub-published SHA-256 digest. Ely.by accounts work with
Ely.by-compatible servers, not official Microsoft-authenticated servers. Microsoft sign-in
remains unavailable until a registered and Minecraft-approved Microsoft application ID
is configured.

## Launcher updates

The launcher checks the latest stable GitHub Release on startup and also provides a manual
check button. It only downloads an update after confirmation, verifies the published SHA-256
checksum, then asks before closing the launcher and starting the installer.

The release repository is `kolyazavertanuy9-sketch/doorC`. It currently has no published
stable releases, so the update check will report that the channel is not published yet.
Pushing a `launcher-vMAJOR.MINOR.PATCH` tag starts
[`release-launcher.yml`](../.github/workflows/release-launcher.yml), which builds the Windows
installer and publishes it with a checksum. The next update version must be greater than the
installed version.

SHA-256 detects incomplete or corrupted downloads; it is not a publisher signature. The
installer is currently unsigned, so Windows may show an unknown-publisher or SmartScreen
warning. Do not treat the checksum as a replacement for Authenticode signing.

Before broad public distribution, configure a trusted code-signing certificate or
[Microsoft Artifact Signing](https://learn.microsoft.com/en-us/azure/artifact-signing/overview)
for the installer and launcher binaries. Keep signing keys in a managed signing service or
hardware-backed store; never commit private keys to this repository. See Microsoft's
[Smart App Control signing guidance](https://learn.microsoft.com/en-us/windows/apps/develop/smart-app-control/code-signing-for-smart-app-control).

## Build and package

```text
gradlew.bat :doorc-launcher:build
```

Build a self-contained app image with the JDK's `jlink` and `jpackage`. The bundled runtime
includes `java.exe`, which is required to run Fabric and other loader installers:

```text
gradlew.bat :doorc-launcher:launcherAppImage
```

Build the Windows installer EXE (requires WiX Toolset 3 on `PATH`):

```text
gradlew.bat :doorc-launcher:launcherExe
```

The app image is written to `doorc-launcher/build/launcher-app-image`; the EXE installer is
written to `doorc-launcher/build/launcher-package`. The installer provides an
installation-folder picker, Start Menu entry, and optional desktop shortcut. The launcher asks
for the separate game-data location on first run. Game data must remain outside the launcher
installation folder. If an older installation has game data inside that folder, the updater
asks to copy it to a safe location before starting the installer; the original files remain
untouched by the copy.

The distributable ZIP is created in `doorc-launcher/build/distributions`.

For a network-backed metadata smoke check:

```text
gradlew.bat :doorc-launcher:run --args=--catalog-check
```
