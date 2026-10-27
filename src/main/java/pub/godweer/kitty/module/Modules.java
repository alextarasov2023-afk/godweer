package pub.godweer.kitty.module;

import lombok.Getter;
import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.kitty.module.impl.combat.*;
import pub.godweer.kitty.module.impl.misc.*;
import pub.godweer.kitty.module.impl.movement.*;
import pub.godweer.kitty.module.impl.player.*;
import pub.godweer.kitty.module.impl.render.*;
import pub.godweer.kitty.module.impl.render.GlowHands;
import pub.godweer.kitty.module.impl.misc.Music;

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
    public SmoothCamera smoothCamera;
    public FireHands fireHands;
    public OutlineHands outlineHands;
    public HitEffect hitEffect;
    public Trails trails;

    public void init() {
        // Only HUD + ClickGUI + Combat (KillAura) + Player (NoDelay, AirStuck,
        // ClickPearl) + Movement (ElytraMotion) + render modules are functional.
        // Everything else is instantiated for null-safety (mixins/commands
        // reference the fields) but never registered, so it can't be enabled, bound, saved
        // or shown in ClickGUI.
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
        this.elytraMotion = new ElytraMotion();
        this.clickPearl = new ClickPearl();
        this.ambience = new Ambience();
        this.shaderSky = new ShaderSky();
        this.nameTags = new NameTags();
        this.rain = new Rain();
        this.particles = new Particles();
        this.targetEsp = new TargetEsp();
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
                this.elytraMotion,
                this.clickPearl,
                this.serverHelper
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

    public void onKey(int key) {
        if (key == 0 || key == 0) return;
        for (Module module : modules) {
            if (module.getBind().matches(key)) {
                module.toggle();
            }
        }
    }

    public void onMouse(int button) {
        for (Module module : modules) {
            if (module.getBind().matchesMouse(button)) {
                module.toggle();
            }
        }
    }
}