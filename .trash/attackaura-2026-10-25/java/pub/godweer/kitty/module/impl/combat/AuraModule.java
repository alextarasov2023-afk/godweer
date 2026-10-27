package pub.godweer.kitty.module.impl.combat;

import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pub.godweer.auxiliary.client.clients.Targets;
import pub.godweer.cc.*;
import pub.godweer.cc.UBoxPoints.TargetPoint;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.*;

import java.util.List;

/**
 * Create by daun kvass
 */
@Getter
public class AuraModule extends Module {
    public static AuraModule INSTANCE;

    // Дистанции (по умолчанию 2.9: серверный лимит reach — 3.0 ровно, удары
    // на границе сервер отклоняет — «замах есть, урона нет»)
    private final SliderSetting attackRange = slider("Attack Range", 2.9f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting aimRange = slider("Aim Range", 4.2f, 1.0f, 8.0f, 0.1f);

    // Ротация: Smooth / None (как TriggerBot — без поворота камеры, бьёт при наведении)
    private final ModeSetting rotMode = mode("Rotation", "Smooth", "Smooth", "None");

    // Коррекция движения: Focus (только EntityMixin — движение считается от серверного
    // угла, т.е. ровно так же, как его реконструирует сервер: легитно) и Free
    // (EntityMixin + KeyboardInputMixin: ввод доворачивается под свободную камеру, из-за
    // чего движение расходится с отправленным поворотом — это то, что видят античиты).
    private final ModeSetting moveFix = mode("Movement Correction", "Focus", "Focus", "Free")
            .visible(() -> !rotMode.getValue().equalsIgnoreCase("None"));

    // Цели (по умолчанию — игроки; multiMode с List задаёт значения по умолчанию,
    // иначе список активных целей пуст и аура никого не бьёт)
    private final MultiModeSetting targets = multiMode("Targets", List.of("Players"),
            "Players", "Mobs", "Animals", "Naked", "Friends", "Villagers");

    // Сброс спринта под крит (серверный: STOP_SPRINTING перед ударом → отстой → START_SPRINTING;
    // локальный флаг спринта не меняется, спринт визуально не выключается)
    private final HeaderSetting sprintHeader = header("Sprint Reset");
    private final CheckBox sprintReset = checkbox("Legit Sprint Reset", true);
    // Legit (ввод): спринт отпускается самим вводом (на тики отстоя убираем forward/sprint),
    // и ваниль сама шлёт STOP_SPRINTING/START_SPRINTING, ведя wasSprinting без десинхрона.
    // Packet: вручную STOP_SPRINTING перед ударом и START_SPRINTING через отстой.
    private final ModeSetting sprintResetMode = mode("Sprint Reset Mode", "Legit (input)", "Legit (input)", "Packet")
            .visible(sprintReset::getValue);
    private final SliderSetting minStopTicks = slider("Min Stop Ticks", 2.0f, 1.0f, 3.0f, 1.0f).visible(sprintReset::getValue);
    private final SliderSetting maxStopTicks = slider("Max Stop Ticks", 3.0f, 1.0f, 3.0f, 1.0f).visible(sprintReset::getValue);

    // Криты и логика удара
    private final HeaderSetting critHeader = header("Criticals & Hit Logic");
    private final CheckBox criticals = checkbox("Criticals", true);
    private final ModeSetting critMode = mode("Crit Mode", "Smart Crit", "Only Crits", "Smart Crit").visible(criticals::getValue);
    // Минимальная высота падения для удара «критом». Ниже — только обычные удары:
    // сервер считает свою высоту падения сам и с задержкой, поэтому запас сюда
    // = сколько лага мы готовы потерять ради того, чтобы античит не увидел крит
    // «из ниоткуда».
    private final SliderSetting minFallDistance = slider("Min Fall Distance", 0.10f, 0.05f, 0.60f, 0.05f)
            .visible(criticals::getValue);

    // Дополнительно
    private final HeaderSetting dopa = header("Settings");
    private final CheckBox raytrace = checkbox("Raytrace", true);
    private final CheckBox throughWalls = checkbox("Through Walls", false);
    private final CheckBox pauseEating = checkbox("Pause while Eating", true);

    private LivingEntity target = null;
    private Vec3 predictedElytraPos = null;

    public AuraModule() {
        super("AttackAura", "Плавная ротация Smooth / None (TriggerBot) с легитным сбросом спринта и критами", Category.COMBAT);
        INSTANCE = this;
    }

    public boolean isNoneRotation() {
        return rotMode.getValue().equalsIgnoreCase("None");
    }

    @Override
    public void onEnable() {
        AttackHandler.reset();
    }

    @Override
    public void onDisable() {
        target = null;
        predictedElytraPos = null;
        RotationHandler.disengage("Instant");
        AttackHandler.reset();
        Targets.reset();
    }

    public PredictUtils.Type getPredictType() {
        return PredictUtils.Type.DEFAULT;
    }

    public boolean isEating() {
        if (player() == null || !player().isUsingItem()) return false;
        ItemUseAnimation anim = player().getUseItem().getUseAnimation();
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!inGame() || player() == null || mc.gameMode == null) return;
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        // Серверный сброс спринта под крит и счётчик тиков в воздухе — каждый
        // тик, до поиска цели и сборки ввода. Локальный флаг спринта при этом
        // не меняется: спринт не выключается.
        AttackHandler.tickTimers();

        boolean noneRot = isNoneRotation();
        float maxFindDist = noneRot ? attackRange.getValue() : Math.max(attackRange.getValue(), aimRange.getValue());
        target = Targets.findTarget(maxFindDist, targets);

        if (target == null) {
            predictedElytraPos = null;
            RotationHandler.disengage(noneRot ? "Instant" : "Smooth");
            // Тайминги между ударами не трогаем
            return;
        }

        if (noneRot) {
            // Режим None (TriggerBot): ротацию вообще не трогаем, бьём только когда прицел игрока наведён на цель
            if (RotationHandler.isActive()) {
                RotationHandler.clear();
            }
            boolean isVisible = RayTraceUtils.isLookingAt(
                    target,
                    attackRange.getValue(),
                    player().getYRot(),
                    player().getXRot(),
                    !throughWalls.getValue()
            );
            processAttack(isVisible, RotationRegistry.get("None"));
            return;
        }

        TargetPoint targetPoint = UBoxPoints.getBestPoint(target);
        if (targetPoint == null) {
            RotationHandler.disengage("Smooth");
            return;
        }

        Vec3 aimPos = targetPoint.point();
        boolean isVisible = targetPoint.isVisible();

        // Временно невидимы (враг за краем блока) — НЕ разоружаемся: ротация
        // продолжает вести прицел к цели, удар просто пропускается. Disengage
        // здесь стоил бы re-engage таксу (1-3 тика) на каждом мерцании
        // видимости — это и были пропадающие удары.
        if (!this.throughWalls.getValue() && !isVisible) {
            AuraRotation rotation = RotationRegistry.get(rotMode.getValue());
            rotation.tick(player(), target, aimPos, false);
            return;
        }

        AuraRotation rotation = RotationRegistry.get(rotMode.getValue());

        // 1. В начале тика (PRE, до aiStep и sendChanges) проверяем удар по ТЕКУЩЕМУ серверному углу,
        // позиции, fallDistance и состоянию спринта.
        processAttack(isVisible, rotation);

        // 2. Затем обновляем плавную ротацию для текущего тика.
        boolean canAttackSoon = !(pauseEating.getValue() && isEating()) && AttackHandler.isInAttackRange(player(), target, this);
        rotation.tick(player(), target, aimPos, canAttackSoon);
    }

