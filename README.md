# 🚀 SkBridge v1.0 - Initial Release

**SkBridge** is a high-performance bridge between **Skript** and **LuckPerms**, specifically designed for modern Minecraft networks. It solves the well-known issue of flickering or "ghost" prefixes in the tablist and fully automates team management via Vanilla scoreboards.

---

## ✨ Features

- **Ghost-Free Team Management:** Manage Vanilla scoreboard teams with absolute stability and zero "ghost" entries
- **Automatic Sorting:** Players are automatically sorted in the tablist based on their LuckPerms group `weight`
- **Collision Handling:** Player collision is automatically disabled to ensure smoother gameplay
- **Real-Time Sync:** Changes to LuckPerms data are detected and processed in real-time
- **Color Support:** Automatic team coloring based on LuckPerms metadata
- **Votifier Integration:** Built-in Votifier listener with RSA-2048 encryption support
- **Vote Tracking:** Offline player vote queuing - votes are stored and triggered when players join
- **Automatic Key Generation:** Auto-generates RSA keys for secure vote communications

---

## 📋 Requirements

- **Minecraft Version:** 1.13 or newer
- **Skript:** 2.6+
- **LuckPerms:** 5.0+

---

## ⚙️ Installation

1. Download the `SkBridge-1.0.jar` file from the releases
2. Place it into your server's `/plugins` folder
3. Restart your server
4. *(Optional)* Add the `color` meta to your LuckPerms groups:
   ```
   /lp group <groupname> meta set color <color>
   ```

---

## 🛠 Skript API

SkBridge expands your Skript capabilities with events, expressions, and effects for seamless LuckPerms integration.

### 1. Events

Two events are available for use in your Skript scripts:

#### Vote Event
Triggered in real-time whenever a player receives a vote (online or when joining):

```skript
on vote:
    broadcast "%player% received a vote!"
    add 1 to {votes::%player%}
```

#### LuckPerms Group Update Event
Triggered in real-time whenever a player's LuckPerms data changes (group, permissions, metadata):

```skript
on luckperms group update:
    set {_group} to luckperms group of event-player
    broadcast "%event-player% is now in the %{_group}% group!"
```

### 2. Expressions

Fetch LuckPerms metadata directly in your scripts:

- `%luckperms prefix of player%` - Get the player's prefix
- `%luckperms suffix of player%` - Get the player's suffix
- `%luckperms group of player%` - Get the player's primary group
- `%luckperms display name of group of player%` - Get the display name of the player's group
- `%luckperms weight of player%` - Get the player's group weight (integer)

**Example:**
```skript
set {_prefix} to luckperms prefix of player
set {_suffix} to luckperms suffix of player
set {_group} to luckperms group of player
set {_weight} to luckperms weight of player
set {_displayname} to luckperms display name of group of player
```

### 3. Effects

#### Set Name Tag Effect

Set the tablist name and automatically create a colored Vanilla team:

```skript
set name tag of player to "§8[§fAdmin§8] §c%name of player%"
```

**How it works:**
- Automatically creates a Vanilla team based on the player's LuckPerms group
- Splits the provided format into prefix and suffix (based on player name position)
- Sets team color based on the LuckPerms meta `color`
- Disables player collision (NEVER mode)
- Sorts players by group weight (higher weight = top of tablist)
- Cleans up "ghost" entries automatically

**Name Format Tips:**
- Include the player's name in the format string: `"§8[§fAdmin§8] §c%name of player%"`
- Everything before the name becomes the prefix
- Everything after the name becomes the suffix
- Maximum length for prefix and suffix: 64 characters each

---

## 🎨 Valid Team Colors

You can use either the full name or the color code as the value in LuckPerms:

| Code | Color Name | Usage |
|------|-----------|-------|
| `0` | BLACK | `/lp group admin meta set color 0` |
| `1` | DARK_BLUE | `/lp group admin meta set color DARK_BLUE` |
| `2` | DARK_GREEN | |
| `3` | DARK_AQUA | |
| `4` | DARK_RED | |
| `5` | DARK_PURPLE | |
| `6` | GOLD | |
| `7` | GRAY | |
| `8` | DARK_GRAY | |
| `9` | BLUE | |
| `a` | GREEN | |
| `b` | AQUA | |
| `c` | RED | |
| `d` | LIGHT_PURPLE | |
| `e` | YELLOW | |
| `f` | WHITE | |

**Example:**
```
/lp group admin meta set color c
/lp group admin meta set color RED
```

---

## 🗳️ Votifier Integration

SkBridge includes a built-in **Votifier listener** with RSA-2048 encryption support. This allows you to receive votes from external voting platforms and handle them via Skript.

### Configuration

Edit your `config.yml` to customize the Votifier port:

```yaml
port: 8192  # Default port for Votifier communication
```

### Keys

RSA keys are automatically generated on first startup in the plugin data folder:

```
/plugins/SkBridge/
├── rsa/
│   ├── private.key   (Keep this secret!)
│   └── public.key    (Share with voting platforms)
└── config.yml
```

