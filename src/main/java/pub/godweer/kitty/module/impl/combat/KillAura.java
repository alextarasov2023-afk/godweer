package pub.godweer.kitty.module.impl.combat;

import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.Angle;
import pub.godweer.cc.RayTraceUtils;
import pub.godweer.cc.RotationHandler;
import pub.godweer.cc.UBoxPoints;
import pub.godweer.auxiliary.player.MoveUtility;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.enjoy.event.list.WorldJoinEvent;
import pub.godweer.enjoy.event.list.WorldLeaveEvent;
import pub.godweer.enjoy.mixin.accessor.ILocalPlayer;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.*;
import pub.godweer.kitty.sosiski.friend.FriendManager;
import pub.godweer.hud.Notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KillAura = удар + ротация в одном модуле.
 * <p>
 * Ротация «Legit» — то, что делал AimAssist: доворот собственной камеры, никогда не снап.
 * Ротация «Smooth» — угол уходит только на сервер (RotationHandler: камера остаётся там, куда
 * смотрит игрок), ведёт быстро и плавно: сильный пропорциональный доворот с коротким разгоном,
 * инерцией и коррелированным дрейфом вместо белого шума.
 * <p>
 * Коррекция движения работает только там, где угол расходится с камерой, то есть в Smooth:
 * «Free» — ввод доворачивается обратно к камере, идёшь куда смотришь; «Targeted» — компенсации
 * нет, W тянет ровно в цель.
 */
@Getter
public class KillAura extends Module {
    public static KillAura INSTANCE;

    public static final String ROT_SMOOTH = "Smooth";
    public static final String ROT_LEGIT = "Legit";

    public static final String MOVE_FREE = "Free";
    public static final String MOVE_TARGETED = "Targeted";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Профиль Legit: плавный доворот камеры, белым шумом подсыпается слегка.
    private static final float LEGIT_CURVE = 1.40F;
    private static final float LEGIT_FALLOFF = 14.0F;
    private static final float LEGIT_SMOOTHING = 0.65F;
    private static final float LEGIT_RAMP_TICKS = 4.0F;
    private static final float LEGIT_PITCH_RATIO = 0.80F;
    private static final float LEGIT_DEADZONE = 0.15F;
    private static final float LEGIT_JITTER = 0.10F;
    private static final float LEGIT_LEAD = 0.25F;

    // Профиль Smooth: прицел ведётся aggressively, но не снапом — потолок скорости растёт с
    // остатком угла, поэтому 180° сводятся за ~5 тиков, а у самой цели шаг падает почти в ноль.
    private static final float SMOOTH_STRENGTH = 0.60F;
    private static final float SMOOTH_CURVE = 1.05F;
    private static final float SMOOTH_FALLOFF = 10.0F;
    // Доля прошлого шага, оставляемая новому: меньше — резче, больше — вязче.
    private static final float SMOOTH_SMOOTHING = 0.35F;
    private static final float SMOOTH_RAMP_TICKS = 2.0F;
    private static final float SMOOTH_DEADZONE = 0.08F;
    private static final float SMOOTH_LEAD = 0.25F;
    private static final float SMOOTH_ACCEL = 0.32F;
    // Минимальный шаг по оси у цели, градусов за тик.
    private static final float SMOOTH_MIN_STEP = 0.45F;
    // Коррелированный дрейф: «лазерная» прямая линия между тиками — это след софта.
    private static final float SMOOTH_DRIFT = 0.05F;
    // Смена цели внутри этого угла (°) считается «довернуть некуда»: инерция и разгон
    // ротации сохраняются, иначе каждый переход отбрасывает ease-in на самый старт.
    private static final double SWITCH_MOMENTUM_ANGLE = 25.0D;
    // Сколько тиков можно ждать сброса серверного спринта, прежде чем бить без крита.
    private static final int SPRINT_WAIT_MAX_TICKS = 6;
    // Столько тиков без единой видимой точки — и липкая цель меняются местами.
    private static final int AIM_BLOCKED_TICKS = 3;
    // Сколько тиков держим уже готовый удар в ожидании окна крита, прежде чем бить
    // без крита. Без этого лимита гейт «только криты» закрывает удар бесконечно.
    private static final int NO_CRIT_HOLD_TICKS = 4;
    // Потолок ожидания крита при зажатом прыжке. Ниже секунды: обычный прыжок-падение
    // укладывается целиком, а дальше аура возвращается к обычным настройкам — молчать
    // из-за залипшей клавиши хуже, чем удар без крита.
    private static final int JUMP_HOLD_MAX_TICKS = 18;
    // Дознание «аура молчит». Цель в радиусе удара, наведение есть, а удара нет больше
    // этих тиков — значит залип какой-то гейт, а не нормальное ожидание (между ударами
    // максимум ~11 тиков кулдауна + окно крита).
    private static final int SILENT_HEAL_TICKS = 25;
    private static final int SILENT_TARGET_TICKS = 50;
    private static final int SILENT_FULL_TICKS = 100;
    // Линия видимости может быть закрыта законно и надолго (цель за блоком), поэтому
    // «aim» считается зависанием только после гораздо более длинного срока.
    private static final int SILENT_AIM_TICKS = 150;
    // Подсказка про «используется предмет» — не сразу: короткая еда/глоток зелья это
    // нормальная пауза, а вот секунды молчания без объяснения игрок читает как поломку.
    private static final int USE_HINT_TICKS = 40;
    // Дозволы по высоте силуэта для проверки видимости: грудь, макушка, ноги.
    private static final double[] AIM_HEIGHTS = {0.65D, 0.9D, 0.3D};
    // Заряд, при котором крит ещё засчитывается полной силы (11/12 тика из 12).
    private static final float CRIT_CHARGE = 0.848F;
    // Полка серверной reach-проверки: дистанция считается от глаз до точки в хитбоксе,
    // ванильный порог ~3.0. Всё, что выше, античит читает как Reach-Hitbox.
    private static final float MAX_SAFE_REACH = 2.92F;

