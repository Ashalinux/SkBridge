package space.ashalinux.skbridge.elements.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class EffSetNameTag extends Effect {

    private Expression<Player> playerExpr;
    private Expression<String> stringExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.playerExpr = (Expression<Player>) exprs[0];
        this.stringExpr = (Expression<String>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event e) {
        Player p = playerExpr.getSingle(e);
        String format = stringExpr.getSingle(e);

        if (p == null || format == null || Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        format = ChatColor.translateAlternateColorCodes('&', format);
        p.setPlayerListName(format);

        String prefix;
        String suffix;
        int nameIndex = format.indexOf(p.getName());

        if (nameIndex != -1) {
            prefix = format.substring(0, nameIndex);
            suffix = format.substring(nameIndex + p.getName().length());
        } else {
            prefix = format;
            suffix = "";
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
                if (group != null && group.getWeight().isPresent()) {
                    weight = group.getWeight().getAsInt();
                }

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

        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }

        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setPrefix(prefix);
        team.setSuffix(suffix);

        if (teamColor != null) {
            team.setColor(teamColor);
        }
        if (!team.hasEntry(p.getName())) {
            team.addEntry(p.getName());
        }
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "set name tag effect";
    }
}