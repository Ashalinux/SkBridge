package space.ashalinux.skbridge.elements.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public class ExprLpPrefix extends SimpleExpression<String> {

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected String[] get(Event e) {
        Player p = playerExpr.getSingle(e);

        if (p == null) {
            return null;
        }

        User u = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());

        if (u == null || u.getCachedData().getMetaData().getPrefix() == null) {
            return null;
        }

        String prefix = u.getCachedData().getMetaData().getPrefix();
        return new String[]{ChatColor.translateAlternateColorCodes('&', prefix)};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "lp prefix";
    }
}