    private final HeaderSetting targetHeader = header("Targets");
    // Дальность удара: дальше этого расстояния удар не выпускаем, даже если цель на прицеле.
    private final SliderSetting attackReach = slider("Attack Reach", 3.2f, 1.0f, 6.0f, 0.1f);
    // Дальность наводки: захват и ротация работают дальше, чем бьёт удар.
    private final SliderSetting aimRange = slider("Aim Range", 4.5f, 1.5f, 8.0f, 0.1f);
    // На элитрах своя дальность захвата: скорость выше, цель нужно брать заранее и издалека.
    private final SliderSetting glideAimRange = slider("Glide Aim Range", 8.0f, 1.5f, 16.0f, 0.5f);
    // 360 = цель берётся в любом направлении, включая за спиной.
    private final SliderSetting fov = slider("FOV", 360.0f, 15.0f, 360.0f, 5.0f);
    // Пауза перед поиском НОВОЙ цели широким сканом — после того, как прошлая отпущена.
    // Работает только для скана «цели в радиусе наводки»: того, кого можно ударить этим
    // тиком, берём без паузы вовсе. 250 мс из прошлой версии = 5 тиков пустоты в бою.
    private final SliderSetting switchDelay = slider("Switch Delay", 40.0f, 0.0f, 250.0f, 10.0f);
    // Категории целей. Друзей здесь нет отдельным пунктом: они отделены своим
    // переключателем, чтобы случайная галка в списке не превращала друга в цель.
    private final MultiModeSetting targets = multiMode("Targets", List.of("Players", "Mobs"),
            "Players", "Mobs", "Animals", "Villagers", "Golems", "Water", "Ambient", "Naked");
    private final CheckBox targetFriends = checkbox("Target Friends", false);
    // Критерий выбора, когда в конусе сразу несколько целей.
    private final ModeSetting priority = mode("Priority", "Angle", "Angle", "Distance", "Health");
    private final CheckBox ignoreInvisible = checkbox("Ignore Invisible", false);
    private final CheckBox ignoreSleeping = checkbox("Ignore Sleeping", false);
    private final CheckBox ignoreRiding = checkbox("Ignore Riding", false);
    // Бить только тех, чьё здоровье не выше этого процента; 100 = фильтр выключен.
    private final SliderSetting belowHealth = slider("Health Below %", 100.0f, 10.0f, 100.0f, 5.0f);
    private final ModeSetting aimPoint = mode("Aim Point", "Multipoint",
            "Multipoint", "Head", "Chest", "Middle", "Legs");

    private final HeaderSetting rotationHeader = header("Rotation");
    // Профиль каждого режима зашит в константы: в GUI остаются только те ручки,
    // которые реально крутят в бою, и только от своего режима.
    private final ModeSetting rotation = mode("Rotation", ROT_SMOOTH, ROT_SMOOTH, ROT_LEGIT);

    // --- Smooth: тихий серверный угол ---
    // Пол потолка скорости серверного угла; выше остатка угла шаг растёт сам
    private final SliderSetting smoothSpeed = slider("Speed", 25.0f, 5.0f, 60.0f, 1.0f)
            .visible(this::isSmooth);
    // Как движение соотносится с серверным углом, пока он расходится с камерой
    private final ModeSetting movement = mode("Movement", MOVE_FREE, MOVE_FREE, MOVE_TARGETED)
            .visible(this::isSmooth);

    // --- Legit: доворот собственной камеры ---
    // Сила доворота за тик, % от полного угла
    private final SliderSetting strength = slider("Strength", 55.0f, 5.0f, 100.0f, 1.0f)
            .visible(this::isLegit);
    // Потолок скорости камеры, градусов за тик
    private final SliderSetting maxSpeed = slider("Max Speed", 7.0f, 1.0f, 30.0f, 0.5f)
            .visible(this::isLegit);

    private final HeaderSetting timingHeader = header("Hit Timing");
    // Кулдаун между ударами в миллисекундах, но шаг = 50 мс, то есть ровно тик:
    // 400/450/500/550 => 8/9/10/11 тиков. Гейт по счётчику тиков, а не по wall-clock:
    // иначе удар уходил на первом тике ПОСЛЕ истечения окна и окно раздувалось.
    private final SliderSetting attackDelay = slider("Attack Delay", 450.0f, 350.0f, 550.0f, 50.0f);
    // 100% = удар ровно на полном заряде. Ниже — удар раньше, ценой урона: сервер
    // масштабирует урон по шкале заряда. Для крита этот же пол заменяет 84.8%.
    private final SliderSetting minCharge = slider("Min Charge", 90.0f, 60.0f, 100.0f, 1.0f);

    private final HeaderSetting critHeader = header("Criticals");
    // Режим Smart (бить и на земле) убран: удар выпускается только когда падение
    // засчитается сервером как крит.
    // Сколько тиков падения должно пройти до удара. Пакет позиции текущего тика
    // уходит ПОСЛЕ PRE, поэтому в первом тику падения сервер ещё видит свою
    // копию «земля / fallDistance = 0», его крит-проверка не проходит и удар
    // считается обычным.
    private final SliderSetting critFallTicks = slider("Crit Fall Ticks", 2.0f, 1.0f, 4.0f, 1.0f);
    // Удар без крита. Выключенный не запрещает удар на земле — он даёт окну падения
    // NO_CRIT_HOLD_TICKS тиков, и только потом бьёт обычным.
    private final CheckBox hitWithoutCrit = checkbox("Hit Without Crit", true);
    // Пока зажата клавиша прыжка (пробел или своя в настройках управления), аура не бьёт
    // обычным ударом: ждёт падения, которое засчитается критом. Дедлайн в JUMP_HOLD_MAX_TICKS
    // оставляем — залипшая клавиша не должна запирать ауру без удара совсем.
    private final CheckBox critOnlyOnJump = checkbox("Crit Only On Jump", true);

    // --- сброс спринта под крит ---
    private final HeaderSetting sprintHeader = header("Sprint Reset");
    private final ModeSetting sprintReset = mode("Sprint Reset", "Legit", "None", "Legit", "Fast");
    // Нижняя гарантия: столько тиков ввод держим без forward/sprint обязательно
    private final SliderSetting sprintMinStopTicks = slider("Min Stop Ticks", 2.0f, 1.0f, 4.0f, 1.0f)
            .visible(() -> !sprintReset.getValue().equals("None"));
    // Потолок: дольше не стоим, даже если сервер всё ещё считает нас бегущими
    private final SliderSetting sprintMaxStopTicks = slider("Max Stop Ticks", 4.0f, 1.0f, 8.0f, 1.0f)
            .visible(() -> !sprintReset.getValue().equals("None"));

    private final HeaderSetting extraHeader = header("Settings");
    private final CheckBox pauseWhileUsing = checkbox("Pause while Using", true);
    private final CheckBox throughWalls = checkbox("Through Walls", false);
    private final CheckBox axeShieldBreak = checkbox("Axe Shield Break", false);
    // Показывать в уведомления, чем именно аура завислась, пока она сама себя чинит.
    // Сбросы работают и с выключенным пунктом — галка только про текст на экране.
    private final CheckBox watchDogNotify = checkbox("Watchdog Notify", true);

    private LivingEntity target = null;

    // Отпущенная цель — по её положению решаем, был ли переход «рядом» и нужен ли вообще
    // новый разгон доворота.
    private LivingEntity previousTarget = null;

    private LivingEntity hoverTarget = null;
    // Абсолютный игровой тик, начиная с которого удар снова разрешён.
    private long readyTick = 0L;

    // Локальный счётчик PRE-тиков. Кулдаун между ударами считаем по нему, а НЕ по
    // level().getGameTime(): новый сервер при переходе присылает СВОЁ время уровня, и
    // getGameTime() откатывается назад — readyTick из прошлого мира остаётся в далёком
    // будущем, гейт «gameTime < readyTick» закрывается надолго, и аура с идеальным
    // наведением молчит. Откатиться локальный счётчик не может.
    private long localTick = 0L;

