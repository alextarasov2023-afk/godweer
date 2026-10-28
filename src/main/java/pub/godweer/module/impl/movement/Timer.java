package pub.godweer.module.impl.movement;

import pub.godweer.Client;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * Create by daun kvass
 */
public class Timer extends Module {

    public final SliderSetting speed = slider("Speed", 2.0F, 0.1F, 10.0F,0.1f);

    public Timer() {
        super("Timer", "Ускоряет время (типо остановка времени в школе)", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        Client.Timer = speed.getValue();
    }

    @Override
    protected void onDisable() {
        Client.Timer = 1.0F;
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() == PlayerTickEvent.Phase.PRE) {
            Client.Timer = speed.getValue();
        }
    }
}