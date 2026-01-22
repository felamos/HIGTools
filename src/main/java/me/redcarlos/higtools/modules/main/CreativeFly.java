package me.redcarlos.higtools.modules.main;

import me.redcarlos.higtools.HIGTools;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class CreativeFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> baseSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("base-speed")
            .description("Base flight speed like vanilla creative.")
            .defaultValue(0.5)
            .min(0.1)
            .max(3)
            .build());

    private final Setting<Double> sprintMultiplier = sgGeneral.add(new DoubleSetting.Builder()
            .name("sprint-multiplier")
            .description("How much sprint (Ctrl) increases flight speed.")
            .defaultValue(2.0)
            .min(1.0)
            .max(4.0)
            .build());

    private boolean isFlying = false;
    private long lastSpacePress = 0;
    private static final long DOUBLE_TAP_MS = 350;
    private boolean spacePressedLastTick = false;

    // Store velocity
    private Vec3d velocity = Vec3d.ZERO;

    public CreativeFly() {
        super(HIGTools.MAIN, "creative-fly", "Fly like vanilla creative (double-tap space).");
    }

    private void sendGroundPacket() {
    if (mc.player == null || mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(
            new PlayerMoveC2SPacket.OnGroundOnly(true, isFlying)
        );
    }

    @Override
    public void onActivate() {
        isFlying = false;
        lastSpacePress = 0;
        spacePressedLastTick = false;
        velocity = Vec3d.ZERO;
        if (mc.player != null) mc.player.setNoGravity(false);
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.setNoGravity(false);
        isFlying = false;
    }

    @EventHandler
    public void onPreTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean spaceCurrently = mc.options.jumpKey.isPressed();

        // Detect new press for double‑tap space
        if (spaceCurrently && !spacePressedLastTick) {
            long now = System.currentTimeMillis();
            if (now - lastSpacePress <= DOUBLE_TAP_MS) {
                // Toggle flying
                isFlying = !isFlying;
                if (!isFlying) {
                    mc.player.setNoGravity(false);
                }
            }
            lastSpacePress = now;
        }
        spacePressedLastTick = spaceCurrently;

        // If touching ground while flying → stop (vanilla behavior) :contentReference[oaicite:2]{index=2}
        if (isFlying && mc.player.isOnGround()) {
            isFlying = false;
            mc.player.setNoGravity(false);
            mc.player.fallDistance = 0;
            mc.player.setVelocity(0, 0, 0); // prevent fall dmg
            velocity = Vec3d.ZERO;
            return;
        }

        if (isFlying) {
            // If falling or about to land, spoof ground
            if (mc.player.getVelocity().y < 0 || mc.player.isOnGround()) {
                sendGroundPacket();
            }
        }

        if (!isFlying) return;

        mc.player.setNoGravity(true);

        double speed = baseSpeed.get();
        if (mc.options.sprintKey.isPressed()) speed *= sprintMultiplier.get();

        // Gather input
        double forward = 0;
        double strafe = 0;
        if (mc.options.forwardKey.isPressed()) forward += 1;
        if (mc.options.backKey.isPressed()) forward -= 1;
        if (mc.options.leftKey.isPressed()) strafe += 1;
        if (mc.options.rightKey.isPressed()) strafe -= 1;

        double verticalInput = 0;
        if (mc.options.jumpKey.isPressed()) verticalInput += 1;
        if (mc.options.sneakKey.isPressed()) verticalInput -= 1;

        // Horizontal motion (correct yaw)
        double yawRad = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double horizX = forward * -sin + strafe * cos;
        double horizZ = forward * cos + strafe * sin;
        Vec3d desiredHorizontal = new Vec3d(horizX, 0, horizZ);

        if (!desiredHorizontal.equals(Vec3d.ZERO)) {
            desiredHorizontal = desiredHorizontal.normalize().multiply(speed);
        }

        // Vertical motion (weaker / slower than horizontal)
        double verticalSpeed = verticalInput * speed * 0.75; // weaker than horizontal

        // Apply inertia:
        // Horizontal: slow decay (smooth drift)
        Vec3d currentHorizontalVel = new Vec3d(velocity.x, 0, velocity.z);
        Vec3d newHorizontalVel = currentHorizontalVel.multiply(0.85) // strong inertia
                .add(desiredHorizontal.multiply(0.15));

        // Vertical: fast decay (stops quickly when releasing space/shift)
        double newVerticalVel = velocity.y * 0.4 + verticalSpeed * 0.6; // faster decay than horizontal

        velocity = new Vec3d(newHorizontalVel.x, newVerticalVel, newHorizontalVel.z);

        if (velocity.y < 0 && mc.player.isOnGround()) {
            velocity = new Vec3d(velocity.x, 0, velocity.z);
        }

        mc.player.setVelocity(velocity);
    }
}

