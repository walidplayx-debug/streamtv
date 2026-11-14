package com.streamtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Interface language code: "en", "fr" or "ar". */
val LocalAppLanguage = staticCompositionLocalOf { "en" }

val AppLanguages = listOf("en" to "English", "fr" to "Français", "ar" to "العربية")

/**
 * Translates an interface text. English is the key; anything without a translation
 * (channel names, categories, addresses) is returned unchanged.
 */
@Composable
@ReadOnlyComposable
fun t(text: String): String = translate(LocalAppLanguage.current, text)

fun translate(language: String, text: String): String = when (language) {
    "fr" -> French[text]
    "ar" -> Arabic[text]
    else -> null
} ?: text

private val French = mapOf(
    // tabs and headings
    "Live TV" to "TV en direct", "Movies" to "Films", "Series" to "Séries", "Favorites" to "Favoris",
    "Search" to "Recherche", "Settings" to "Réglages",
    "Last watched" to "Vus récemment", "All channels" to "Toutes les chaînes", "Search results" to "Résultats",
    "BY LANGUAGE" to "PAR LANGUE", "BY COUNTRY" to "PAR PAYS", "BY CATEGORY" to "PAR CATÉGORIE",
    "channels" to "chaînes", "channel" to "chaîne", "saved channels" to "chaînes enregistrées",
    "titles" to "titres", "results" to "résultats", "live" to "en direct", "movies" to "films", "series" to "séries",
    "Other" to "Autres",
    // empty states
    "No channels yet" to "Aucune chaîne", "Add a playlist or choose another group." to "Ajoutez une playlist ou choisissez un autre groupe.",
    "No movies yet" to "Aucun film", "No series yet" to "Aucune série",
    "Your playlist has no movies. Add one that includes them in Settings." to "Votre playlist ne contient pas de films. Ajoutez-en une dans Réglages.",
    "Your playlist has no series. Add one that includes them in Settings." to "Votre playlist ne contient pas de séries. Ajoutez-en une dans Réglages.",
    // search
    "Find a channel or category" to "Trouver une chaîne ou une catégorie", "Search channels" to "Rechercher des chaînes",
    "Start typing to search" to "Tapez pour rechercher",
    // welcome
    "Welcome to STV" to "Bienvenue sur STV",
    "Add your M3U playlist to start watching live TV, movies and series.\nYou can change it any time in Settings." to
        "Ajoutez votre playlist M3U pour regarder la TV en direct, des films et des séries.\nVous pouvez la changer à tout moment dans Réglages.",
    "Add M3U playlist" to "Ajouter une playlist M3U", "Xtream login" to "Connexion Xtream", "Try an example" to "Essayer un exemple",
    "Loading…" to "Chargement…",
    // settings
    "Playback" to "Lecture", "Playlist" to "Playlist", "Language" to "Langue", "Parental control" to "Contrôle parental",
    "TV guide" to "Guide TV", "Appearance" to "Apparence", "About" to "À propos",
    "NOW PLAYING FROM" to "LECTURE DEPUIS", "Add a playlist" to "Ajouter une playlist", "M3U link" to "Lien M3U",
    "Import file" to "Importer un fichier", "Reload" to "Recharger", "Your playlists" to "Vos playlists",
    "Active" to "Active", "Remove" to "Supprimer", "None yet" to "Aucune", "Imported file" to "Fichier importé",
    "XMLTV guide URL" to "Adresse du guide XMLTV", "Optional programme guide · not set" to "Guide des programmes facultatif · non défini",
    "Set guide address" to "Définir l'adresse du guide", "Reload TV guide" to "Recharger le guide TV",
    "programmes loaded" to "programmes chargés",
    "Playback engine" to "Moteur de lecture", "Automatic · HLS / DASH" to "Automatique · HLS / DASH",
    "Interface language" to "Langue de l'interface", "Parental lock" to "Verrou parental", "Not configured" to "Non configuré",
    "Theme" to "Thème", "Dark · tvOS inspired" to "Sombre · inspiré de tvOS", "Version 1.0.0" to "Version 1.0.0",
    // text entry
    "Name (optional)" to "Nom (facultatif)", "Playlist address  https://…" to "Adresse de la playlist  https://…",
    "Save & load" to "Enregistrer et charger", "Cancel" to "Annuler", "Paste" to "Coller", "Clear" to "Effacer", "Example" to "Exemple",
    "TV guide address (XMLTV)" to "Adresse du guide TV (XMLTV)", "Xtream Codes login" to "Connexion Xtream Codes",
    "Server  http://example.com:8080" to "Serveur  http://example.com:8080", "Username" to "Nom d'utilisateur", "Password" to "Mot de passe",
    "The address must start with http:// or https://" to "L'adresse doit commencer par http:// ou https://",
    "Nothing to paste: the clipboard is empty" to "Rien à coller : le presse-papiers est vide",
    "Fill in the server, username and password" to "Renseignez le serveur, le nom d'utilisateur et le mot de passe",
    // player
    "Can't play this channel" to "Impossible de lire cette chaîne", "The stream is not responding right now." to "Le flux ne répond pas pour le moment.",
    "Retry" to "Réessayer", "Next channel" to "Chaîne suivante", "CH" to "CH"
)

