package pub.godweer.auxiliary.client.persiki;

import hh.ru.Info;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import pub.godweer.IMinecraft;
import pub.godweer.auxiliary.client.clients.Theme;

/**
 * Create by daun kvass
 */
@UtilityClass
public class ChatUtil {

    public void print(String message) {
        info(message);
    }

    public void info(String message) {
        MutableComponent text = getPrefix();
        text.append(Component.literal(message).withStyle(Style.EMPTY.withColor(0xE0E0E0)));
        sendMessage(text);
    }

    public void success(String message) {
        MutableComponent text = Component.literal(Info.NAME + " ")
                .withStyle(Style.EMPTY.withColor(Theme.getAccentColor() & 0xFFFFFF));
        text.append(Component.literal("» ").withStyle(Style.EMPTY.withColor(0x888888)));
        text.append(Component.literal(message).withStyle(Style.EMPTY.withColor(0x55FF55)));
        sendMessage(text);
    }

    public void error(String message) {
        MutableComponent text = Component.literal(Info.NAME + " ")
                .withStyle(Style.EMPTY.withColor(0xFF5555));
        text.append(Component.literal("» ").withStyle(Style.EMPTY.withColor(0x888888)));
        text.append(Component.literal(message).withStyle(Style.EMPTY.withColor(0xFFAAAA)));
        sendMessage(text);
    }

    public void entry(String name, String description) {
        MutableComponent text = Component.literal("• ").withStyle(Style.EMPTY.withColor(0x888888));

        text.append(Component.literal(name).withStyle(Style.EMPTY.withColor(Theme.getAccentColor() & 0xFFFFFF)));

        if (description != null && !description.isBlank()) {
            text.append(Component.literal(" - ").withStyle(Style.EMPTY.withColor(0x666666)));
            text.append(Component.literal(description).withStyle(Style.EMPTY.withColor(0xCCCCCC)));
        }
        sendMessage(text);
    }

    public void entry(String emoji, String title, String detail) {
        entry(title, detail);
    }

    private MutableComponent getPrefix() {
        MutableComponent prefix = Component.literal(Info.NAME + " ")
                .withStyle(Style.EMPTY.withColor(Theme.getAccentColor() & 0xFFFFFF));
        prefix.append(Component.literal("» ").withStyle(Style.EMPTY.withColor(0x888888)));
        return prefix;
    }

    private void sendMessage(Component component) {
        Minecraft mc = IMinecraft.mc;
        if (mc.gui != null) {
            mc.gui.hud.getChat().addClientSystemMessage(component);
        }
    }
}