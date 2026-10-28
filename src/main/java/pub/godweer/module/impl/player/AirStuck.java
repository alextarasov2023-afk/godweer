package pub.godweer.module.impl.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.EventTravel;
import pub.godweer.event.list.LookEvent;
import pub.godweer.event.list.PacketEvent;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.util.network.NetworkUtils;
import pub.godweer.util.player.InventorySwaps;
import pub.godweer.util.render.Render3D;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;

import java.awt.Color;
import java.util.List;

/**
 * Замораживает игрока в воздухе: физика отменяется, позиция каждый тик
 * возвращается в точку якоря, пакеты движения уходят с координатами якоря.
 *
 * Модуль один и работает только при падении: пока игрок стоит на земле или
 * летит на элитрах, он не тронут. Как только началось падение (fallDistance
 * растёт, скорость по вертикали отрицательная) — снимается якорь с текущей
 * точки и игрок замирает в воздухе до выключения модуля.
 *
 * Пакеты движения по умолчанию не пропадают: вместо оригинала уходит такой же
 * пакет, но с замороженными координатами. Сервер видит игрока на якоре, а не
 * «молчащего» клиента, поэтому нет отката и Moved Too Quickly при выключении.
 * Полное молчание — отдельная галка «Не пересылать пакеты» для серверов, где
 * нужен именно обрыв движения.
 *
 * Фейковый TeleportConfirm(-1337) из туториала не отправляется: невалидный id
 * телепорта — прямой флаг BadPackets, а сервер всё равно ждёт подтверждения
 * настоящего телепорта от ванильного обработчика.
 */
public class AirStuck extends Module {

    public static AirStuck INSTANCE;

    /** Слот нагрудника в InventoryMenu. */
    private static final int CHESTPLATE_SLOT = 6;

    private static final List<Item> CHESTPLATE_TIERS = List.of(
            Items.NETHERITE_CHESTPLATE,
            Items.DIAMOND_CHESTPLATE,
            Items.COPPER_CHESTPLATE,
            Items.IRON_CHESTPLATE,
            Items.CHAINMAIL_CHESTPLATE,
            Items.GOLDEN_CHESTPLATE,
            Items.LEATHER_CHESTPLATE);

    private final CheckBox allowRotation = checkbox("Крутить головой", false);
    private final CheckBox allowAttack = checkbox("Бить", false);
    private final CheckBox noKnockback = checkbox("Без отдачи", true);
    private final CheckBox cancelPackets = checkbox("Не пересылать пакеты", false);
    private final CheckBox elytraChestplate = checkbox("Нагрудник в элитрах", true);
    private final CheckBox anchorBox = checkbox("Рамка якоря", true);

    private boolean active;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private float anchorYaw;
    private float anchorPitch;

