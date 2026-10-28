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
    public GuiWalk guiWalk;
    public Removals removals;
    public Rain rain;
    public Ambience ambience;
    public ShaderSky shaderSky;
    public AirStuck airStuck;
    public AutoSwap autoSwap;
    public ElytraMotion elytraMotion;
    public ClickPearl clickPearl;
    public ServerHelper serverHelper;
    public WaterSpeed waterSpeed;
    public NoForward noForward;
    public Timer timer;
    public Music music;
    public BetterMinecraft betterMinecraft;
    public FreeLook freeLook;
    public ShaderHands shaderHands;
    public GlowHands glowHands;
    public Chams chams;
    public ViewModel viewModel;
    public SwingAnimation swingAnimation;
    public PopEffect popEffect;
    public AutoTotem autoTotem;
    public BlockHighlight blockHighlight;
    public JumpCircles jumpCircles;
    public FreeCam freeCam;
    public ItemScroller itemScroller;
    public FullBright fullBright;
    public AHHelper ahHelper;
    public AntiPush antiPush;
    public AutoTool autoTool;
    public Particles particles;
    public Criticals criticals;
    public KillAura killAura;
    public NoDelay noDelay;
    public TargetEsp targetEsp;
    public Prediction prediction;
    public Lines lines;
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
        this.criticals = new Criticals();
        this.killAura = new KillAura();
        this.noDelay = new NoDelay();
        this.ahHelper = new AHHelper();
        this.antiPush = new AntiPush();
        this.autoTool = new AutoTool();
        this.popEffect = new PopEffect();
        this.itemScroller = new ItemScroller();
        this.waterSpeed = new WaterSpeed();
        this.noForward = new NoForward();
        this.jumpCircles = new JumpCircles();
        this.freeCam = new FreeCam();
        this.swingAnimation = new SwingAnimation();
        this.viewModel = new ViewModel();
        this.blockHighlight = new BlockHighlight();
        this.autoTotem = new AutoTotem();
        this.shaderHands = new ShaderHands();
        this.glowHands = new GlowHands();
        this.chams = new Chams();
        this.betterMinecraft = new BetterMinecraft();
        this.guiWalk = new GuiWalk();
        this.removals = new Removals();
        this.freeLook = new FreeLook();
        this.serverHelper = new ServerHelper();
        this.timer = new Timer();
        this.airStuck = new AirStuck();
        this.autoSwap = new AutoSwap();
        this.elytraMotion = new ElytraMotion();
        this.clickPearl = new ClickPearl();
        this.ambience = new Ambience();
        this.shaderSky = new ShaderSky();
        this.nameTags = new NameTags();
        this.rain = new Rain();
        this.particles = new Particles();
        this.targetEsp = new TargetEsp();
        this.prediction = new Prediction();
        this.lines = new Lines();
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
                this.noDelay,
                this.fullBright,
                this.nameTags,
                this.shaderSky,
                this.shaderHands,
                this.glowHands,
                this.chams,
                this.rain,
                this.targetEsp,
                this.prediction,
                this.lines,
                this.xray,
                this.blockEsp,
                this.entityEsp,
                this.particles,
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
                this.criticals,
                this.autoTotem,
                this.autoTool,
                this.antiPush,
                this.ahHelper,
                this.itemScroller,
                this.waterSpeed,
                this.timer,
                this.freeCam,
                this.freeLook,
                this.guiWalk,
                this.removals,
                this.blockHighlight,
                this.jumpCircles,
                this.popEffect,
                this.betterMinecraft,
                this.ambience
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