package pub.godweer.module.impl.render;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pub.godweer.util.style.Theme;
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
 * Подсветка блоков сквозь стены: сундуки, спавнеры, руды и прочее — каждая группа
 * своим цветом, стили — мягкое свечение, только углы или полный каркас.
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

    private static final List<Group> GROUPS = List.of(
            new Group("Сундуки", 0xE0B341, Blocks.CHEST, Blocks.TRAPPED_CHEST),
            new Group("Эндер-сундук", 0x1E9E86, Blocks.ENDER_CHEST),
            new Group("Шалкеры", 0xC06BD8, Blocks.SHULKER_BOX),
            new Group("Спавнеры", 0x8A5BD8, Blocks.SPAWNER),
            new Group("Маяки", 0x7FD8E8, Blocks.BEACON),
            new Group("Руды", 0x9FBFC8,
                    Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
                    Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                    Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                    Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
                    Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
                    Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                    Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                    Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                    Blocks.NETHER_QUARTZ_ORE),
            new Group("Драгметаллы", 0x6FE0C0,
                    Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK, Blocks.NETHERITE_BLOCK),
            new Group("Обсидиан", 0x7A50C8, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN),
            new Group("Древний обломок", 0xBE5A28, Blocks.ANCIENT_DEBRIS),
            new Group("Драконье яйцо", 0xD84A6E, Blocks.DRAGON_EGG));

    public final ModeSetting style = mode("Стиль", STYLE_GLOW, STYLE_GLOW, STYLE_CORNERS, STYLE_WIREFRAME);
    public final SliderSetting radius = slider("Радиус", 32.0F, 8.0F, 64.0F, 1.0F);
    public final MultiModeSetting blocks = multiMode("Блоки",
            List.of("Сундуки", "Эндер-сундук", "Шалкеры", "Спавнеры", "Руды", "Драгметаллы"),
            "Сундуки", "Эндер-сундук", "Шалкеры", "Спавнеры", "Маяки", "Руды", "Драгметаллы",
            "Обсидиан", "Древний обломок", "Драконье яйцо");
    public final CheckBox themeColor = checkbox("Цвет из темы", false);
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

        int accent = Theme.getAccentColor();
        for (Group group : GROUPS) {
            if (!blocks.isEnabled(group.label())) continue;
            Colors colors = group.colors(themeColor.get() ? accent : group.rgb);
            for (Block block : group.blocks) {
                wanted.put(block, colors);
            }
        }
        if (wanted.isEmpty()) return;

        BlockScan.around(level(), player().position(), (int) radius.get(), MAX_BLOCKS, wanted,
                (x, y, z, colors) -> boxes.add(new Render3D.WorldBox(
                        x - EXPAND, y - EXPAND, z - EXPAND,
                        1.0F + EXPAND * 2.0F, 1.0F + EXPAND * 2.0F, 1.0F + EXPAND * 2.0F,
                        colors.fill, colors.edge)));
    }

    private record Colors(Color fill, Color edge) {}

    private static final class Group {
        private final String label;
        private final int rgb;
        private final Block[] blocks;

        private Group(String label, int rgb, Block... blocks) {
            this.label = label;
            this.rgb = rgb;
            this.blocks = blocks;
        }

        private String label() {
            return label;
        }

        /** Заливка почти прозрачная, грани яркие — иначе 300 кубов превращаются в кашу. */
        private Colors colors(int color) {
            int red = color >> 16 & 255;
            int green = color >> 8 & 255;
            int blue = color & 255;
            return new Colors(new Color(red, green, blue, 46), new Color(red, green, blue, 230));
        }
    }
}
