package pub.godweer.kitty.module.impl.movement;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;

/**
 * Полностью невидимый инвентарь игрока.
 * Используется ElytraHelper для «легит»-действий из инвентаря:
 * клиент считает инвентарь открытым (обычные клики по слотам),
 * но на экране ничего не рисуется.
 */
public final class GhostInventoryScreen extends InventoryScreen {

    public GhostInventoryScreen(Player player) {
        super(player);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // ничего не рисуем — инвентарь полностью невидим
    }

    @Override
    protected void init() {
        super.init();
        // убираем все виджеты (кнопки в т.ч. из миксина) — они всё равно не видны,
        // но чтобы случайный клик не попал в невидимую кнопку
        this.clearWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}