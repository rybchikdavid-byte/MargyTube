// MargyTube — плагин «Полная кастомизация».
//
// onApp() вызывается при запуске приложения и задаёт оформление через
// margytube.applyStyle({...}). accent — один из: dynamic, green, blue, purple,
// pink, orange, red. theme — system, light, dark. cards — elevated, filled,
// outlined. radius — 0..28 dp, font — 85..125 %.
//
// Плагин меняет акцент по времени суток: утром тёплый, днём зелёный,
// вечером синий, ночью фиолетовый. При следующем запуске он применит
// акцент снова, поэтому ручной выбор в настройках живёт до перезапуска.

var PALETTE = [
  { hour: 0, accent: 'purple' },
  { hour: 6, accent: 'orange' },
  { hour: 11, accent: 'green' },
  { hour: 17, accent: 'blue' },
  { hour: 22, accent: 'purple' }
];

function accentForHour(hour) {
  var accent = PALETTE[0].accent;
  for (var i = 0; i < PALETTE.length; i++) {
    if (hour >= PALETTE[i].hour) {
      accent = PALETTE[i].accent;
    }
  }
  return accent;
}

function onApp() {
  var hour = new Date().getHours();
  var accent = accentForHour(hour);
  var changed = margytube.applyStyle({
    accent: accent,
    theme: 'system',
    cards: 'filled',
    radius: 20,
    font: 100
  });
  margytube.log('оформление: ' + accent + ' в ' + hour + ':00, изменено: ' + changed);
  return 'ok';
}

// Лента: убираем повторную отметку и слишком длинные заголовки.
function onFeed(json) {
  var list = JSON.parse(json);
  for (var i = 0; i < list.length; i++) {
    var title = list[i].title || '';
    if (title.indexOf('\u25B6 ') !== 0) {
      title = '\u25B6 ' + title;
    }
    if (title.length > 96) {
      title = title.substring(0, 95) + '\u2026';
    }
    list[i].title = title;
  }
  return JSON.stringify(list);
}

// Экран видео: заметку не показываем, чтобы не закрывать описание.
function onVideo(id) {
  return null;
}
