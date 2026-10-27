package pub.godweer.enjoy.mixin.client;

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
import pub.godweer.auxiliary.client.persiki.AHUtils;
import pub.godweer.kitty.module.impl.misc.AHHelper;
import pub.godweer.kitty.module.impl.misc.ItemScroller;

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

    @Unique private boolean godweer$isAuction = false;
    @Unique private Slot godweer$cheapSlot = null;
    @Unique private Slot godweer$goodSlot = null;
    @Unique private long godweer$lastAhScan = 0L;

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

        if (AHHelper.INSTANCE != null && AHHelper.INSTANCE.isEnabled()) {
            this.godweer$isAuction = AHUtils.isAuction(screen.getMenu(), this.title);
            long now = System.currentTimeMillis();
            if (this.godweer$isAuction && now - this.godweer$lastAhScan > 100L) {
                this.godweer$lastAhScan = now;
                godweer$scanAuctionSlots(screen);
            }
        } else {
            this.godweer$cheapSlot = null;
            this.godweer$goodSlot = null;
        }
    }

    @Unique
    private void godweer$scanAuctionSlots(AbstractContainerScreen<?> screen) {
        List<Slot> validSlots = new ArrayList<>();
        int maxSlots = Math.min(45, screen.getMenu().slots.size());

        for (int i = 0; i < maxSlots; i++) {
            Slot slot = screen.getMenu().slots.get(i);
            ItemStack stack = slot.getItem();
            if (AHHelper.INSTANCE.shouldIgnore(stack)) continue;

            long price = AHUtils.getPrice(stack);
            if (price > 0 && price != Long.MAX_VALUE) {
                validSlots.add(slot);
            }
        }

        Slot green = null;
        Slot yellow = null;

        if (!validSlots.isEmpty()) {
            long lowestTotal = Long.MAX_VALUE;
            int bestGreenScore = -1;

            for (Slot slot : validSlots) {
                ItemStack stack = slot.getItem();
                long price = AHUtils.getPrice(stack);
                int score = AHHelper.INSTANCE.getEnchantPriorityScore(stack);

                if (score > bestGreenScore || (score == bestGreenScore && price < lowestTotal)) {
                    bestGreenScore = score;
                    lowestTotal = price;
                    green = slot;
                }
            }

            if (validSlots.size() > 1) {
                long lowestUnit = Long.MAX_VALUE;
                int bestYellowScore = -1;

                for (Slot slot : validSlots) {
                    if (slot == green) continue;
                    ItemStack stack = slot.getItem();
                    long price = AHUtils.getPrice(stack);
                    int count = Math.max(1, stack.getCount());
                    long unitPrice = price / count;
                    int score = AHHelper.INSTANCE.getEnchantPriorityScore(stack);

                    if (score > bestYellowScore || (score == bestYellowScore && unitPrice < lowestUnit)) {
                        bestYellowScore = score;
                        lowestUnit = unitPrice;
                        yellow = slot;
                    }
                }
            }
        }

        this.godweer$cheapSlot = green;
        this.godweer$goodSlot = yellow;
    }

    @Inject(method = "extractSlots", at = @At("TAIL"))
    private void godweer$drawAhHighlights(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (AHHelper.INSTANCE == null || !AHHelper.INSTANCE.isEnabled() || !this.godweer$isAuction) return;

        if (this.godweer$cheapSlot != null) {
            int sx = this.godweer$cheapSlot.x;
            int sy = this.godweer$cheapSlot.y;
            graphics.fill(sx, sy, sx + 16, sy + 16, AHHelper.INSTANCE.cheapSlotColor.getValue());
        }

        if (this.godweer$goodSlot != null) {
            int sx = this.godweer$goodSlot.x;
            int sy = this.godweer$goodSlot.y;
            graphics.fill(sx, sy, sx + 16, sy + 16, AHHelper.INSTANCE.goodSlotColor.getValue());
        }
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void godweer$addUnitPriceToTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        if (AHHelper.INSTANCE == null || !AHHelper.INSTANCE.isEnabled() || !AHHelper.INSTANCE.showPricePerUnit.get() || !this.godweer$isAuction) return;
        if (stack.isEmpty() || stack.getCount() <= 1) return;

        long totalPrice = AHUtils.getPrice(stack);
        if (totalPrice == Long.MAX_VALUE || totalPrice <= 0) return;

        long unitPrice = totalPrice / stack.getCount();
        List<Component> original = new ArrayList<>(cir.getReturnValue());

        Component unitText = Component.literal("§7Цена за шт: §a$" + formatPrice(unitPrice));
        original.add(unitText);
        cir.setReturnValue(original);
    }

    @Unique
    private String formatPrice(long price) {
        if (price >= 1_000_000) return String.format("%.1fM", price / 1_000_000.0);
        if (price >= 1_000) return String.format("%.1fK", price / 1_000.0);
        return String.valueOf(price);
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
        this.godweer$cheapSlot = null;
        this.godweer$goodSlot = null;
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