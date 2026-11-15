# MargyTube

<p align="center"><img src="art/logo.png" alt="MargyTube" width="220"></p>

Отдельный клиент YouTube для Android: лента, поиск, видео, комментарии и плагины.
Написан на Java, интерфейс — настоящий Material You 3, плеер — официальный YouTube.

**[Скачать MargyTube.apk](https://github.com/rybchikdavid-byte/MargyTube/releases/latest)** · Android 8.0+ · 5,3 МБ

## Что умеет

- **Лента «В тренде»** по выбранной стране и **поиск** — через официальный YouTube Data API v3.
- **Экран видео**: официальный IFrame-плеер, описание, просмотры, лайки, «Поделиться», «Открыть в YouTube».
- **Комментарии**: чтение, отправка своего, лайк видео.
- **Оформление**: Material You из системных обоев на Android 12+, шесть своих акцентов,
  светлая/тёмная/системная тема, три стиля карточек, радиус 0–28 dp, масштаб шрифта 85–125 %.
- **Плагины** формата `.mgtp` — как идея плагинов MargyT, но без хуков в методы чужого приложения.
- **Демо-режим**: без ключа показывает настоящие публичные видео и честно помечает их «ДЕМО».

## Настройка

### Ключ YouTube Data API

1. Открой [console.cloud.google.com](https://console.cloud.google.com) и создай проект.
2. **APIs & Services → Library → YouTube Data API v3 → Enable**.
3. **Credentials → Create credentials → API key**, скопируй ключ.
4. По желанию ограничь ключ: *Android apps*, package `com.wexresprise.margytube`
   и SHA-1 подписи.
5. В приложении: шестерёнка → раздел **YouTube** → вставь ключ → **Проверить** → **Сохранить**.

Бесплатная квота — 10 000 единиц в день. Лента стоит 1, поиск с уточнением статистики — около 3.
Ключ хранится только на телефоне.

### OAuth-токен для комментариев и лайков

Чтение комментариев работает по ключу. Чтобы **отправлять** комментарии и ставить лайк,
нужен доступ к аккаунту: access token со scope `youtube.force-ssl`.

1. Открой [Google OAuth 2.0 Playground](https://developers.google.com/oauthplayground).
2. В списке API выбери **YouTube Data API v3** и
   `https://www.googleapis.com/auth/youtube.force-ssl`, нажми **Authorize**.
3. **Exchange authorization code for tokens** → скопируй **Access token**.
4. В приложении: шестерёнка → раздел **Комментарии** → вставь токен → **Проверить**.

Токен живёт около часа. Приложение не обновляет его само: когда истечёт, вставь новый.
Полноценный вход Google с refresh-токеном — следующий шаг, его в этой версии нет.

### Аккаунты под Family Link

Если YouTube-аккаунт ограничен родительским контролем, Google сам запрещает комментарии,
лайки и часть данных. Это ограничение на стороне аккаунта, и приложение его не обходит —
оно просто показывает сообщение YouTube. Взрослый аккаунт без ограничений работает штатно.

## Плагины

`.mgtp` — это обычный ZIP:

```
manifest.json
plugin.js        # или classes.dex
icon.png         # необязательно
```

`manifest.json`:

```json
{
  "id": "com.example.myplugin",
  "name": "Мой плагин",
  "version": "1.0",
  "author": "you",
  "description": "Что делает",
  "hooks": ["onFeed", "onVideo"],
  "entry": "com.example.MyPlugin"
}
```

Два хука:

- `onFeed(json)` — получает JSON-массив ленты, возвращает изменённый JSON (или `null`);
- `onVideo(id)` — получает id видео, возвращает короткую заметку для экрана видео (или `null`).

JS выполняется в песочнице Rhino: только безопасные стандартные объекты, без `java.*` и
`Packages`, с жёстким лимитом 2,5 секунды. Сеть из плагина недоступна.

Вместо JS можно положить `classes.dex` с классом, реализующим
`com.wexresprise.margytube.plugin.MargyTubePlugin`. У такого плагина права приложения,
поэтому ставь только свой код.

Пример: вкладка **Плагины → Создать пример** — появится рабочий плагин, который ставит
звёздочку в заголовки и заметку на экране видео.

## Сборка

**GitHub Actions** — `.github/workflows/build.yml` собирает release APK и кладёт его
в артефакт `MargyTube-apk`. Готовый файл также лежит в **Releases**.

**Локально** — Android Studio, JDK 17, Android SDK 35, Gradle 8.9:

```
gradle :app:assembleRelease
```

Подпись — `keystore/margytube.p12`, alias `margytube`, пароль `margytube123`
(хранилище и ключ). Ключ лежит в репозитории намеренно: так любой может собрать
и обновить приложение. SHA-256 сертификата:
`A4:ED:67:65:4F:50:63:F9:B4:82:63:28:ED:66:F8:32:21:57:8F:59:83:F7:25:42:78:51:5D:EF:56:89:39:BE`.

## Чего приложение не делает

- Не скачивает видео и не вытаскивает потоки: воспроизведение идёт в официальном плеере YouTube.
- Не обходит блокировки, возрастные и родительские ограничения.
- Не показывает рекламу, не собирает аналитику, не отправляет ничего на свои серверы.
- Не хранит ключи в зашифрованном виде: это обычные SharedPreferences внутри приложения.

## Структура

```
app/src/main/java/com/wexresprise/margytube/
  MainActivity.java     — лента, поиск, плагины
  PlayerActivity.java   — IFrame-плеер, описание, лайк, комментарии
  SettingsActivity.java — оформление, ключ, токен, регион
  YouTubeApi.java       — YouTube Data API v3
  Prefs.java            — настройки, тема, акценты, шрифт
  plugin/               — .mgtp: манифест, Rhino-песочница, DEX-загрузчик
app/src/main/res/       — Material 3 темы, экраны, иконки
art/                    — логотип и его исходники
keystore/               — ключ подписи
```

## Лицензия

MIT — см. [LICENSE](LICENSE).

MargyTube не связан с YouTube и Google. «YouTube» — товарный знак Google LLC.
