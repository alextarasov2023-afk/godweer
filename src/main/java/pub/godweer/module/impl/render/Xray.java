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
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Xray как ESP блоков: руды обводятся рамками сквозь стены, ванильные чанки не
 * трогаются. Настоящий xray (спрятать камень и увидеть руду самими текстурами)
 * требует миксинов в сборку меша секции чанка, а это ломает и свет, и то, что
 * видит античит сервера, поэтому здесь только подсветка.
 *
 * Create by daun kvass
 */
public final class Xray extends Module {

    private static final int REFRESH_TICKS = 10;
    private static final int MAX_BLOCKS = 512;

    private static final List<Ore> ORES = List.of(
            new Ore("Алмаз", 0x5CE5D6, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
            new Ore("Изумруд", 0x17B85A, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
            new Ore("Древний обломок", 0xBE5A28, Blocks.ANCIENT_DEBRIS),
            new Ore("Золото", 0xEFC63C, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
            new Ore("Железо", 0xD8BFA9, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
            new Ore("Лазурит", 0x2A58C8, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
            new Ore("Редстоун", 0xC82828, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
            new Ore("Медь", 0xD2825A, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
            new Ore("Уголь", 0x787878, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
            new Ore("Кварц", 0xE8E0D8, Blocks.NETHER_QUARTZ_ORE),
            new Ore("Обсидиан", 0x7A50C8, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN));

    public final SliderSetting radius = slider("Радиус", 24.0f, 8.0f, 48.0f, 1.0f);
    public final MultiModeSetting ores = multiMode("Руды",
            List.of("Алмаз", "Изумруд", "Древний обломок", "Золото", "Лазурит", "Редстоун", "Обсидиан"),
            "Алмаз", "Изумруд", "Древний обломок", "Золото", "Железо", "Лазурит", "Редстоун",
            "Медь", "Уголь", "Кварц", "Обсидиан");
    public final CheckBox throughWalls = checkbox("Сквозь стены", true);

    private final List<Render3D.WorldBox> boxes = new ArrayList<>();
    private final Map<Block, Ore> wanted = new HashMap<>();
    private int ticksUntilScan = REFRESH_TICKS;

    public Xray() {
        super("Xray", "Рамки руд сквозь блоки", Category.RENDER);
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

        wanted.clear();
        for (Ore ore : ORES) {
            if (!ores.isEnabled(ore.label())) continue;
            for (Block block : ore.blocks) {
                wanted.put(block, ore);
            }
        }

        boxes.clear();
        if (wanted.isEmpty()) return;
        BlockScan.around(level(), player().position(), (int) radius.get(), MAX_BLOCKS, wanted,
                (x, y, z, ore) -> boxes.add(Render3D.WorldBox.ofBlock(x, y, z, ore.fill, ore.outline)));
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (boxes.isEmpty()) return;
        Render3D.drawBoxes(boxes, throughWalls.get());
    }

    private static final class Ore {
        private final String label;
        private final Block[] blocks;
        private final Color fill;
        private final Color outline;

        private Ore(String label, int rgb, Block... blocks) {
            this.label = label;
            this.blocks = blocks;
            this.fill = new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 42);
            this.outline = new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 215);
        }

        private String label() {
            return label;
        }
    }
}
