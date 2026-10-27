package pub.godweer.kitty.module.impl.combat;

import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.RayTraceUtils;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.enjoy.mixin.accessor.ILocalPlayer;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.*;
import pub.godweer.kitty.sosiski.friend.FriendManager;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TriggerBot V2 — бьёт только когда цель уже под прицелом (ротацию и камеру не трогаем).
 * Горячий путь сделан без аллокаций: ванильный пик прицела используется как есть,
 * а скан ближайших сущностей — только когда им промахнулись.
 */
@Getter
public class TriggerBot extends Module {
    public static TriggerBot INSTANCE;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // [НАСТРОЙКА] Дальность удара
    private final SliderSetting reach = slider("Reach", 3.2f, 1.0f, 6.0f, 0.1f);
    // [НАСТРОЙКА] Кого бить
    private final MultiModeSetting targets = multiMode("Targets", List.of("Players"),
            "Players", "Mobs", "Animals", "Naked", "Friends", "Villagers");

    // [НАСТРОЙКА] Время наведения: бить только если цель на прицеле дольше этого
    private final SliderSetting hoverTime = slider("Hover Time", 200.0f, 0.0f, 1000.0f, 10.0f);
    // [НАСТРОЙКА] Кулдаун между ударами: 550 мс + рандом 0-50 мс = 550-600 мс
    private final SliderSetting attackDelay = slider("Attack Delay", 550.0f, 200.0f, 900.0f, 10.0f);

    // [НАСТРОЙКА] Тип ударов: Only Crits — бьёт только в падении (с исключениями),
    // Smart — в падении или на земле
    private final ModeSetting attackMode = mode("Attack Mode", "Smart", "Smart", "Only Crits");

    // [НАСТРОЙКА] Сброс спринта:
    //  None       — не трогает спринт;
    //  Legit      — не даёт идти вперёд от лица сервера. Отстой армится заранее, на
    //               подъёме прыжка (2-4 тика), плюс 1-2 тика после удара, поэтому
    //               короткое окно падения уходит целиком на удар;
    //  Wait Stop  — не бьёт, пока сервер считает, что мы бежим спринтом: ждёт, когда
    //               игрок сам перейдёт на шаг/остановку (только в падении).
    private final ModeSetting sprintReset = mode("Sprint Reset", "Legit", "None", "Legit", "Wait Stop");

    // [НАСТРОЙКА] Не бить во время еды
    private final CheckBox pauseWhileEating = checkbox("Pause while Eating", true);
    // [НАСТРОЙКА] Не бить через стены
    private final CheckBox throughWalls = checkbox("Through Walls", false);
    // [НАСТРОЙКА] Ломать щит топором (цель держит щит >= 5 тиков -> 1 тик с топором)
    private final CheckBox axeShieldBreak = checkbox("Axe Shield Break", false);

    private LivingEntity target = null;

    private LivingEntity hoverTarget = null;
    private long hoverStartMs = -1L;
    private long lastAttackMs = 0L;
    private long nextDelayMs = 550L;

    private int shieldTicks = 0;

    // Машина смены на топор: 0 — нет, 1 — топор в руке, ждёт удара
    private int axeStage = 0;
    private int previousSlot = -1;

    // Машина легитного отстоя: > 0 — сколько тиков ещё не даём идти вперёд,
    // sprintReleaseHoldTicks — сколько тиков уже стоим (удар после 1-2 таких тиков)
    private int sprintReleaseTicks = 0;
    private int sprintReleaseHoldTicks = 0;
    private int requiredLeadTicks = 1;

