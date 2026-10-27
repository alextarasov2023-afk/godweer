package pub.godweer.kitty.module.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import pub.godweer.auxiliary.client.persiki.ChatUtil;
import pub.godweer.auxiliary.player.InventoryUtil;
import pub.godweer.cc.Angle;
import pub.godweer.cc.RayTraceUtils;
import pub.godweer.cc.RotationHandler;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.MouseInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.BindSetting;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * ClickPearl: перл летит ровно в точку под прицелом.
 * <p>
 * Ваниль бросает перл по углу камеры, поэтому на дистанции он всегда оседает коротким.
 * Здесь угол считается баллистикой самого перла (v = 1.5, гравитация 0.03, сопротивление
 * 0.99 по горизонтали) и ставится в тихую ротацию перед броском: сервер берёт направление
 * из пакета использования, а камера игрока не дёргается.
 * <p>
 * Разброс в 0.5° у ванильного броска никуда не делся — «ровно» это в его пределах.
 */
public final class ClickPearl extends Module {

    private static final double THROW_VELOCITY = 1.5D;
    private static final double GRAVITY = 0.03D;
    private static final double AIR_DRAG = 0.99D;
    private static final int MAX_SIM_TICKS = 200;

    private static final int HOTBAR_SIZE = 9;

    private final BindSetting throwBind =
            new BindSetting("Throw", BindSetting.mouse(InputConstants.MOUSE_BUTTON_RIGHT));
    private final SliderSetting maxRange = slider("Max Range", 24.0f, 4.0f, 48.0f, 1.0f);
    private final CheckBox swapBack = checkbox("Swap Back", true);

    // Клик только помечает бросок: сам бросок делается на следующем тике, там же, где
    // обновляется mc.hitResult, и не из GLFW-колбэка.
    private boolean pendingThrow = false;
    private int cooldownTicks = 0;

    public ClickPearl() {
        super("ClickPearl", "Перл точно в точку под прицелом: угол считается баллистикой", Category.PLAYER);
    }

    @EventTarget
    public void onMouse(MouseInputEvent event) {
        if (event.getAction() != InputConstants.PRESS || mc.gui.screen() != null) return;
        if (cooldownTicks > 0 || pendingThrow || !throwBind.matchesMouse(event.getButton())) return;
        // Без перла в хотбаре не трогаем клик вовсе: пусть работает штатное использование.
        if (player() == null || findPearl(player()) < 0) return;

        pendingThrow = true;
        event.cancel();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        if (!pendingThrow) return;

        pendingThrow = false;
        cooldownTicks = throwPearl() ? 3 : 0;
    }

    private boolean throwPearl() {
        LocalPlayer player = player();
        if (player == null || mc.gameMode == null) return false;

        Vec3 point = targetPoint();
        if (point == null) {
            ChatUtil.error("Нет точки прицела: наведи на блок или сущность");
            return false;
        }

        Angle solved = solve(player, point);
        if (solved == null) {
            ChatUtil.error("Перл туда не долетит");
            return false;
        }

        int slot = findPearl(player);
        if (slot < 0) {
            ChatUtil.error("Нет эндер-перла в хотбаре");
            return false;
        }

        int previousSlot = player.getInventory().getSelectedSlot();
        if (slot != previousSlot) select(player, slot);

        RotationHandler.setRotation(solved.getYaw(), solved.getPitch());
        InventoryUtil.useDirect(player);
        // Угол больше не нужен: бросок уже ушёл в пакет. Отпускаем плавно, чтобы
        // камера и серверный угол сошлись, а не щёлкнулись.
        RotationHandler.disengage("Smooth");

        if (slot != previousSlot && swapBack.getValue()) select(player, previousSlot);
        return true;
    }

    // --------------------------------------------------------------- баллистика

    /**
     * Подбираем питч, при котором траектория пересекает нужную горизонталь
     * расстояние на высоте точки. Грубый шаг 0.5°, потом уточнение до 0.05°.
     */
    private Angle solve(LocalPlayer player, Vec3 point) {
        Vec3 start = player.getEyePosition();
        Vec3 delta = point.subtract(start);
        double horizontal = Math.hypot(delta.x, delta.z);
        if (horizontal < 0.5D || horizontal > maxRange.get()) return null;

        float yaw = Angle.fromVec(start, point).getYaw();

        double bestPitch = 0.0D;
        double bestError = Double.MAX_VALUE;
        for (double pitch = -45.0D; pitch <= 55.0D; pitch += 0.5D) {
            Double height = flightHeight(start, yaw, pitch, horizontal);
            if (height == null) continue;
            double error = Math.abs(height - point.y);
            if (error < bestError) {
                bestError = error;
                bestPitch = pitch;
            }
        }
        if (bestError == Double.MAX_VALUE) return null;

        for (double pitch = bestPitch - 0.5D; pitch <= bestPitch + 0.5D; pitch += 0.05D) {
            Double height = flightHeight(start, yaw, pitch, horizontal);
            if (height == null) continue;
            double error = Math.abs(height - point.y);
            if (error < bestError) {
                bestError = error;
                bestPitch = pitch;
            }
        }
        return new Angle(yaw, (float) bestPitch);
    }

    /** Высота в момент, когда пройдено нужное расстояние по горизонтали; null — недолёт. */
    private static Double flightHeight(Vec3 start, float yaw, double pitch, double distance) {
        Vec3 look = RayTraceUtils.getLookVec(yaw, (float) pitch);
        Vec3 position = start;
        Vec3 velocity = look.scale(THROW_VELOCITY);

        for (int tick = 0; tick < MAX_SIM_TICKS; tick++) {
            velocity = new Vec3(velocity.x * AIR_DRAG, velocity.y - GRAVITY, velocity.z * AIR_DRAG);
            position = position.add(velocity);

            if (Math.hypot(position.x - start.x, position.z - start.z) >= distance) {
                return position.y;
            }
            // Ушла слишком низко под точку старта — дальше только земля.
            if (position.y < start.y - 12.0D) return null;
        }
        return null;
    }

    // ------------------------------------------------------------------- утилиты

    private Vec3 targetPoint() {
        if (mc.hitResult instanceof BlockHitResult blockHit) {
            return blockHit.getLocation().add(0.0D, 0.1D, 0.0D);
        }
        if (mc.hitResult instanceof EntityHitResult entityHit) {
            return entityHit.getEntity().getBoundingBox().getCenter();
        }
        return null;
    }

    private static int findPearl(LocalPlayer player) {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (player.getInventory().getItem(slot).is(Items.ENDER_PEARL)) return slot;
        }
        return -1;
    }

    private void select(LocalPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        if (player.connection != null) {
            player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    @Override
    public void onDisable() {
        pendingThrow = false;
        cooldownTicks = 0;
    }
}