    // Игрок, под которым аура уже всё сбросила. LocalPlayer пересоздаётся при смене
    // мира, респавне и Bungee/Velocity-переходе; сверка ссылки ловит это надёжнее, чем
    // события setLevel (переход через прокси их не всегда порождает).
    private LocalPlayer lastPlayer = null;

    // Сколько тиков подряд цель в радиусе удара, а удара нет, и чем закончился прошлый тик.
    private int silentTicks = 0;
    private String blockedBy = "none";
    // Подсказку про предмет показываем один раз за эпизод молчания, а не каждые 40 тиков.
    private boolean useHintShown = false;

    private int shieldTicks = 0;
    private int axeStage = 0;
    private int previousSlot = -1;

    // Тики падения подряд по состоянию на начало тика (0 = стоим на земле).
    private int fallingTicks = 0;

    // Сколько тиков подряд цели нет ни одной видимой точки прицела (стена между нами).
    private int blindTicks = 0;

    // Сколько тиков подряд ждём, пока сервер перестанет считать нас бегущими.
    private int sprintWaitTicks = 0;

    // Сколько тиков держим готовый удар в ожидании крита, прежде чем бить без него.
    private int noCritTicks = 0;

    // Сколько тиков подряд зажата клавиша прыжка без падения — ждём крит.
    private int jumpHoldTicks = 0;

    // Сколько тиков ещё держим ввод без forward/sprint (легитный сброс спринта).
    // Снимаем не «N тиков», а ровно до тех пор, пока сервер не перестал считать нас
    // бегущими: wasSprinting == false означает, что STOP_SPRINTING уже ушёл в канал.
    private int sprintReleaseTicks = 0;

    // --- состояние ротации
    // Угол, который сервер УЖЕ получил: пакет движения этого тика уходит после PRE,
    // а interact удара — внутри PRE, значит удар сервер судит по прошлому углу.
    private float sentYaw = 0.0F;
    private float sentPitch = 0.0F;
    private long switchBlockedUntilMs = 0L;
    private int engagedTicks = 0;
    private float velYaw = 0.0F;
    private float velPitch = 0.0F;
    private float driftYaw = 0.0F;
    private float driftPitch = 0.0F;

    // Первый тик после смены мира: углы ротации берём из нового игрока, а не из памяти
    // старого уровня.
    private boolean rotationResync = false;

    public KillAura() {
        super("KillAura", "Аура с ротацией: Smooth (тихий серверный угол) и Legit (доворот камеры)", Category.COMBAT);
        INSTANCE = this;
    }

    /** Всё, что не Legit, — тихая серверная ротация (и старый «Human» из конфигa тоже). */
    public boolean isSmooth() {
        return !rotation.is(ROT_LEGIT);
    }

    public boolean isLegit() {
        return rotation.is(ROT_LEGIT);
    }

    /**
     * Дальность наводки: на элитрах своя — там и скорость другая, и цель надо брать
     * заранее, пока она не прошла мимо.
     */
    private double aimRangeNow(LocalPlayer player) {
        return player.isFallFlying() ? glideAimRange.get() : aimRange.get();
    }

    @Override
    public void onDisable() {
        target = null;
        previousTarget = null;
        hoverTarget = null;
        lastPlayer = null;
        silentTicks = 0;
        blockedBy = "none";
        shieldTicks = 0;
        sprintReleaseTicks = 0;
        sprintWaitTicks = 0;
        fallingTicks = 0;
        blindTicks = 0;
        noCritTicks = 0;
        jumpHoldTicks = 0;
        clearRotationState();
        RotationHandler.disengage("Smooth");
        restoreAxeSlot();
    }

