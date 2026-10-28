package pub.godweer.rotation.builder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import pub.godweer.util.game.ChatUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.AttackEvent;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.event.list.Render2DEvent;

import java.awt.Desktop;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RotationBuilderManager {
    public static final RotationBuilderManager INSTANCE = new RotationBuilderManager();
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path rootDir = Minecraft.getInstance().gameDirectory.toPath().resolve("femboy").resolve("builder");
    private final List<BuilderData.AimSample> sessionData = new ArrayList<>();

    @Getter private boolean recording = false;
    @Getter private String currentSessionName = "default";
    private long startTime = 0L;

    private float lastYaw, lastPitch;
    private float lastVelYaw, lastVelPitch;
    private boolean attackedThisTick = false;
    private int loadedPreviousSamples = 0;

    @Getter @Setter
    private TrainedKinematicsProfile activeProfile = new TrainedKinematicsProfile();

    public static class TrainedKinematicsProfile {
        public double groundRelY = 0.65;
        public double critRelY = 0.78;
        public double relX = 0.5;
        public double relZ = 0.5;

        public float pitchBias = 0.0F;
        public float pitchDistWeight = 0.0F;
        public float pitchHeightWeight = 0.0F;

        public List<double[]> learnedClusters = new ArrayList<>();

        public float microSpeed = 16.0F;
        public float trackingSpeed = 35.0F;
        public float flickSpeed = 70.0F;
        public float snapSpeed = 100.0F;

        public float inertiaFactor = 0.22F;
        public float jitterIntensity = 0.3F;
    }

    public RotationBuilderManager() {
        try {
            Files.createDirectories(rootDir);
            createDefaultTrainScript();
            loadProfileIfExists("trained_model.json");
        } catch (Exception ignored) {}
    }

    public boolean isHoldingSword() {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS) || stack.getItem().toString().toLowerCase().contains("sword");
    }

    public void start(String name) {
        if (recording) {
            ChatUtil.error("Запись уже активна в сессию §a" + currentSessionName + "§r! Остановите: .builder stop");
            return;
        }

        this.currentSessionName = (name == null || name.trim().isEmpty()) ? "default" : name.trim().toLowerCase();
        sessionData.clear();
        loadedPreviousSamples = 0;

        File targetFile = getRawFile(currentSessionName);
        if (targetFile.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8)) {
                Type listType = new TypeToken<ArrayList<BuilderData.AimSample>>() {}.getType();
                List<BuilderData.AimSample> existing = GSON.fromJson(reader, listType);
                if (existing != null) {
                    sessionData.addAll(existing);
                    loadedPreviousSamples = existing.size();
                }
                ChatUtil.info("Сессия §a" + currentSessionName + "§r: загружено §e" + loadedPreviousSamples + "§r кадров. Продолжаем сбор данных...");
            } catch (Exception e) {
                ChatUtil.error("Ошибка чтения: " + e.getMessage());
            }
        } else {
            ChatUtil.info("Начата новая запись сессии: §a" + currentSessionName + "§r.");
        }

        recording = true;
        startTime = System.currentTimeMillis();
        if (mc.player != null) {
            lastYaw = mc.player.getYRot();
            lastPitch = mc.player.getXRot();
            lastVelYaw = 0;
            lastVelPitch = 0;
        }
    }

    public void stop() {
        if (!recording) {
            ChatUtil.error("Запись не запущена!");
            return;
        }
        recording = false;
        long duration = System.currentTimeMillis() - startTime;
        int newSamples = sessionData.size() - loadedPreviousSamples;

        if (sessionData.isEmpty()) {
            ChatUtil.error("Нет записанных данных с мечом.");
            return;
        }

        File outFile = getRawFile(currentSessionName);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            GSON.toJson(sessionData, writer);
            ChatUtil.info("Сессия сохранена в §a" + outFile.getName() + "§r (Кадров: §e" + sessionData.size() + "§r, новых: +" + newSamples + ")");
            ChatUtil.entry(".builder train", "Запустить нейро-обучение по эпохам");
        } catch (Exception e) {
            ChatUtil.error("Ошибка сохранения: " + e.getMessage());
        }
    }

    public void train() {
        if (recording) {
            ChatUtil.error("Сначала остановите текущую запись (.builder stop)!");
            return;
        }

        File scriptFile = rootDir.resolve("train.py").toFile();
        createDefaultTrainScript();

        ChatUtil.info("Инициализация эпох глубокого обучения Python...");

        new Thread(() -> {
            try {
                String pythonCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
                ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptFile.getAbsolutePath());
                pb.directory(rootDir.toFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String outputLine = line;
                        mc.execute(() -> {
                            if (outputLine.startsWith("[EPOCH]")) {
                                ChatUtil.entry("Обучение", outputLine.replace("[EPOCH]", "").trim());
                            } else if (outputLine.startsWith("[INFO]")) {
                                ChatUtil.info(outputLine.replace("[INFO]", "").trim());
                            } else if (outputLine.startsWith("[ERROR]")) {
                                ChatUtil.error(outputLine.replace("[ERROR]", "").trim());
                            }
                        });
                    }
                }

                int exitCode = process.waitFor();
                mc.execute(() -> {
                    if (exitCode == 0) {
                        loadProfileIfExists("trained_model.json");
                        ChatUtil.info("§aВсе эпохи успешно пройдены! Обученная модель активна в ротации Builder.");
                    } else {
                        ChatUtil.error("Python завершился с кодом: " + exitCode);
                    }
                });

            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                mc.execute(() -> ChatUtil.error("Ошибка вызова Python: " + errorMsg));
            }
        }, "Builder-NeuralTrainer").start();
    }

    public void load(String name) {
        String cleanName = name.endsWith(".json") ? name : name + ".json";
        if (loadProfileIfExists(cleanName)) {
            ChatUtil.info("Профиль §a" + cleanName + "§r активирован!");
        } else {
            ChatUtil.error("Профиль '" + cleanName + "' не найден.");
        }
    }

    public boolean loadProfileIfExists(String fileName) {
        File modelFile = rootDir.resolve(fileName).toFile();
        if (!modelFile.exists()) return false;

        try (Reader reader = new InputStreamReader(new FileInputStream(modelFile), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            TrainedKinematicsProfile prof = new TrainedKinematicsProfile();

            if (json.has("hitbox_points")) {
                JsonObject hp = json.getAsJsonObject("hitbox_points");
                prof.groundRelY = hp.get("ground_y").getAsDouble();
                prof.critRelY = hp.get("crit_y").getAsDouble();
                prof.relX = hp.get("rel_x").getAsDouble();
                prof.relZ = hp.get("rel_z").getAsDouble();
            }

            if (json.has("pitch_model")) {
                JsonObject pm = json.getAsJsonObject("pitch_model");
                prof.pitchBias = (float) pm.get("bias").getAsDouble();
                prof.pitchDistWeight = (float) pm.get("dist_weight").getAsDouble();
                prof.pitchHeightWeight = (float) pm.get("height_weight").getAsDouble();
            }

            if (json.has("clusters")) {
                JsonArray arr = json.getAsJsonArray("clusters");
                prof.learnedClusters.clear();
                for (JsonElement el : arr) {
                    JsonArray pt = el.getAsJsonArray();
                    prof.learnedClusters.add(new double[]{pt.get(0).getAsDouble(), pt.get(1).getAsDouble(), pt.get(2).getAsDouble()});
                }
            }

            if (json.has("velocity_curve")) {
                JsonObject vc = json.getAsJsonObject("velocity_curve");
                prof.microSpeed = (float) vc.get("micro_0_5").getAsDouble();
                prof.trackingSpeed = (float) vc.get("tracking_5_20").getAsDouble();
                prof.flickSpeed = (float) vc.get("flick_20_50").getAsDouble();
                prof.snapSpeed = (float) vc.get("snap_50_plus").getAsDouble();
            }

            if (json.has("hand_dynamics")) {
                JsonObject hd = json.getAsJsonObject("hand_dynamics");
                prof.inertiaFactor = (float) hd.get("inertia").getAsDouble();
                prof.jitterIntensity = (float) hd.get("jitter").getAsDouble();
            }

            this.activeProfile = prof;
            return true;
        } catch (Exception e) {
            ChatUtil.error("Ошибка парсинга профиля: " + e.getMessage());
            return false;
        }
    }

    public List<String> getAvailableProfiles() {
        File[] files = rootDir.toFile().listFiles((dir, name) -> name.endsWith(".json") && !name.startsWith("raw_"));
        if (files == null) return Collections.emptyList();
        return Arrays.stream(files).map(f -> f.getName().replace(".json", "")).toList();
    }

    public List<String> getAvailableRawSessions() {
        File[] files = rootDir.toFile().listFiles((dir, name) -> name.startsWith("raw_") && name.endsWith(".json"));
        if (files == null) return Collections.emptyList();
        return Arrays.stream(files).map(f -> f.getName().replace("raw_", "").replace(".json", "")).toList();
    }

    public File getRawFile(String name) {
        return rootDir.resolve("raw_" + name + ".json").toFile();
    }

    public void openFolder() {
        try {
            Desktop.getDesktop().open(rootDir.toFile());
        } catch (Exception e) {
            ChatUtil.error("Не удалось открыть папку: " + e.getMessage());
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (recording && isHoldingSword()) {
            attackedThisTick = true;
        }
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!recording || mc.player == null || mc.level == null) return;
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;

        if (!isHoldingSword()) {
            attackedThisTick = false;
            return;
        }

        LivingEntity target = findTargetInReach();
        if (target != null) {
            float currentYaw = mc.player.getYRot();
            float currentPitch = mc.player.getXRot();

            float velYaw = Mth.wrapDegrees(currentYaw - lastYaw);
            float velPitch = currentPitch - lastPitch;

            float accelYaw = velYaw - lastVelYaw;
            float accelPitch = velPitch - lastVelPitch;

            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getViewVector(1.0F);
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 toTarget = targetCenter.subtract(eye);

            float targetYaw = (float) Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0F;
            float targetPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, Math.hypot(toTarget.x, toTarget.z)));

            float errorYaw = Math.abs(Mth.wrapDegrees(targetYaw - currentYaw));
            float errorPitch = Math.abs(targetPitch - currentPitch);

            Vec3 boxRel = BuilderData.getHitboxRelativePoint(eye, look, target);
            boolean isCrit = mc.player.fallDistance > 0.0F && !mc.player.onGround() && !mc.player.isInWater();

            sessionData.add(new BuilderData.AimSample(
                    System.currentTimeMillis(),
                    eye.distanceTo(targetCenter),
                    errorYaw,
                    errorPitch,
                    Math.abs(velYaw),
                    Math.abs(velPitch),
                    Math.abs(accelYaw),
                    Math.abs(accelPitch),
                    boxRel.x,
                    boxRel.y,
                    boxRel.z,
                    attackedThisTick,
                    isCrit,
                    !mc.player.onGround(),
                    target.getDeltaMovement().lengthSqr() > 0.001
            ));

            lastYaw = currentYaw;
            lastPitch = currentPitch;
            lastVelYaw = velYaw;
            lastVelPitch = velPitch;
        }

        attackedThisTick = false;
    }

    private LivingEntity findTargetInReach() {
        LivingEntity closest = null;
        double minDistance = 6.0;

        for (Player entity : mc.level.players()) {
            if (entity == mc.player || !entity.isAlive()) continue;
            double dist = mc.player.distanceTo(entity);
            if (dist <= minDistance) {
                closest = entity;
                minDistance = dist;
            }
        }
        return closest;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!recording) return;

        long elapsed = System.currentTimeMillis() - startTime;
        long mins = (elapsed / 1000) / 60;
        long secs = (elapsed / 1000) % 60;
        long millis = (elapsed % 1000) / 100;

        String timeStr = String.format("%02d:%02d.%d", mins, secs, millis);
        String infoStr = "REC [" + currentSessionName + "] " + timeStr + " (" + sessionData.size() + ")";

        float width = 195.0F;
        float height = 24.0F;
        float x = (mc.getWindow().getGuiScaledWidth() - width) / 2.0F;
        float y = 14.0F;

        Render2D.drawShadow(x, y, width, height, 6.0F, 12.0F, 0x55000000);
        Fonts.drawString(Fonts.SF_MEDIUM, infoStr, x + 22.0F, y + 7.5F, 11.5F, 0xFFFFFFFF);
    }

    private void createDefaultTrainScript() {
        File file = rootDir.resolve("train.py").toFile();
        String deepScript = """
import os
import json
import glob
import math
import time

def train():
    raw_files = glob.glob("raw_*.json")
    if not raw_files:
        print("[ERROR] Файлы raw_*.json не найдены!")
        return

    print(f"[INFO] Сбор датасетов из {len(raw_files)} файлов...")
    samples = []
    for f in raw_files:
        try:
            with open(f, "r", encoding="utf-8") as fl:
                samples.extend(json.load(fl))
        except Exception as e:
            print(f"[ERROR] Не удалось открыть {f}: {e}")

    N = len(samples)
    if N < 40:
        print(f"[ERROR] Недостаточно кадров для обучения ({N}/40). Подеритесь подольше с мечом!")
        return

    print(f"[INFO] Старт оптимизации по {N} сэмплам. Обучение весов питча и кинематики...")

    # 1. Извлечение признаков для оптимизации Pitch
    # Модель: errorPitch = Bias + W_dist * dist + W_vel * vel
    y_true = []
    x_dist = []
    x_vel = []
    ground_y = []
    crit_y = []

    # Скорости для биннинга
    bin_micro, bin_track, bin_flick, bin_snap = [], [], [], []

    for s in samples:
        ep = s.get("errorPitch", 0.0)
        dist = s.get("dist", 3.0)
        vp = s.get("velPitch", 0.0)
        err = math.hypot(s.get("errorYaw", 0), ep)
        vel = math.hypot(s.get("velYaw", 0), vp)

        y_true.append(ep)
        x_dist.append(dist)
        x_vel.append(vp)

        if s.get("isCrit", False):
            crit_y.append(s.get("boxY", 0.78))
        else:
            ground_y.append(s.get("boxY", 0.65))

        if err <= 5.0:
            if vel > 0.01: bin_micro.append(vel)
        elif err <= 20.0:
            if vel > 0.01: bin_track.append(vel)
        elif err <= 50.0:
            if vel > 0.01: bin_flick.append(vel)
        else:
            if vel > 0.01: bin_snap.append(vel)

    # 2. Обучение градиентным спуском (30 Эпох)
    w_bias = 0.0
    w_dist = 0.0
    w_height = 0.0
    lr = 0.005
    epochs = 25

    for epoch in range(1, epochs + 1):
        loss = 0.0
        grad_bias = 0.0
        grad_dist = 0.0
        grad_height = 0.0

        for i in range(N):
            pred = w_bias + w_dist * (x_dist[i] - 3.0) + w_height * x_vel[i]
            diff = pred - y_true[i]
            loss += diff ** 2
            grad_bias += diff
            grad_dist += diff * (x_dist[i] - 3.0)
            grad_height += diff * x_vel[i]

        loss /= N
        w_bias -= lr * (grad_bias / N)
        w_dist -= lr * (grad_dist / N)
        w_height -= lr * (grad_height / N)

        # Выводим динамический прогресс в чат игры
        if epoch % 5 == 0 or epoch == 1 or epoch == epochs:
            print(f"[EPOCH] Эпоха {epoch}/{epochs} | Loss (MSE): {round(loss, 4)} | Bias: {round(w_bias, 3)}")
            time.sleep(0.35)

    def avg(arr, defval):
        return round(sum(arr) / len(arr), 4) if arr else defval

    # 3. Кластеризация лучших поинтов хитбокса
    hit_samples = [s for s in samples if s.get("isAttack", False)]
    if not hit_samples:
        hit_samples = samples[:min(50, N)]

    clusters = []
    step = max(1, len(hit_samples) // 4)
    for i in range(0, min(len(hit_samples), step * 4), step):
        hs = hit_samples[i]
        clusters.append([round(hs.get("boxX", 0.5), 3), round(hs.get("boxY", 0.65), 3), round(hs.get("boxZ", 0.5), 3)])

    model = {
        "hitbox_points": {
            "ground_y": avg(ground_y, 0.65),
            "crit_y": avg(crit_y, 0.78),
            "rel_x": 0.5,
            "rel_z": 0.5
        },
        "pitch_model": {
            "bias": round(w_bias, 4),
            "dist_weight": round(w_dist, 4),
            "height_weight": round(w_height, 4)
        },
        "clusters": clusters,
        "velocity_curve": {
            "micro_0_5": max(14.0, avg(bin_micro, 16.0) * 2.8),
            "tracking_5_20": max(26.0, avg(bin_track, 32.0) * 2.5),
            "flick_20_50": max(55.0, avg(bin_flick, 65.0) * 2.2),
            "snap_50_plus": max(80.0, avg(bin_snap, 95.0) * 2.0)
        },
        "hand_dynamics": {
            "inertia": 0.22,
            "jitter": 0.32
        }
    }

    out_file = "trained_model.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(model, f, indent=4, ensure_ascii=False)

    print(f"[INFO] Модель успешно обновлена! Сохранено поинтов: {len(clusters)}")
    print(f"[INFO] Pitch Bias: {model['pitch_model']['bias']} | Ground Y: {model['hitbox_points']['ground_y']}")

if __name__ == "__main__":
    train()
""";
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            fw.write(deepScript);
        } catch (Exception ignored) {}
    }
}