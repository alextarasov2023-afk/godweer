package pub.godweer.module.impl.render;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pub.godweer.util.render.Render3D;
import pub.godweer.util.render.world.BlockScan;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.GameTickEvent;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Подсветка блоков сквозь стены: сундуки, спавнеры, руды и прочее — каждый блок
 * цветом, который он сам и означает (алмазная руда — алмазная бирюза, золото —
 * золотой и т.д.), без выбора цвета из темы.
 *
 * Работает через батч-примитивы Render3D (заливка/каркас/углы/свечение), а не
 * drawBox() на каждый блок: шейдерные стили BlockHighlight считают uv из локальных
 * координат куба 0..1, поэтому на несколько блоков одним прогоном их не разложить,
 * а 300 отдельных render pass в кадр не влезают.
 *
 * Create by daun kvass
 */
public final class BlockEsp extends Module {

    public static final String STYLE_GLOW = "Свечение";
    public static final String STYLE_CORNERS = "Углы";
    public static final String STYLE_WIREFRAME = "Каркас";

    private static final int REFRESH_TICKS = 20;
    private static final int MAX_BLOCKS = 384;
    private static final float CORNER = 0.34F;
    /** Рамка чуть больше блока, иначе угла сливаются с текстурной сеткой. */
    private static final float EXPAND = 0.02F;

    /** Цвета блоков — как их видно в игре, без темизации. */
    private static final List<BlockColor> ORES = List.of(
            bc(Blocks.DIAMOND_ORE, 0x5CE5D6), bc(Blocks.DEEPSLATE_DIAMOND_ORE, 0x5CE5D6),
            bc(Blocks.EMERALD_ORE, 0x17B85A), bc(Blocks.DEEPSLATE_EMERALD_ORE, 0x17B85A),
            bc(Blocks.GOLD_ORE, 0xEFC63C), bc(Blocks.DEEPSLATE_GOLD_ORE, 0xEFC63C), bc(Blocks.NETHER_GOLD_ORE, 0xEFC63C),
            bc(Blocks.IRON_ORE, 0xD8BFA9), bc(Blocks.DEEPSLATE_IRON_ORE, 0xD8BFA9),
            bc(Blocks.LAPIS_ORE, 0x2A58C8), bc(Blocks.DEEPSLATE_LAPIS_ORE, 0x2A58C8),
            bc(Blocks.REDSTONE_ORE, 0xC82828), bc(Blocks.DEEPSLATE_REDSTONE_ORE, 0xC82828),
            bc(Blocks.COPPER_ORE, 0xD2825A), bc(Blocks.DEEPSLATE_COPPER_ORE, 0xD2825A),
            bc(Blocks.COAL_ORE, 0x787878), bc(Blocks.DEEPSLATE_COAL_ORE, 0x787878),
            bc(Blocks.NETHER_QUARTZ_ORE, 0xE8E0D8));

    private static final List<BlockColor> ORE_BLOCKS = buildOreBlocks();

    private static List<BlockColor> buildOreBlocks() {
        List<BlockColor> out = new ArrayList<>();
        out.add(bc(Blocks.DIAMOND_BLOCK, 0x5CE5D6));
        out.add(bc(Blocks.EMERALD_BLOCK, 0x17B85A));
        out.add(bc(Blocks.GOLD_BLOCK, 0xEFC63C));
        out.add(bc(Blocks.IRON_BLOCK, 0xD8BFA9));
        out.add(bc(Blocks.LAPIS_BLOCK, 0x2A58C8));
        out.add(bc(Blocks.REDSTONE_BLOCK, 0xC82828));
        out.add(bc(Blocks.COAL_BLOCK, 0x787878));
        out.add(bc(Blocks.QUARTZ_BLOCK, 0xE8E0D8));
        // Медь в 26.3 — семья (обычная/окисленная/восковая), берём целиком.
        Blocks.COPPER_BLOCK.forEach(block -> out.add(bc(block, 0xD2825A)));
        return List.copyOf(out);
    }

    private static final List<BlockColor> NETHERITE = List.of(
            bc(Blocks.NETHERITE_BLOCK, 0x5E5454),
            bc(Blocks.ANCIENT_DEBRIS, 0xBE5A28));

    private static final List<BlockColor> OBSIDIAN = List.of(
            bc(Blocks.OBSIDIAN, 0x7A50C8),
            bc(Blocks.CRYING_OBSIDIAN, 0x9A2BC8));

