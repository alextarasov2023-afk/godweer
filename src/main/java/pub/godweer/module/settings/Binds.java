package pub.godweer.module.settings;

import pub.godweer.Client;
import pub.godweer.hud.Notifications;
import pub.godweer.util.game.KeyUtil;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.BindSetting;

/**
 * Один вход для назначения клавиши бинду. Конфликт найден и снят здесь же: два
 * модуля на одной клавише переключались бы парой щелчков «вкл/выкл» и выглядело
 * бы это как зависший бинд, поэтому новый владелец забирает код себе и
 * предупреждает, у кого он был.
 */
public final class Binds {

    private Binds() {
    }

    /** Кто прямо сейчас держит этот код (кроме самого бинда) — метка для сообщения. */
    public static String owner(int code, BindSetting ignore) {
        if (!BindSetting.isValidKey(code)) {
            return null;
        }

        for (Module module : modules()) {
            BindSetting bind = module.getBind();
            if (bind != ignore && bind != null && bind.isExclusive() && bind.contains(code)) {
                return module.getName();
            }
            for (Setting<?> setting : module.getSettings()) {
                if (setting instanceof BindSetting other && other != ignore
                        && other.isExclusive() && other.contains(code)) {
                    return module.getName() + " → " + other.getName();
                }
            }
        }
        return null;
    }

    /**
     * Навешивает клавишу, сняв её с прежнего владельца. Именно навешивает одну:
     * повторная запись из UI заменяет прежнюю клавишу, а не копит их — иначе
     * бинд «не ставится», пока в нём висят старые коды. Пустой код — молча false.
     */
    public static boolean assign(BindSetting target, int code) {
        if (target == null || !BindSetting.isValidKey(code)) {
            return false;
        }

        String previous = detach(code, target);
        if (previous != null) {
            Notifications.push("Бинд " + KeyUtil.getKeyName(code) + " забран у " + previous);
        }
        target.setSingle(code);
        return target.isBound();
    }

    /**
     * Убирает код у всех, кроме keep. Общие бинды (ServerHelper) не трогаем:
     * именно на них несколько предметов делят одну клавишу. Возвращает метку
     * того, у кого забрали.
     */
    private static String detach(int code, BindSetting keep) {
        String previous = null;

        for (Module module : modules()) {
            BindSetting bind = module.getBind();
            if (bind != keep && bind != null && bind.isExclusive()) {
                int index = bind.getValue().indexOf(code);
                if (index != -1) {
                    bind.removeAt(index);
                    previous = module.getName();
                }
            }
            for (Setting<?> setting : module.getSettings()) {
                if (setting instanceof BindSetting other && other != keep && other.isExclusive()) {
                    int index = other.getValue().indexOf(code);
                    if (index != -1) {
                        other.removeAt(index);
                        previous = module.getName() + " → " + other.getName();
                    }
                }
            }
        }
        return previous;
    }

    private static java.util.List<Module> modules() {
        if (Client.INSTANCE == null || Client.INSTANCE.moduleManager == null) {
            return java.util.List.of();
        }
        return Client.INSTANCE.moduleManager.getModules();
    }
}
