package space.ashalinux.skbridge.elements.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public class ExprLpWeight extends SimpleExpression<Integer> {

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        this.playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected Integer[] get(Event e) {
        Player p = playerExpr.getSingle(e);

        if (p == null) {
            return null;
        }

        User u = LuckPermsProvider.get().getUserManager().getUser(p.getUniqueId());

        if (u == null) {
            return new Integer[]{0};
        }

        Group g = LuckPermsProvider.get().getGroupManager().getGroup(u.getPrimaryGroup());

        if (g != null && g.getWeight().isPresent()) {
            return new Integer[]{g.getWeight().getAsInt()};
        }

        return new Integer[]{0};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends Integer> getReturnType() {
        return Integer.class;
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "lp weight";
    }
}