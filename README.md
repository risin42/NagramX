# Nagram X

A variant of [Nagram](https://github.com/NextAlone/Nagram) with additional features.

## Download

You can grab the latest versions in these two ways:

*   **CI Channel:** [https://t.me/NagramX](https://t.me/NagramX)
*   **GitHub Actions Artifacts:**  You can also download artifacts from the [GitHub Actions](https://github.com/risin42/NagramX/actions/workflows/staging.yml) page

## NagramX Changes
- **Additional Features**
  - AI Translator
  - AI Transcription
  - Hide the Premium and Help sections in settings
  - Hide side share button
  - Avoids requesting camera permission when selecting images
  - Ask before sending bot command
  - Ask before opening inline links
  - Translate Entire Chats
  - Center title in chats
  - Custom drawer elements
  - Remove Archived Chats from dialog list
  - Use icons instead of "deleted"/"edited"
  - Save attachments by chat name
  - Send messages silently by default
  - Use folder name as title
  - Spring Animations
  - Disable message background color and emojis
  - Custom chat name
  - Hide Reactions
  - Hide gift button
  - Hide dividers
  - Solar icons
  - Tab style (Default, Pure, Pills)
  - Admin shortcuts in chats
  - Left button action (NoQuote forward, Reply, Save, Direct Share)
  - And more ?

----
> [!NOTE]
> Some changes of Nagram and NekoX have been removed

----
<details> 
<summary><strong>Nagram Changes</strong></summary>

1. Nice icon (thanks to MaitungTM)
2. Combine message
3. Editable text style
4. Forced copy
5. Invert reply
6. Quick reply in longClick menu (thanks to @blxueya)
7. Undo and Redo
8. Scrollable chat preview (thanks to TeleTux)
9. Noise suppress and voice enhance
</details>

----
<details>
<summary><strong>NekoX Changes</strong></summary>

- Most of Nekogram's features
- Unlimited login accounts
- **Proxy**
  - Built-in VMess, Shadowsocks, SSR, Trojan-GFW proxies support (No longer maintained)
  - Built-in public proxy (WebSocket relay via Cloudflare CDN)
  - Proxy subscription support
  - Ipv6 MTProxy support
  - Able to parse all proxy subscription format: SIP008, ssr, v2rayN, vmess1, shit ios app formats, clash config and more
  - Proxies import and export, remarks, speed measurement, sorting, delete unusable nodes, etc
  - Scan the QR code (any link, can add a proxy)
  - The ( vmess / vmess1 / ss / ssr / trojan ) proxy link in the message can be clicked
  - Allow auto-disabling proxy when VPN is enabled
  - Proxy automatic switcher
  - Don't alert "Proxy unavailable" for non-current account
- **Stickers**
  - Custom
  - Add stickers without sticker pack
  - Sticker set list backup / restore / share
- **Internationalization**
  - OpenCC Chinese Convert
  - Full InstantView translation support
  - Translation support for selected text on input and in messages
  - Google Cloud Translate / Yandex.Translate support
  - Force English emoji keywords to be loaded
  - Persian calendar support
- **Additional Options**
  - Option to disable vibration
  - Dialog sorting is optional "Unread and can be prioritized for reminding" etc
  - Option to skip "regret within five seconds"
  - Option to not send comment first when forwarding
  - Option to use nekox chat input menu: replace record button with a menu which contains an switch to control link preview (enabled by default)
  - Option to disable link preview by default: to prevent the server from knowing that the link is shared through Telegram.
  - Option to ignore Android-only content restrictions (except for the Play Store version).
  - Custom cache directory (supports external storage)
  - Custom server (official, test DC)
  - Option to block others from starting a secret chat with you
  - Option to disable trending
- **Additional Actions**
  - Allow clicking on links in self profile
  - Delete all messages in group
  - Unblock all users support
  - Login via QR code
  - Scan and confirm the login QR code directly
  - Allow clearing app data
  - Proxies, groups, channels, sticker packs are able to be shared as QR codes
  - Add "@Name" when long-pressing @user option
  - Allow creating a group without inviting anyone
  - Allow upgrading a group to a supergroup
  - Mark dialogs as read using tab menu
  - Enabled set auto delete timer option for private chats and private groups
  - Support saving multiple selected messages to Saved Messages
  - Support unpinning multiple selected messages
  - View stats option for messages
- **Optimization**
  - Keep the original file name when downloading files
  - View the data center you belong to when you don't have an avatar
  - Enhanced notification service, optional version without Google Services
  - Improved session dialog
  - Improved link long click menu
  - Improved hide messages from blocked users feature
  - Don't process cleanup draft events after opening chat
- **Others**
  - OpenKeychain client (sign / verify / decrypt / import)
  - Text replacer
- **UI**
  - Telegram X style menu for unpinning messages
  - Built-in Material Design themes / Telegram X style icons
- And more :)
</details>

----
## Compilation Guide

**NOTE: For Windows users, please consider using a Linux VM (such as WSL2) or dual booting.**

Environment:

- Linux distribution based on Debian or Arch Linux, or macOS

- Native tools: `gcc` `go` `make` `cmake` `ninja` `yasm` `meson` `pkgconf`

  ```shell
  # for Debian based distribution
  sudo apt install gcc golang make cmake ninja-build yasm
  # for Arch Linux based distribution
  sudo pacman -S base-devel go ninja cmake yasm meson
  # for macOS
  xcode-select --install # install developer tools (will open confirm dialog)
  brew install go cmake ninja yasm meson pkgconf # install other tools by homebrew
  ```
- Android SDK: `build-tools;33.0.0` `platforms;android-33` `ndk;21.4.7075529` `cmake;3.18.1` `cmake;3.22.1` (the default location is **$HOME/Android/SDK**, otherwise you need to specify **$ANDROID_HOME** for it)

  It is recommended to use [Android Studio](https://developer.android.com/studio) to install, but you can also use `sdkmanager` command on distributions based on Debian:

  ```shell
  sudo apt install sdkmanager
  sdkmanager --sdk_root $HOME/Android/SDK --install "build-tools;33.0.0" "platforms;android-33" "ndk;21.4.7075529" "cmake;3.18.1" "cmake;3.22.1"
  ```

Build:

1. Checkout submodules

   ```shell
   git submodule update --init --recursive
   ```

2. Build native dependencies:
   ```shell
   ./run init libs
   ```

3. Build external libraries and native code:
   ```shell
   ./run libs native
   ```

4. Fill out `TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH` in **local.properties** (from [Telegram Developer](https://my.telegram.org/auth))

5. Replace **TMessagesProj/google-services.json** if you want FCM to work.

6. Replace **release.keystore** with yours and fill out `ALIAS_NAME`, `KEYSTORE_PASS` and `ALIAS_PASS` in **local.properties**.

7. Build with Gradle:

   ```shell
   ./gradlew assemble<Release/Staging/Debug>
   ```

----

## Compilation with GitHub Action

1. Create your own `release.keystore` to replace `TMessagesProj/release.keystore`.

2. Prepare LOCAL_PROPERTIES

- KEYSTORE_PASS: from your keystore
- ALIAS_NAME: from your keystore
- ALIAS_PASS: from your keystore
- TELEGRAM_APP_ID: from [Telegram Developer](https://my.telegram.org/auth)
- TELEGRAM_APP_HASH: from [Telegram Developer](https://my.telegram.org/auth)

```env
KEYSTORE_PASS=123456
ALIAS_NAME=key0
ALIAS_PASS=123456
TELEGRAM_APP_ID=123456
TELEGRAM_APP_HASH=abcdefg
```

Then, use base64 to encode the above.

3. Add Repo Action Secrets

- LOCAL_PROPERTIES: from step 2
- HELPER_BOT_TOKEN: from telegram [@Botfather](https://t.me/Botfather), such as `1111:abcd`
- HELPER_BOT_TARGET: from telegram chat id, such as `777000`

4. Run Release Build

## Thanks to

- [AyuGram](https://github.com/AyuGram/AyuGram4A)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [Dr4iv3rNope](https://github.com/Dr4iv3rNope/NotSoAndroidAyuGram)
- [Nagram](https://github.com/NextAlone/Nagram)
- [OctoGram](https://github.com/OctoGramApp/OctoGram)
