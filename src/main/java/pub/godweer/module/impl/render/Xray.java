package pub.godweer.module.impl.render;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.GameTickEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.MultiModeSetting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Xray через прозрачность мира: при сборке секций чанка квады всех блоков, кроме
 * выбранных руд, не пишутся в меш (миксины SectionCompiler), поэтому мир вокруг
 * полностью прозрачен, а руда остаётся непрозрачной, видна сквозь стены и без
 * рамок и сканов. Выключение возвращает ванильные меши пересборкой секций.
 *
 * Create by daun kvass
 */
public final class Xray extends Module {

    private static final Set<Block> EMPTY = Set.of();

    private static final List<Ore> ORES = List.of(
            new Ore("Алмаз", Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
            new Ore("Изумруд", Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
            new Ore("Древний обломок", Blocks.ANCIENT_DEBRIS),
            new Ore("Золото", Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
            new Ore("Железо", Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
            new Ore("Лазурит", Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
            new Ore("Редстоун", Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
            new Ore("Медь", Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
            new Ore("Уголь", Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
            new Ore("Кварц", Blocks.NETHER_QUARTZ_ORE),
            new Ore("Обсидиан", Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN));

    public final MultiModeSetting ores = multiMode("Руды",
            List.of("Алмаз", "Изумруд", "Древний обломок", "Золото", "Лазурит", "Редстоун", "Обсидиан"),
            "Алмаз", "Изумруд", "Древний обломок", "Золото", "Железо", "Лазурит", "Редстоун",
            "Медь", "Уголь", "Кварц", "Обсидиан");

    private boolean wasActive;
    private Set<Block> lastSelection = EMPTY;

    public Xray() {
        super("Xray", "Мир полностью прозрачен, выбранные руды видны сквозь стены", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        XrayState.set(false, EMPTY);
        XrayState.rebuildAll();
        wasActive = false;
        lastSelection = EMPTY;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!inGame()) {
            if (wasActive) {
                XrayState.set(false, EMPTY);
                XrayState.rebuildAll();
                wasActive = false;
            }
            return;
        }

        Set<Block> selection = selectedBlocks();
        if (!wasActive || !selection.equals(lastSelection)) {
            apply(selection);
        }
    }

    private void apply() {
        apply(selectedBlocks());
    }

    private void apply(Set<Block> selection) {
        wasActive = true;
        lastSelection = selection;
        XrayState.set(true, selection);
        XrayState.rebuildAll();
    }

    private Set<Block> selectedBlocks() {
        Set<Block> blocks = new HashSet<>();
        for (Ore ore : ORES) {
            if (!ores.isEnabled(ore.label())) continue;
            for (Block block : ore.blocks) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private record Ore(String label, Block... blocks) {
    }
}
