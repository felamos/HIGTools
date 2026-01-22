package me.redcarlos.higtools.modules.main;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.redcarlos.higtools.HIGTools;

public class MassTPA extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ================= SETTINGS =================

    private final Setting<String> command = sgGeneral.add(
        new StringSetting.Builder()
            .name("command")
            .description("TPA command. Use {player} as placeholder.")
            .defaultValue("/tpa {player}")
            .build()
    );

    private final Setting<Boolean> stopOnAccept = sgGeneral.add(
        new BoolSetting.Builder()
            .name("stop-on-accept")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> delay = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("delay")
            .description("Delay between requests (seconds).")
            .defaultValue(1.0)
            .min(0.1)
            .build()
    );

    private final Setting<String> acceptKeywords = sgGeneral.add(
        new StringSetting.Builder()
            .name("accept-keywords")
            .description("Comma-separated words that indicate a TPA was accepted.")
            .defaultValue("accepted,teleporting,teleported")
            .build()
    );


    // ================= STATE =================

    private List<PlayerListEntry> players;
    private Iterator<PlayerListEntry> iterator;
    private long lastSend;
    private boolean accepted;

    // ================= MODULE =================

    public MassTPA() {
        super(HIGTools.MAIN, "mass-tpa", "Sends TPA requests to all players.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            toggle();
            return;
        }

        players = new ArrayList<>(mc.getNetworkHandler().getPlayerList());
        iterator = players.iterator();
        lastSend = 0;
        accepted = false;
    }

    @Override
    public void onDeactivate() {
        players = null;
        iterator = null;
    }

    // ================= TICK =================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (iterator == null || accepted) return;

        long now = System.currentTimeMillis();
        if (now - lastSend < delay.get() * 1000) return;

        while (iterator.hasNext()) {
            PlayerListEntry entry = iterator.next();
            String name = entry.getProfile().name();

            if (name.equals(mc.player.getGameProfile().name())) continue;

            sendTpa(name);
            lastSend = now;
            return;
        }

        // Done
        toggle();
    }

    // ================= LOGIC =================

    private void sendTpa(String player) {
        String cmd = command.get().replace("{player}", player);

        if (cmd.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(cmd.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(cmd);
        }
    }

    private void onAccepted() {
        accepted = true;

        if (stopOnAccept.get()) {
            toggle();
        }
    }

    // ================= CHAT =================

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().toLowerCase();
    
        for (String keyword : acceptKeywords.get().toLowerCase().split(",")) {
            if (!keyword.isBlank() && msg.contains(keyword.trim())) {
                onAccepted();
                return;
            }
        }
    }

}