    public AirStuck() {
        super("AirStuck", "Замораживает тебя в воздухе при падении", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        LocalPlayer player = player();
        if (!inGame() || player == null) {
            reset();
            return;
        }

        // Смерть/респаун: замораживать труп незачем, а на новом месте якорь мёртв.
        if (!player.isAlive()) {
            setEnabled(false);
            return;
        }

        // Модуль просыпается только на падении: стоя на земле или летя на
        // элитрах, игрок не тронут.
        if (!active) {
            if (!isFalling(player)) return;
            freeze(player);
        }

        // Позицию не обновляем — возвращаем в якорь; ввод и физику гасим.
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setPos(anchorX, anchorY, anchorZ);
        player.xo = anchorX;
        player.yo = anchorY;
        player.zo = anchorZ;
        player.fallDistance = 0.0F;

        if (!allowRotation.get()) {
            player.setYRot(anchorYaw);
            player.setXRot(anchorPitch);
            player.yRotO = anchorYaw;
            player.xRotO = anchorPitch;
        }

        swapElytraForChestplate(player);
    }

    /** Начало падения: игрок оторвался от земли и уходит вниз. */
    private boolean isFalling(LocalPlayer player) {
        return !player.onGround()
                && player.fallDistance > 0.0F
                && player.getDeltaMovement().y < 0.0D;
    }

    /** Снимает точку якоря с текущей позиции и глушит накопленную физику. */
    private void freeze(LocalPlayer player) {
        anchorX = player.getX();
        anchorY = player.getY();
        anchorZ = player.getZ();
        anchorYaw = player.getYRot();
        anchorPitch = player.getXRot();
        active = true;

        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    /**
     * Планирование на элитрах античит читает как elytra-flight; в нагруднике это
     * обычное падение. Пока модуль включён, а игрок летит на элитрах (или уже
     * висит на якоре), элитра снимается и вместо неё надевается нагрудник.
     * Нагрудника нет — элитру не снимаем: лучше планировать, чем остаться с
     * голым торсом.
     */
    private void swapElytraForChestplate(LocalPlayer player) {
        if (!elytraChestplate.get()) return;
        if (!player.isFallFlying() && !active) return;
        if (!player.inventoryMenu.getSlot(CHESTPLATE_SLOT).getItem().is(Items.ELYTRA)) return;

        int source = bestChestplateSlot(player);
        if (source == -1) return;

        InventorySwaps.executeContainerSwap(player, source, CHESTPLATE_SLOT);
    }

    /** Лучший нагрудник: сначала по материалу, внутри материала — ближайший слот. */
    private int bestChestplateSlot(LocalPlayer player) {
        int bestSlot = -1;
        int bestTier = CHESTPLATE_TIERS.size();

        for (int slot = InventoryMenu.INV_SLOT_START; slot < InventoryMenu.USE_ROW_SLOT_END; slot++) {
            ItemStack stack = player.inventoryMenu.getSlot(slot).getItem();
            int tier = chestplateTier(stack);
            if (tier != -1 && tier < bestTier) {
                bestTier = tier;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int chestplateTier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        for (int i = 0; i < CHESTPLATE_TIERS.size(); i++) {
            if (stack.is(CHESTPLATE_TIERS.get(i))) return i;
        }
        return -1;
    }

    @EventTarget
    public void onTravel(EventTravel event) {
        if (active) {
            event.cancel();
        }
    }

    @EventTarget
    public void onLook(LookEvent event) {
        if (active && !allowRotation.get()) {
            event.cancel();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.isSend()) {
            onSendPacket(event);
            return;
        }
        if (!active) return;

        if (noKnockback.get()) {
            if (event.is(ClientboundSetEntityMotionPacket.class)) {
                ClientboundSetEntityMotionPacket motion = event.get(ClientboundSetEntityMotionPacket.class);
                if (player() != null && motion.id() == player().getId()) {
                    event.cancel();
                }
            } else if (event.is(ClientboundExplodePacket.class)) {
                event.cancel();
            }
        }

        // Сервер сам переставил игрока — якорь переезжает вместе с ним. Абсолютную
        // позицию считаем через ванильный хелпер, чтобы учесть relative-флаги.
        if (event.is(ClientboundPlayerPositionPacket.class)) {
            LocalPlayer player = player();
            if (player == null) return;

            ClientboundPlayerPositionPacket packet = event.get(ClientboundPlayerPositionPacket.class);
            PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(
                    PositionMoveRotation.of(player), packet.change(), packet.relatives());

            anchorX = absolute.position().x;
            anchorY = absolute.position().y;
            anchorZ = absolute.position().z;
            anchorYaw = absolute.yRot();
            anchorPitch = absolute.xRot();
        }
    }

    /**
     * Оригинальный пакет движения отменяем и сразу шлём замену с координатами
     * якоря: сервер должен видеть игрока каждый тик, но на одной точке.
     */
    private void onSendPacket(PacketEvent event) {
        if (!active || !event.is(ServerboundMovePlayerPacket.class)) return;

        ServerboundMovePlayerPacket move = event.get(ServerboundMovePlayerPacket.class);
        event.cancel();

        if (!cancelPackets.get()) {
            NetworkUtils.sendSilentPacket(anchorMove(move));
        }
    }

    private ServerboundMovePlayerPacket anchorMove(ServerboundMovePlayerPacket original) {
        LocalPlayer player = player();
        float yaw = allowRotation.get() && player != null ? player.getYRot() : anchorYaw;
        float pitch = allowRotation.get() && player != null ? player.getXRot() : anchorPitch;
        boolean onGround = original.isOnGround();
        boolean horizontalCollision = original.horizontalCollision();

        if (original.hasPosition() && original.hasRotation()) {
            return new ServerboundMovePlayerPacket.PosRot(anchorX, anchorY, anchorZ, yaw, pitch, onGround, horizontalCollision);
        }
        if (original.hasPosition()) {
            return new ServerboundMovePlayerPacket.Pos(anchorX, anchorY, anchorZ, onGround, horizontalCollision);
        }
        if (original.hasRotation()) {
            return new ServerboundMovePlayerPacket.Rot(yaw, pitch, onGround, horizontalCollision);
        }
        return new ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision);
    }

    /** Атака глушится, пока стоим на якоре без разрешения «Бить». */
    public static boolean blocksAttack() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.active && !INSTANCE.allowAttack.get();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!active || !anchorBox.get() || event.getDeltaTracker() == null || event.getClient() == null) {
            return;
        }

        int accent = Theme.getAccentColor();
        Color fill = new Color(ColorUtil.red(accent), ColorUtil.green(accent), ColorUtil.blue(accent), 48);
        Color outline = new Color(ColorUtil.red(accent), ColorUtil.green(accent), ColorUtil.blue(accent), 210);

        Vec3 point = new Vec3(anchorX, anchorY, anchorZ);
        AABB box = new AABB(point.x - 0.3D, point.y, point.z - 0.3D, point.x + 0.3D, point.y + 1.8D, point.z + 0.3D);
        Render3D.drawBox(box, fill, outline, true, true, true);
    }

    private void reset() {
        active = false;
    }
}
