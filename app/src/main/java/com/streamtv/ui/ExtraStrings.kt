package com.streamtv.ui

/** Words added after the main translation tables (the subtitles button, logo search), per language; English is the key. */
internal val ExtraTexts: Map<String, Map<String, String>> = mapOf(
    "fr" to mapOf("Subtitles" to "Sous-titres", "Off" to "Désactivés", "Search logo" to "Chercher un logo", "Searching…" to "Recherche…",
        "No logos found" to "Aucun logo trouvé", "Original logo" to "Logo d'origine"),
    "ar" to mapOf("Subtitles" to "الترجمة", "Off" to "إيقاف", "Search logo" to "البحث عن شعار", "Searching…" to "جارٍ البحث…",
        "No logos found" to "لم يتم العثور على شعارات", "Original logo" to "الشعار الأصلي"),
    "es" to mapOf("Subtitles" to "Subtítulos", "Off" to "Desactivados", "Search logo" to "Buscar logo", "Searching…" to "Buscando…",
        "No logos found" to "No se encontraron logos", "Original logo" to "Logo original"),
    "ru" to mapOf("Subtitles" to "Субтитры", "Off" to "Выкл.", "Search logo" to "Найти логотип", "Searching…" to "Поиск…",
        "No logos found" to "Логотипы не найдены", "Original logo" to "Исходный логотип"),
    "zh" to mapOf("Subtitles" to "字幕", "Off" to "关闭", "Search logo" to "搜索台标", "Searching…" to "正在搜索…",
        "No logos found" to "未找到台标", "Original logo" to "原始台标"),
    "tr" to mapOf("Subtitles" to "Altyazılar", "Off" to "Kapalı", "Search logo" to "Logo ara", "Searching…" to "Aranıyor…",
        "No logos found" to "Logo bulunamadı", "Original logo" to "Orijinal logo"),
    "de" to mapOf("Subtitles" to "Untertitel", "Off" to "Aus", "Search logo" to "Logo suchen", "Searching…" to "Suche…",
        "No logos found" to "Keine Logos gefunden", "Original logo" to "Originallogo"),
    "pt" to mapOf("Subtitles" to "Legendas", "Off" to "Desligadas", "Search logo" to "Procurar logótipo", "Searching…" to "A procurar…",
        "No logos found" to "Nenhum logótipo encontrado", "Original logo" to "Logótipo original"),
    "nl" to mapOf("Subtitles" to "Ondertiteling", "Off" to "Uit", "Search logo" to "Logo zoeken", "Searching…" to "Zoeken…",
        "No logos found" to "Geen logo's gevonden", "Original logo" to "Originele logo")
)
