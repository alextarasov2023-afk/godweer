package pub.godweer;

import net.fabricmc.api.ModInitializer;
import pub.godweer.event.EventManager;
import pub.godweer.rotation.builder.RotationBuilderManager;
import pub.godweer.account.AccountManager;
import pub.godweer.command.CommandManager;
import pub.godweer.config.ConfigManager;
import pub.godweer.hud.HudManager;
import pub.godweer.hud.Notifications;
import pub.godweer.ui.clickgui.PanelKeyBoardHandler;
import pub.godweer.ui.clickgui.PanelLapRenderHandler;
import pub.godweer.friend.FriendManager;
import pub.godweer.module.Modules;
import pub.godweer.util.game.Tps;
import pub.godweer.util.music.MusicService;

public class Client implements ModInitializer {
    public static Client INSTANCE;
    public final EventManager eventManager = new EventManager();
    public CommandManager commandManager;
    public ConfigManager configManager;
    public FriendManager friendManager;
    public final Modules moduleManager = new Modules();


    @Override
    public void onInitialize() {
        INSTANCE = this;
        this.moduleManager.init();
        FriendManager.getInstance().load();
        AccountManager.getInstance().load();
        this.commandManager = new CommandManager();
        this.configManager = new ConfigManager();
        EventManager.register(RotationBuilderManager.INSTANCE);
        // Музыку регистрируем до загрузки конфига: созданная после load модуль
        // никогда не получал ни своё состояние, ни бинд из сохранённого конфига.
        this.moduleManager.music = new pub.godweer.module.impl.misc.Music();
        this.moduleManager.register(this.moduleManager.music);
        this.moduleManager.music.setEnabled(true);
        this.eventManager.register(HudManager.getInstance());
        this.eventManager.register(Notifications.getInstance());
        this.eventManager.register(HudManager.getInstance().getMusicHud());
        String startupConfig = this.configManager.startupConfig();
        if (!startupConfig.isEmpty()) {
            this.configManager.loadConfig(startupConfig, false);
        }
        this.eventManager.register(Tps.INSTANCE);
        this.eventManager.register(new PanelKeyBoardHandler());
        this.eventManager.register(new PanelLapRenderHandler());
        MusicService.getInstance().start();

    }

    public static Client getInstance() {
        return INSTANCE;
    }

    public EventManager getEventManager() {
        return eventManager;
    }
}