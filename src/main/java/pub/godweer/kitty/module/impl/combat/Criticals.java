package pub.godweer.kitty.module.impl.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.AttackEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;

/**
 * Create by daun kvass
 */
public class Criticals extends Module {
    public static Criticals INSTANCE;

    public Criticals() {
        super("Criticals", "фффыы", Category.COMBAT);
        INSTANCE = this;
    }

    @EventTarget
    public void onAttack(final AttackEvent event) {
        if (mc.player == null || mc.level == null) return;



        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        if (isInCobweb()) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y + 0.00300, z, false, false));
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
        }
    }
      boolean isInCobweb() {
        if (mc.player == null || mc.level == null) return false;
        AABB box = mc.player.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) {
                return true;
            }
        }
        return false;
    }

}