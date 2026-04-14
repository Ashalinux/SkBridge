package space.ashalinux.skbridge;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleEvent;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;

import javax.crypto.Cipher;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;

@SuppressWarnings({"deprecation", "unused", "ResultOfMethodCallIgnored"})
public class Main extends JavaPlugin implements Listener {

    private final HashMap<String, Integer> voteQueue = new HashMap<>();
    private PrivateKey privateKey;
    private ServerSocket serverSocket;
    private boolean running = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        LuckPerms luckPerms;
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            getLogger().severe("LuckPerms not found! Disabling SkBridge.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        luckPerms.getEventBus().subscribe(this, UserDataRecalculateEvent.class, e -> {
            Player p = Bukkit.getPlayer(e.getUser().getUniqueId());
            if (p != null && p.isOnline()) {
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.getPluginManager().callEvent(new LpGroupUpdateEvent(p));
                });
            }
        });

        if (Bukkit.getScoreboardManager() != null) {
            Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Team team : mainBoard.getTeams()) {
                if (team.getName().matches("\\d{5}.*")) {
                    team.unregister();
                }
            }
        }

        SkriptAddon addon = Skript.registerAddon(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        initializeKeys();

        Skript.registerEvent("Vote", SimpleEvent.class, OnlineVoteEvent.class, "vote");
        Skript.registerEvent("LuckPerms Group Update", SimpleEvent.class, LpGroupUpdateEvent.class, "luckperms group update");

        EventValueRegistry registry = addon.registry(EventValueRegistry.class);
        registry.register(EventValue.simple(OnlineVoteEvent.class, String.class, e -> e.getPlayer().getName()));
        registry.register(EventValue.simple(OnlineVoteEvent.class, Player.class, OnlineVoteEvent::getPlayer));
        registry.register(EventValue.simple(LpGroupUpdateEvent.class, Player.class, LpGroupUpdateEvent::getPlayer));

        Skript.registerExpression(ExprLpPrefix.class, String.class, ExpressionType.PROPERTY, "luckperms prefix of %player%");
        Skript.registerExpression(ExprLpSuffix.class, String.class, ExpressionType.PROPERTY, "luckperms suffix of %player%");
        Skript.registerExpression(ExprLpGroup.class, String.class, ExpressionType.PROPERTY, "luckperms group of %player%");
        Skript.registerExpression(ExprLpWeight.class, Integer.class, ExpressionType.PROPERTY, "luckperms weight of %player%");
        Skript.registerExpression(ExprLpGroupDisplayName.class, String.class, ExpressionType.PROPERTY, "luckperms display name of group of %player%");

        Skript.registerEffect(EffSetNameTag.class, "set name tag of %player% to %string%");

        startVoteListener();
    }

    @Override
    public void onDisable() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String n = p.getName();
        if (voteQueue.containsKey(n)) {
            int count = voteQueue.get(n);
            for (int i = 0; i < count; i++) {
                Bukkit.getPluginManager().callEvent(new OnlineVoteEvent(p));
            }
            voteQueue.remove(n);
        }
    }

    public static class OnlineVoteEvent extends Event {
        private static final HandlerList h = new HandlerList();
        private final Player p;
        public OnlineVoteEvent(Player p) { this.p = p; }
        public Player getPlayer() { return p; }
        @Override public HandlerList getHandlers() { return h; }
        public static HandlerList getHandlerList() { return h; }
    }

    public static class LpGroupUpdateEvent extends Event {
        private static final HandlerList h = new HandlerList();
        private final Player p;
        public LpGroupUpdateEvent(Player p) { this.p = p; }
        public Player getPlayer() { return p; }
        @Override public HandlerList getHandlers() { return h; }
        public static HandlerList getHandlerList() { return h; }
    }

    public static class EffSetNameTag extends Effect {
        private Expression<Player> playerExpr;
        private Expression<String> stringExpr;

        @Override
        public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            playerExpr = (Expression<Player>) exprs[0];
            stringExpr = (Expression<String>) exprs[1];
            return true;
        }

        @Override
        protected void execute(Event e) {
            Player p = playerExpr.getSingle(e);
            String format = stringExpr.getSingle(e);
            if (p == null || format == null || Bukkit.getScoreboardManager() == null) return;

            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            format = ChatColor.translateAlternateColorCodes('&', format);
            p.setPlayerListName(format);

            String prefix, suffix;
            int nameIndex = format.indexOf(p.getName());
            if (nameIndex != -1) {
                prefix = format.substring(0, nameIndex);
                suffix = format.substring(nameIndex + p.getName().length());
            } else {
                prefix = format; suffix = "";
            }

            if (prefix.length() > 64) prefix = prefix.substring(0, 64);
            if (suffix.length() > 64) suffix = suffix.substring(0, 64);

            int weight = 0;
            String groupName = "default";
            ChatColor teamColor = null;

            try {
                User user = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());
                if (user != null) {
                    groupName = user.getPrimaryGroup();
                    Group group = LuckPermsProvider.get().getGroupManager().getGroup(groupName);
                    if (group != null && group.getWeight().isPresent()) weight = group.getWeight().getAsInt();

                    String colorMeta = user.getCachedData().getMetaData().getMetaValue("color");
                    if (colorMeta != null) {
                        colorMeta = colorMeta.replace("&", "").toUpperCase();
                        teamColor = colorMeta.length() == 1 ? ChatColor.getByChar(colorMeta.charAt(0)) : ChatColor.valueOf(colorMeta);
                    }
                }
            } catch (Exception ignored) {}

            String safeGroupName = groupName.replaceAll("[^a-zA-Z0-9]", "");
            if (safeGroupName.isEmpty()) safeGroupName = "team";

            String teamName = String.format("%05d_%s", Math.max(0, 10000 - weight), safeGroupName);
            if (teamName.length() > 64) teamName = teamName.substring(0, 64);

            Team team = board.getTeam(teamName) == null ? board.registerNewTeam(teamName) : board.getTeam(teamName);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.setPrefix(prefix); team.setSuffix(suffix);
            if (teamColor != null) team.setColor(teamColor);
            if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
        }

        @Override public String toString(Event e, boolean debug) { return "set name tag"; }
    }

    public static class ExprLpPrefix extends SimpleExpression<String> {
        private Expression<Player> playerExpr;
        @Override public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) { playerExpr = (Expression<Player>) exprs[0]; return true; }
        @Override protected String[] get(Event e) {
            Player p = playerExpr.getSingle(e);
            if (p == null) return new String[]{""};
            User u = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());
            String pre = (u != null) ? u.getCachedData().getMetaData().getPrefix() : "";
            return new String[]{ChatColor.translateAlternateColorCodes('&', pre != null ? pre : "")};
        }
        @Override public boolean isSingle() { return true; }
        @Override public Class<? extends String> getReturnType() { return String.class; }
        @Override public String toString(Event e, boolean d) { return "lp prefix"; }
    }

    public static class ExprLpSuffix extends SimpleExpression<String> {
        private Expression<Player> playerExpr;
        @Override public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) { playerExpr = (Expression<Player>) exprs[0]; return true; }
        @Override protected String[] get(Event e) {
            Player p = playerExpr.getSingle(e);
            if (p == null) return new String[]{""};
            User u = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());
            String suf = (u != null) ? u.getCachedData().getMetaData().getSuffix() : "";
            return new String[]{ChatColor.translateAlternateColorCodes('&', suf != null ? suf : "")};
        }
        @Override public boolean isSingle() { return true; }
        @Override public Class<? extends String> getReturnType() { return String.class; }
        @Override public String toString(Event e, boolean d) { return "lp suffix"; }
    }

    public static class ExprLpGroup extends SimpleExpression<String> {
        private Expression<Player> playerExpr;
        @Override public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) { playerExpr = (Expression<Player>) exprs[0]; return true; }
        @Override protected String[] get(Event e) {
            Player p = playerExpr.getSingle(e);
            User u = (p != null) ? LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId()) : null;
            return new String[]{u != null ? u.getPrimaryGroup() : ""};
        }
        @Override public boolean isSingle() { return true; }
        @Override public Class<? extends String> getReturnType() { return String.class; }
        @Override public String toString(Event e, boolean d) { return "lp group"; }
    }

    public static class ExprLpGroupDisplayName extends SimpleExpression<String> {
        private Expression<Player> playerExpr;
        @Override public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) { playerExpr = (Expression<Player>) exprs[0]; return true; }
        @Override protected String[] get(Event e) {
            Player p = playerExpr.getSingle(e);
            if (p == null) return new String[]{""};
            User u = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());
            if (u == null) return new String[]{""};
            Group g = LuckPermsProvider.get().getGroupManager().getGroup(u.getPrimaryGroup());
            String display = (g != null && g.getDisplayName() != null) ? g.getDisplayName() : u.getPrimaryGroup();
            return new String[]{ChatColor.translateAlternateColorCodes('&', display)};
        }
        @Override public boolean isSingle() { return true; }
        @Override public Class<? extends String> getReturnType() { return String.class; }
        @Override public String toString(Event e, boolean d) { return "lp display name"; }
    }

    public static class ExprLpWeight extends SimpleExpression<Integer> {
        private Expression<Player> playerExpr;
        @Override public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) { playerExpr = (Expression<Player>) exprs[0]; return true; }
        @Override protected Integer[] get(Event e) {
            Player p = playerExpr.getSingle(e);
            if (p == null) return new Integer[]{0};
            User u = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());
            if (u == null) return new Integer[]{0};
            Group g = LuckPermsProvider.get().getGroupManager().getGroup(u.getPrimaryGroup());
            return new Integer[]{g != null && g.getWeight().isPresent() ? g.getWeight().getAsInt() : 0};
        }
        @Override public boolean isSingle() { return true; }
        @Override public Class<? extends Integer> getReturnType() { return Integer.class; }
        @Override public String toString(Event e, boolean d) { return "lp weight"; }
    }

    private void initializeKeys() {
        File rsaDir = new File(getDataFolder(), "rsa");
        File privFile = new File(rsaDir, "private.key");
        if (!rsaDir.exists()) rsaDir.mkdirs();
        try {
            if (!privFile.exists()) {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048); KeyPair pair = keyGen.generateKeyPair();
                try (FileOutputStream out = new FileOutputStream(privFile)) { out.write(pair.getPrivate().getEncoded()); }
                String pubBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
                try (PrintWriter pw = new PrintWriter(new File(rsaDir, "public.key"))) { pw.print(pubBase64); }
                privateKey = pair.getPrivate();
            } else {
                byte[] privBytes = java.nio.file.Files.readAllBytes(privFile.toPath());
                privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            }
        } catch (Exception ignored) {}
    }

    private void startVoteListener() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            int port = getConfig().getInt("port", 8192);
            try (ServerSocket ss = new ServerSocket(port)) {
                serverSocket = ss;
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout(5000);

                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        out.write("VOTIFIER 1.9\n"); out.flush();

                        InputStream in = socket.getInputStream();
                        byte[] data = new byte[256]; int read = in.read(data);
                        if (read > 0) {
                            String decrypted = decrypt(data);
                            if (decrypted != null) {
                                String[] parts = decrypted.split("\\r?\\n");
                                int userIndex = parts[0].trim().equalsIgnoreCase("VOTE") ? 2 : 1;
                                if (parts.length > userIndex) handleVote(parts[userIndex].trim());
                            }
                        }
                        socket.close();
                    } catch (SocketTimeoutException ignored) {
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        });
    }

    private String decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(data));
        } catch (Exception e) { return null; }
    }

    private void handleVote(String name) {
        Bukkit.getScheduler().runTask(this, () -> {
            Player p = Bukkit.getPlayer(name);
            if (p != null && p.isOnline()) { Bukkit.getPluginManager().callEvent(new OnlineVoteEvent(p)); }
            else { voteQueue.put(name, voteQueue.getOrDefault(name, 0) + 1); }
        });
    }
}