package pub.godweer.module.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.player.Input;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.PacketEvent;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.ui.clickgui.PanelRefractions;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.util.misc.KeyCheck;
import pub.godweer.util.player.MoveBlockUtility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GuiWalk extends Module {

    public static GuiWalk INSTANCE;

    private final ModeSetting clickBypass = mode("Click Bypass", "Multi", "None", "Legit", "Multi");
    private final CheckBox noCloseOnEmpty = checkbox("No Close On Empty", true);
    private final CheckBox sneak = checkbox("Sneak", false);
    private final CheckBox jump = checkbox("Jump", true);

    private final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private boolean isSendingInternal = false;
    private boolean hasInteracted = false;

    private enum State {
        IDLE,
        STOP_SPRINT,
        SYNC_WAIT,
        SEND_CLICKS,
        COOLDOWN
    }

    private State state = State.IDLE;
    private int cooldownTicks = 0;

    public GuiWalk() {
        super("GuiWalk", "Бля даж не знаю вроде не дают робуксы за голду", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        forceSendQueue();
        MoveBlockUtility.unblock(this);
        state = State.IDLE;
        hasInteracted = false;
    }

    public static Input screenInput() {
        if (INSTANCE == null || !INSTANCE.isEnabled() || !INSTANCE.inGame() || Minecraft.getInstance().player == null) {
            return null;
        }

        if (INSTANCE.state != State.IDLE || MoveBlockUtility.isFrozen()) {
            return null;
        }

        boolean inVanillaScreen = mc.gui.screen() != null;
        boolean inMenuOverlay = PanelRefractions.isOpen();

        if (!inVanillaScreen && !inMenuOverlay) return null;
        if (inVanillaScreen && !canWalkInScreen(mc.gui.screen())) return null;
        if (inMenuOverlay && PanelRefractions.isTyping()) return null;

        boolean f = KeyCheck.isPressed(mc.options.keyUp);
        boolean b = KeyCheck.isPressed(mc.options.keyDown);
        boolean l = KeyCheck.isPressed(mc.options.keyLeft);
        boolean r = KeyCheck.isPressed(mc.options.keyRight);
        boolean j = INSTANCE.jump.getValue() && KeyCheck.isPressed(mc.options.keyJump);
        boolean s = INSTANCE.sneak.getValue() && KeyCheck.isPressed(mc.options.keyShift);
        boolean sp = KeyCheck.isPressed(mc.options.keySprint);

        return new Input(f, b, l, r, j, s, sp);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!inGame() || player() == null || isSendingInternal) return;
        boolean inGui = screen() != null || PanelRefractions.isOpen();
        if (!inGui) return;

        if (event.isSend()) {
            String mode = clickBypass.getValue();

            if (event.is(ServerboundContainerClickPacket.class)) {
                hasInteracted = true;

                if (mode.equalsIgnoreCase("Multi")) {
                    if (isPlayerMoving() || state != State.IDLE) {
                        event.setCancelled(true);
                        packetQueue.add(event.getPacket());
                    }
                } else if (mode.equalsIgnoreCase("Legit")) {
                    event.setCancelled(true);
                    packetQueue.add(event.getPacket());
                    if (state == State.IDLE) {
                        state = State.STOP_SPRINT;
                    }
                }
            }

            if (event.is(ServerboundContainerClosePacket.class)) {
                if (noCloseOnEmpty.getValue() && !hasInteracted && packetQueue.isEmpty()) {
                    event.setCancelled(true);
                    return;
                }

                if (!packetQueue.isEmpty() || state != State.IDLE) {
                    event.setCancelled(true);
                    packetQueue.add(event.getPacket());
                    if (state == State.IDLE) {
                        state = State.STOP_SPRINT;
                    }
                }
            }
        }
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!inGame() || player() == null) return;

        if (event.getPhase() == PlayerTickEvent.Phase.PRE) {
            if (screen() == null && !PanelRefractions.isOpen() && packetQueue.isEmpty() && state == State.IDLE) {
                hasInteracted = false;
            }

            if (clickBypass.is("Multi") && !packetQueue.isEmpty() && state == State.IDLE) {
                if (!isMovingKeysPressed() || (screen() == null && !PanelRefractions.isOpen())) {
                    state = State.STOP_SPRINT;
                }
            }

            switch (state) {
                case STOP_SPRINT -> {
                    if (player().isSprinting()) {
                        player().setSprinting(false);
                    }
               //     sendDirect(new ServerboundPlayerCommandPacket(player(), ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));

                    if (player().onGround()) {
                        player().setDeltaMovement(0.0, player().getDeltaMovement().y, 0.0);
                    }
                    MoveBlockUtility.freeze(this, 5);
                    MoveBlockUtility.blockSprint(this, 6);

                    state = State.SYNC_WAIT;
                }

                case SYNC_WAIT -> {
                    if (player().onGround()) {
                        player().setDeltaMovement(0.0, player().getDeltaMovement().y, 0.0);
                    }
                    state = State.SEND_CLICKS;
                }

                case SEND_CLICKS -> {
                    if (player().onGround()) {
                        player().setDeltaMovement(0.0, player().getDeltaMovement().y, 0.0);
                    }
                    isSendingInternal = true;
                    while (!packetQueue.isEmpty()) {
                        Packet<?> packet = packetQueue.poll();
                        if (packet != null) {
                            sendDirect(packet);
                        }
                    }
                    isSendingInternal = false;

                    cooldownTicks = 2;
                    state = State.COOLDOWN;
                }

                case COOLDOWN -> {
                    MoveBlockUtility.freeze(this, 2);
                    if (player().onGround()) {
                        player().setDeltaMovement(0.0, player().getDeltaMovement().y, 0.0);
                    }

                    if (--cooldownTicks <= 0) {
                        if (!packetQueue.isEmpty()) {
                            state = State.SEND_CLICKS;
                        } else {
                            state = State.IDLE;
                            hasInteracted = false;
                            MoveBlockUtility.unblock(this);
                        }
                    }
                }

                case IDLE -> {
                }
            }
        }
    }

    private void sendDirect(Packet<?> packet) {
        if (mc.getConnection() != null) {
            mc.getConnection().send(packet);
        }
    }

    private void forceSendQueue() {
        isSendingInternal = true;
        while (!packetQueue.isEmpty()) {
            Packet<?> p = packetQueue.poll();
            if (p != null) sendDirect(p);
        }
        isSendingInternal = false;
    }

    private boolean isMovingKeysPressed() {
        return KeyCheck.isPressed(mc.options.keyUp)
                || KeyCheck.isPressed(mc.options.keyDown)
                || KeyCheck.isPressed(mc.options.keyLeft)
                || KeyCheck.isPressed(mc.options.keyRight);
    }

    private boolean isPlayerMoving() {
        if (player() == null) return false;
        return isMovingKeysPressed() || player().getDeltaMovement().horizontalDistanceSqr() > 0.0005;
    }

    private static boolean canWalkInScreen(Screen s) {
        if (s == null) return false;
        return !(s instanceof ChatScreen)
                && !(s instanceof AbstractSignEditScreen)
                && !(s instanceof CommandBlockEditScreen)
                && !(s instanceof BookEditScreen)
                && !(s.getFocused() instanceof EditBox);
    }
}