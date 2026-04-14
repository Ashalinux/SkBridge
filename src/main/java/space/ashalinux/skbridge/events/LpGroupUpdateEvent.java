package space.ashalinux.skbridge.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class LpGroupUpdateEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    public LpGroupUpdateEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}