    public TriggerBot() {
        super("TriggerBot V2", "Удар по цели под прицелом: наведение 0.2с, криты, щиты, отстой спринта", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        target = null;
        hoverTarget = null;
        hoverStartMs = -1L;
        shieldTicks = 0;
        sprintReleaseTicks = 0;
        sprintReleaseHoldTicks = 0;
        requiredLeadTicks = 1;
        restoreAxeSlot();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        LocalPlayer player = player();
        if (player == null || level() == null || gameMode() == null) return;

        tickSprintTimers();

        // [НАСТРОЙКА] не бить при еде
        if (pauseWhileEating.getValue() && isEating(player)) return;

        LivingEntity hit = pickTarget(player);
        if (hit == null) {
            target = null;
            resetHover();
            shieldTicks = 0;
            return;
        }
        target = hit;

        long now = System.currentTimeMillis();
        if (hoverTarget != hit) {
            hoverTarget = hit;
            hoverStartMs = now;
            shieldTicks = 0;
        }

        // [НАСТРОЙКА] щит: меньше 5 тиков — игнорируем, 5 и больше —break топором
        if (axeShieldBreak.getValue() && hit.isBlocking()) shieldTicks++;
        else shieldTicks = 0;

        // Условие: цель на прицеле дольше 0.2 сек
        if (now - hoverStartMs < hoverTime.get()) return;
        // Условие: интервал между ударами (550-600 мс)
        if (now - lastAttackMs < nextDelayMs) return;

        if (axeShieldBreak.getValue() && shieldTicks >= 5 && axeStage == 0) {
            int slot = findAxeSlot();
            int current = player.getInventory().getSelectedSlot();
            if (slot != -1 && slot != current) {
                previousSlot = current;
                setSlot(slot);
                axeStage = 1;
                return; // один тик на смену слота, удар в следующем
            }
        }

        // Исключения, при которых бьём всегда и со 100% заряда
        boolean exception = isCritException(player);
        boolean falling = player.fallDistance > 0.0F && !player.onGround();

        // [НАСТРОЙКА] Легитный отстой армимся заранее — на подъёме прыжка, а не перед
        // ударом: lead-тики съедают подъём, поэтому к первому падающему тику сервер уже
        // считает нас идущим пешком и крит бьётся сразу, не тратя короткое окно.
        if (!falling && !player.onGround() && sprintReset.getValue().equals("Legit")) {
            armSprintReleaseOnRise(player);
        }

        // [НАСТРОЙКА] Only Crits: только падение (плюс исключения); Smart: падение или земля
        if (!exception && attackMode.getValue().equals("Only Crits") && !falling) return;
        if (!exception && attackMode.getValue().equals("Smart") && !falling && !player.onGround()) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && player.getCooldowns().isOnCooldown(mainHand)) return;

        // Кулдаун атаки: в падении (крит) — минимум 84.8%, на земле и в исключениях — 100%.
        // Берём честный заряд (0.0 тика оптимизма): сервер считает свой тикер и удар
        // «раньше готовности» даёт ровно «замах есть, урона нет».
        float required = (falling && !exception) ? 0.848F : 1.0F;
        boolean bypassCharge = axeStage == 1; // у топора кулдаун не важен
        if (!bypassCharge && player.getAttackStrengthScale(0.0F) < required) return;

        // Сброс спринта — только в падении и только под бегом (иначе нечего сбрасывать)
        if (falling && isServerSprinting(player)) {
            String mode = sprintReset.getValue();
            if (mode.equals("Wait Stop")) return;
            if (mode.equals("Legit") && !readyAfterStop(player)) return;
        }

        executeAttack(player, falling);
    }

    private void executeAttack(LocalPlayer player, boolean falling) {
        lastAttackMs = System.currentTimeMillis();
        nextDelayMs = (long) attackDelay.get() + ThreadLocalRandom.current().nextInt(0, 51);

        // Легитный отстоя: после удара ещё 1-2 тика не даём идти вперёд
        if (falling && sprintReset.getValue().equals("Legit") && isMovingForward(player)) {
            sprintReleaseTicks = Math.max(sprintReleaseTicks, 1 + ThreadLocalRandom.current().nextInt(2));
        } else {
            sprintReleaseTicks = 0;
            sprintReleaseHoldTicks = 0;
        }

        // Удар без локального спринта: клиентские критовые партиклы тогда совпадают
        // с серверным результатом. Само движение этот тик не меняем.
        boolean sprinting = player.isSprinting();
        player.setSprinting(false);
        gameMode().attack(player, target);
        player.setSprinting(sprinting);

        SwingAnimation swingAnim = player.getMainHandItem().getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, swingAnim, false);

