package pub.godweer.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Мост между Xray и миксинами сборки секций чанка (SectionCompiler / ModelBlockRenderer).
 *
 * Классический xray: квады всех блоков, кроме выбранных руд, не попадают в меш
 * секции вовсе, поэтому мир вокруг полностью прозрачен, а руда рисуется
 * непрозрачно и видна сквозь стены без рамок и сканов. Жидкости гасятся там же —
 * иначе лава в Нижнем мире закрывала бы древний обломок. Выключение возвращает
 * ванильные меши пересборкой секций.
 *
 * Create by daun kvass
 */
public final class XrayState {

    private static final ThreadLocal<BlockState> CURRENT = new ThreadLocal<>();

    private static volatile boolean active;
    private static volatile Set<Block> targets = Set.of();

    private XrayState() {}

    public static void set(boolean on, Set<Block> targetBlocks) {
        active = on;
        targets = targetBlocks == null ? Set.of() : Set.copyOf(targetBlocks);
    }

    public static boolean isActive() {
        return active;
    }

    public static void beginBlock(BlockState state) {
        CURRENT.set(state);
    }

    public static void endBlock() {
        CURRENT.remove();
    }

    /**
     * Прятать ли текущий квад. Вне tesselateBlock (CURRENT == null) не трогаем
     * ничего: чужие вызовы рендера блоков xray не касаются.
     */
    public static boolean hideCurrent() {
        if (!active) return false;
        BlockState state = CURRENT.get();
        return state != null && !targets.contains(state.getBlock());
    }

    /**
     * Полная пересборка геометрии мира ванильным путём: заново создаются
     * ViewArea и SectionCompiler, все буферы секций освобождаются. Только так
     * xray/возврат видны на всей прогруженной дистанции, а не в радиусе ±8
     * секций вокруг игрока.
     */
    public static void rebuildAll() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.gameRenderer == null) return;
        mc.levelRenderer.invalidateCompiledGeometry(
                level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
    }
}