    private static final List<Section> GROUPS = List.of(
            new Section("Сундуки", bc(Blocks.CHEST, 0xE0B341), bc(Blocks.TRAPPED_CHEST, 0xE0B341)),
            new Section("Эндер-сундук", bc(Blocks.ENDER_CHEST, 0x1E9E86)),
            new Section("Шалкеры", bc(Blocks.SHULKER_BOX, 0xC06BD8)),
            new Section("Спавнеры", bc(Blocks.SPAWNER, 0x8A5BD8)),
            new Section("Маяки", bc(Blocks.BEACON, 0x7FD8E8)),
            new Section("Драконье яйцо", bc(Blocks.DRAGON_EGG, 0xD84A6E)),
            new Section("Ценности", merge(ORES, ORE_BLOCKS, NETHERITE, OBSIDIAN)),
            new Section("Руда", ORES),
            new Section("Блоки из руды", ORE_BLOCKS),
            new Section("Обсидиан", OBSIDIAN),
            new Section("Блоки незерита", NETHERITE));

    public final ModeSetting style = mode("Стиль", STYLE_GLOW, STYLE_GLOW, STYLE_CORNERS, STYLE_WIREFRAME);
    public final SliderSetting radius = slider("Радиус", 32.0F, 8.0F, 64.0F, 1.0F);
    public final MultiModeSetting blocks = multiMode("Блоки",
            List.of("Сундуки", "Эндер-сундук", "Шалкеры", "Спавнеры", "Маяки", "Драконье яйцо",
                    "Ценности", "Руда", "Блоки из руды", "Обсидиан", "Блоки незерита"),
            "Сундуки", "Эндер-сундук", "Шалкеры", "Спавнеры", "Ценности");
    public final CheckBox throughWalls = checkbox("Сквозь стены", true);
    public final CheckBox pulse = checkbox("Пульс", true);

    private final List<Render3D.WorldBox> boxes = new ArrayList<>();
    private final Map<Block, Colors> wanted = new HashMap<>();
    private int ticksUntilScan = REFRESH_TICKS;

    public BlockEsp() {
        super("BlockEsp", "Подсветка выбранных блоков сквозь стены", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        ticksUntilScan = 0;
    }

    @Override
    protected void onDisable() {
        boxes.clear();
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!inGame()) {
            boxes.clear();
            return;
        }
        if (--ticksUntilScan > 0) return;
        ticksUntilScan = REFRESH_TICKS;
        refresh();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (boxes.isEmpty()) return;

        boolean through = throughWalls.get();
        float reach = radius.get();
        float fadeNear = reach * 0.5F;
        float fadeFar = reach * 1.15F;
        float alpha = pulse.get()
                ? 0.72F + 0.28F * (float) Math.sin(System.currentTimeMillis() * 0.004D)
                : 1.0F;

        if (style.is(STYLE_GLOW)) {
            Render3D.drawBoxGlow(boxes, through, alpha, fadeNear, fadeFar);
        }
        Render3D.drawBoxFills(boxes, through, alpha, fadeNear, fadeFar);

        if (style.is(STYLE_WIREFRAME)) {
            Render3D.drawBoxOutlines(boxes, through, alpha, fadeNear, fadeFar);
        } else {
            Render3D.drawBoxCorners(boxes, through, alpha, fadeNear, fadeFar, CORNER);
        }
    }

    private void refresh() {
        boxes.clear();
        wanted.clear();

        for (Section section : GROUPS) {
            if (!blocks.isEnabled(section.label())) continue;
            for (BlockColor bc : section.blocks()) {
                wanted.put(bc.block(), bc.colors());
            }
        }
        if (wanted.isEmpty()) return;

        BlockScan.around(level(), player().position(), (int) radius.get(), MAX_BLOCKS, wanted,
                (x, y, z, colors) -> boxes.add(new Render3D.WorldBox(
                        x - EXPAND, y - EXPAND, z - EXPAND,
                        1.0F + EXPAND * 2.0F, 1.0F + EXPAND * 2.0F, 1.0F + EXPAND * 2.0F,
                        colors.fill, colors.edge)));
    }

    private static BlockColor bc(Block block, int rgb) {
        return new BlockColor(block, rgb);
    }

    @SafeVarargs
    private static List<BlockColor> merge(List<BlockColor>... lists) {
        List<BlockColor> all = new ArrayList<>();
        for (List<BlockColor> list : lists) {
            all.addAll(list);
        }
        return List.copyOf(all);
    }

    private record BlockColor(Block block, int rgb) {
        /** Заливка почти прозрачная, грани яркие — иначе 300 кубов превращаются в кашу. */
        private Colors colors() {
            int red = rgb >> 16 & 255;
            int green = rgb >> 8 & 255;
            int blue = rgb & 255;
            return new Colors(new Color(red, green, blue, 46), new Color(red, green, blue, 230));
        }
    }

    private record Section(String label, List<BlockColor> blocks) {
        private Section(String label, BlockColor... blocks) {
            this(label, List.of(blocks));
        }
    }

    private record Colors(Color fill, Color edge) {}
}
