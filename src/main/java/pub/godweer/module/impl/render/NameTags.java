package pub.godweer.module.impl.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Matrix3x2fStack;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.util.render.font.MsdfFont;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.AttackEvent;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.hud.HudStyle;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.friend.FriendManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watermark-style NameTags with segmented glass pill cards, clean rounded player head
 * (no outline around face), clean nickname without extra badges next to it,
 * HP + Absorption, Distance, Active Effects, Armor/Equipment slots with durability bars
 * & key enchantments, and LogOut ghost tags.
 */
public final class NameTags extends Module {

    private static final float BASE_SCALE = 1.0F;
    private static final float PILL_H = 22.0F;
    private static final float SUB_H = 15.0F;
    private static final float RADIUS = 7.5F;
    private static final float PAD_X = 7.5F;
    private static final float SEG_GAP = 5.5F;
    private static final float FONT = 8.0F;
    private static final float UNIT_FONT = 7.2F;
    private static final float ICON = 7.5F;
    private static final float HEAD = 12.0F;
    private static final float HEAD_OFFSET = 0.30F;

    private static final EquipmentSlot[] EQUIPMENT_ORDER = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };

    private record Donate(String name, int color) {}
    private record ItemStyle(int color, float textSize, float iconSize) {}
    private record EffectTag(String label, int color) {}

    private record LoggedOutPlayer(
            UUID uuid,
            String name,
            Identifier skin,
            Vec3 position,
            float height,
            long logOutTime,
            List<ItemStack> equipment,
            Donate donates,
            int health,
            int absorption,
            float maxHealth,
            boolean isFriend
    ) {}

    private record CachedPlayer(
            UUID uuid,
            String name,
            Identifier skin,
            Vec3 position,
            float height,
            List<ItemStack> equipment,
            Donate donates,
            int health,
            int absorption,
            float maxHealth,
            boolean isFriend,
            boolean isAlive
    ) {}

    private final Map<UUID, CachedPlayer> playerCache = new HashMap<>();
    private final Map<UUID, LoggedOutPlayer> loggedOutPlayers = new ConcurrentHashMap<>();

    private int targetId = -1;
    private long firstAttackTime = 0L;
    private long lastAttackTime = 0L;

    public final MultiModeSetting targets = multiMode(
            "Targets",
            List.of("Игроки", "Голые", "Друзья"),
            "Игроки", "Мобы", "Животные", "Голые", "Друзья", "Предметы", "Снаряды"
    );

    public final MultiModeSetting infoElements = multiMode(
            "Информация",
            List.of("ХП", "Дистанция", "Эффекты"),
            "ХП", "Дистанция", "Эффекты"
    );

    public final SliderSetting scale = slider("Размер", 1.0f, 0.5f, 1.8f, 0.05f);
    public final CheckBox highlightTarget = checkbox("Выделять таргет", true);
    public final CheckBox highlightValuables = checkbox("Выделять ценное", true);
    public final CheckBox items = checkbox("Броня и предметы", true);
    public final CheckBox armorDurability = checkbox("Прочность брони", true).visible(items::getValue);
    public final CheckBox armorEnchants = checkbox("Зачарования", true).visible(items::getValue);
    public final ModeSetting itemsPos = mode("Позиция брони", "Сверху", "Сверху", "Под ногами").visible(items::getValue);
    public final CheckBox stackItems = checkbox("Стакать предметы", true).visible(() -> targets.isEnabled("Предметы"));
    public final CheckBox logOut = checkbox("LogOut", true);

    public NameTags() {
        super("NameTags", "Информативные теги над противниками в стиле Watermark", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        loggedOutPlayers.clear();
        playerCache.clear();
        targetId = -1;
        firstAttackTime = 0L;
        lastAttackTime = 0L;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof LivingEntity living && living != mc.player) {
            long now = System.currentTimeMillis();
            if (this.targetId != living.getId() || (now - this.lastAttackTime) > 5000L) {
                this.firstAttackTime = now;
            }
            this.targetId = living.getId();
            this.lastAttackTime = now;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = event.getClient();
        if (mc == null || mc.level == null || mc.player == null) {
            loggedOutPlayers.clear();
            playerCache.clear();
            return;
        }

        float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float unit = BASE_SCALE * this.scale.getValue();
        double maxDistSq = 228.0 * 228.0;

        List<ItemEntity> itemEntities = new ArrayList<>();
        Set<UUID> currentFramePlayers = new HashSet<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }
            if (entity.isRemoved()) {
                continue;
            }
            if (mc.player.distanceToSqr(entity) > maxDistSq) {
                continue;
            }

            if (entity instanceof ItemEntity itemEntity) {
                if (this.targets.isEnabled("Предметы") && !itemEntity.getItem().isEmpty()) {
                    itemEntities.add(itemEntity);
                }
                continue;
            }

            if (entity instanceof Projectile projectile) {
                if (this.targets.isEnabled("Снаряды")) {
                    drawProjectileTag(event, projectile, tickDelta, unit);
                }
                continue;
            }

            if (entity instanceof LivingEntity living) {
                if (living instanceof AbstractClientPlayer player && player != mc.player) {
                    currentFramePlayers.add(player.getUUID());
                    loggedOutPlayers.remove(player.getUUID());

                    boolean isFriend = FriendManager.getInstance().isFriend(player);
                    playerCache.put(player.getUUID(), new CachedPlayer(
                            player.getUUID(),
                            player.getGameProfile().name(),
                            player.getSkin().body().texturePath(),
                            player.position(),
                            player.getBbHeight(),
                            equipment(player),
                            donates(player),
                            (int) Math.ceil(player.getHealth()),
                            (int) Math.ceil(player.getAbsorptionAmount()),
                            player.getMaxHealth(),
                            isFriend,
                            player.isAlive() && !player.isDeadOrDying()
                    ));
                }

                if (!living.isAlive() || living.isSpectator()) {
                    continue;
                }
                if (!isValidTarget(living)) {
                    continue;
                }

                drawTag(event, living, tickDelta, unit);
            }
        }

        if (this.logOut.getValue()) {
            Iterator<Map.Entry<UUID, CachedPlayer>> cacheIt = playerCache.entrySet().iterator();
            while (cacheIt.hasNext()) {
                Map.Entry<UUID, CachedPlayer> entry = cacheIt.next();
                UUID uuid = entry.getKey();
                CachedPlayer cached = entry.getValue();

                if (!currentFramePlayers.contains(uuid)) {
                    if (cached.isAlive && !loggedOutPlayers.containsKey(uuid)) {
                        loggedOutPlayers.put(uuid, new LoggedOutPlayer(
                                cached.uuid,
                                cached.name,
                                cached.skin,
                                cached.position,
                                cached.height,
                                System.currentTimeMillis(),
                                cached.equipment,
                                cached.donates,
                                cached.health,
                                cached.absorption,
                                cached.maxHealth,
                                cached.isFriend
                        ));
                    }
                    cacheIt.remove();
                }
            }

            renderLogOutTags(event, unit);
        } else {
            loggedOutPlayers.clear();
        }

        if (!itemEntities.isEmpty() && this.targets.isEnabled("Предметы")) {
            if (this.stackItems.getValue()) {
                renderClusteredItems(event, itemEntities, tickDelta, unit);
            } else {
                for (ItemEntity item : itemEntities) {
                    drawItemTag(event, item, tickDelta, unit);
                }
            }
        }
    }

    private void drawTag(Render2DEvent event, LivingEntity entity, float tickDelta, float baseUnit) {
        Minecraft mc = event.getClient();

        float targetFactor = 0.0F;
        if (this.highlightTarget.getValue() && entity.getId() == this.targetId) {
            long currentTime = System.currentTimeMillis();
            long elapsedSinceLast = currentTime - this.lastAttackTime;
            if (elapsedSinceLast <= 5000L) {
                long elapsedSinceFirst = currentTime - this.firstAttackTime;
                if (elapsedSinceFirst < 200L) {
                    targetFactor = elapsedSinceFirst / 200.0F;
                } else if (elapsedSinceLast > 4000L) {
                    targetFactor = 1.0F - ((elapsedSinceLast - 4000.0F) / 1000.0F);
                } else {
                    targetFactor = 1.0F;
                }
                targetFactor = Mth.clamp(targetFactor, 0.0F, 1.0F);
            }
        }

        float unit = baseUnit * (1.0F + 0.08F * targetFactor);

        Vec3 headPosition = Render3DUtil.interpolatedPosition(entity, tickDelta)
                .add(0.0D, entity.getBbHeight() + HEAD_OFFSET, 0.0D);
        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, headPosition);
        if (anchor == null) return;

        MsdfFont font = Fonts.SF_MEDIUM;
        int accent = Theme.getAccentColor();
        int primary = HudStyle.TEXT_PRIMARY;
        int secondary = HudStyle.TEXT_SECONDARY;

        float fontSz = FONT * unit;
        float unitSz = UNIT_FONT * unit;
        float iconSz = ICON * unit;
        float padX = PAD_X * unit;
        float segGap = SEG_GAP * unit;
        float headSz = HEAD * unit;
        float pillH = PILL_H * unit;
        float radius = RADIUS * unit;

        boolean hasHead = entity instanceof AbstractClientPlayer;
        boolean isFriend = entity instanceof Player player && FriendManager.getInstance().isFriend(player);

        String name = (entity instanceof AbstractClientPlayer player)
                ? player.getGameProfile().name()
                : entity.getDisplayName().getString();

        float maxHp = Math.max(1.0F, entity.getMaxHealth());
        float curHp = Mth.clamp(entity.getHealth(), 0.0F, maxHp);
        float curAbs = Math.max(0.0F, entity.getAbsorptionAmount());
        float hpPct = Mth.clamp(curHp / maxHp, 0.0F, 1.0F);

        boolean showHp = this.infoElements.isEnabled("ХП");
        String hpNum = String.valueOf((int) Math.ceil(curHp));
        String absNum = curAbs >= 0.5F ? "+" + (int) Math.ceil(curAbs) : "";

        boolean showDist = this.infoElements.isEnabled("Дистанция") && mc.player != null;
        float distVal = showDist ? mc.player.distanceTo(entity) : 0.0F;
        String distNum = showDist ? String.format(Locale.ROOT, distVal < 10.0F ? "%.1f" : "%.0f", distVal) : "";

        // Calculate Main Pill Width (Watermark style: [Head] Nick | 20 hp | [Compass] 5.2m)
        float totalW = padX * 2.0F;
        if (hasHead) {
            totalW += headSz + 4.5F * unit;
        }
        totalW += font.getWidth(name, fontSz);

        if (showHp) {
            totalW += segGap * 2.0F + 1.0F * unit;
            totalW += font.getWidth(hpNum, fontSz);
            if (!absNum.isEmpty()) {
                totalW += 1.0F * unit + font.getWidth(absNum, unitSz);
            }
            totalW += 1.5F * unit + font.getWidth("hp", unitSz);
        }

        if (showDist) {
            totalW += segGap * 2.0F + 1.0F * unit;
            totalW += Fonts.iconFittedWidth(IconUse.COMPASS, iconSz) + 3.0F * unit
                    + font.getWidth(distNum, fontSz) + 0.8F * unit + font.getWidth("m", unitSz);
        }

        float pillX = anchor.x() - totalW / 2.0F;
        float pillY = anchor.y() - pillH;
        float cy = pillY + pillH / 2.0F;

        // Draw Watermark-style Card with Friend / Target tinting
        drawTagCard(pillX, pillY, totalW, pillH, radius, isFriend, targetFactor, 1.0F);

        float cx = pillX + padX;

        // 1. Clean Player Head without outline/ring (just like WatermarkHud)
        if (hasHead) {
            AbstractClientPlayer p = (AbstractClientPlayer) entity;
            float hy = cy - headSz / 2.0F;
            try {
                Render2D.drawHead(p.getSkin().body().texturePath(), cx, hy, headSz, 3.0F * unit);
            } catch (Exception ignored) {
                Render2D.drawRoundedRect(cx, hy, headSz, headSz, 3.0F * unit, ColorUtil.withAlpha(accent, 45));
            }
            cx += headSz + 4.5F * unit;
        }

        // 2. Player Nickname (clean, no extra labels around it)
        int nameCol = isFriend ? ColorUtil.rgba(120, 245, 155, 255)
                : targetFactor > 0.1F ? ColorUtil.lerp(primary, ColorUtil.rgba(255, 210, 215, 255), targetFactor)
                : primary;
        float ty = cy - font.textHeight(fontSz) / 2.0F + 0.35F * unit;
        Fonts.drawString(font, name, cx, ty, fontSz, nameCol);
        cx += font.getWidth(name, fontSz);

        // 3. HP + Absorption Segment
        if (showHp) {
            cx = drawWatermarkDivider(cx, cy, segGap, unit, 1.0F);
            int hpCol = getHpColor(hpPct, false, accent);
            Fonts.drawString(font, hpNum, cx, ty, fontSz, hpCol);
            cx += font.getWidth(hpNum, fontSz);

            float unitTy = cy - font.textHeight(unitSz) / 2.0F + 0.45F * unit;
            if (!absNum.isEmpty()) {
                cx += 1.0F * unit;
                Fonts.drawString(font, absNum, cx, unitTy, unitSz, ColorUtil.rgba(255, 212, 75, 255));
                cx += font.getWidth(absNum, unitSz);
            }
            cx += 1.5F * unit;
            Fonts.drawString(font, "hp", cx, unitTy, unitSz, secondary);
            cx += font.getWidth("hp", unitSz);
        }

        // 4. Distance Segment (like WatermarkHud)
        if (showDist) {
            cx = drawWatermarkDivider(cx, cy, segGap, unit, 1.0F);
            int distTint = distVal <= 4.5F ? ColorUtil.rgba(255, 95, 100, 255)
                    : distVal <= 15.0F ? ColorUtil.rgba(255, 200, 75, 255)
                    : accent;
            drawWatermarkMetric(cx, cy, IconUse.COMPASS, distNum, "m",
                    distTint, primary, secondary, iconSz, fontSz, unitSz, unit);
        }

        // 5. Active Effects sub-pill (Strength, Speed, Regen, Res, FireRes, Slow, etc.)
        float topEdgeY = pillY;
        if (this.infoElements.isEnabled("Эффекты")) {
            List<EffectTag> effects = collectImportantEffects(entity);
            if (!effects.isEmpty()) {
                float subH = SUB_H * unit;
                float subY = pillY - subH - 3.0F * unit;
                drawEffectsSubPill(anchor.x(), subY, subH, effects, unit);
                topEdgeY = subY;
            }
        }

        // 6. Equipment & Armor Strip (with durability bars & key enchantments)
        List<ItemStack> equipment = this.items.getValue() ? equipment(entity) : List.of();
        if (!equipment.isEmpty()) {
            if (itemsPos.getValue().equals("Под ногами")) {
                Vec3 feetPos = Render3DUtil.interpolatedPosition(entity, tickDelta).subtract(0.0D, 0.05D, 0.0D);
                Render3DUtil.ScreenPoint feetAnchor = Render3DUtil.projectToScreen(mc, feetPos);
                if (feetAnchor != null) {
                    drawEquipmentStrip(event, equipment, feetAnchor.x(), feetAnchor.y() + 4.0F * unit, unit, false);
                }
            } else {
                float slotBox = 18.0F * unit;
                float equipY = topEdgeY - slotBox - 4.0F * unit;
                drawEquipmentStrip(event, equipment, anchor.x(), equipY, unit, true);
            }
        }
    }

    private void drawEffectsSubPill(float centerX, float y, float h, List<EffectTag> effects, float unit) {
        MsdfFont font = Fonts.SF_MEDIUM;
        float fontSz = 6.8F * unit;
        float padX = 6.0F * unit;
        float segGap = 4.5F * unit;

        float totalW = padX * 2.0F;
        for (int i = 0; i < effects.size(); i++) {
            totalW += 3.5F * unit + font.getWidth(effects.get(i).label(), fontSz);
            if (i < effects.size() - 1) {
                totalW += segGap * 2.0F + 1.0F * unit;
            }
        }

        float x = centerX - totalW / 2.0F;
        HudStyle.drawCard(x, y, totalW, h, 5.5F * unit);

        float cy = y + h / 2.0F;
        float cx = x + padX;
        float ty = cy - font.textHeight(fontSz) / 2.0F + 0.35F * unit;

        for (int i = 0; i < effects.size(); i++) {
            EffectTag ef = effects.get(i);
            Render2D.drawCircle(cx + 1.2F * unit, cy, 1.3F * unit, ef.color());
            cx += 3.5F * unit;
            Fonts.drawString(font, ef.label(), cx, ty, fontSz, ef.color());
            cx += font.getWidth(ef.label(), fontSz);
            if (i < effects.size() - 1) {
                cx = drawWatermarkDivider(cx, cy, segGap, unit, 0.85F);
            }
        }
    }

    private void drawEquipmentStrip(Render2DEvent event, List<ItemStack> equipment, float anchorX, float y, float unit, boolean enchantsAbove) {
        var extractor = event.getGuiGraphicsExtractor();
        if (extractor == null || equipment.isEmpty()) return;

        float slotSz = 17.0F * unit;
        float gap = 3.0F * unit;
        float pad = 3.5F * unit;
        float cardW = pad * 2.0F + equipment.size() * slotSz + (equipment.size() - 1) * gap;
        float cardH = slotSz + pad * 2.0F;
        float cardX = anchorX - cardW / 2.0F;

        HudStyle.drawCard(cardX, y, cardW, cardH, 6.5F * unit);

        int accent = Theme.getAccentColor();
        MsdfFont font = Fonts.SF_MEDIUM;

        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            float sx = cardX + pad + i * (slotSz + gap);
            float sy = y + pad;

            boolean valuable = highlightValuables.getValue() && isValuableItem(stack);
            int slotBg = valuable
                    ? ColorUtil.withAlpha(accent, 42)
                    : ColorUtil.rgba(20, 23, 35, 200);
            Render2D.drawRoundedRect(sx, sy, slotSz, slotSz, 4.0F * unit, slotBg);
            if (valuable) {
                Render2D.drawRoundedOutline(sx, sy, slotSz, slotSz, 4.0F * unit, 0.8F * unit,
                        ColorUtil.withAlpha(accent, 175));
            }

            // Durability bar at bottom of slot
            if (this.armorDurability.getValue() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                float durPct = Mth.clamp(1.0F - (float) stack.getDamageValue() / (float) stack.getMaxDamage(), 0.0F, 1.0F);
                float barX = sx + 2.0F * unit;
                float barY = sy + slotSz - 2.3F * unit;
                float barW = slotSz - 4.0F * unit;
                float barH = 1.4F * unit;
                Render2D.drawRoundedRect(barX, barY, barW, barH, 0.7F * unit, ColorUtil.rgba(0, 0, 0, 160));
                int durCol = getHpColor(durPct, false, accent);
                Render2D.drawRoundedRect(barX, barY, Math.max(1.2F * unit, barW * durPct), barH, 0.7F * unit, durCol);
            }

            // Enchant summary
            if (this.armorEnchants.getValue()) {
                List<String> enchLines = extractShortEnchants(stack);
                if (!enchLines.isEmpty()) {
                    float eSize = 5.6F * unit;
                    float lineStep = 6.2F * unit;
                    for (int l = 0; l < enchLines.size(); l++) {
                        String line = enchLines.get(l);
                        float lw = font.getWidth(line, eSize);
                        float lx = sx + (slotSz - lw) / 2.0F;
                        float ly = enchantsAbove
                                ? (y - (enchLines.size() - l) * lineStep - 1.0F * unit)
                                : (y + cardH + 2.0F * unit + l * lineStep);
                        Render2D.drawRoundedRect(lx - 1.5F * unit, ly - 0.5F * unit,
                                lw + 3.0F * unit, eSize + 1.5F * unit, 2.0F * unit, ColorUtil.rgba(10, 12, 18, 195));
                        Fonts.drawString(font, line, lx, ly, eSize, HudStyle.TEXT_PRIMARY);
                    }
                }
            }
        }

        // Flush custom 2D geometry before rendering Minecraft ItemStacks
        Render2DUtil.flush();
        Matrix3x2fStack pose = extractor.pose();

        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            float sx = cardX + pad + i * (slotSz + gap);
            float sy = y + pad;
            float iconDrawSz = slotSz - 3.5F * unit;
            float itemScale = iconDrawSz / 16.0F;

            pose.pushMatrix();
            pose.translate(sx + (slotSz - iconDrawSz) / 2.0F, sy + (slotSz - iconDrawSz) / 2.0F - 0.5F * unit);
            pose.scale(itemScale, itemScale);
            extractor.item(stack, 0, 0);
            pose.popMatrix();
        }

        // Stack counts drawn on top of items
        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            if (stack.getCount() > 1) {
                float sx = cardX + pad + i * (slotSz + gap);
                float sy = y + pad;
                String cnt = String.valueOf(stack.getCount());
                float cSz = 6.2F * unit;
                float cw = font.getWidth(cnt, cSz);
                float cx = sx + slotSz - cw - 1.5F * unit;
                float cy = sy + slotSz - font.textHeight(cSz) - 2.0F * unit;
                Fonts.drawString(font, cnt, cx, cy, cSz, HudStyle.TEXT_PRIMARY);
            }
        }
    }

    private void renderLogOutTags(Render2DEvent event, float unit) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, LoggedOutPlayer>> it = loggedOutPlayers.entrySet().iterator();

        while (it.hasNext()) {
            LoggedOutPlayer logged = it.next().getValue();
            long elapsed = now - logged.logOutTime;

            if (elapsed >= 120_000L) {
                it.remove();
                continue;
            }

            float alpha = 1.0F;
            if (elapsed > 115_000L) {
                alpha = Math.max(0.0F, 1.0F - (float) (elapsed - 115_000L) / 5000.0F);
            }

            int totalSec = (int) (elapsed / 1000L);
            String timerStr = String.format(Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60);
            drawLoggedOutTag(event, logged, timerStr, alpha, unit);
        }
    }

    private void drawLoggedOutTag(Render2DEvent event, LoggedOutPlayer logged, String timerStr, float alpha, float unit) {
        Minecraft mc = event.getClient();
        Vec3 headPosition = logged.position.add(0.0D, logged.height + HEAD_OFFSET, 0.0D);
        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, headPosition);
        if (anchor == null) return;

        MsdfFont font = Fonts.SF_MEDIUM;
        float fontSz = FONT * unit;
        float unitSz = UNIT_FONT * unit;
        float padX = PAD_X * unit;
        float segGap = SEG_GAP * unit;
        float headSz = HEAD * unit;
        float pillH = PILL_H * unit;

        String hpNum = String.valueOf(logged.health + logged.absorption);
        float totalW = padX * 2.0F
                + headSz + 4.5F * unit
                + font.getWidth(logged.name, fontSz)
                + segGap * 2.0F + 1.0F * unit
                + font.getWidth(hpNum, fontSz) + 1.5F * unit + font.getWidth("hp", unitSz)
                + segGap * 2.0F + 1.0F * unit
                + font.getWidth(timerStr, fontSz);

        float pillX = anchor.x() - totalW / 2.0F;
        float pillY = anchor.y() - pillH;
        float cy = pillY + pillH / 2.0F;

        drawTagCard(pillX, pillY, totalW, pillH, RADIUS * unit, logged.isFriend, 0.6F, alpha);

        float cx = pillX + padX;
        if (logged.skin != null) {
            try {
                Render2D.drawHead(logged.skin, cx, cy - headSz / 2.0F, headSz, 3.0F * unit);
            } catch (Exception ignored) {
            }
        }
        cx += headSz + 4.5F * unit;

        float ty = cy - font.textHeight(fontSz) / 2.0F + 0.35F * unit;
        float unitTy = cy - font.textHeight(unitSz) / 2.0F + 0.45F * unit;
        Fonts.drawString(font, logged.name, cx, ty, fontSz, ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, alpha));
        cx += font.getWidth(logged.name, fontSz);

        cx = drawWatermarkDivider(cx, cy, segGap, unit, alpha);
        float maxHp = Math.max(1.0F, logged.maxHealth);
        int hpCol = ColorUtil.multiplyAlpha(getHpColor(logged.health / maxHp, logged.absorption > 0, Theme.getAccentColor()), alpha);
        Fonts.drawString(font, hpNum, cx, ty, fontSz, hpCol);
        cx += font.getWidth(hpNum, fontSz) + 1.5F * unit;
        Fonts.drawString(font, "hp", cx, unitTy, unitSz, ColorUtil.multiplyAlpha(HudStyle.TEXT_SECONDARY, alpha));
        cx += font.getWidth("hp", unitSz);

        cx = drawWatermarkDivider(cx, cy, segGap, unit, alpha);
        Fonts.drawString(font, timerStr, cx, ty, fontSz, ColorUtil.rgba(255, 105, 110, Math.round(255 * alpha)));
    }

    private static void drawTagCard(float x, float y, float w, float h, float radius,
                                    boolean isFriend, float targetFactor, float alpha) {
        HudStyle.drawCard(x, y, w, h, radius, alpha);

        if (isFriend) {
            int friendCol = ColorUtil.rgba(72, 232, 128, Math.round(255 * alpha));
            Render2D.drawGradientRound(x, y, w, h, radius,
                    ColorUtil.withAlpha(friendCol, Math.round(28 * alpha)),
                    ColorUtil.withAlpha(friendCol, Math.round(10 * alpha)),
                    ColorUtil.rgba(0, 0, 0, 0),
                    ColorUtil.rgba(0, 0, 0, 0));
            Render2D.drawRoundedOutline(x, y, w, h, radius, 0.9F,
                    ColorUtil.withAlpha(friendCol, Math.round(120 * alpha)));
        } else if (targetFactor > 0.02F) {
            int targetCol = ColorUtil.rgba(255, 76, 88, 255);
            int tintA = Math.round(42 * targetFactor * alpha);
            int outA = Math.round(150 * targetFactor * alpha);
            Render2D.drawGradientRound(x, y, w, h, radius,
                    ColorUtil.withAlpha(targetCol, tintA),
                    ColorUtil.withAlpha(targetCol, tintA / 2),
                    ColorUtil.rgba(0, 0, 0, 0),
                    ColorUtil.rgba(0, 0, 0, 0));
            Render2D.drawRoundedOutline(x, y, w, h, radius, 0.95F,
                    ColorUtil.withAlpha(targetCol, outA));
        }
    }

    private static float drawWatermarkDivider(float cx, float cy, float segGap, float unit, float alpha) {
        cx += segGap;
        float h = 9.0F * unit;
        Render2D.drawRoundedRect(cx, cy - h / 2.0F, Math.max(0.8F, 1.0F * unit), h, 0.5F * unit,
                ColorUtil.rgba(255, 255, 255, Math.round(28 * alpha)));
        return cx + Math.max(0.8F, 1.0F * unit) + segGap;
    }

    private static float drawWatermarkMetric(float cx, float cy, IconUse icon, String num, String unitText,
                                             int iconColor, int numColor, int unitColor,
                                             float iconSz, float fontSz, float unitSz, float unit) {
        MsdfFont font = Fonts.SF_MEDIUM;
        Fonts.drawIconFitted(icon, cx, cy, iconSz, ColorUtil.withAlpha(iconColor, 255));
        cx += Fonts.iconFittedWidth(icon, iconSz) + 3.0F * unit;
        float ty = cy - font.textHeight(fontSz) / 2.0F + 0.35F * unit;
        Fonts.drawString(font, num, cx, ty, fontSz, numColor);
        cx += font.getWidth(num, fontSz) + 0.8F * unit;
        float unitTy = cy - font.textHeight(unitSz) / 2.0F + 0.45F * unit;
        Fonts.drawString(font, unitText, cx, unitTy, unitSz, unitColor);
        return cx + font.getWidth(unitText, unitSz);
    }

    private static int getHpColor(float hpPct, boolean hasAbsorption, int accent) {
        if (hasAbsorption && hpPct >= 0.85F) {
            return ColorUtil.rgba(255, 214, 76, 255);
        }
        if (hpPct >= 0.65F) {
            return ColorUtil.rgba(86, 232, 136, 255);
        }
        if (hpPct >= 0.35F) {
            return ColorUtil.rgba(255, 198, 72, 255);
        }
        return ColorUtil.rgba(255, 86, 94, 255);
    }

    private static List<EffectTag> collectImportantEffects(LivingEntity entity) {
        Collection<MobEffectInstance> active = entity.getActiveEffects();
        if (active == null || active.isEmpty()) return List.of();

        List<EffectTag> list = new ArrayList<>(4);
        for (MobEffectInstance inst : active) {
            if (inst == null) continue;
            String descId = inst.getDescriptionId().toLowerCase(Locale.ROOT);
            String shortCode = null;
            int col = HudStyle.TEXT_PRIMARY;

            if (descId.contains("strength") || descId.contains("damage_boost")) {
                shortCode = "СИЛ";
                col = ColorUtil.rgba(255, 96, 102, 255);
            } else if (descId.contains("speed") || descId.contains("movement_speed")) {
                shortCode = "СКР";
                col = ColorUtil.rgba(96, 208, 255, 255);
            } else if (descId.contains("regeneration")) {
                shortCode = "РЕГ";
                col = ColorUtil.rgba(255, 130, 200, 255);
            } else if (descId.contains("resistance") || descId.contains("damage_resistance")) {
                shortCode = "СОПР";
                col = ColorUtil.rgba(175, 145, 255, 255);
            } else if (descId.contains("fire_resistance")) {
                shortCode = "ОГН";
                col = ColorUtil.rgba(255, 172, 72, 255);
            } else if (descId.contains("weakness")) {
                shortCode = "СЛБ";
                col = ColorUtil.rgba(170, 175, 195, 255);
            } else if (descId.contains("slowness") || descId.contains("movement_slowdown")) {
                shortCode = "ЗАМ";
                col = ColorUtil.rgba(150, 165, 210, 255);
            } else if (descId.contains("invisibility")) {
                shortCode = "ИНВ";
                col = ColorUtil.rgba(205, 215, 235, 255);
            }

            if (shortCode != null) {
                int amp = inst.getAmplifier() + 1;
                int sec = Math.max(0, inst.getDuration() / 20);
                String timeStr = sec >= 60
                        ? (sec / 60) + ":" + String.format(Locale.ROOT, "%02d", sec % 60)
                        : sec + "s";
                String label = shortCode + (amp > 1 ? amp : "") + " " + timeStr;
                list.add(new EffectTag(label, col));
                if (list.size() >= 4) break;
            }
        }
        return list;
    }

    private static List<String> extractShortEnchants(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        ItemEnchantments enchants = stack.getEnchantments();
        if (enchants == null || enchants.isEmpty()) return List.of();

        List<String> out = new ArrayList<>(3);
        for (Holder<Enchantment> holder : enchants.keySet()) {
            int lvl = enchants.getLevel(holder);
            String path = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            String code = switch (path) {
                case "protection" -> "Зщ";
                case "fire_protection" -> "ОгЗ";
                case "blast_protection" -> "ВзЗ";
                case "projectile_protection" -> "СнЗ";
                case "thorns" -> "Шп";
                case "unbreaking" -> "Пр";
                case "mending" -> "Пч";
                case "sharpness" -> "Ост";
                case "fire_aspect" -> "Зг";
                case "knockback" -> "Отд";
                case "power" -> "Сл";
                case "feather_falling" -> "Нв";
                default -> null;
            };
            if (code != null) {
                out.add(lvl > 1 ? (code + lvl) : code);
                if (out.size() >= 3) break;
            }
        }
        return out;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof Player player) {
            boolean isFriend = FriendManager.getInstance().isFriend(player);
            if (isFriend) {
                return this.targets.isEnabled("Друзья");
            }
            boolean naked = isNaked(player);
            if (naked) {
                return this.targets.isEnabled("Голые");
            }
            return this.targets.isEnabled("Игроки");
        }
        if (entity instanceof Monster || entity instanceof Enemy) {
            return this.targets.isEnabled("Мобы");
        }
        if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature) {
            return this.targets.isEnabled("Животные");
        }
        return false;
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ItemStyle getItemStyle(ItemStack stack, float baseTextSize, float baseItemSize) {
        if (stack == null || stack.isEmpty()) {
            return new ItemStyle(HudStyle.TEXT_PRIMARY, baseTextSize, baseItemSize);
        }

        boolean isHead = stack.is(Items.PLAYER_HEAD);
        boolean isTotem = stack.is(Items.TOTEM_OF_UNDYING) && (stack.isEnchanted() || stack.hasFoil());
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        boolean isTalisman = name.contains("талисман") || name.contains("талик") || name.contains("сфера") || name.contains("шар") || name.contains("шарик");

        if (highlightValuables.getValue()) {
            if (isTalisman) {
                return new ItemStyle(ColorUtil.rgba(255, 92, 102, 255), baseTextSize * 1.1F, baseItemSize * 1.15F);
            } else if (isHead) {
                return new ItemStyle(ColorUtil.rgba(255, 225, 85, 255), baseTextSize * 1.1F, baseItemSize * 1.15F);
            } else if (isTotem) {
                return new ItemStyle(ColorUtil.rgba(255, 210, 65, 255), baseTextSize * 1.1F, baseItemSize * 1.15F);
            }
        }

        return new ItemStyle(HudStyle.TEXT_PRIMARY, baseTextSize, baseItemSize);
    }

    private boolean isValuableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.TOTEM_OF_UNDYING) && (stack.isEnchanted() || stack.hasFoil())) return true;
        if (stack.is(Items.PLAYER_HEAD)) return true;
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return name.contains("талисман") || name.contains("сфера") || name.contains("шар") || name.contains("шарик") || name.contains("талик");
    }

    private void renderClusteredItems(Render2DEvent event, List<ItemEntity> items, float tickDelta, float unit) {
        List<List<ItemEntity>> clusters = new ArrayList<>();
        boolean[] visited = new boolean[items.size()];
        double maxDistSq = 3.0 * 3.0;

        for (int i = 0; i < items.size(); i++) {
            if (visited[i]) continue;
            List<ItemEntity> cluster = new ArrayList<>();
            ItemEntity current = items.get(i);
            cluster.add(current);
            visited[i] = true;

            for (int j = i + 1; j < items.size(); j++) {
                if (!visited[j]) {
                    ItemEntity other = items.get(j);
                    if (current.distanceToSqr(other) <= maxDistSq) {
                        cluster.add(other);
                        visited[j] = true;
                    }
                }
            }
            clusters.add(cluster);
        }

        for (List<ItemEntity> cluster : clusters) {
            if (cluster.size() == 1) {
                drawItemTag(event, cluster.get(0), tickDelta, unit);
            } else {
                drawClusteredItemTag(event, cluster, tickDelta, unit);
            }
        }
    }

    private void drawClusteredItemTag(Render2DEvent event, List<ItemEntity> cluster, float tickDelta, float unit) {
        Minecraft mc = event.getClient();

        double avgX = 0, avgY = 0, avgZ = 0;
        for (ItemEntity e : cluster) {
            Vec3 pos = Render3DUtil.interpolatedPosition(e, tickDelta);
            avgX += pos.x;
            avgY += pos.y;
            avgZ += pos.z;
        }
        avgX /= cluster.size();
        avgY /= cluster.size();
        avgZ /= cluster.size();

        Vec3 position = new Vec3(avgX, avgY + 0.35D, avgZ);
        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, position);
        if (anchor == null) return;

        MsdfFont font = Fonts.SF_MEDIUM;
        float baseTextSize = FONT * unit;
        float baseItemSize = 11.5F * unit;
        float gap = 4.5F * unit;
        float rowGap = 2.5F * unit;
        float pad = 6.0F * unit;

        float maxRowWidth = 0.0F;
        float totalHeight = pad * 2.0F;

        for (int i = 0; i < cluster.size(); i++) {
            ItemStack stack = cluster.get(i).getItem();
            ItemStyle style = getItemStyle(stack, baseTextSize, baseItemSize);
            String countStr = stack.getCount() > 1 ? ("x" + stack.getCount() + " ") : "";
            String itemName = countStr + stack.getHoverName().getString();
            float textWidth = font.getWidth(itemName, style.textSize());

            float rowWidth = style.iconSize() + gap + textWidth;
            if (rowWidth > maxRowWidth) maxRowWidth = rowWidth;

            float rowH = Math.max(style.iconSize(), font.textHeight(style.textSize())) + 2.0F * unit;
            totalHeight += rowH;
            if (i < cluster.size() - 1) totalHeight += rowGap;
        }

        float pillWidth = pad * 2.0F + maxRowWidth;
        float pillX = anchor.x() - pillWidth / 2.0F;
        float pillY = anchor.y() - totalHeight;

        HudStyle.drawCard(pillX, pillY, pillWidth, totalHeight, RADIUS * unit);

        float cursorY = pillY + pad;
        for (ItemEntity entity : cluster) {
            ItemStack stack = entity.getItem();
            ItemStyle style = getItemStyle(stack, baseTextSize, baseItemSize);
            String countStr = stack.getCount() > 1 ? ("x" + stack.getCount() + " ") : "";
            String itemName = countStr + stack.getHoverName().getString();

            float rowH = Math.max(style.iconSize(), font.textHeight(style.textSize())) + 2.0F * unit;
            float centerY = cursorY + rowH / 2.0F;

            var extractor = event.getGuiGraphicsExtractor();
            if (extractor != null) {
                Render2DUtil.flush();
                Matrix3x2fStack pose = extractor.pose();
                float itemScale = style.iconSize() / 16.0F;
                pose.pushMatrix();
                pose.translate(pillX + pad, centerY - style.iconSize() / 2.0F);
                pose.scale(itemScale, itemScale);
                extractor.item(stack, 0, 0);
                pose.popMatrix();
            }

            float textX = pillX + pad + style.iconSize() + gap;
            float textY = centerY - font.textHeight(style.textSize()) / 2.0F + 0.35F * unit;
            Fonts.drawString(font, itemName, textX, textY, style.textSize(), style.color());
            cursorY += rowH + rowGap;
        }
    }

    private void drawItemTag(Render2DEvent event, ItemEntity entity, float tickDelta, float unit) {
        Minecraft mc = event.getClient();
        Vec3 position = Render3DUtil.interpolatedPosition(entity, tickDelta)
                .add(0.0D, entity.getBbHeight() + 0.25D, 0.0D);
        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, position);
        if (anchor == null) return;

        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;

        ItemStyle style = getItemStyle(stack, FONT * unit, 11.5F * unit);
        MsdfFont font = Fonts.SF_MEDIUM;
        float gap = 4.5F * unit;
        float pad = 6.5F * unit;

        String countStr = stack.getCount() > 1 ? ("x" + stack.getCount() + " ") : "";
        String itemName = countStr + stack.getHoverName().getString();

        float textWidth = font.getWidth(itemName, style.textSize());
        float width = pad * 2.0F + style.iconSize() + gap + textWidth;
        float pillHeight = Math.max(19.0F * unit, style.iconSize() + 5.0F * unit);
        float pillX = anchor.x() - width / 2.0F;
        float pillY = anchor.y() - pillHeight;
        float centerY = pillY + pillHeight / 2.0F;

        HudStyle.drawCard(pillX, pillY, width, pillHeight, 6.5F * unit);

        var extractor = event.getGuiGraphicsExtractor();
        if (extractor != null) {
            Render2DUtil.flush();
            Matrix3x2fStack pose = extractor.pose();
            float itemScale = style.iconSize() / 16.0F;
            pose.pushMatrix();
            pose.translate(pillX + pad, centerY - style.iconSize() / 2.0F);
            pose.scale(itemScale, itemScale);
            extractor.item(stack, 0, 0);
            pose.popMatrix();
        }

        float textX = pillX + pad + style.iconSize() + gap;
        float textY = centerY - font.textHeight(style.textSize()) / 2.0F + 0.35F * unit;
        Fonts.drawString(font, itemName, textX, textY, style.textSize(), style.color());
    }

    private void drawProjectileTag(Render2DEvent event, Projectile projectile, float tickDelta, float unit) {
        Minecraft mc = event.getClient();
        Vec3 position = Render3DUtil.interpolatedPosition(projectile, tickDelta)
                .add(0.0D, projectile.getBbHeight() + 0.25D, 0.0D);
        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, position);
        if (anchor == null) return;

        ItemStack stack = getProjectileItemStack(projectile);
        String name = projectile.getDisplayName().getString();
        if (stack != null && !stack.isEmpty()) {
            name = stack.getHoverName().getString();
        }

        MsdfFont font = Fonts.SF_MEDIUM;
        float textSize = FONT * unit;
        float itemSize = 11.5F * unit;
        float gap = 4.5F * unit;
        float pad = 6.5F * unit;

        float textWidth = font.getWidth(name, textSize);
        boolean hasItem = stack != null && !stack.isEmpty();
        float width = pad * 2.0F + (hasItem ? (itemSize + gap) : 0.0F) + textWidth;
        float pillHeight = 19.0F * unit;
        float pillX = anchor.x() - width / 2.0F;
        float pillY = anchor.y() - pillHeight;
        float centerY = pillY + pillHeight / 2.0F;

        HudStyle.drawCard(pillX, pillY, width, pillHeight, 6.5F * unit);

        float cursor = pillX + pad;
        if (hasItem) {
            var extractor = event.getGuiGraphicsExtractor();
            if (extractor != null) {
                Render2DUtil.flush();
                Matrix3x2fStack pose = extractor.pose();
                float itemScale = itemSize / 16.0F;
                pose.pushMatrix();
                pose.translate(cursor, centerY - itemSize / 2.0F);
                pose.scale(itemScale, itemScale);
                extractor.item(stack, 0, 0);
                pose.popMatrix();
            }
            cursor += itemSize + gap;
        }

        float textY = centerY - font.textHeight(textSize) / 2.0F + 0.35F * unit;
        Fonts.drawString(font, name, cursor, textY, textSize, HudStyle.TEXT_PRIMARY);
    }

    private static ItemStack getProjectileItemStack(Projectile projectile) {
        if (projectile instanceof ThrownEnderpearl) {
            return Items.ENDER_PEARL.getDefaultInstance();
        } else if (projectile instanceof ThrownTrident) {
            return Items.TRIDENT.getDefaultInstance();
        } else if (projectile instanceof SpectralArrow) {
            return Items.SPECTRAL_ARROW.getDefaultInstance();
        } else if (projectile instanceof AbstractArrow) {
            return Items.ARROW.getDefaultInstance();
        } else if (projectile instanceof ThrownSplashPotion potion) {
            return potion.getItem();
        } else if (projectile instanceof ThrownExperienceBottle) {
            return Items.EXPERIENCE_BOTTLE.getDefaultInstance();
        } else if (projectile instanceof Fireball || projectile instanceof SmallFireball) {
            return Items.FIRE_CHARGE.getDefaultInstance();
        } else if (projectile instanceof WindCharge || projectile instanceof BreezeWindCharge) {
            return Items.WIND_CHARGE.getDefaultInstance();
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> equipment(LivingEntity entity) {
        List<ItemStack> stacks = new ArrayList<>(EQUIPMENT_ORDER.length);
        for (EquipmentSlot slot : EQUIPMENT_ORDER) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return stacks;
    }

    private static Donate donates(AbstractClientPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team == null) return null;
        Component prefix = team.getPlayerPrefix();
        if (prefix == null) return null;

        String raw = prefix.getString();
        String name = cleanName(raw);
        if (name.isEmpty()) return null;

        Integer color = extractColor(prefix);
        if (color == null) color = legacyColor(raw);

        int rgb = color != null
                ? ColorUtil.rgba((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255)
                : Theme.getAccentColor();
        return new Donate(name, rgb);
    }

    private static String cleanName(String text) {
        if (text == null || text.isEmpty()) return "";
        String noColor = text.replaceAll("(?i)§[0-9a-fk-orx]", "").replaceAll("§.", "");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < noColor.length(); i++) {
            char c = normalizeSmallCapChar(noColor.charAt(i));
            if (isRenderableChar(c)) {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static char normalizeSmallCapChar(char c) {
        return switch (c) {
            case 'ᴀ' -> 'A';
            case 'ʙ' -> 'B';
            case 'ᴄ' -> 'C';
            case 'ᴅ' -> 'D';
            case 'ᴇ' -> 'E';
            case 'ғ' -> 'F';
            case 'ɢ' -> 'G';
            case 'ʜ' -> 'H';
            case 'ɪ' -> 'I';
            case 'ᴊ' -> 'J';
            case 'ᴋ' -> 'K';
            case 'ʟ' -> 'L';
            case 'ᴍ' -> 'M';
            case 'ɴ' -> 'N';
            case 'ᴏ' -> 'O';
            case 'ᴘ' -> 'P';
            case 'ǫ', 'ϙ' -> 'Q';
            case 'ʀ' -> 'R';
            case 'ꜱ', 's' -> 'S';
            case 'ᴛ' -> 'T';
            case 'ᴜ' -> 'U';
            case 'ᴠ' -> 'V';
            case 'ᴡ' -> 'W';
            case 'ʏ' -> 'Y';
            case 'ᴢ' -> 'Z';
            case 'а' -> 'А';
            case 'б' -> 'Б';
            case 'в' -> 'В';
            case 'г' -> 'Г';
            case 'д' -> 'Д';
            case 'е' -> 'E';
            case 'ж' -> 'Ж';
            case 'з' -> 'З';
            case 'и' -> 'И';
            case 'й' -> 'Й';
            case 'к' -> 'К';
            case 'л' -> 'Л';
            case 'м' -> 'М';
            case 'н' -> 'Н';
            case 'о' -> 'О';
            case 'п' -> 'П';
            case 'р' -> 'Р';
            case 'с' -> 'С';
            case 'т' -> 'Т';
            case 'у' -> 'У';
            case 'ф' -> 'Ф';
            case 'х' -> 'Х';
            case 'ц' -> 'Ц';
            case 'ч' -> 'Ч';
            case 'ш' -> 'Ш';
            case 'щ' -> 'Щ';
            case 'ъ' -> 'Ъ';
            case 'ы' -> 'Ы';
            case 'ь' -> 'Ь';
            case 'э' -> 'Э';
            case 'ю' -> 'Ю';
            case 'я' -> 'Я';
            default -> c;
        };
    }

    private static boolean isRenderableChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || (c >= 'а' && c <= 'я') || (c >= 'А' && c <= 'Я') || c == 'ё' || c == 'Ё'
                || c == ' ' || c == '[' || c == ']' || c == '(' || c == ')' || c == '{' || c == '}'
                || c == '-' || c == '+' || c == '_' || c == '|' || c == ':' || c == '.' || c == '!'
                || c == '?' || c == '*' || c == '#' || c == '<' || c == '>';
    }

    private static Integer legacyColor(String text) {
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '§') continue;
            ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(i + 1));
            if (formatting == null) continue;
            TextColor color = TextColor.fromLegacyFormat(formatting);
            if (color != null) return color.getValue();
        }
        return null;
    }

    private static Integer extractColor(Component component) {
        if (component == null) return null;
        TextColor color = component.getStyle().getColor();
        if (color != null) return color.getValue();
        for (Component sibling : component.getSiblings()) {
            Integer siblingColor = extractColor(sibling);
            if (siblingColor != null) return siblingColor;
        }
        return null;
    }
}
