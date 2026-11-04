package pub.godweer.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;
import pub.godweer.util.style.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Серверные теги игроков (префиксы рангов) из таблиста.
 *
 * Префикс приходит не в scoreboard-команде, а в displayName записи
 * {@code ClientboundPlayerInfoUpdatePacket}: строка вида «[Гл.Админ] Nick».
 * Храним тег кусками (текст как есть + цвет на каждый символ), чтобы NameTags
 * рисовал ровно серверный текст с его окраской, без собственной раскраски.
 * scoreboard-команда остаётся запасным источником для серверов без тегов в табе.
 */
public final class ServerTags {

    private ServerTags() {
    }

    /** text — кусок серверного текста как есть, color — ARGB; 0 = цвета нет. */
    public record TagRun(String text, int color) {
    }

    /** name — очищенный текст (поиск ключевых слов), runs — серверный вид для рендера. */
    public record Tag(String name, int color, List<TagRun> runs) {
    }

    public static Tag tab(PlayerInfo info, String nick) {
        if (info == null) return null;
        try {
            return parse(info.getTabListDisplayName(), nick);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Tag team(PlayerTeam team) {
        if (team == null) return null;
        try {
            return parse(team.getPlayerPrefix(), null);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Тег из компонента: текст до ника (если ника нет — весь текст),
     * иначе текст после ника. Края обрезаются только по пробелам,
     * скобки и разделители остаются как на сервере.
     */
    public static Tag parse(Component component, String nick) {
        if (component == null) return null;

        List<Component> flat = component.toFlatList();
        StringBuilder text = new StringBuilder();
        List<Integer> colors = new ArrayList<>();

        for (Component part : flat) {
            String raw = part.getString();
            if (raw.isEmpty()) continue;
            TextColor style = part.getStyle().getColor();
            int color = style != null ? rgba(style.getValue()) : 0;

            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '§' && i + 1 < raw.length()) {
                    int codeAt = i + 1;
                    char code = Character.toLowerCase(raw.charAt(codeAt));
                    if (code == 'x') {
                        int hex = hex(raw, codeAt + 1);
                        if (hex >= 0) {
                            color = rgba(hex);
                            i = codeAt + 12;
                            continue;
                        }
                    }
                    ChatFormatting formatting = ChatFormatting.getByCode(code);
                    if (formatting != null) {
                        if (formatting == ChatFormatting.RESET) {
                            color = 0;
                        } else {
                            TextColor legacy = TextColor.fromLegacyFormat(formatting);
                            if (legacy != null) color = rgba(legacy.getValue());
                        }
                    }
                    i = codeAt;
                    continue;
                }
                text.append(c);
                colors.add(color);
            }
        }

        if (text.isEmpty()) return null;

        String full = text.toString();
        int from = 0;
        int to = full.length();

        int nickAt = findNick(full, nick);
        if (nickAt >= 0) {
            if (hasVisible(full.substring(0, nickAt))) {
                to = nickAt;
            } else {
                from = nickAt + nick.length();
            }
        }

        while (from < to && isEdge(full.charAt(from))) from++;
        while (to > from && isEdge(full.charAt(to - 1))) to--;
        if (from >= to) return null;
        if (to - from > 48) to = from + 48;

        String shown = full.substring(from, to);
        List<TagRun> runs = runs(shown, colors, from);
        if (runs.isEmpty()) return null;

        int color = 0;
        for (TagRun run : runs) {
            if (run.color() != 0) {
                color = run.color();
                break;
            }
        }
        return new Tag(clean(shown), color, runs);
    }

    /** §x§R§R§G§G§B§B начиная с позиции первого § кода; -1, если последовательность битая. */
    private static int hex(String raw, int from) {
        if (from + 12 > raw.length()) return -1;
        int value = 0;
        for (int k = 0; k < 6; k++) {
            int a = from + k * 2;
            if (raw.charAt(a) != '§') return -1;
            int digit = Character.digit(raw.charAt(a + 1), 16);
            if (digit < 0) return -1;
            value = value << 4 | digit;
        }
        return value;
    }

    private static int findNick(String text, String nick) {
        if (nick == null || nick.isEmpty()) return -1;
        String target = normalizeKey(nick);
        if (target.isEmpty()) return -1;
        return normalizeKey(text).indexOf(target);
    }

    private static String normalizeKey(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            out.append(Character.toLowerCase(normalizeSmallCap(text.charAt(i))));
        }
        return out.toString();
    }

    private static boolean hasVisible(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) return true;
        }
        return false;
    }

    private static boolean isEdge(char c) {
        return c == ' ' || c == '\t';
    }

    private static List<TagRun> runs(String shown, List<Integer> colors, int offset) {
        List<TagRun> runs = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int chunkColor = -1;
        for (int i = 0; i < shown.length(); i++) {
            int color = colors.get(offset + i);
            if (chunkColor != color && !chunk.isEmpty()) {
                runs.add(new TagRun(chunk.toString(), chunkColor));
                chunk = new StringBuilder();
            }
            chunkColor = color;
            chunk.append(shown.charAt(i));
        }
        if (!chunk.isEmpty()) runs.add(new TagRun(chunk.toString(), chunkColor));
        return runs;
    }

    public static String clean(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            char normalized = normalizeSmallCap(c);
            if (isRenderable(normalized)) {
                out.append(normalized);
            }
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static int rgba(int rgb) {
        return ColorUtil.rgba((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }

    private static char normalizeSmallCap(char c) {
        return switch (c) {
            case 'ᴀ' -> 'A';
            case 'ʙ' -> 'B';
            case 'ᴄ' -> 'C';
            case 'ᴅ' -> 'D';
            case 'ᴇ' -> 'E';
            case 'ғ' -> 'F';
            case 'ɢ' -> 'G';
            case 'ʜ' -> 'H';
            case 'ɪ' -> 'I';
            case 'ᴊ' -> 'J';
            case 'ᴋ' -> 'K';
            case 'ʟ' -> 'L';
            case 'ᴍ' -> 'M';
            case 'ɴ' -> 'N';
            case 'ᴏ' -> 'O';
            case 'ᴘ' -> 'P';
            case 'ǫ', 'ϙ' -> 'Q';
            case 'ʀ' -> 'R';
            case 'ꜱ' -> 'S';
            case 'ᴛ' -> 'T';
            case 'ᴜ' -> 'U';
            case 'ᴠ' -> 'V';
            case 'ᴡ' -> 'W';
            case 'ʏ' -> 'Y';
            case 'ᴢ' -> 'Z';
            default -> c;
        };
    }

    private static boolean isRenderable(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || (c >= 'а' && c <= 'я') || (c >= 'А' && c <= 'Я') || c == 'ё' || c == 'Ё'
                || c == ' ' || c == '[' || c == ']' || c == '(' || c == ')'
                || c == '{' || c == '}' || c == '-' || c == '+' || c == '_'
                || c == '|' || c == ':' || c == '.' || c == '!' || c == '?'
                || c == '*' || c == '#' || c == '<' || c == '>';
    }
}
