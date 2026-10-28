package pub.godweer.util.render.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Поиск блоков вокруг игрока по секциям чанков.
 *
 * Обход `level.getBlockState(new BlockPos(...))` по кубу не годится для ESP: на каждый
 * блок аллокация координат и поиск чанка, а радиус 48 — это полмиллиона блоков. Здесь
 * сначала читается палитра секции (maybeHas), и полные 4096 чтений делаются только в
 * тех секциях, где искомый блок вообще есть.
 *
 * Create by daun kvass
 */
public final class BlockScan {

    public interface Visitor<T> {
        void visit(int x, int y, int z, T value);
    }

    private BlockScan() {}

    /**
     * @param wanted  блок → метка (группа/цвет); пустой маппинг = искать нечего
     * @param maxHits потолок найденных блоков: больше сотни рамок кадр не вытянет
     * @return число вызовов visitor
     */
    public static <T> int around(ClientLevel level, Vec3 center, int radius, int maxHits,
                                 Map<Block, T> wanted, Visitor<T> visitor) {
        if (level == null || wanted.isEmpty()) return 0;

        double px = center.x, py = center.y, pz = center.z;
        double squared = (double) radius * radius;
        int minY = Math.max(level.getMinY(), (int) Math.floor(py) - radius);
        int maxY = Math.min(level.getMaxY(), (int) Math.floor(py) + radius);

        int chunkRadius = radius / 16 + 1;
        int centerChunkX = (int) Math.floor(px) >> 4;
        int centerChunkZ = (int) Math.floor(pz) >> 4;

        int found = 0;
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                int originX = chunkX << 4;
                int originZ = chunkZ << 4;

                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir()) continue;

                    int baseY = chunk.getMinY() + (i << 4);
                    if (baseY > maxY || baseY + 15 < minY) continue;
                    if (!section.maybeHas(state -> wanted.containsKey(state.getBlock()))) continue;

                    for (int localY = 0; localY < 16; localY++) {
                        int worldY = baseY + localY;
                        double dy = worldY + 0.5 - py;
                        if (dy * dy > squared) continue;

                        for (int localZ = 0; localZ < 16; localZ++) {
                            int worldZ = originZ + localZ;
                            double dz = worldZ + 0.5 - pz;
                            double flat = dy * dy + dz * dz;
                            if (flat > squared) continue;

                            for (int localX = 0; localX < 16; localX++) {
                                T value = wanted.get(section.getBlockState(localX, localY, localZ).getBlock());
                                if (value == null) continue;

                                int worldX = originX + localX;
                                double dx = worldX + 0.5 - px;
                                if (dx * dx + flat > squared) continue;

                                visitor.visit(worldX, worldY, worldZ, value);
                                if (++found >= maxHits) return found;
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