        // Если ломали щит — возвращаем предмет, который держали (если это был не топор)
        restoreAxeSlot();
    }

    /**
     * Легитный сброс спринта: НЕ останавливает игрока, а просто не даёт идти вперёд —
     * убираем forward/sprint из ввода и гасим moveVector. keyPresses уходит в пакет ввода
     * и в логику спринта, а реальное замедление сервер видит по пакетам движения, т.е.
     * именно по погашенному moveVector. Ваниль сама шлёт STOP/START_SPRINTING, поэтому
     * wasSprinting ведётся без десинхрона.
     */
    @EventTarget
    public void onInput(PlayerInputEvent event) {
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

    // ------------------------------------------------------------------ таймеры

    private void tickSprintTimers() {
        if (sprintReleaseTicks > 0) {
            sprintReleaseTicks--;
            sprintReleaseHoldTicks++;
        } else {
            sprintReleaseHoldTicks = 0;
        }
    }

    /**
     * Подъём прыжка: снимаем forward/sprint заранее, чтобы ваниль успела отправить
     * STOP_SPRINTING, пока окно крита ещё не открылось. Держим 2-4 тика — длиннее, чем
     * вариант «прямо перед ударом», зато lead не съедает падение. Если сервер всё ещё
     * считает нас бегущим, отстой просто перечится ещё раз.
     */
    private void armSprintReleaseOnRise(LocalPlayer player) {
        if (sprintReleaseTicks > 0) return;
        if (player.getDeltaMovement().y <= 0.0D) return;
        if (!isMovingForward(player) || !isServerSprinting(player)) return;
        requiredLeadTicks = ThreadLocalRandom.current().nextInt(2, 5);
        sprintReleaseTicks = requiredLeadTicks;
    }

    /**
     * Фолбэк для случаев без фазы подъёма (сошли с блока, выпрыгнули из воды): если
     * сервер всё ещё считает нас бегущими, стоим lead тиков уже в самом падении.
     */
    private boolean readyAfterStop(LocalPlayer player) {
        if (!isMovingForward(player)) return true;
        if (sprintReleaseHoldTicks >= requiredLeadTicks) return true;
        if (sprintReleaseTicks <= 0) {
            requiredLeadTicks = ThreadLocalRandom.current().nextInt(1, 3);
            sprintReleaseTicks = requiredLeadTicks + 1;
        }
        return false;
    }

    private static boolean isMovingForward(LocalPlayer player) {
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

    private void resetHover() {
        hoverTarget = null;
        hoverStartMs = -1L;
    }

    private static boolean isEating(LocalPlayer player) {
        if (!player.isUsingItem()) return false;
        ItemUseAnimation anim = player.getUseItem().getUseAnimation();
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
    }

    /**
     * Исключения, при которых бьём всегда и при 100% заряда: элитра именно в полёте,
     * вода, лестница, транспортная сущность, слепота, лава.
     */
    private static boolean isCritException(LocalPlayer player) {
        return player.isFallFlying()
                || player.isInWater()
                || player.onClimbable()
                || player.isPassenger()
                || player.hasEffect(MobEffects.BLINDNESS)
                || player.isInLava();
    }

    // --------------------------------------------------------------------- цели

    /**
     * Быстрый путь: Minecraft сам считает пик прицела каждый кадр (mc.hitResult), с учётом
     * блоков — берём его как есть и не делаем ни одного рейкаста. Скан — только если в
     * hitResult никого нет (например, reach больше ванильной дистанции пика).
     */
    private LivingEntity pickTarget(LocalPlayer player) {
        double range = reach.get();
        Vec3 eyes = player.getEyePosition();

        // Ванильный пик прицела блоки учитывает, поэтому быстрый путь годится только
        // когда стены и правда мешают бить; со «Through Walls» идём сразу в скан.
        if (!throughWalls.getValue()
                && mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                && living != player && acceptable(living)
                && RayTraceUtils.getClosestDistanceSqr(eyes, living.getBoundingBox()) <= range * range) {
            return living;
        }
        return scanTarget(player, range, eyes);
    }

    /**
     * Скан без аллокаций: slab-тест луча против хитбокса каждой цели, победитель —
     * ближайший по лучу. Проверку стен делаем ровно один раз, уже для победителя.
     */
    private LivingEntity scanTarget(LocalPlayer player, double range, Vec3 eyes) {
        Vec3 dir = RayTraceUtils.getLookVec(player.getYRot(), player.getXRot());

        AABB area = player.getBoundingBox().inflate(range + 1.0D);
        LivingEntity best = null;
        double bestT = range;

        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player)) {
            if (!acceptable(living)) continue;

            AABB box = living.getBoundingBox();
            float pick = living.getPickRadius();
            if (pick > 0.0F) box = box.inflate(pick);

            double t = rayBox(eyes, dir, box);
            if (t < 0.0D || t > bestT) continue;

            bestT = t;
            best = living;
        }

        if (best != null && !throughWalls.getValue()) {
            Vec3 hitPoint = new Vec3(eyes.x + dir.x * bestT, eyes.y + dir.y * bestT, eyes.z + dir.z * bestT);
            if (!visible(eyes, hitPoint)) return null;
        }
        return best;
    }

    /**
     * Slab-метод: расстояние от глаз до пересечения луча с боксом (0 — внутри бокса),
     * -1 — луч мимо или бокс за спиной.
     */
    private static double rayBox(Vec3 o, Vec3 d, AABB b) {
        double tNear = 0.0D;
        double tFar = Double.MAX_VALUE;

        if (d.x == 0.0D) {
            if (o.x < b.minX || o.x > b.maxX) return -1.0D;
        } else {
            double t1 = (b.minX - o.x) / d.x;
            double t2 = (b.maxX - o.x) / d.x;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tNear) tNear = t1;
            if (t2 < tFar) tFar = t2;
            if (tNear > tFar) return -1.0D;
        }

        if (d.y == 0.0D) {
            if (o.y < b.minY || o.y > b.maxY) return -1.0D;
        } else {
            double t1 = (b.minY - o.y) / d.y;
            double t2 = (b.maxY - o.y) / d.y;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tNear) tNear = t1;
            if (t2 < tFar) tFar = t2;
            if (tNear > tFar) return -1.0D;
        }

        if (d.z == 0.0D) {
            if (o.z < b.minZ || o.z > b.maxZ) return -1.0D;
        } else {
            double t1 = (b.minZ - o.z) / d.z;
            double t2 = (b.maxZ - o.z) / d.z;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tNear) tNear = t1;
            if (t2 < tFar) tFar = t2;
            if (tNear > tFar) return -1.0D;
        }

        if (tFar < 0.0D) return -1.0D;
        return tNear;
    }

    /** Луч глаз -> точка попадания не пересекает блоки. */
    private boolean visible(Vec3 from, Vec3 to) {
        BlockHitResult blockHit = level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        ));
        return blockHit.getType() == HitResult.Type.MISS;
    }

    private boolean acceptable(LivingEntity living) {
        if (!living.isAlive() || living.isDeadOrDying() || living.isRemoved()) return false;
        if (living.isSpectator()) return false;

        if (living instanceof Player other) {
            if (FriendManager.getInstance().isFriend(other)) return targets.isEnabled("Friends");
            if (targets.isEnabled("Players")) return true;
            // «Голые» — отдельная категория: игрок без брони бьётся и без «Игроков»
            return targets.isEnabled("Naked") && isNaked(other);
        }

        if (targets.isEnabled("Villagers") && (living instanceof Villager || living instanceof IronGolem)) {
            return true;
        }
        if (targets.isEnabled("Mobs") && living instanceof Enemy) return true;
        return targets.isEnabled("Animals")
                && (living instanceof Animal || living instanceof WaterAnimal
                || living instanceof AmbientCreature);
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
