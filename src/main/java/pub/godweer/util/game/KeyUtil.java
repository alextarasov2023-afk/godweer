package pub.godweer.util.game;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLMouse;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Create by daun kvass
 */
public final class KeyUtil {
    public static final int UNBOUND = -1;
    public static final int MOUSE_FLAG = 1 << 30;

    public static int fromMouseButton(int button) {
        return MOUSE_FLAG | button;
    }

    public static boolean isMouseButton(int code) {
        return code != UNBOUND && (code & MOUSE_FLAG) != 0;
    }

    public static int toMouseButton(int code) {
        return code & ~MOUSE_FLAG;
    }

    /** SDL-совместимая нумерация: 1 = ЛКМ, 2 = СКМ, 3 = ПКМ, 4..8 = доп. кнопки. */
    public static boolean isMouseButtonDown(int button) {
        if (button <= 0) return false;
        FloatBuffer xBuf = BufferUtils.createFloatBuffer(1);
        FloatBuffer yBuf = BufferUtils.createFloatBuffer(1);
        int mask = SDLMouse.SDL_GetMouseState(xBuf, yBuf);
        return (mask & (1 << (button - 1))) != 0;
    }

    public static boolean isKeyDown(int code) {
        if (code == UNBOUND || code <= 0) return false;
        if (isMouseButton(code)) {
            return isMouseButtonDown(toMouseButton(code));
        }
        return InputConstants.isKeyDown(code);
    }

    /** Верхний ряд цифр под Shift — другой символ, а не регистр того же. */
    private static final char[] SHIFT_DIGITS = {'!', '@', '#', '$', '%', '^', '&', '*', '('};

    /**
     * Символ клавиши по её коду: US-раскладка, независимо от раскладки системы.
     * Гуи-оверлей экраном не является и может не дождаться настоящих символьных
     * событий — тогда ник хотя бы набирается.
     */
    public static int typedChar(int code) {
        boolean shift = isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);

        if (code >= InputConstants.KEY_A && code <= InputConstants.KEY_Z) {
            return (shift ? 'A' : 'a') + (code - InputConstants.KEY_A);
        }
        if (code >= InputConstants.KEY_NUMPAD0 && code <= InputConstants.KEY_NUMPAD9) {
            return '0' + (code - InputConstants.KEY_NUMPAD0);
        }
        if (code >= InputConstants.KEY_1 && code <= InputConstants.KEY_9) {
            return shift ? SHIFT_DIGITS[code - InputConstants.KEY_1] : '1' + (code - InputConstants.KEY_1);
        }
        if (code == InputConstants.KEY_0) {
            return shift ? ')' : '0';
        }

        String pair = switch (code) {
            default -> "";
            case InputConstants.KEY_SPACE -> "  ";
            case InputConstants.KEY_APOSTROPHE -> "'\"";
            case InputConstants.KEY_SEMICOLON -> ";:";
            case InputConstants.KEY_GRAVE -> "`~";
            case InputConstants.KEY_COMMA -> ",<";
            case InputConstants.KEY_PERIOD -> ".>";
            case InputConstants.KEY_SLASH -> "/?";
            case InputConstants.KEY_BACKSLASH -> "\\|";
            case InputConstants.KEY_LBRACKET -> "[{";
            case InputConstants.KEY_RBRACKET -> "]}";
            case InputConstants.KEY_MINUS -> "-_";
            case InputConstants.KEY_EQUALS -> "=+";
            case InputConstants.KEY_MULTIPLY -> "**";
            case InputConstants.KEY_ADD -> "++";
            case InputConstants.KEY_NUMPADCOMMA -> ",,";
        };
        return pair.isEmpty() ? 0 : pair.charAt(shift ? 1 : 0);
    }

    public static String getKeyName(int code) {
        if (code == UNBOUND || code <= 0) return "NONE";

        if (isMouseButton(code)) {
            int btn = toMouseButton(code);
            return switch (btn) {
                case InputConstants.MOUSE_BUTTON_LEFT -> "M1";
                case InputConstants.MOUSE_BUTTON_RIGHT -> "M2";
                case InputConstants.MOUSE_BUTTON_MIDDLE -> "M3";
                case InputConstants.MOUSE_BUTTON_4 -> "M4";
                case InputConstants.MOUSE_BUTTON_5 -> "M5";
                case InputConstants.MOUSE_BUTTON_6 -> "M6";
                case InputConstants.MOUSE_BUTTON_7 -> "M7";
                case InputConstants.MOUSE_BUTTON_8 -> "M8";
                default -> "M" + btn;
            };
        }

        if (code >= InputConstants.KEY_F1 && code <= InputConstants.KEY_F24) {
            return "F" + (code - InputConstants.KEY_F1 + 1);
        }
        if (code >= InputConstants.KEY_NUMPAD0 && code <= InputConstants.KEY_NUMPAD9) {
            return "NUM " + (code - InputConstants.KEY_NUMPAD0);
        }

        return switch (code) {
            case InputConstants.KEY_RSHIFT -> "RSHIFT";
            case InputConstants.KEY_LSHIFT -> "LSHIFT";
            case InputConstants.KEY_RCONTROL -> "RCTRL";
            case InputConstants.KEY_LCONTROL -> "LCTRL";
            case InputConstants.KEY_RALT -> "RALT";
            case InputConstants.KEY_LALT -> "LALT";
            case InputConstants.KEY_CAPSLOCK -> "CAPS";
            case InputConstants.KEY_TAB -> "TAB";
            case InputConstants.KEY_SPACE -> "SPACE";
            case InputConstants.KEY_RETURN -> "ENTER";
            case InputConstants.KEY_BACKSPACE -> "BSPC";
            case InputConstants.KEY_DELETE -> "DEL";
            case InputConstants.KEY_ESCAPE -> "ESC";
            case InputConstants.KEY_INSERT -> "INS";
            case InputConstants.KEY_HOME -> "HOME";
            case InputConstants.KEY_END -> "END";
            case InputConstants.KEY_PAGEUP -> "PGUP";
            case InputConstants.KEY_PAGEDOWN -> "PGDN";
            case InputConstants.KEY_UP -> "UP";
            case InputConstants.KEY_DOWN -> "DOWN";
            case InputConstants.KEY_LEFT -> "LEFT";
            case InputConstants.KEY_RIGHT -> "RIGHT";
            default -> {
                // Латинские буквы и цифры в SDL-сканкодах лежат по порядку
                if (code >= InputConstants.KEY_A && code <= InputConstants.KEY_Z) {
                    yield String.valueOf((char) ('a' + (code - InputConstants.KEY_A))).toUpperCase();
                }
                if (code >= InputConstants.KEY_1 && code <= InputConstants.KEY_9) {
                    yield String.valueOf(code - InputConstants.KEY_1 + 1);
                }
                if (code == InputConstants.KEY_0) {
                    yield "0";
                }
                yield "KEY " + code;
            }
        };
    }
}
