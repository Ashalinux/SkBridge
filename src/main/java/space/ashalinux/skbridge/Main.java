package space.ashalinux.skbridge;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.util.SimpleEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;

import space.ashalinux.skbridge.elements.effects.EffSetNameTag;
import space.ashalinux.skbridge.elements.expressions.*;
import space.ashalinux.skbridge.events.LpGroupUpdateEvent;
import space.ashalinux.skbridge.events.OnlineVoteEvent;
import space.ashalinux.skbridge.manager.VotifierManager;

public class Main extends JavaPlugin {

    private VotifierManager votifierManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupLuckPerms()) return;
        cleanGhostTeams();
        setupSkript();

        this.votifierManager = new VotifierManager(this);
        this.votifierManager.start();

        getLogger().info("SkBridge v" + getDescription().getVersion() + " successfully loaded.");
    }

    private boolean setupLuckPerms() {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            luckPerms.getEventBus().subscribe(this, UserDataRecalculateEvent.class, e -> {
                Player p = Bukkit.getPlayer(e.getUser().getUniqueId());
                if (p != null && p.isOnline()) {
                    Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().callEvent(new LpGroupUpdateEvent(p)));
                }
            });
            return true;
        } catch (IllegalStateException e) {
            getLogger().severe("LuckPerms API not found!");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void setupSkript() {
        SkriptAddon addon = Skript.registerAddon(this);

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
    }

    private void cleanGhostTeams() {
        if (Bukkit.getScoreboardManager() == null) return;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        board.getTeams().stream()
                .filter(t -> t.getName().matches("\\d{5}.*"))
                .forEach(Team::unregister);
    }

    @Override
    public void onDisable() {
        if (this.votifierManager != null) {
            this.votifierManager.stop();
        }
    }
}