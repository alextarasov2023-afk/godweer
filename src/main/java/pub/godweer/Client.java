package pub.godweer;

import net.fabricmc.api.ModInitializer;
import pub.godweer.enjoy.event.EventManager;
import pub.godweer.kitty.builder.RotationBuilderManager;
import pub.godweer.kitty.sosiski.account.AccountManager;
import pub.godweer.kitty.sosiski.command.CommandManager;
import pub.godweer.kitty.sosiski.config.ConfigManager;
import pub.godweer.hud.HudManager;
import pub.godweer.hud.Notifications;
import pub.godweer.kitty.display.mainmenu.PanelKeyBoardHandler;
import pub.godweer.kitty.display.mainmenu.PanelLapRenderHandler;
import pub.godweer.kitty.sosiski.friend.FriendManager;
import pub.godweer.kitty.module.Modules;
import pub.godweer.auxiliary.client.persiki.Tps;
import pub.godweer.auxiliary.client.music.MusicService;

public class Client implements ModInitializer {
    public static Client INSTANCE;
    public final EventManager eventManager = new EventManager();
    public CommandManager commandManager;
    public ConfigManager configManager;
    public FriendManager friendManager;
    public final Modules moduleManager = new Modules();
    public static float Timer = 1.0f;


    @Override
    public void onInitialize() {
        INSTANCE = this;
        this.moduleManager.init();
        FriendManager.getInstance().load();
        AccountManager.getInstance().load();
        this.commandManager = new CommandManager();
        this.configManager = new ConfigManager();
        EventManager.register(RotationBuilderManager.INSTANCE);
        this.configManager.loadConfig("default", false);
        this.eventManager.register(HudManager.getInstance());
        this.eventManager.register(Notifications.getInstance());
        this.eventManager.register(HudManager.getInstance().getMusicHud());
        this.moduleManager.music = new pub.godweer.kitty.module.impl.misc.Music();
        // Register the Music module so its hotkeys are active by default.
        this.moduleManager.register(this.moduleManager.music);
        this.moduleManager.music.setEnabled(true);
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