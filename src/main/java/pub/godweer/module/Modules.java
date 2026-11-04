package pub.godweer.module;

import lombok.Getter;
import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.module.impl.combat.*;
import pub.godweer.module.impl.misc.*;
import pub.godweer.module.impl.movement.*;
import pub.godweer.module.impl.player.*;
import pub.godweer.module.impl.render.*;
import pub.godweer.module.impl.render.GlowHands;
import pub.godweer.module.impl.misc.Music;
import pub.godweer.module.settings.BindMode;
import pub.godweer.module.settings.impl.BindSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Create by daun kvass
 */
@Getter
public class Modules {
    private final List<Module> modules = new ArrayList<>();
    public ClickGui clickGui;
    public Sprint sprint;
    public ElytraHelper elytraHelper;
    public NameTags nameTags;
    public Interface anInterface;
    public Rain rain;
    public ShaderSky shaderSky;
    public AirStuck airStuck;
    public AutoSwap autoSwap;
    public ElytraMotion elytraMotion;
    public ClickPearl clickPearl;
    public ServerHelper serverHelper;
    public NoForward noForward;
    public Music music;
    public ShaderHands shaderHands;
    public GlowHands glowHands;
    public Chams chams;
    public ViewModel viewModel;
    public SwingAnimation swingAnimation;
    public FreeCam freeCam;
    public ItemScroller itemScroller;
    public FullBright fullBright;
    public KillAura killAura;
    public KillAura1 killAura1;
    public KillAura2 killAura2;
    public KillAura3 killAura3;
    public NoDelay noDelay;
    public TargetEsp targetEsp;
    public Xray xray;
    public BlockEsp blockEsp;
    public EntityEsp entityEsp;
    public SmoothCamera smoothCamera;
    public FireHands fireHands;
    public OutlineHands outlineHands;
    public HitEffect hitEffect;
    public Trails trails;

    public void init() {
        this.clickGui = new ClickGui();
        this.anInterface = new Interface();

        this.sprint = new Sprint();
        this.elytraHelper = new ElytraHelper();
        this.fullBright = new FullBright();
        this.killAura = new KillAura();
        this.killAura1 = new KillAura1();
        this.killAura2 = new KillAura2();
        this.killAura3 = new KillAura3();
        this.noDelay = new NoDelay();
        this.itemScroller = new ItemScroller();
        this.noForward = new NoForward();
        this.freeCam = new FreeCam();
        this.swingAnimation = new SwingAnimation();
        this.viewModel = new ViewModel();
        this.shaderHands = new ShaderHands();
        this.glowHands = new GlowHands();
        this.chams = new Chams();
        this.serverHelper = new ServerHelper();
        this.airStuck = new AirStuck();
        this.autoSwap = new AutoSwap();
        this.elytraMotion = new ElytraMotion();
        this.clickPearl = new ClickPearl();
        this.shaderSky = new ShaderSky();
        this.nameTags = new NameTags();
        this.rain = new Rain();
        this.targetEsp = new TargetEsp();
        this.xray = new Xray();
        this.blockEsp = new BlockEsp();
        this.entityEsp = new EntityEsp();
        this.smoothCamera = new SmoothCamera();
        this.fireHands = new FireHands();

        this.outlineHands = new OutlineHands();
        this.hitEffect = new HitEffect();
        this.trails = new Trails();

        register(
                this.clickGui,
                this.anInterface,
                this.killAura,
                this.killAura1,
                this.killAura2,
                this.killAura3,
                this.noDelay,
                this.fullBright,
                this.nameTags,
                this.shaderSky,
                this.shaderHands,
                this.glowHands,
                this.chams,
                this.rain,
                this.targetEsp,
                this.xray,
                this.blockEsp,
                this.entityEsp,
                this.trails,
                this.sprint,
                this.elytraHelper,
                this.smoothCamera,
                this.fireHands,
                this.outlineHands,
                this.hitEffect,
                this.noForward,
                this.airStuck,
                this.autoSwap,
                this.elytraMotion,
                this.clickPearl,
                this.swingAnimation,
                this.viewModel,
                this.serverHelper,
                this.itemScroller,
                this.freeCam
        );
    }

    public void register(Module... mods) {
        for (Module mod : mods) {
            modules.add(mod);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module module : modules) {
            if (module.getClass() == clazz) {
                return (T) module;
            }
        }
        return null;
    }

    public Module getModule(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public List<Module> getByCategory(Category category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * HOLD-модули запоминаем отдельно и снимаем только свои: иначе случайное
     * отжатие клавиши выключало бы модуль, включённый конфигом или из GUI.
     */
    private final List<Module> held = new ArrayList<>();

    public void onKey(int key, int action) {
        apply(key, action);
    }

    public void onMouse(int button, int action) {
        apply(BindSetting.mouse(button), action);
    }

    private void apply(int code, int action) {
        if (!BindSetting.isValidKey(code)) return;

        boolean press = action == InputConstants.PRESS;
        boolean release = action == InputConstants.RELEASE;
        // REPEAT (автоповтор клавиши) игнорируем: иначе HOLD-модуль мигал бы при
        // удержании, а TOGGLE переключался бы каждый повтор.
        if (!press && !release) return;

        for (Module module : modules) {
            BindSetting bind = module.getBind();
            if (bind == null || !bind.contains(code)) continue;

            if (bind.getModeForCode(code, BindMode.TOGGLE) == BindMode.HOLD) {
                if (press) {
                    if (!held.contains(module)) held.add(module);
                    if (!module.isEnabled()) module.setEnabled(true);
                } else if (held.remove(module) && module.isEnabled()) {
                    module.setEnabled(false);
                }
            } else if (press) {
                module.toggle();
            }
        }
    }

    /** Клавиши «залегли»: отпускаем всё, что держали, при смене экрана/конфига. */
    public void releaseHeld() {
        for (Module module : held) {
            if (module.isEnabled()) module.setEnabled(false);
        }
        held.clear();
    }
}