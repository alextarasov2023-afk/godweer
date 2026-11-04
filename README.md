# GodWeer

Клиент-мод для Minecraft **26.3** (Fabric, Mojmap, Java 25). Сборка — `Phone-Sosiski.jar`.

## Сборка

```
JAVA_HOME=<jdk-25> ./gradlew build
```

Результат: `build/libs/Phone-Sosiski.jar`, кладётся в папку `mods`.

Версии зависят от `gradle.properties`: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Geckolib 5.5.7.

## Что внутри

- `pub/godweer/Client.java` — точка входа, инициализация менеджеров.
- `pub/godweer/module/` — модули (combat, movement, player, render, misc) и настройки.
- `pub/godweer/hud/` — элементы HUD; общий визуальный слой — `HudStyle.java`.
- `pub/godweer/event/` — событийная шина, `pub/godweer/mixin/` — миксины на клиент.
- `pub/godweer/util/render/` — `Render2D`, MSDF-шрифты, иконочный атлас (`IconUse`).
- `pub/godweer/ui/` — ClickGUI, модалки, оверлеи.

## Замечания

- Токены Spotify/Yandex для Music-модуля не в репозитории: они хранятся в `~/femboy/music.json`.
- В `src/main/resources/assets/godweer/images/ui/logo.png` лежит превью стокового логотипа с Dreamstime (лицензия не куплена) — перед тем как открывать репозиторий публично, его надо заменить.