private val Arabic = mapOf(
    // tabs and headings
    "Live TV" to "البث المباشر", "Movies" to "أفلام", "Series" to "مسلسلات", "Favorites" to "المفضلة",
    "Search" to "بحث", "Settings" to "الإعدادات",
    "Last watched" to "آخر مشاهدة", "All channels" to "كل القنوات", "Search results" to "نتائج البحث",
    "BY LANGUAGE" to "حسب اللغة", "BY COUNTRY" to "حسب البلد", "BY CATEGORY" to "حسب التصنيف",
    "channels" to "قناة", "channel" to "قناة", "saved channels" to "قناة محفوظة",
    "titles" to "عنوان", "results" to "نتيجة", "live" to "مباشر", "movies" to "أفلام", "series" to "مسلسلات",
    "Other" to "أخرى",
    // empty states
    "No channels yet" to "لا توجد قنوات", "Add a playlist or choose another group." to "أضف قائمة تشغيل أو اختر مجموعة أخرى.",
    "No movies yet" to "لا توجد أفلام", "No series yet" to "لا توجد مسلسلات",
    "Your playlist has no movies. Add one that includes them in Settings." to "قائمتك لا تحتوي على أفلام. أضف قائمة تحتويها من الإعدادات.",
    "Your playlist has no series. Add one that includes them in Settings." to "قائمتك لا تحتوي على مسلسلات. أضف قائمة تحتويها من الإعدادات.",
    // search
    "Find a channel or category" to "ابحث عن قناة أو تصنيف", "Search channels" to "ابحث في القنوات",
    "Start typing to search" to "اكتب للبحث",
    // welcome
    "Welcome to STV" to "أهلاً بك في STV",
    "Add your M3U playlist to start watching live TV, movies and series.\nYou can change it any time in Settings." to
        "أضف قائمة M3U لتبدأ مشاهدة البث المباشر والأفلام والمسلسلات.\nيمكنك تغييرها في أي وقت من الإعدادات.",
    "Add M3U playlist" to "إضافة قائمة M3U", "Xtream login" to "دخول Xtream", "Try an example" to "جرّب مثالاً",
    "Loading…" to "جارٍ التحميل…",
    // settings
    "Playback" to "التشغيل", "Playlist" to "قائمة التشغيل", "Language" to "اللغة", "Parental control" to "الرقابة الأبوية",
    "TV guide" to "دليل البرامج", "Appearance" to "المظهر", "About" to "حول",
    "NOW PLAYING FROM" to "التشغيل من", "Add a playlist" to "إضافة قائمة", "M3U link" to "رابط M3U",
    "Import file" to "استيراد ملف", "Reload" to "إعادة تحميل", "Your playlists" to "قوائمك",
    "Active" to "مفعّلة", "Remove" to "حذف", "None yet" to "لا يوجد", "Imported file" to "ملف مستورد",
    "XMLTV guide URL" to "رابط دليل XMLTV", "Optional programme guide · not set" to "دليل برامج اختياري · غير محدد",
    "Set guide address" to "تحديد رابط الدليل", "Reload TV guide" to "إعادة تحميل الدليل",
    "programmes loaded" to "برنامج محمّل",
    "Playback engine" to "محرك التشغيل", "Automatic · HLS / DASH" to "تلقائي · HLS / DASH",
    "Interface language" to "لغة الواجهة", "Parental lock" to "قفل الرقابة", "Not configured" to "غير مفعّل",
    "Theme" to "السمة", "Dark · tvOS inspired" to "داكن · بأسلوب tvOS", "Version 1.0.0" to "الإصدار 1.0.0",
    // text entry
    "Name (optional)" to "الاسم (اختياري)", "Playlist address  https://…" to "رابط القائمة  https://…",
    "Save & load" to "حفظ وتحميل", "Cancel" to "إلغاء", "Paste" to "لصق", "Clear" to "مسح", "Example" to "مثال",
    "TV guide address (XMLTV)" to "رابط دليل البرامج (XMLTV)", "Xtream Codes login" to "دخول Xtream Codes",
    "Server  http://example.com:8080" to "السيرفر  http://example.com:8080", "Username" to "اسم المستخدم", "Password" to "كلمة السر",
    "The address must start with http:// or https://" to "الرابط يجب أن يبدأ بـ http:// أو https://",
    "Nothing to paste: the clipboard is empty" to "لا يوجد شيء للصقه: الحافظة فارغة",
    "Fill in the server, username and password" to "أدخل السيرفر واسم المستخدم وكلمة السر",
    // player
    "Can't play this channel" to "تعذّر تشغيل هذه القناة", "The stream is not responding right now." to "البث لا يستجيب حالياً.",
    "Retry" to "إعادة المحاولة", "Next channel" to "القناة التالية", "CH" to "قناة"
)