    private void processAttack(boolean isVisible, AuraRotation rotation) {
        if (target == null) return;

        boolean noneRot = isNoneRotation();

        if (pauseEating.getValue() && isEating()) return;

        // Ротация ещё не активна — это НЕ повод пропускать удар: при IDLE
        // серверный угол равен клиентскому, и если прицел уже смотрит на цель,
        // бьём сразу, без таксы re-engage (1-3 тика на каждый engagement).
        float checkYaw = (!noneRot && RotationHandler.isActive()) ? RotationHandler.getServerYaw() : player().getYRot();
        float checkPitch = (!noneRot && RotationHandler.isActive()) ? RotationHandler.getServerPitch() : player().getXRot();

        // Наведение обязательно всегда: бьём только когда прицел (или серверный
        // угол ротации) реально смотрит в хитбокс цели. Никаких ударов «вслепую».
        boolean isAimed = noneRot
                ? isVisible
                : RayTraceUtils.isLookingAt(target, attackRange.getValue(), checkYaw, checkPitch, !throughWalls.getValue());
        if (!isAimed) return;

        boolean canHit = this.throughWalls.getValue() || isVisible;
        if (!canHit) return;

        if (!AttackHandler.shouldAttack(target, this)) return;

        AttackHandler.attack(target, this, rotation);
    }

    /**
     * Убираем forward/sprint из ввода на время отстоя спринта. Прыжок не трогаем.
     * ВАЖНО: гасим и moveVector — именно он двигает игрока в aiStep/travel. Если
     * убрать только keyPresses, клиент (и сервер за ним) продолжит движение
     * вперёд: keyPresses уходит в ServerboundPlayerInputPacket и логику спринта,
     * а реальная остановка видна серверу только через packets движения, т.е.
     * через погашенный moveVector. Так стоп честный: сервер видит замедление
     * и STOP_SPRINTING за 1-2 тика до удара и ещё 1-2 тика после.
     */
    @EventTarget
    public void onInput(PlayerInputEvent event) {
        if (!isEnabled() || player() == null) return;
        if (!AttackHandler.wantsSprintRelease()) return;

        Input current = event.getKeyPresses();
        if (current == null || (!current.forward() && !current.sprint())) return;

        event.setKeyPresses(new Input(
                false,
                current.backward(),
                current.left(),
                current.right(),
                current.jump(),
                current.shift(),
                false
        ));
        Vec2 move = event.getMoveVector();
        if (move != null && move.y > 0.0F) {
            event.setMoveVector(new Vec2(move.x, 0.0F));
        }
    }

    public boolean usesCubesTargetEsp() { return false; }
    public boolean hasTargetEsp() { return false; }
    public boolean usesMarkerTargetEsp() { return false; }
    public boolean usesGhostTargetEsp() { return false; }
    public boolean usesCircleTargetEsp() { return false; }
}
