package pub.godweer.enjoy.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.kitty.module.impl.render.BetterMinecraft;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Create by daun kvass
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Shadow protected abstract void refreshTrimmedMessages();
    @Shadow protected abstract void addMessage(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag);

    @Unique private static final Map<String, Integer> godweer$countMap = new ConcurrentHashMap<>();
    @Unique private static final Map<GuiMessage.Line, Long> godweer$lineTimestamps = new WeakHashMap<>();
    @Unique private static final Pattern STACK_PATTERN = Pattern.compile("^(.*?)(?:\\s*§7\\(x\\d+\\))+$", Pattern.DOTALL);

    @Unique private boolean godweer$isModifying = false;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (godweer$isModifying) return;

        BetterMinecraft mod = BetterMinecraft.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.modes.isEnabled("Chat") || contents == null) return;

        String rawText = stripStack(contents.getString());
        if (rawText.isBlank()) return;

        Integer currentCount = godweer$countMap.get(rawText);

        if (currentCount != null) {
            int newCount = currentCount + 1;
            godweer$countMap.put(rawText, newCount);

            if (this.allMessages != null) {
                this.allMessages.removeIf(msg -> {
                    if (msg == null || msg.content() == null) return false;
                    return stripStack(msg.content().getString()).equals(rawText);
                });
            }

            this.refreshTrimmedMessages();

            godweer$isModifying = true;
            MutableComponent stacked = contents.copy().append(Component.literal(" §7(x" + newCount + ")"));
            this.addMessage(stacked, signature, source, tag);
            godweer$isModifying = false;

            ci.cancel();
        } else {
            godweer$countMap.put(rawText, 1);
        }
    }

    @WrapOperation(method = "forEachLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V"))
    private void wrapLineConsumerAccept(@Coerce Object consumer, GuiMessage.Line line, int lineIndex, float alpha, Operation<Void> original) {
        BetterMinecraft mod = BetterMinecraft.INSTANCE;
        if (mod == null || !mod.isEnabled() || !mod.modes.isEnabled("Чат") ) {
            original.call(consumer, line, lineIndex, alpha);
            return;
        }

        long now = System.currentTimeMillis();
        long spawnTime = godweer$lineTimestamps.computeIfAbsent(line, k -> now);
        float duration = Math.max(10.0F, mod.animSpeed.getValue());
        float progress = Math.min(1.0F, (now - spawnTime) / duration);

        float ease = 1.0F - (float) Math.pow(1.0F - progress, 3.0F);
        original.call(consumer, line, lineIndex, alpha * ease);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void onClearMessages(boolean history, CallbackInfo ci) {
        godweer$countMap.clear();
        godweer$lineTimestamps.clear();
    }

    @Unique
    private static String stripStack(String s) {
        if (s == null) return "";
        Matcher m = STACK_PATTERN.matcher(s.trim());
        if (m.matches()) {
            return m.group(1).trim();
        }
        return s.trim();
    }
}