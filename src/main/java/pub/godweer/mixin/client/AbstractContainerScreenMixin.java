package pub.godweer.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.module.impl.misc.ItemScroller;

import java.util.ArrayList;
import java.util.List;

/**
 * Create by daun kvass
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {

    @Shadow @Nullable protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;

    @Shadow private @Nullable Slot getHoveredSlot(final double x, final double y) { return null; }

    @Unique private boolean godweer$isDroppingInv = false;
    @Unique private boolean godweer$isLootingChest = false;
    @Unique private boolean godweer$isStoringChest = false;
    @Unique private boolean godweer$isDroppingChest = false;

    @Unique private Button godweer$dropInvBtn;
    @Unique private Button godweer$lootChestBtn;
    @Unique private Button godweer$storeChestBtn;
    @Unique private Button godweer$dropChestBtn;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void godweer$initButtons(CallbackInfo ci) {
        if ((Object) this instanceof CreativeModeInventoryScreen) return;

        boolean isPlayerInv = (Object) this instanceof InventoryScreen;
        int btnY = this.topPos - 22;

        if (isPlayerInv) {
            int btnWidth = 100;
            int btnX = this.leftPos + (this.imageWidth - btnWidth) / 2;
            this.godweer$dropInvBtn = Button.builder(Component.literal("Выбросить все"), b -> {
                this.godweer$isDroppingInv = !this.godweer$isDroppingInv;
                this.godweer$updateButtonLabels();
            }).bounds(btnX, btnY, btnWidth, 18).build();
            this.addRenderableWidget(this.godweer$dropInvBtn);
        } else {
            int btnWidth = 56;
            int gap = 3;
            int totalWidth = btnWidth * 3 + gap * 2;
            int startX = this.leftPos + (this.imageWidth - totalWidth) / 2;

            this.godweer$lootChestBtn = Button.builder(Component.literal("Забрать"), b -> {
                this.godweer$isLootingChest = !this.godweer$isLootingChest;
                this.godweer$isStoringChest = false;
                this.godweer$isDroppingChest = false;
                this.godweer$updateButtonLabels();
            }).bounds(startX, btnY, btnWidth, 18).build();

            this.godweer$storeChestBtn = Button.builder(Component.literal("Сложить"), b -> {
                this.godweer$isStoringChest = !this.godweer$isStoringChest;
                this.godweer$isLootingChest = false;
                this.godweer$isDroppingChest = false;
                this.godweer$updateButtonLabels();
            }).bounds(startX + btnWidth + gap, btnY, btnWidth, 18).build();

            this.godweer$dropChestBtn = Button.builder(Component.literal("Выкинуть"), b -> {
                this.godweer$isDroppingChest = !this.godweer$isDroppingChest;
                this.godweer$isLootingChest = false;
                this.godweer$isStoringChest = false;
                this.godweer$updateButtonLabels();
            }).bounds(startX + (btnWidth + gap) * 2, btnY, btnWidth, 18).build();

            this.addRenderableWidget(this.godweer$lootChestBtn);
            this.addRenderableWidget(this.godweer$storeChestBtn);
            this.addRenderableWidget(this.godweer$dropChestBtn);
        }
    }

    @Inject(method = "containerTick", at = @At("HEAD"))
    private void godweer$onContainerTick(CallbackInfo ci) {
        if (this.minecraft.player == null || this.minecraft.gameMode == null) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        int itemsPerTick = 1;
        int processed = 0;

        if (this.godweer$isDroppingInv) {
            boolean hasMore = false;
            for (Slot slot : screen.getMenu().slots) {
                if (slot.hasItem() && !slot.getItem().isEmpty()) {
                    this.minecraft.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 1, ContainerInput.THROW, this.minecraft.player);
                    hasMore = true;
                    if (++processed >= itemsPerTick) break;
                }
            }
            if (!hasMore) { this.godweer$isDroppingInv = false; this.godweer$updateButtonLabels(); }
        } else if (this.godweer$isLootingChest) {
            boolean hasMore = false;
            for (Slot slot : screen.getMenu().slots) {
                if (godweer$isContainerSlot(slot) && slot.hasItem() && !slot.getItem().isEmpty()) {
                    this.minecraft.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
                    hasMore = true;
                    if (++processed >= itemsPerTick) break;
                }
            }
            if (!hasMore) { this.godweer$isLootingChest = false; this.godweer$updateButtonLabels(); }
        } else if (this.godweer$isStoringChest) {
            boolean hasMore = false;
            for (Slot slot : screen.getMenu().slots) {
                if (!godweer$isContainerSlot(slot) && slot.hasItem() && !slot.getItem().isEmpty()) {
                    this.minecraft.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
                    hasMore = true;
                    if (++processed >= itemsPerTick) break;
                }
            }
            if (!hasMore) { this.godweer$isStoringChest = false; this.godweer$updateButtonLabels(); }
        } else if (this.godweer$isDroppingChest) {
            boolean hasMore = false;
            for (Slot slot : screen.getMenu().slots) {
                if (godweer$isContainerSlot(slot) && slot.hasItem() && !slot.getItem().isEmpty()) {
                    this.minecraft.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 1, ContainerInput.THROW, this.minecraft.player);
                    hasMore = true;
                    if (++processed >= itemsPerTick) break;
                }
            }
            if (!hasMore) { this.godweer$isDroppingChest = false; this.godweer$updateButtonLabels(); }
        }
    }

    @Unique
    private boolean godweer$isContainerSlot(Slot slot) {
        if (this.minecraft.player == null) return false;
        return slot.container != this.minecraft.player.getInventory();
    }

    @Unique
    private void godweer$updateButtonLabels() {
        if (this.godweer$dropInvBtn != null) this.godweer$dropInvBtn.setMessage(Component.literal(this.godweer$isDroppingInv ? "§cСтоп..." : "Выбросить все"));
        if (this.godweer$lootChestBtn != null) this.godweer$lootChestBtn.setMessage(Component.literal(this.godweer$isLootingChest ? "§cСтоп..." : "Забрать"));
        if (this.godweer$storeChestBtn != null) this.godweer$storeChestBtn.setMessage(Component.literal(this.godweer$isStoringChest ? "§cСтоп..." : "Сложить"));
        if (this.godweer$dropChestBtn != null) this.godweer$dropChestBtn.setMessage(Component.literal(this.godweer$isDroppingChest ? "§cСтоп..." : "Выкинуть"));
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void godweer$onRemoved(CallbackInfo ci) {
        this.godweer$isDroppingInv = false;
        this.godweer$isLootingChest = false;
        this.godweer$isStoringChest = false;
        this.godweer$dropChestBtn = null;
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onItemScrollerMouseDragged(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (ItemScroller.INSTANCE != null && ItemScroller.INSTANCE.isEnabled()) {
            Slot slot = this.getHoveredSlot(event.x(), event.y());
            if (ItemScroller.INSTANCE.onMouseDragged((AbstractContainerScreen<?>) (Object) this, slot, event)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void onItemScrollerMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ItemScroller.INSTANCE != null && ItemScroller.INSTANCE.isEnabled()) {
            Slot slot = this.getHoveredSlot(event.x(), event.y());
            ItemScroller.INSTANCE.onMouseClicked(slot);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void onItemScrollerMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ItemScroller.INSTANCE != null) {
            ItemScroller.INSTANCE.onMouseReleased();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onItemScrollerKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ItemScroller.INSTANCE != null && ItemScroller.INSTANCE.isEnabled()) {
            if (ItemScroller.INSTANCE.onKeyPressed((AbstractContainerScreen<?>) (Object) this, this.hoveredSlot, event)) {
                cir.setReturnValue(true);
            }
        }
    }
}