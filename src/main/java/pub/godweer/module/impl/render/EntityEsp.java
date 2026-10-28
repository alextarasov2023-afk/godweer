package pub.godweer.module.impl.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.friend.FriendManager;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.util.render.Render3D;
import pub.godweer.util.style.Theme;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Подсветка сущностей сквозь стены: рамки берутся прямо из хитбокса, поэтому
 * корректно обходят и ползущего муравья, и слизня, и летящий снаряд.
 *
 * В отличие от блоков список собирается каждый кадр: сущности двигаются между
 * тиками, а {@link Entity#getInterpolatedBoundingBox(float)} даёт ровно тот же
 * бокс, который рисует ванильный рендерер, — рамка не дёргается на 20 TPS.
 *
 * Create by daun kvass
 */
public final class EntityEsp extends Module {

    public static final String GROUP_PLAYERS = "Игроки";
    public static final String GROUP_MOBS = "Мобы";
    public static final String GROUP_ANIMALS = "Животные";
    public static final String GROUP_ITEMS = "Предметы";
    public static final String GROUP_PROJECTILES = "Снаряды";

    /** Кадр больше 160 рамок уже превращается в кашу, а процессор грузит зря. */
    private static final int MAX_BOXES = 160;
    private static final float CORNER = 0.28F;
    private static final float EXPAND = 0.03F;

    private static final int RED = 0xE04A4A;
    private static final int FRIEND = 0x4CD964;

    public final ModeSetting style = mode("Стиль", BlockEsp.STYLE_GLOW,
            BlockEsp.STYLE_GLOW, BlockEsp.STYLE_CORNERS, BlockEsp.STYLE_WIREFRAME);
    public final SliderSetting radius = slider("Радиус", 48.0F, 8.0F, 96.0F, 1.0F);
    public final MultiModeSetting entities = multiMode("Сущности",
            List.of(GROUP_PLAYERS, GROUP_MOBS, GROUP_ITEMS),
            GROUP_PLAYERS, GROUP_MOBS, GROUP_ANIMALS, GROUP_ITEMS, GROUP_PROJECTILES);
    public final CheckBox throughWalls = checkbox("Сквозь стены", true);

    private final List<Render3D.WorldBox> boxes = new ArrayList<>();

    public EntityEsp() {
        super("EntityEsp", "Подсветка игроков, мобов и предметов сквозь стены", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        boxes.clear();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!inGame()) {
            boxes.clear();
            return;
        }

        float delta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        collect(delta);
        if (boxes.isEmpty()) return;

        boolean through = throughWalls.get();
        float reach = radius.get();
        float fadeNear = reach * 0.5F;
        float fadeFar = reach * 1.15F;

        if (style.is(BlockEsp.STYLE_GLOW)) {
            Render3D.drawBoxGlow(boxes, through, 1.0F, fadeNear, fadeFar);
        }
        Render3D.drawBoxFills(boxes, through, 1.0F, fadeNear, fadeFar);

        if (style.is(BlockEsp.STYLE_WIREFRAME)) {
            Render3D.drawBoxOutlines(boxes, through, 1.0F, fadeNear, fadeFar);
        } else {
            Render3D.drawBoxCorners(boxes, through, 1.0F, fadeNear, fadeFar, CORNER);
        }
    }

    private void collect(float delta) {
        boxes.clear();

        double radiusBlocks = radius.get();
        double radiusSq = radiusBlocks * radiusBlocks;
        FriendManager friends = FriendManager.getInstance();

        for (Entity entity : level().entitiesForRendering()) {
            if (boxes.size() >= MAX_BOXES) return;
            if (entity == player() || entity.isRemoved()) continue;
            if (player().distanceToSqr(entity) > radiusSq) continue;

            String group = group(entity);
            if (group == null || !entities.isEnabled(group)) continue;

            AABB hit = entity.getInterpolatedBoundingBox(delta);
            if (hit == null) continue;

            Color color = color(entity, group, friends);
            boxes.add(new Render3D.WorldBox(
                    hit.minX - EXPAND, hit.minY - EXPAND, hit.minZ - EXPAND,
                    (hit.maxX - hit.minX) + EXPAND * 2.0D,
                    (hit.maxY - hit.minY) + EXPAND * 2.0D,
                    (hit.maxZ - hit.minZ) + EXPAND * 2.0D,
                    fade(color, 42), fade(color, 225)));
        }
    }

    /** null — сущность не относится ни к одной из наших категорий (столбы, таблички, души опыта). */
    private String group(Entity entity) {
        if (entity instanceof Projectile) return GROUP_PROJECTILES;
        if (entity instanceof ItemEntity item) return item.getItem().isEmpty() ? null : GROUP_ITEMS;
        if (entity instanceof Player) return GROUP_PLAYERS;
        if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature) {
            return GROUP_ANIMALS;
        }
        if (entity instanceof Mob) return GROUP_MOBS;
        return null;
    }

    private Color color(Entity entity, String group, FriendManager friends) {
        int rgb = switch (group) {
            case GROUP_ITEMS -> 0xBFA76A;
            case GROUP_PROJECTILES -> 0xE8A23F;
            case GROUP_ANIMALS -> 0x7FD14A;
            case GROUP_MOBS -> RED;
            case GROUP_PLAYERS -> friends.isFriend((Player) entity) ? FRIEND : Theme.getAccentColor();
            default -> 0xB0B6C0;
        };

        if (entity instanceof LivingEntity living && !group.equals(GROUP_ITEMS)) {
            rgb = hurt(rgb, living.getHealth() / living.getMaxHealth());
        }
        return new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
    }

    /** Полоска здоровья прямо в цвете: у цели под 30% рамка уже красная. */
    private static int hurt(int rgb, float healthFraction) {
        if (healthFraction >= 0.999F) return rgb;
        float k = Math.min(1.0F, (1.0F - healthFraction) * 1.4F);
        int red = (int) ((rgb >> 16 & 255) * (1.0F - k) + 224.0F * k);
        int green = (int) ((rgb >> 8 & 255) * (1.0F - k) + 74.0F * k);
        int blue = (int) ((rgb & 255) * (1.0F - k) + 74.0F * k);
        return red << 16 | green << 8 | blue;
    }

    private static Color fade(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