**Key Generation:**
- Private Key (2048-bit): Stored securely in `private.key`
- Public Key (Base64 encoded): Available in `public.key`
- Keys are auto-generated if they don't exist

### How Voting Works

1. **Online Players:** When a player votes and is online, the vote event triggers immediately
2. **Offline Players:** Votes are queued and triggered when the player joins the server
3. **Events:** All votes trigger the `on vote:` event with the player's name

**Example - Vote Tracking:**
```skript
on vote:
    set {_player} to player
    add 1 to {total-votes::%{_player}%}
    
    if {_player} is online:
        send "&6Thanks for voting!" to {_player}
    broadcast "&e%{_player}% just voted!"
```

---

## 📝 Usage Examples

### Example 1: Complete Team Setup with LuckPerms

```skript
on luckperms group update:
    set {_player} to event-player
    set {_prefix} to luckperms prefix of {_player}
    set {_suffix} to luckperms suffix of {_player}
    set {_group} to luckperms group of {_player}
    
    set name tag of {_player} to "%{_prefix}% %name of {_player}%%{_suffix}%"
    broadcast "&aRank Updated: %{_player}% is now a %{_group}%!"
```

### Example 2: Vote Tracking and Rewards

```skript
on vote:
    set {_votes} to {votes::%player%} + 1
    set {votes::%player%} to {_votes}
    
    broadcast "&6%player% has voted! (Total: %{_votes}%)"
    
    if {_votes} is 10:
        broadcast "&c%player% reached 10 votes!"
        give player diamond block
```

### Example 3: Prefix-Only Name Tag

```skript
on luckperms group update:
    set {_prefix} to luckperms prefix of event-player
    set name tag of event-player to "%{_prefix}% %name of event-player%"
```

### Example 4: Get All Player Info

```skript
command /playerinfo <player>:
    trigger:
        set {_p} to arg-1
        set {_prefix} to luckperms prefix of {_p}
        set {_suffix} to luckperms suffix of {_p}
        set {_group} to luckperms group of {_p}
        set {_weight} to luckperms weight of {_p}
        set {_displayname} to luckperms display name of group of {_p}
        
        send "&6=== Player Info: %{_p}% ===" to sender
        send "&ePrefix: %{_prefix}%" to sender
        send "&eSuffix: %{_suffix}%" to sender
        send "&eGroup: %{_group}% (%{_displayname}%)" to sender
        send "&eWeight: %{_weight}%" to sender
```

---

## 📖 Release Notes

### v1.0 - Initial Standalone Release

- ✅ First official standalone release following separation from the SkVote project
- ✅ All legacy code has been completely removed
- ✅ Codebase fully rewritten and optimized for maximum performance
- ✅ Zero ghost entries in team management with automatic cleanup
- ✅ Real-time LuckPerms synchronization via UserDataRecalculateEvent
- ✅ Built-in Votifier listener with RSA-2048 encryption
- ✅ Vote queue system for offline players (votes trigger on join)
- ✅ Automatic RSA key generation and management
- ✅ Complete Skript API with 5 expressions, 1 effect, and 2 events
- ✅ Collision rule automatically disabled on all teams
- ✅ Automatic scoreboard cleanup on startup

---

## 👨‍💻 Developer Info

- **Author:** Ashalinux
- **Main Class:** `space.ashalinux.skbridge.Main`
- **API Version:** 1.13+
- **Version:** 1.0

**Key Classes:**
- `OnlineVoteEvent` - Custom event for vote handling
- `LpGroupUpdateEvent` - Custom event for LuckPerms updates
- Expression classes: `ExprLpPrefix`, `ExprLpSuffix`, `ExprLpGroup`, `ExprLpWeight`, `ExprLpGroupDisplayName`
- Effect class: `EffSetNameTag`

---

## 📦 Dependencies

- **Required:**
  - [Skript](https://github.com/SkriptLang/Skript) 2.6+
  - [LuckPerms](https://github.com/LuckPermsdev/LuckPerms) 5.0+
- **Provided by Bukkit:**
  - Bukkit/Spigot API 1.13+

---

## 🔒 Security Notes

- Private RSA keys are stored in `/plugins/SkBridge/rsa/private.key` - keep this file secure
- Never share your private key with anyone
- The public key can be safely shared with voting platforms
- RSA-2048 encryption ensures secure vote communication

---

## 📄 License

Please refer to the LICENSE file in this repository.

---

## 🤝 Support & Troubleshooting

### Common Issues

**"Ghost" prefixes still appearing:**
- Make sure LuckPerms is properly installed and loaded before SkBridge
- Restart your server completely
- Clear old teams: Delete teams matching pattern `\d{5}.*` from scoreboard

**Votes not working:**
- Check if port 8192 is open/accessible (or your configured port)
- Verify public key is correctly configured on voting platform
- Check server logs for RSA decryption errors

**Colors not applying:**
- Ensure you've set the `color` meta on your LuckPerms groups
- Use either the letter code (a-f, 0-9) or full name (RED, DARK_BLUE, etc.)
- Example: `/lp group admin meta set color c`

**For issues, feature requests, or questions**, please open an issue on GitHub.

---

**Made with ❤️ by Ashalinux**
