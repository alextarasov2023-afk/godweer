package pub.godweer.module.impl.render;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;

import java.util.List;

/**
 * Create by daun kvass
 */
@Getter
public class Removals extends Module {

    public static Removals INSTANCE;

    private final HeaderSetting overlaysHeader = header("Overlays");
    private final MultiModeSetting overlays = multiMode("Overlays",
            List.of("Fire", "Underwater", "Block", "HurtCam", "Vignette", "Bad Effects", "Portal"),
            "Fire", "Underwater", "Block", "HurtCam", "Totem", "Vignette", "Bad Effects", "Portal");

    private final HeaderSetting particlesHeader = header("Particles");
    private final MultiModeSetting particles = multiMode("Particles",
            List.of("Explosions", "Smoke", "Potions", "Block Break"),
            "Explosions", "Smoke", "Potions", "Campfire", "Block Break", "Criticals", "Hearts","Totem");

    private final HeaderSetting otherHeader = header("Other");
    private final CheckBox foliage = checkbox("Растительность", false);
    private final CheckBox leaves = checkbox("Листва", false);
    private final CheckBox banners = checkbox("Баннера", false);
    private final CheckBox weather = checkbox("Погода ", true);

    public Removals() {
        super("Removals", "Убирает лишнее в мире: траву, листву, поверхностные блоки", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reloadChunks();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reloadChunks();
    }


    public void reloadChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.clearTintCaches();

            int renderDistance = mc.options.getEffectiveRenderDistance();
            int chunkX = mc.player.getBlockX() >> 4;
            int chunkZ = mc.player.getBlockZ() >> 4;

            mc.level.setSectionRangeDirty(
                    chunkX - renderDistance - 2, mc.level.getMinSectionY(), chunkZ - renderDistance - 2,
                    chunkX + renderDistance + 2, mc.level.getMaxSectionY(), chunkZ + renderDistance + 2
            );

            if (mc.levelRenderer != null) {
                mc.levelRenderer.clearVisibleSections();
            }
        }
    }

    public boolean isWeatherDisabled() {
        return isEnabled() && weather.getValue();
    }

    public boolean isOverlayDisabled(String name) {
        return isEnabled() && overlays.isEnabled(name);
    }

    public boolean isBadEffectsDisabled() {
        return isEnabled() && overlays.isEnabled("Bad Effects");
    }

    public boolean isBannersDisabled() {
        return isEnabled() && banners.getValue();
    }

    public boolean isBlockBreakParticlesDisabled() {
        return isEnabled() && particles.isEnabled("Block Break");
    }

    public boolean shouldCancelParticle(ParticleOptions options) {
        if (!isEnabled() || options == null) return false;

        ParticleType<?> type = options.getType();

        if (weather.getValue() && (type == ParticleTypes.RAIN || type == ParticleTypes.SPLASH)) {
            return true;
        }

        if (particles.isEnabled("Potions") && (
                type == ParticleTypes.ENTITY_EFFECT
                        || type == ParticleTypes.INSTANT_EFFECT
                        || type == ParticleTypes.WITCH
                        || type == ParticleTypes.FLASH)) {
            return true;
        }
        if (particles.isEnabled("Totem") && (
                type == ParticleTypes.TOTEM_OF_UNDYING)) {
            return true;
        }
        if (particles.isEnabled("Block Break") && (
                type == ParticleTypes.BLOCK
                        || type == ParticleTypes.DUST
                        || type == ParticleTypes.FALLING_DUST
                        || type == ParticleTypes.BLOCK_MARKER)) {
            return true;
        }

        if (particles.isEnabled("Explosions") && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER)) {
            return true;
        }
        if (particles.isEnabled("Smoke") && (type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE || type == ParticleTypes.POOF)) {
            return true;
        }
        if (particles.isEnabled("Campfire") && (type == ParticleTypes.CAMPFIRE_COSY_SMOKE || type == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE)) {
            return true;
        }
        if (particles.isEnabled("Criticals") && (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT)) {
            return true;
        }
        if (particles.isEnabled("Hearts") && (type == ParticleTypes.HEART || type == ParticleTypes.DAMAGE_INDICATOR)) {
            return true;
        }

        return false;
    }


    public boolean isFoliage(BlockState state) {
        if (!isEnabled() || state == null) return false;

        Block block = state.getBlock();

        if (block instanceof GrassBlock
                || block == Blocks.FARMLAND
                || block instanceof MyceliumBlock
                || block instanceof PathBlock
                || block == Blocks.MOSS_BLOCK
                || block instanceof MangroveRootsBlock
                || block instanceof HugeMushroomBlock) {
            return false;
        }
        if (leaves.getValue()) {
            if (block instanceof LeavesBlock || state.is(BlockTags.LEAVES)) {
                return true;
            }
        }

        if (foliage.getValue()) {
            if (state.is(BlockTags.FLOWERS)
                    || state.is(BlockTags.CROPS)
                    || state.is(BlockTags.CAVE_VINES)) {
                return true;
            }

            if (block instanceof BushBlock
                    || block instanceof CropBlock
                    || block instanceof StemBlock
                    || block instanceof AttachedStemBlock
                    || block instanceof SweetBerryBushBlock
                    || block instanceof SugarCaneBlock
                    || block instanceof CactusBlock
                    || block instanceof BambooStalkBlock
                    || block instanceof BambooSaplingBlock
                    || block instanceof VineBlock
                    || block instanceof GrowingPlantBlock
                    || block instanceof GrowingPlantHeadBlock
                    || block instanceof GrowingPlantBodyBlock
                    || block instanceof SeaPickleBlock
                    || block instanceof SeagrassBlock
                    || block instanceof TallSeagrassBlock
                    || block instanceof HangingRootsBlock
                    || block instanceof SporeBlossomBlock
                    || block instanceof BigDripleafBlock
                    || block instanceof BigDripleafStemBlock
                    || block instanceof SmallDripleafBlock
                    || block instanceof CocoaBlock
                    || block instanceof GlowLichenBlock
                    || block instanceof SculkVeinBlock
                    || block == Blocks.MOSS_CARPET) {
                return true;
            }

            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (path.contains("dead_bush") || path.contains("bush")
                    || path.contains("grass") || path.contains("fern")
                    || path.contains("wheat") || path.contains("carrot") || path.contains("potato") || path.contains("beetroot")
                    || path.contains("sugar_cane") || path.contains("reeds")
                    || path.contains("flower") || path.contains("tulip") || path.contains("rose") || path.contains("orchid")
                    || path.contains("daisy") || path.contains("dandelion") || path.contains("poppy")
                    || path.contains("sapling") || path.contains("sprout") || path.contains("roots")
                    || path.contains("pitcher") || path.contains("torchflower") || path.contains("petal")) {
                return true;
            }
        }

        return false;
    }
}