    /**
     * Смена сервера/мира: игровое время начинается заново, цели старого мира — мёртвые
     * ссылки, а ротация и счётчики держат старое состояние. Без сброса аура на новом
     * сервере молчит: гейт gameTime < readyTick никогда не открывается, если время
     * нового мира меньше сохранённого readyTick.
     */
    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        resetWorldState();
    }

    @EventTarget
    public void onWorldJoin(WorldJoinEvent event) {
        resetWorldState();
    }

    /** Полный сброс привязанного к миру состояния; сам модуль включённым остаётся. */
    private void resetWorldState() {
        target = null;
        previousTarget = null;
        hoverTarget = null;
        readyTick = 0L;
        silentTicks = 0;
        blockedBy = "none";
        useHintShown = false;
        shieldTicks = 0;
        axeStage = 0;
        previousSlot = -1;
        fallingTicks = 0;
        blindTicks = 0;
        sprintWaitTicks = 0;
        noCritTicks = 0;
        jumpHoldTicks = 0;
        sprintReleaseTicks = 0;
        switchBlockedUntilMs = 0L;
        engagedTicks = 0;
        velYaw = 0.0F;
        velPitch = 0.0F;
        driftYaw = 0.0F;
        driftPitch = 0.0F;
        sentYaw = 0.0F;
        sentPitch = 0.0F;
        // Только в прошлый раз: «плавно отпустить» нельзя — возвращать камеру некуда,
        // это угол игрока из уже закрытого мира.
        RotationHandler.resetToPlayer(player());
        rotationResync = true;
        restoreAxeSlot();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!isEnabled() || player() == null || level() == null || gameMode() == null) return;
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        tickTimers();
        localTick++;

        LocalPlayer player = player();
        if (player != lastPlayer) {
            // LocalPlayer пересоздаётся при смене мира, респавне и переходе через прокси.
            // Новая ссылка — это уже другой игрок: серверные углы, id целей и readyTick
            // от старого ничего не стоят, а события setLevel такие переходы ловят не
            // всегда (Bungee/Velocity умеет менять уровень мимо них).
            lastPlayer = player;
            resetWorldState();
        }

        Vec3 eyes = player.getEyePosition();

        // Молчание считаем только пока цель реально в радиусе удара: ждать, пока враг
        // подойдёт, — это не зависание.
        silentTicks = (target != null && !beyond(target, eyes, hitReach())) ? silentTicks + 1 : 0;

        if (rotationResync) {
            // Новый мир: обработчик ротации мог остаться активным от цели прошлого уровня,
            // и до первого доворота конус захвата и пакеты считались бы по мёртвым углам.
            rotationResync = false;
            RotationHandler.resetToPlayer(player);
        }
        // Снимаем серверный угол до этого доворота: именно с ним пакет удара уйдёт
        // раньше, чем обновится пакет позиции.
        sentYaw = sendYaw(player);
        sentPitch = sendPitch(player);
        boolean falling = player.fallDistance > 0.0F && !player.onGround();
        fallingTicks = falling ? fallingTicks + 1 : 0;

        LivingEntity hit = pickTarget(player, eyes);
        if (hit == null) {
            blockedBy = "no target";
            silentTicks = 0;
            releaseRotation();
            hoverTarget = null;
            shieldTicks = 0;
            return;
        }
        target = hit;

        if (hoverTarget != target) {
            hoverTarget = target;
            shieldTicks = 0;
        }

        // true — сброс был жёстким (цель отпущена), этот тик пропускаем.
        if (watchDog(player)) return;

        rotate(player, eyes, target);

        // Отстой спринта армимся на подъёме прыжка, независимо от окна удара: флаг
        // должен погаснуть К ТОМУ моменту, как удар будет можно, а не начинаться в
        // нём — иначе STOP_SPRINTING не успевает дойти до сервера и крит теряется.
        if (!falling && !player.onGround()) {
            armSprintReleaseOnRise(player);
        }

        if (isPausedByUsing()) {
            blockedBy = "use";
            return;
        }

        if (axeShieldBreak.getValue() && target.isBlocking()) shieldTicks++;
        else shieldTicks = 0;

        long gameTime = localTick;
        if (gameTime < readyTick) {
            blockedBy = "cooldown";
            return;
        }

        // --- смена на топор, если цель долго держит щит ---
        if (axeShieldBreak.getValue() && shieldTicks >= 5 && axeStage == 0) {
            int slot = findAxeSlot();
            int current = player.getInventory().getSelectedSlot();
            if (slot != -1 && slot != current) {
                previousSlot = current;
                setSlot(slot);
                axeStage = 1;
                blockedBy = "axe slot";
                return; // один тик на смену слота, удар в следующем
            }
        }

        // Удар выпускаем по углу, который сервер уже видит, и в дистанции ниже
        // ванильного порога: «на 0.2 дальше» — это ровно Reach-Hitbox, за который
        // и кикают. Стены же серверной проверке удара не нужны — важна только
        // дистанция, поэтому сквозь стены бьём ровно тогда, когда включено Through
        // Walls. Выключенное Through Walls оставляем с жёсткой линией видимости.
        float hitReach = (float) hitReach();
        if (!RayTraceUtils.isLookingAt(target, hitReach, sentYaw, sentPitch,
                !throughWalls.getValue())) {
            blockedBy = "aim";
            return;
        }

        boolean exception = isCritException(player);
        // Зажатый прыжок при исключениях (элитры, вода, лестница, пассажир) крита не даёт
        // в принципе, поэтому условие работает только там, где падение возможно.
        boolean jumpHeld = critOnlyOnJump.getValue() && !exception && isJumpHeld();
        jumpHoldTicks = (jumpHeld && !falling) ? jumpHoldTicks + 1 : 0;

        if (!exception) {
            if (falling) {
                noCritTicks = 0;
                // Крит бьём только по «созревшему» падению: пока сервер не получил
                // пакет позиции уже в воздухе, его проверка fallDistance > 0 &&
                // !onGround не проходит и удар уходит обычным.
                if (fallingTicks < (int) critFallTicks.get()) {
                    blockedBy = "crit fall";
                    return;
                }
            } else if (jumpHeld && jumpHoldTicks < JUMP_HOLD_MAX_TICKS) {
                // Игрок держит прыжок — значит он собирается (или уже) падать, и обычный
                // удар в этой паузе только сжигает кри-цикл. Ждём, но с дедлайном.
                noCritTicks = 0;
                blockedBy = "jump hold";
                return;
            } else if (hitWithoutCrit.getValue()) {
                noCritTicks = 0;
            } else if (++noCritTicks < NO_CRIT_HOLD_TICKS) {
                // Окно крита ждём, но с дедлайном. Прежний вариант «выключил галку —
                // жди падения вечно» запирал удар на земле: цель подходит по земле,
                // аура наведена и молчит, пока сам не прыгнешь.
                blockedBy = "crit window";
                return;
            }
        } else {
            noCritTicks = 0;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && player.getCooldowns().isOnCooldown(mainHand)) {
            blockedBy = "item cooldown";
            return;
        }

        // Заряд: в падении (крит) достаточно CRIT_CHARGE, на земле и при исключениях —
        // Min Charge. Min Charge ниже 84.8% ускоряет и критический удар тоже: он снимает
        // тик-другой с паузы ценой части урона (сервер масштабирует урон по заряду).
        float required = (falling && !exception)
                ? Math.min(CRIT_CHARGE, minCharge.get() / 100.0F)
                : minCharge.get() / 100.0F;
        boolean bypassCharge = axeStage == 1;
        if (!bypassCharge && player.getAttackStrengthScale(0.0F) < required) {
            blockedBy = "charge";
            return;
        }

        // --- сброс спринта под крит: бьём ровно в том тику, где сервер перестал
        // считать нас бегущими. Ждать «N тиков отстоя» не нужно вовсе.
        // Выключенный сброс не должен задерживать удар вообще.
        if (!sprintReset.getValue().equals("None") && falling && isServerSprinting(player)) {
            keepSprintReleaseArmed(player);
            // Ожидание не должно быть вечным: если флаг не гаснет (ввод уже отпущен,
            // серверный STOP не уходит), молчание хуже удара без крита.
            if (++sprintWaitTicks < SPRINT_WAIT_MAX_TICKS) {
                blockedBy = "sprint reset";
                return;
            }
        }
        sprintWaitTicks = 0;

        attack(player, gameTime);
    }

    /**
     * До́знание «всё нормально, но не бьёт». Цель в радиусе удара, наведение есть, а
     * удара нет дольше {@link #SILENT_HEAL_TICKS} тиков — значит залип какой-то гейт, а
     * не идёт легитимное ожидание. Лестница деградации от дешёвого к дорогому:
     * сброс серверного угла и кулдауна (именно так выглядят мёртвый угол после перехода
     * и откатившееся время уровня) → отпускание цели (её id старый сервер уже не знает) →
     * полный сброс состояния мира.
     * <p>
     * Античит-полки этим не снимаются: reach 2.92, линия видимости и требования заряда
     * остаются обязательными — молчание лечится состоянием, а не разрешением бить дальше.
     *
     * @return true, если сброс был жёстким (цель отпущена) и этот тик бить нечем
     */
    private boolean watchDog(LocalPlayer player) {
        if (silentTicks < SILENT_HEAL_TICKS) return false;

        // «Используется предмет» — легитимная пауза (еда, поднятый щит), сбросами её
        // лечить нельзя: игрок держит щит столько, сколько хочет. Но молчание без
        // объяснения выглядит как поломка, поэтому один раз за эпизод подсказываем
        // причину прямо на экран.
        if (blockedBy.equals("use")) {
            if (!useHintShown && silentTicks >= USE_HINT_TICKS && watchDogNotify.getValue()) {
                useHintShown = true;
                Notifications.push("KillAura ждёт: используется предмет (Pause while Using)");
            }
            return false;
        }
        if (!stuckByState()) return false;

        String reason = blockedBy;
        boolean hard = silentTicks >= SILENT_TARGET_TICKS;
        int window = silentTicks >= SILENT_FULL_TICKS ? SILENT_FULL_TICKS
                : silentTicks >= SILENT_TARGET_TICKS ? SILENT_TARGET_TICKS : SILENT_HEAL_TICKS;

        if (silentTicks >= SILENT_FULL_TICKS) {
            resetWorldState();
            sentYaw = sendYaw(player);
            sentPitch = sendPitch(player);
        } else if (hard) {
            releaseTarget();
            readyTick = localTick;
        } else {
            RotationHandler.resetToPlayer(player);
            rotationResync = true;
            readyTick = localTick;
            sentYaw = sendYaw(player);
            sentPitch = sendPitch(player);
        }

        silentTicks = 0;
        blockedBy = "watchdog";

        if (watchDogNotify.getValue()) {
            Notifications.push("KillAura: молчание " + window + " тиков (" + reason + ") — сброшено");
        }
        return hard;
    }

    /**
     * Залип по состоянию, а не нормальное ожидание. Окна крита, прыжка и «цели нет»
     * сюда сознательно не входят: они сами упираются в свои дедлайны, а цель за блоком
     * может законно не пробиваться очень долго — её считаем зависшей только после
     * {@link #SILENT_AIM_TICKS}.
     */
    private boolean stuckByState() {
        if (blockedBy.equals("aim")) return silentTicks >= SILENT_AIM_TICKS;
        return switch (blockedBy) {
            case "cooldown", "charge", "item cooldown", "sprint reset" -> true;
            default -> false;
        };
    }

    private void attack(LocalPlayer player, long gameTime) {
        readyTick = gameTime + delayTicks();
        noCritTicks = 0;
        silentTicks = 0;
        blockedBy = "none";
        useHintShown = false;

        // После удара отстой не держим: серверный крит считается по последнему
        // пакету движения, то есть по тому, что ушло ДО удара, а лишние тики
        // стояния только отодвигают следующий прыжок и весь кри-цикл.
        sprintReleaseTicks = 0;

        // Локальную симуляцию удара считаем без спринта, чтобы клиентские критовые
        // партиклы совпадали с серверным результатом.
        boolean sprinting = player.isSprinting();
        player.setSprinting(false);
        gameMode().attack(player, target);
        player.setSprinting(sprinting);

        SwingAnimation swingAnim = player.getMainHandItem().getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, swingAnim, false);

        restoreAxeSlot();
    }

    /**
     * Убираем forward/sprint из ввода на время отстоя. Гасим и moveVector: keyPresses
     * уходит в пакет ввода и в логику спринта, а реальную остановку сервер видит только
     * по пакетам движения, т.е. по погашенному moveVector.
     * <p>
     * Коррекция движения считается ДО отстоя и после неё уже ничего не разгоняем:
     * Free — «идёшь куда смотришь» (компенсацию клавиш делает KeyboardInputMixin),
     * Targeted — «вжимаешься в цель»: клавиши пересчитываются от серверного угла так,
     * чтобы вектор тянул ровно в таргет. Legit не трогает вовсе: там камера и есть
     * серверный угол, корректировать нечего.
     */
    @EventTarget
    public void onInput(PlayerInputEvent event) {
        if (!isEnabled() || player() == null) return;

        if (isSmooth() && movement.is(MOVE_TARGETED) && target != null && RotationHandler.isActive()) {
            MoveUtility.fixMovementFocus(event, target);
        }

        if (sprintReleaseTicks <= 0) return;

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

    // ------------------------------------------------------------------ ротация

    private void rotate(LocalPlayer player, Vec3 eyes, LivingEntity entity) {
        boolean smooth = isSmooth();
        if (!smooth && RotationHandler.isActive()) {
            RotationHandler.disengage("Smooth");
        }

        Vec3 point = aimPosition(entity, eyes);
        if (point == null) {
            // Ни одной видимой точки: цель стоит между нами блоком. Считаем тики
            // слепоты — по ним липкая цель уступает место той, по которой можно бить.
            blindTicks++;
            return;
        }
        blindTicks = 0;

        Angle aim = Angle.fromVec(eyes, point);
        float wantYaw = aim.getYaw();
        float wantPitch = aim.getPitch();

        float curYaw = sendYaw(player);
        float curPitch = sendPitch(player);
        float deltaYaw = Mth.wrapDegrees(wantYaw - curYaw);
        float deltaPitch = wantPitch - curPitch;
        float error = (float) Math.hypot(deltaYaw, deltaPitch);

        // Мёртвая зона: не доворачиваем то, что и так попали, иначе прицел звенит.
        float dead = smooth ? SMOOTH_DEADZONE : LEGIT_DEADZONE;
        if (error < dead) {
            velYaw *= 0.35F;
            velPitch *= 0.35F;
            return;
        }

        float strengthPct = smooth ? SMOOTH_STRENGTH : strength.get() / 100.0F;
        float curveExp = smooth ? SMOOTH_CURVE : LEGIT_CURVE;
        float falloffDeg = smooth ? SMOOTH_FALLOFF : LEGIT_FALLOFF;

        // Нелинейность: сила падает как степень от доли ошибки, оставшейся до цели.
        float t = Mth.clamp(error / Math.max(1.0F, falloffDeg), 0.0F, 1.0F);
        double factor = strengthPct * Math.pow(t, curveExp);
        double ramp = ramp(smooth);

        // Legit: питч догоняем чуть медленнее ява, иначе камера задирает голову.
        float pitchWeight = smooth ? 1.0F : LEGIT_PITCH_RATIO;
        float wantStepYaw = (float) (deltaYaw * factor * ramp);
        float wantStepPitch = (float) (deltaPitch * factor * ramp * pitchWeight);

        // Инерция: экспоненциальное сглаживание шага по каждой оси.
        float smoothing = smooth ? SMOOTH_SMOOTHING : LEGIT_SMOOTHING;
        velYaw = Mth.lerp(smoothing, wantStepYaw, velYaw);
        velPitch = Mth.lerp(smoothing, wantStepPitch, velPitch);

        // Пропорциональный шаг у цели ползёт по десятым градуса. Для «быстро и плавно»
        // держим пол, но не больше четверти остатка угла — иначе перелёт и звон на месте.
        if (smooth) {
            float floor = Math.min(SMOOTH_MIN_STEP, error * 0.25F);
            velYaw = floorStep(velYaw, deltaYaw, floor);
            velPitch = floorStep(velPitch, deltaPitch, floor);
        }

        // Legit: жёсткий потолок °/тик, иначе камера крутится винтом.
        // Smooth: потолок растёт с остатком угла, но ограничен 1.6 от настройки Speed:
        // без полки 180° сводятся снапом за пару тиков, и это читается как aim-флаг.
        float limit = smooth
                ? Math.min(Math.max(smoothSpeed.get(), error * SMOOTH_ACCEL), smoothSpeed.get() * 1.6F)
                  * (float) ramp
                : maxSpeed.get() * (float) ramp;
        velYaw = Mth.clamp(velYaw, -limit, limit);
        velPitch = Mth.clamp(velPitch, -limit, limit);

        if (smooth) {
            applyDrift();
        } else {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            velYaw += (float) random.nextDouble(-LEGIT_JITTER, LEGIT_JITTER);
            velPitch += (float) random.nextDouble(-LEGIT_JITTER, LEGIT_JITTER);
        }

        // Шаг всегда квантуем под GCD мыши: неквантованные доли градуса — это след
        // софта, а не руки.
        velYaw = quantize(velYaw);
        velPitch = quantize(velPitch);

        if (Math.abs(velYaw) < 1.0E-4F && Math.abs(velPitch) < 1.0E-4F) return;

        if (smooth) {
            // Тихий режим: угол уходит на сервер, камера остаётся у игрока.
            RotationHandler.setRotation(curYaw + velYaw, curPitch + velPitch);
        } else {
            float newYaw = Mth.wrapDegrees(player.getYRot() + velYaw);
            float newPitch = Mth.clamp(player.getXRot() + velPitch, -90.0F, 90.0F);
            player.setYRot(newYaw);
            player.setXRot(newPitch);
            player.yHeadRot = newYaw;
        }
        engagedTicks++;
    }

    /**
     * Коррелированный дрейф (OU-процесс): шаг шума зависит от предыдущего. Белый шум
     * по тикам — как раз то, что детектят как «не-мышиную» ротацию.
     */
    private void applyDrift() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        driftYaw = driftYaw * 0.72F + (float) random.nextDouble(-SMOOTH_DRIFT, SMOOTH_DRIFT) * 0.28F;
        driftPitch = driftPitch * 0.72F + (float) random.nextDouble(-SMOOTH_DRIFT, SMOOTH_DRIFT) * 0.28F;
        velYaw += driftYaw;
        velPitch += driftPitch;
    }

    /** Пол скорости по оси: не форсируем, если остаток по этой оси меньше самого пола. */
    private static float floorStep(float step, float delta, float min) {
        float floor = Math.min(min, Math.abs(delta));
        if (floor <= 0.0F || Math.abs(step) >= floor) return step;
        float sign = step != 0.0F ? Math.signum(step) : Math.signum(delta);
        return sign * floor;
    }

    /** Квадратичный ease-in: первый тик почти ничего, дальше — доворот до полной силы. */
    private double ramp(boolean smooth) {
        float total = smooth ? SMOOTH_RAMP_TICKS : LEGIT_RAMP_TICKS;
        if (total <= 0.0F) return 1.0D;
        double p = Math.min(1.0D, (engagedTicks + 1.0D) / total);
        return p * p;
    }

    private static float quantize(float delta) {
        if (mc.options == null) return delta;
        double sens = mc.options.sensitivity().get() * 0.6D + 0.2D;
        double gcd = sens * sens * sens * 1.2D;
        if (gcd < 1.0E-4D) return delta;
        return (float) (Math.round(delta / gcd) * gcd);
    }

    /** Угол, который видит сервер: свой при тихой ротации, обычный при Legit. */
    private static float sendYaw(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
    }

    private static float sendPitch(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();
    }

    /** Угол, куда реально смотрит игрок (камера), — для захвата цели по конусу. */
    private static float viewYaw(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getFreeYaw() : player.getYRot();
    }

    private static float viewPitch(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getFreePitch() : player.getXRot();
    }

    private void releaseRotation() {
        target = null;
        engagedTicks = 0;
        velYaw = 0.0F;
        velPitch = 0.0F;
        if (RotationHandler.isActive()) RotationHandler.disengage("Smooth");
    }

    private void clearRotationState() {
        engagedTicks = 0;
        velYaw = 0.0F;
        velPitch = 0.0F;
        driftYaw = 0.0F;
        driftPitch = 0.0F;
    }

    // ----------------------------------------------------------------------- цели

    /**
     * Липкая цель: пока текущая жива, в радиусе наводки и внутри конуса — не ищем новую,
     * иначе ротация начинает дёргаться между двумя игроками.
     *
     * Порядок захвата для скорости: сначала ванильный пик прицела (игрок навёл сам),
     * потом тот, кого реально можно ударить ЭТИМ тиком, и только затем широкий скан по
     * радиусу наводки — он-то и блокируется Switch Delay. Прежняя версия шла сразу в
     * широкий скан, выбирала лучшего «в конусе» независимо от дистанции удара и линии
     * видимости, залипала на цели за блоком и теряла тики на доворот в неё.
     */
    private LivingEntity pickTarget(LocalPlayer player, Vec3 eyes) {
        double lockFov = fov.get();
        double lockRange = aimRangeNow(player);

        if (target != null && (target.level() != level() || target.isRemoved() || !target.isAlive()
                || beyond(target, eyes, lockRange)
                || angleTo(eyes, target, player) > lockFov)) {
            releaseTarget();
        }
        if (target != null) {
            // Липкость дешевле доворота, но наводится она по радиусу наводки, а бьёт —
            // только по радиусу удара. Если текущая цель вне удара, а рядом та, до которой
            // реально достать, переключаемся сразу: иначе аура целится в недосягаемого.
            if (beyond(target, eyes, hitReach())) {
                LivingEntity reachable = findReachable(player, eyes);
                if (reachable != null) return engage(reachable);
            }
            // Те же грабли, когда цель видна, но закрыта блоком: наведение уходит в null,
            // удар не выходит, а липкая цель никогда не отпускается сама.
            if (blindTicks >= AIM_BLOCKED_TICKS) {
                LivingEntity aimable = findReachable(player, eyes);
                if (aimable != null && aimable != target) return engage(aimable);
            }
            return target;
        }

        if (!throughWalls.getValue()
                && mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                && living != player && acceptable(living)
                && !beyond(living, eyes, lockRange)) {
            return engage(living);
        }

        LivingEntity inReach = findReachable(player, eyes);
        if (inReach != null) return engage(inReach);

        if (System.currentTimeMillis() < switchBlockedUntilMs) return null;

        Vec3 dir = RayTraceUtils.getLookVec(viewYaw(player), viewPitch(player));
        AABB area = player.getBoundingBox().inflate(lockRange + 1.0D);

LivingEntity best = null;
        double bestKey = Double.MAX_VALUE;
        List<LivingEntity> candidates = new ArrayList<>(level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player));
        for (Player p : level().players()) {
            if (p != player && p.getBoundingBox().intersects(area) && !candidates.contains(p)) {
                candidates.add(p);
            }
        }
        for (LivingEntity living : candidates) {
            if (!acceptable(living)) continue;
            // Скан «наведение впрок» не имеет права брать цель, в которую нельзя
            // навеститься: без этого фильтры Targets и молчание удара выглядят как
            // «аура сама выбрала того, кого не достать».
            if (!hasAimPoint(living, eyes)) continue;

            double angle = angleTo(eyes, living, player);
            if (angle > lockFov) continue;

            // Центр за спиной формально «в конусе» по углу — при узком FOV такое
            // отрезаем; при широком (360) цель за спиной как раз и нужна.
            Vec3 toCenter = living.getBoundingBox().getCenter().subtract(eyes);
            if (lockFov < 180.0D && toCenter.dot(dir) <= 0.0D) continue;

            double key = priorityKey(living, angle, toCenter.length());
            if (key < bestKey) {
                bestKey = key;
                best = living;
            }
        }

        return best == null ? null : engage(best);
    }

    private LivingEntity engage(LivingEntity entity) {
        LocalPlayer player = player();
        // Переход между целями, которые стоят рядом, доворота почти не требует —
        // обнулять разгон здесь значит каждый раз возвращать ротацию на старт ease-in
        // и выглядеть как «аура тупит при смене цели».
        boolean keepMomentum = previousTarget != null
                && previousTarget != entity
                && player != null
                && angleBetween(previousTarget, entity, player) <= SWITCH_MOMENTUM_ANGLE;
        previousTarget = null;

        target = entity;
        if (!keepMomentum) {
            engagedTicks = 0;
            velYaw = 0.0F;
            velPitch = 0.0F;
            driftYaw = 0.0F;
            driftPitch = 0.0F;
        }
        blindTicks = 0;
        noCritTicks = 0;
        return entity;
    }

    private void releaseTarget() {
        boolean died = target != null && (target.isRemoved() || !target.isAlive());
        previousTarget = target;
        target = null;
        blindTicks = 0;
        noCritTicks = 0;
        // engagedTicks и скорости НЕ обнуляем: решение принимает engage(). Если новая
        // цель стоит в паре градусов от отпущенной, разгон доворота продолжается,
        // а не стартует с нуля каждый переход.
        //
        // Пауза от мерцания — когда ЖИВАЯ цель уходила то в конус, то из конуса, и ротация
        // дёргалась между двумя. Умершую цель менять не с чем: здесь пауза только лишает
        // ауру удара на 5 тиков ровно в тот момент, когда рядом стоит следующий.
        switchBlockedUntilMs = died ? 0L : System.currentTimeMillis() + (long) switchDelay.get();
    }

    /** Дальность удара с серверной полкой: выше ванильного порога удар уже флаг. */
    private double hitReach() {
        return Math.min(attackReach.get(), MAX_SAFE_REACH);
    }

    /** Ближайшая цель, до которой реально можно дотянуться ударом. */
    private LivingEntity findReachable(LocalPlayer player, Vec3 eyes) {
        double reach = hitReach();
        AABB area = player.getBoundingBox().inflate(reach + 1.0D);

LivingEntity best = null;
        double bestKey = Double.MAX_VALUE;
        List<LivingEntity> candidates2 = new ArrayList<>(level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player));
        for (Player p : level().players()) {
            if (p != player && p.getBoundingBox().intersects(area) && !candidates2.contains(p)) {
                candidates2.add(p);
            }
        }
        for (LivingEntity living : candidates2) {
            if (!acceptable(living)) continue;
            if (!hasAimPoint(living, eyes)) continue;
            double distSqr = RayTraceUtils.getClosestDistanceSqr(eyes, living.getBoundingBox());
            if (distSqr > reach * reach) continue;
            // Приоритет тот же, что и при захвате: «здоровье» или «угол» важнее
            // ближайшего, иначе аура упорно бьёт в танка, пока рядом дохнет хилый.
            double angle = angleTo(eyes, living, player);
            // Конус наводки уважаем и здесь, иначе быстрых захват выбивался бы за
            // спину при узком FOV, который игрок поставил осознанно.
            if (angle > fov.get()) continue;
            double key = priorityKey(living, angle, Math.sqrt(distSqr));
            if (key < bestKey) {
                bestKey = key;
                best = living;
            }
        }
        return best;
    }

    /**
     * Можно ли на эту цель вообще навеститься без режима «сквозь стены»: достаточно
     * одной видимой точки на силуэте, а не обязательно груди.
     */
    private boolean hasAimPoint(LivingEntity entity, Vec3 eyes) {
        if (throughWalls.getValue()) return true;
        AABB box = entity.getBoundingBox();
        double cx = box.minX + (box.maxX - box.minX) * 0.5D;
        double cz = box.minZ + (box.maxZ - box.minZ) * 0.5D;
        double height = box.maxY - box.minY;
        for (double frac : AIM_HEIGHTS) {
            if (RayTraceUtils.canSeePoint(eyes, new Vec3(cx, box.minY + height * frac, cz))) return true;
        }
        return false;
    }

    private static boolean beyond(LivingEntity entity, Vec3 eyes, double range) {
        return RayTraceUtils.getClosestDistanceSqr(eyes, entity.getBoundingBox()) > range * range;
    }

    private static double angleTo(Vec3 eyes, LivingEntity entity, LocalPlayer player) {
        Angle aim = Angle.fromVec(eyes, entity.getBoundingBox().getCenter());
        return Math.hypot(Mth.wrapDegrees(aim.getYaw() - viewYaw(player)),
                aim.getPitch() - viewPitch(player));
    }

    /** Угол между двумя целями с точки зрения игрока — по нему решаем, был ли переход «рядом». */
    private static double angleBetween(LivingEntity a, LivingEntity b, LocalPlayer player) {
        Vec3 eyes = player.getEyePosition();
        Vec3 va = a.getBoundingBox().getCenter().subtract(eyes);
        Vec3 vb = b.getBoundingBox().getCenter().subtract(eyes);
        double la = va.length();
        double lb = vb.length();
        if (la < 1.0E-4D || lb < 1.0E-4D) return 0.0D;
        return Math.toDegrees(Math.acos(Mth.clamp(va.dot(vb) / (la * lb), -1.0D, 1.0D)));
    }

    // -------------------------------------------------------------- точка прицела

    private Vec3 aimPosition(LivingEntity entity, Vec3 eyes) {
        Vec3 point;

        if (aimPoint.getValue().equals("Multipoint")) {
            UBoxPoints.TargetPoint best = UBoxPoints.getBestPoint(entity);
            if (best == null) return null;
            if (!best.isVisible() && !throughWalls.getValue()) return null;
            point = best.point();
        } else {
            AABB box = entity.getBoundingBox();
            double height = box.maxY - box.minY;
            double frac = switch (aimPoint.getValue()) {
                case "Head" -> 0.92D;
                case "Chest" -> 0.70D;
                case "Legs" -> 0.28D;
                default -> 0.55D;
            };
            point = new Vec3(box.minX + (box.maxX - box.minX) * 0.5,
                    box.minY + height * frac,
                    box.minZ + (box.maxZ - box.minZ) * 0.5);
            if (!throughWalls.getValue() && !RayTraceUtils.canSeePoint(eyes, point)) return null;
        }

        double leadAmount = isSmooth() ? SMOOTH_LEAD : LEGIT_LEAD;
        if (leadAmount > 0.0D) {
            Vec3 velocity = new Vec3(entity.getX() - entity.xo,
                    entity.getY() - entity.yo,
                    entity.getZ() - entity.zo);
            if (velocity.lengthSqr() > 1.0E-6D) {
                double ticks = eyes.distanceTo(point) * 0.05D;
                double scale = Math.min(2.5D, leadAmount * ticks);
                point = point.add(velocity.scale(scale));
            }
        }
        return point;
    }

    // ------------------------------------------------------------------ таймеры

    /**
     * Attack Delay в тиках: окно 520-570 мс целиком лежит в одном тиковом шаге,
     * поэтому джиттер «+0-50 мс» внутри него физически невозможен.
     */
    private long delayTicks() {
        return Math.max(1L, (long) Math.ceil(attackDelay.get() / 50.0F));
    }

    private void tickTimers() {
        if (sprintReleaseTicks <= 0) return;
        sprintReleaseTicks--;
        // Как только сервер перестал считать нас бегущими (STOP_SPRINTING ушёл),
        // держать снятый ввод незачем — возвращаем ходьбу/спринт.
        LocalPlayer player = player();
        if (player != null && !isServerSprinting(player)) {
            sprintReleaseTicks = 0;
        }
    }

    /**
     * Подъём прыжка: снимаем forward/sprint заранее, пока сервер ещё считает нас
     * бегущими и окно крита не открылось. Держим не фиксированный lead, а до
     * флипа wasSprinting — как только STOP_SPRINTING ушёл, ввод возвращаем.
     */
    private void armSprintReleaseOnRise(LocalPlayer player) {
        if (sprintReset.getValue().equals("None")) return;
        if (sprintReleaseTicks > 0) return;
        if (player.getDeltaMovement().y <= 0.0D) return;
        if (!isMovingForward(player) || !isServerSprinting(player)) return;
        sprintReleaseTicks = sprintStopCap();
    }

    /**
     * Падем, а сервер всё ещё считает нас бегущими: перечим ввод ещё пару тиков.
     * Удар выпустит тот тик, где isServerSprinting() уже false.
     */
    private void keepSprintReleaseArmed(LocalPlayer player) {
        if (!isMovingForward(player)) return;
        if (sprintReleaseTicks < sprintReArmTicks()) {
            sprintReleaseTicks = sprintReArmTicks();
        }
    }

    /** Нижняя гарантия отстоя в тиках. */
    private int sprintReArmTicks() {
        return Math.round(sprintMinStopTicks.get());
    }

    /** Потолок отстоя в тиках; ниже нижней гарантии он быть не может. */
    private int sprintStopCap() {
        return Math.max(sprintReArmTicks(), Math.round(sprintMaxStopTicks.get()));
    }

    private boolean isMovingForward(LocalPlayer player) {
        Input input = player.input != null ? player.input.keyPresses : null;
        return player.isSprinting() || (input != null && input.forward());
    }

    /** Спринт таким, каким его видит сервер (wasSprinting), а не локальный флаг. */
    private static boolean isServerSprinting(LocalPlayer player) {
        try {
            return ((ILocalPlayer) player).serverSprintState();
        } catch (Throwable ignored) {
            return player.isSprinting();
        }
    }

    private boolean isPausedByUsing() {
        return pauseWhileUsing.getValue() && player().isUsingItem();
    }

    private static boolean isCritException(LocalPlayer player) {
        return player.getAbilities().flying
                || player.isFallFlying()
                || player.hasEffect(MobEffects.BLINDNESS)
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.isPassenger();
    }

    /** Зажата ли клавиша прыжка — берём именно бинд из настроек управления, а не пробел. */
    private static boolean isJumpHeld() {
        return mc != null && mc.options != null && mc.options.keyJump.isDown();
    }

    // ------------------------------------------------------------------- фильтры

    private boolean acceptable(LivingEntity living) {
        LocalPlayer player = player();
        if (player == null || living == player) return false;
        if (!living.isAlive() || living.isDeadOrDying() || living.isRemoved()) return false;
        if (living.isSpectator()) return false;
        if (RayTraceUtils.getClosestDistanceSqr(player.getEyePosition(), living.getBoundingBox())
                > aimRangeNow(player) * aimRangeNow(player)) {
            return false;
        }

        if (ignoreInvisible.getValue() && living.isInvisible()) return false;
        if (ignoreSleeping.getValue() && living.isSleeping()) return false;
        if (ignoreRiding.getValue() && living.isPassenger()) return false;
        if (!belowHealthAllows(living)) return false;

        if (living instanceof Player other) {
            // Друзья вне списка категорий: пока «Target Friends» выключен, друг
            // не становится целью ни при каких галках в «Targets».
            if (FriendManager.getInstance().isFriend(other)) return targetFriends.getValue();
            if (targets.isEnabled("Players")) return true;
            // «Голые» — отдельная категория: игрок без брони бьётся и без «Игроков»
            return targets.isEnabled("Naked") && isNaked(other);
        }

        if (targets.isEnabled("Villagers") && living instanceof Villager) return true;
        if (targets.isEnabled("Golems") && (living instanceof IronGolem || living instanceof SnowGolem)) {
            return true;
        }
        if (targets.isEnabled("Water") && living instanceof WaterAnimal) return true;
        if (targets.isEnabled("Ambient") && living instanceof AmbientCreature) return true;
        if (targets.isEnabled("Mobs") && living instanceof Enemy) return true;
        return targets.isEnabled("Animals") && living instanceof Animal;
    }

    /** Процент здоровья не выше порога; при 100% фильтр не мешает вовсе. */
    private boolean belowHealthAllows(LivingEntity living) {
        float pct = belowHealth.get();
        if (pct >= 100.0F) return true;
        float max = living.getMaxHealth();
        if (max <= 0.0F) return true;
        return living.getHealth() / max * 100.0F <= pct;
    }

    /**
     * Чем меньше, тем лучше. У равных значений решают меткие: угол как минимальный
     * множитель не даёт разорвать связку «ближе к прицелу».
     */
    private double priorityKey(LivingEntity living, double angle, double dist) {
        return switch (priority.getValue()) {
            case "Distance" -> dist + angle * 0.001D;
            case "Health" -> living.getHealth() + dist * 0.001D;
            default -> angle + dist * 0.001D;
        };
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    // -------------------------------------------------------------------- топор

    private static int findAxeSlot() {
        LocalPlayer player = mc.player;
        if (player == null) return -1;
        List<ItemStack> hotbar = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < Math.min(9, hotbar.size()); i++) {
            if (hotbar.get(i).is(ItemTags.AXES)) return i;
        }
        return -1;
    }

    private void setSlot(int slot) {
        LocalPlayer player = player();
        if (player == null || slot < 0 || slot > 8 || player.connection == null) return;
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void restoreAxeSlot() {
        if (axeStage != 1) return;
        if (previousSlot != -1) setSlot(previousSlot);
        previousSlot = -1;
        axeStage = 0;
        shieldTicks = 0;
    }
}
