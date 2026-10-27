package pub.godweer.auxiliary.client.localization;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by daun kvass
 */
public final class Localization {
    public enum Language {
        ENG("ENG"),
        RU("RU");

        private final String name;

        Language(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static Language currentLanguage = Language.ENG;
    private static final Map<String, String> RU_DICT = new HashMap<>();

    static {
        RU_DICT.put("Combat", "Бой");
        RU_DICT.put("Movement", "Движение");
        RU_DICT.put("Render", "Визуалы");
        RU_DICT.put("Player", "Игрок");
        RU_DICT.put("Misc", "Разное");
        RU_DICT.put("Single Player", "Одиночная игра");
        RU_DICT.put("Multi Player", "Сетевая игра");
        RU_DICT.put("Alt Manager", "Менеджер аккаунтов");
        RU_DICT.put("Settings", "Настройки");
        RU_DICT.put("Quit", "Выход");
        RU_DICT.put("Wallpaper", "Обои");
        RU_DICT.put("Account Manager", "Менеджер Аккаунтов");
        RU_DICT.put("Enter nickname...", "Введите никнейм...");
        RU_DICT.put("Account list is empty", "Список аккаунтов пуст");
        RU_DICT.put("Are you sure you want to quit?", "Вы действительно хотите выйти из игры?");
        RU_DICT.put("Binds", "Бинды");
        RU_DICT.put("Accounts", "Аккаунты");
        RU_DICT.put("GUI Settings", "Настройки GUI");
        RU_DICT.put("Ctrl + F to Search", "Ctrl + F для поиска");
        RU_DICT.put("Double click to select & relogin", "Двойной клик для выбора и входа");
        RU_DICT.put("Hold bind key to inspect", "Зажмите клавишу бинда для просмотра");
        RU_DICT.put("Add", "Добавить");
        RU_DICT.put("Nickname...", "Никнейм...");
        RU_DICT.put("No accounts", "Нет аккаунтов");
        RU_DICT.put("Bound", "С биндом");
        RU_DICT.put("Unbound", "Без бинда");
        RU_DICT.put("Language", "Язык");
        RU_DICT.put("Background", "Задний фон");
        RU_DICT.put("Accent", "Акцент темы");
        RU_DICT.put("Blur", "Размытие");
        RU_DICT.put("None", "Нет");
        RU_DICT.put("Main", "Основное");
        RU_DICT.put("Checks", "Проверки");
        RU_DICT.put("Settings", "Настройки");

        RU_DICT.put("Shows arrows pointing towards nearby players", "Показывает стрелочки в направлении ближайших игроков");
        RU_DICT.put("Distance from Crosshair", "Дистанция от прицела");
        RU_DICT.put("Scale", "Масштаб");
        RU_DICT.put("Show Distance", "Показывать дистанцию");
        RU_DICT.put("Highlight Target", "Подсвечивать цель");
        RU_DICT.put("Highlight target", "Подсвечивать цель");
        RU_DICT.put("Ignore Naked", "Игнорировать голых");
        RU_DICT.put("Assists with aiming and visualizes projectile trajectories", "Помощь в прицеливании и отображение траектории снарядов");
        RU_DICT.put("Aimbot", "Аимбот");
        RU_DICT.put("Weapons", "Оружие");
        RU_DICT.put("Bow", "Лук");
        RU_DICT.put("Trident", "Трезубец");
        RU_DICT.put("Crossbow", "Арбалет");
        RU_DICT.put("FOV", "Угол обзора (FOV)");
        RU_DICT.put("Search Range", "Дистанция поиска");
        RU_DICT.put("Prediction", "Предикт");
        RU_DICT.put("Trajectory", "Траектория");
        RU_DICT.put("Line Thickness", "Толщина линии");
        RU_DICT.put("Landing Marker", "Маркер приземления");
        RU_DICT.put("Marker Size", "Размер маркера");
        RU_DICT.put("All", "Все");

        RU_DICT.put("Smart auction assistant", "Умный помощник для аукциона");
        RU_DICT.put("Cheap Price Color", "Цвет дешёвой цены");
        RU_DICT.put("Good Price Color", "Цвет хорошей цены");
        RU_DICT.put("Price per Unit", "Цена за штуку");
        RU_DICT.put("Ignore Damaged", "Игнорировать сломанные");
        RU_DICT.put("Ignore Thorns", "Игнорировать шипы");
        RU_DICT.put("Priority Enchants", "Приоритетные чары");
        RU_DICT.put("Protection", "Защита");
        RU_DICT.put("Mending", "Починка");
        RU_DICT.put("Unbreaking", "Прочность");
        RU_DICT.put("Depth Strider", "Подводная ходьба");
        RU_DICT.put("Feather Falling", "Невесомость");
        RU_DICT.put("Search Item in Hand", "Поиск предмета в руке");

        RU_DICT.put("Tracks enemy actions (Totems, Potions, Items)", "Отслеживает действия врагов (Тотемы, Зелья, Предметы)");
        RU_DICT.put("Notifications", "Уведомления");
        RU_DICT.put("Chat", "Чат");
        RU_DICT.put("Notify", "Notify");
        RU_DICT.put("Usage", "Использование");
        RU_DICT.put("Golden Apple", "Золотое яблоко");
        RU_DICT.put("Enchanted Apple", "Зачарованное яблоко");
        RU_DICT.put("Ender Pearl", "Эндер перл");
        RU_DICT.put("Drinking Potions", "Питьё зелий");
        RU_DICT.put("Totems", "Тотемы");
        RU_DICT.put("Totem Pop", "Снос тотема");
        RU_DICT.put("Check Enchanted", "Проверка зачарования");
        RU_DICT.put("Splash Potions", "Сплеш зелья");
        RU_DICT.put("Potion Hits", "Попадание зелий");
        RU_DICT.put("Tracking Radius", "Радиус отслеживания");
        RU_DICT.put("Holy Water", "Святая вода");
        RU_DICT.put("Potion of Wrath", "Зелье Гнева");
        RU_DICT.put("Potion of Paladin", "Зелье Паладина");
        RU_DICT.put("Potion of Assassin", "Зелье Ассасина");

        RU_DICT.put("Automatically attacks when hovering over target", "Автоматически атакует при наведении на цель");
        RU_DICT.put("Space Only", "Только с пробелом");
        RU_DICT.put("Checks Shield", "Проверка щита");
        RU_DICT.put("Attack shield", "Атаковать со щитом");
        RU_DICT.put("Pause while Eating", "Пауза во время еды");

        RU_DICT.put("Automatically places and explodes end crystals and anchors", "Автоматически ставит и взрывает кристаллы и якоря");
        RU_DICT.put("Target Range", "Дистанция поиска");
        RU_DICT.put("Action Range", "Дистанция взаимодействия");
        RU_DICT.put("Max Self Damage", "Макс. урон по себе");
        RU_DICT.put("Min Target Damage", "Мин. урон по цели");
        RU_DICT.put("Predict Ticks", "Тики предикта");
        RU_DICT.put("Break Delay", "Задержка ломания");
        RU_DICT.put("Place Delay", "Задержка установки");
        RU_DICT.put("Fast Explode", "Быстрый взрыв");
        RU_DICT.put("Anchor Aura", "Аура якорей");
        RU_DICT.put("Auto Obsidian", "Авто обсидиан");
        RU_DICT.put("Render ESP", "Отображение");
        RU_DICT.put("Render Mode", "Режим рендера");
        RU_DICT.put("Tile", "Плитка");
        RU_DICT.put("Cube", "Куб");

        RU_DICT.put("Automatically puts a totem in your offhand", "Автоматически берет тотем в левую руку");
        RU_DICT.put("Bypass", "Обход");
        RU_DICT.put("Health", "Здоровье");
        RU_DICT.put("Swap Back", "Свапать обратно");
        RU_DICT.put("Save Enchanted", "Сохранять зачарованные");
        RU_DICT.put("No Swap in Screen", "Не свапать в меню");
        RU_DICT.put("Check Using", "Проверка использования");
        RU_DICT.put("Check Elytra", "Проверка элитр");
        RU_DICT.put("Elytra Health", "Здоровье на элитрах");
        RU_DICT.put("Check Crystal", "Проверка кристаллов");
        RU_DICT.put("Crystal Distance", "Дистанция кристаллов");
        RU_DICT.put("Check Anchor", "Проверка якорей");
        RU_DICT.put("Anchor Distance", "Дистанция якорей");

        RU_DICT.put("Smoothly aims at the target", "Доводит прицел до цели");
        RU_DICT.put("Check Wall", "Проверка стен");
        RU_DICT.put("Speed", "Скорость");
        RU_DICT.put("Only Weapon", "Только с оружием");

        RU_DICT.put("Combat Aura", "Автоматическая атака целей");
        RU_DICT.put("Attack Range", "Дистанция атаки");
        RU_DICT.put("Aim Range", "Дистанция наводки");
        RU_DICT.put("Rotation", "Ротация");
        RU_DICT.put("Movement Correction", "Коррекция движения");
        RU_DICT.put("Disengage", "Отводка");
        RU_DICT.put("Sprint Reset", "Сброс спринта");
        RU_DICT.put("Targets", "Таргеты");
        RU_DICT.put("Players", "Игроки");
        RU_DICT.put("Mobs", "Мобы");
        RU_DICT.put("Animals", "Животные");
        RU_DICT.put("Naked", "Голые");
        RU_DICT.put("Friends", "Друзья");
        RU_DICT.put("Villagers", "Жители");
        RU_DICT.put("Elytra", "Элитра");
        RU_DICT.put("Elytra Predict", "Предикт на элитрах");
        RU_DICT.put("Predict Type", "Тип предикта");
        RU_DICT.put("Elytra Distance", "Дистанция на эликах");
        RU_DICT.put("Raytrace", "Проверка наводки");
        RU_DICT.put("Aim Through Walls", "Наводка сквозь стены");
        RU_DICT.put("Through Walls", "Бить сквозь стены");
        RU_DICT.put("Only Crits", "Только криты");
        RU_DICT.put("Smart Crits", "Умные криты");
        RU_DICT.put("Criticals & Hit Logic", "Криты и логика удара");
        RU_DICT.put("Criticals", "Криты");
        RU_DICT.put("Crit Mode", "Режим критов");
        RU_DICT.put("Smart Crit", "Умные криты");
        RU_DICT.put("Min Fall Distance", "Мин. высота падения");
        RU_DICT.put("Full Charge Swing", "Бить полным зарядом");
        RU_DICT.put("Sprint Reset Mode", "Режим сброса спринта");
        RU_DICT.put("Legit (input)", "Легит (вводом)");
        RU_DICT.put("Legit Sprint Reset", "Легит сброс спринта");
        RU_DICT.put("Focus", "Строгая");
        RU_DICT.put("Free", "Свободная");
        RU_DICT.put("Min Stop Ticks", "Мин. тиков без спринта");
        RU_DICT.put("Max Stop Ticks", "Макс. тиков без спринта");
        RU_DICT.put("Ground Cooldown %", "Заряд на земле");
        RU_DICT.put("Random Fall Cooldown (85-90%)", "Рандом заряда при падении (85-90%)");
        RU_DICT.put("Random Fall Distance", "Рандом атаки при падении");
        RU_DICT.put("Target ESP", "Таргет ESP");
        RU_DICT.put("Marker", "Маркер");
        RU_DICT.put("Ghosts", "Призраки");
        RU_DICT.put("Circle", "Круг");
        RU_DICT.put("Silent", "Свободная");
        RU_DICT.put("Current", "Строгая");
        RU_DICT.put("Smooth", "Плавная");
        RU_DICT.put("Instant", "Мгновенная");
        RU_DICT.put("Legit", "Легит");
        RU_DICT.put("Packet", "Пакетный");
        RU_DICT.put("Default", "Обычный");
        RU_DICT.put("Limit", "Лимит");

        RU_DICT.put("Custom Fog", "Кастомный туман");
        RU_DICT.put("Fog", "Туман");
        RU_DICT.put("Distance start", "Дистанция начала");
        RU_DICT.put("Distance end", "Дистанция конца");
        RU_DICT.put("Color Fog from Theme", "Цвет тумана из темы");
        RU_DICT.put("Color Fog", "Цвет тумана");
        RU_DICT.put("Volume Fog", "Объемный туман");
        RU_DICT.put("Color Volume from Theme", "Цвет объема из темы");
        RU_DICT.put("Density", "Плотность");
        RU_DICT.put("Thickness", "Толщина");
        RU_DICT.put("Height", "Высота");
        RU_DICT.put("Lightning", "Молнии");
        RU_DICT.put("Color Light from Theme", "Цвет молний из темы");
        RU_DICT.put("Color", "Цвет");
        RU_DICT.put("Frequency", "Частота");
        RU_DICT.put("Intensity", "Интенсивность");
        RU_DICT.put("Shader Sky", "Шейдерное небо");
        RU_DICT.put("Sky", "Небо");
        RU_DICT.put("Color Sky from Theme", "Цвет неба из темы");
        RU_DICT.put("Color1", "Цвет 1");
        RU_DICT.put("Color2", "Цвет 2");
        RU_DICT.put("Saturation", "Насыщенность");
        RU_DICT.put("Puddles", "Лужи");
        RU_DICT.put("Puddle Coverage", "Покрытие луж");
        RU_DICT.put("Wave Speed", "Скорость волн");
        RU_DICT.put("Wave Ripples", "Рябь волн");
        RU_DICT.put("Reflectivity", "Отражения");
        RU_DICT.put("Puddle Scale", "Размер луж");
        RU_DICT.put("Time Change", "Смена времени");
        RU_DICT.put("Time", "Время");
        RU_DICT.put("Custom Time", "Кастомное время");
        RU_DICT.put("Day", "День");
        RU_DICT.put("Dawn", "Рассвет");
        RU_DICT.put("Evening", "Вечер");
        RU_DICT.put("Night", "Ночь");
        RU_DICT.put("Custom", "Кастомное");
        RU_DICT.put("Starry Sky", "Звездное небо");
        RU_DICT.put("Nebula", "Туманность");
        RU_DICT.put("Plasma", "Плазма");
        RU_DICT.put("Caustic", "Каустика");
    }

    public static String get(String text) {
        if (text == null) return "";
        if (currentLanguage == Language.RU) {
            return RU_DICT.getOrDefault(text, text);
        }
        return text;
    }

    public static void setLanguage(String lang) {
        if ("RU".equalsIgnoreCase(lang)) {
            currentLanguage = Language.RU;
        } else {
            currentLanguage = Language.ENG;
        }
    }

    public static Language getLanguage() {
        return currentLanguage;
    }

    public static boolean isRussian() {
        return currentLanguage == Language.RU;
    }

    private Localization() {}
}