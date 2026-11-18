package com.streamtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Interface language code, one of the codes in [AppLanguages]. */
val LocalAppLanguage = staticCompositionLocalOf { "en" }

val AppLanguages = listOf(
    "en" to "English", "fr" to "Français", "ar" to "العربية", "es" to "Español", "ru" to "Русский",
    "zh" to "中文", "tr" to "Türkçe", "de" to "Deutsch", "pt" to "Português", "nl" to "Nederlands"
)

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
    "es" -> Spanish[text]
    "ru" -> Russian[text]
    "zh" -> Chinese[text]
    "tr" -> Turkish[text]
    "de" -> German[text]
    "pt" -> Portuguese[text]
    "nl" -> Dutch[text]
    else -> null
} ?: text

private val French = mapOf(
    // tabs and headings
    "Live TV" to "En direct", "Movies" to "Films", "Series" to "Séries", "Favorites" to "Favoris",
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
    "Search channels" to "Rechercher des chaînes",
    "Start typing to search" to "Tapez pour rechercher",
    // welcome
    "Add your M3U playlist to start watching live TV, movies and series.\nYou can change it any time in Settings." to
        "Ajoutez votre playlist M3U pour regarder la TV en direct, des films et des séries.\nVous pouvez la changer à tout moment dans Réglages.",
    "Add M3U playlist" to "Ajouter une playlist M3U", "Xtream login" to "Connexion Xtream",
    "Loading…" to "Chargement…",
    // settings
    "Playback" to "Lecture", "Playlist" to "Playlist", "Language" to "Langue", "Parental control" to "Contrôle parental",
    "TV guide" to "Guide TV", "About" to "À propos",
    "NOW PLAYING FROM" to "LECTURE DEPUIS", "Add a playlist" to "Ajouter une playlist", "M3U link" to "Lien M3U",
    "Import file" to "Importer un fichier", "Reload" to "Recharger",
    "Go to number" to "Aller au numéro", "No channel with this number" to "Aucune chaîne à ce numéro", "So many channels? CH+ / CH− jump 16 at a time, or type the channel number" to "Trop de chaînes ? CH+ / CH− sautent de 16, ou tapez le numéro de la chaîne", "Want this country first? Hold OK and move it up" to "Ce pays en premier ? Maintenez OK et remontez-le", "Want this language first? Hold OK and move it up" to "Cette langue en premier ? Maintenez OK et remontez-la", "Prefer this section higher? Hold OK and arrange the list your way" to "Cette section plus haut ? Maintenez OK et rangez la liste à votre goût",
    "Hold OK for channel options" to "Maintenez OK pour les options de la chaîne", "Hold OK to move or hide this section" to "Maintenez OK pour déplacer ou masquer cette section", "↑↓ change channel · ←→ channel list · Hold OK for options" to "↑↓ changer de chaîne · ←→ liste des chaînes · OK maintenu pour les options",
    "Move section" to "Déplacer la section", "Hide this section" to "Masquer cette section", "↑↓ to move · OK to drop" to "↑↓ pour déplacer · OK pour poser", "Reset list order" to "Rétablir l\'ordre de la liste",
    "Group channels by" to "Classer les chaînes par", "Provider" to "Fournisseur", "Country" to "Pays", "Genre" to "Genre", "BY GENRE" to "PAR GENRE", "News" to "Actualités", "Sports" to "Sport", "Kids" to "Enfants", "Music" to "Musique", "Documentary" to "Documentaires", "Religious" to "Religion", "Entertainment" to "Divertissement", "General" to "Généraliste", "Business" to "Économie", "Education" to "Éducation", "Lifestyle" to "Art de vivre", "Shopping" to "Shopping", "File" to "Fichier", "Your playlists" to "Vos playlists",
    "Active" to "Active", "Remove" to "Supprimer", "None yet" to "Aucune", "Imported file" to "Fichier importé",
    "XMLTV guide URL" to "Adresse du guide XMLTV", "Optional programme guide · not set" to "Guide des programmes facultatif · non défini",
    "Set guide address" to "Définir l'adresse du guide", "Reload TV guide" to "Recharger le guide TV",
    "programmes loaded" to "programmes chargés",
    "Playback engine" to "Moteur de lecture", "Automatic · HLS / DASH" to "Automatique · HLS / DASH",
    "Version 1.0.0" to "Version 1.0.0",
    "Check for updates" to "Vérifier les mises à jour", "Checking for updates…" to "Recherche des mises à jour…",
    "Install update" to "Installer la mise à jour", "You're up to date" to "Vous êtes à jour",
    "Update downloaded. Choose Install update to continue." to "Mise à jour téléchargée. Choisissez Installer la mise à jour pour continuer.",
    "Allow STV to install updates in the next screen, then return and choose Install update." to "Autorisez STV à installer les mises à jour, puis revenez et choisissez Installer la mise à jour.",
    "Could not check for updates. Check your internet connection and try again." to "Impossible de vérifier les mises à jour. Vérifiez votre connexion Internet et réessayez.",
    "This update is not compatible with this app." to "Cette mise à jour n’est pas compatible avec cette application.",
    "Available version" to "Version disponible",
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
    "Retry" to "Réessayer", "Next channel" to "Chaîne suivante",
    // channel options and parental control
    "Add to favorites" to "Ajouter aux favoris", "Remove from favorites" to "Retirer des favoris",
    "Hide channel" to "Masquer la chaîne", "Lock channel" to "Verrouiller la chaîne", "Unlock channel" to "Déverrouiller la chaîne",
    "Rename" to "Renommer", "Rename channel" to "Renommer la chaîne", "Channel name" to "Nom de la chaîne", "Save" to "Enregistrer",
    "Wrong PIN" to "Code PIN incorrect",
    "Please enter the PIN to lock this channel" to "Veuillez saisir le code PIN pour verrouiller cette chaîne",

    "Watch history" to "Historique",
    "GENRES" to "GENRES",
    "Search movies" to "Rechercher des films",
    "Search series" to "Rechercher des séries",
    "No results" to "Aucun résultat",
    "Nothing here yet" to "Rien pour l'instant",
    "Movie" to "Film",
    "Resume" to "Reprendre",
    "Start over" to "Recommencer",
    "Play" to "Lire",
    "left" to "restantes",
    "More in" to "Plus dans",
    "Can't play this title" to "Impossible de lire ce titre",
    "Back" to "Retour",
    "Enter the same PIN again to confirm" to "Saisissez à nouveau le même code PIN pour confirmer",
    "The PINs did not match" to "Les codes PIN ne correspondent pas",
    "Please enter the PIN to remove this playlist" to "Veuillez saisir le code PIN pour supprimer cette playlist",
    "Please enter the PIN to hide this channel" to "Veuillez saisir le code PIN pour masquer cette chaîne",
    "Choose a 4-digit PIN to hide this channel" to "Choisissez un code PIN à 4 chiffres pour masquer cette chaîne",
    "Please enter the PIN to unlock this channel" to "Veuillez saisir le code PIN pour déverrouiller cette chaîne",
    "Please enter the PIN to open this channel" to "Veuillez saisir le code PIN pour ouvrir cette chaîne",
    "Choose a 4-digit PIN to lock this channel" to "Choisissez un code PIN à 4 chiffres pour verrouiller cette chaîne",
    "Choose a new 4-digit PIN" to "Choisissez un nouveau code PIN à 4 chiffres",
    "Enter your current PIN" to "Saisissez votre code PIN actuel",
    "Please enter the PIN to continue" to "Veuillez saisir le code PIN pour continuer",
    "PIN" to "Code PIN", "Set" to "Défini", "Not set" to "Non défini", "Set PIN" to "Définir le code PIN", "Change PIN" to "Changer le code PIN",
    "Locked channels" to "Chaînes verrouillées", "Hidden channels" to "Chaînes masquées", "Restore hidden channels" to "Restaurer les chaînes masquées",
    "Remove this playlist?" to "Supprimer cette playlist ?", "Hide this channel?" to "Masquer cette chaîne ?",
    // welcome
    "All" to "Tout", "space" to "espace",
    "Welcome" to "Bienvenue",
    "Choose how to add your playlist. You can change it any time in Settings." to "Choisissez comment ajouter votre playlist. Vous pouvez la changer à tout moment dans Réglages.",
    "Paste the address of an M3U or M3U8 playlist" to "Collez l'adresse d'une playlist M3U ou M3U8",
    "Sign in with a server, username and password" to "Connectez-vous avec un serveur, un nom d'utilisateur et un mot de passe",
    "Pick an M3U file stored on this TV" to "Choisissez un fichier M3U enregistré sur ce téléviseur",
    "Remove from history" to "Retirer de l'historique"
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
    "Search channels" to "ابحث في القنوات",
    "Start typing to search" to "اكتب للبحث",
    // welcome
    "Add your M3U playlist to start watching live TV, movies and series.\nYou can change it any time in Settings." to
        "أضف قائمة M3U لتبدأ مشاهدة البث المباشر والأفلام والمسلسلات.\nيمكنك تغييرها في أي وقت من الإعدادات.",
    "Add M3U playlist" to "إضافة قائمة M3U", "Xtream login" to "دخول Xtream",
    "Loading…" to "جارٍ التحميل…",
    // settings
    "Playback" to "التشغيل", "Playlist" to "قائمة التشغيل", "Language" to "اللغة", "Parental control" to "الرقابة الأبوية",
    "TV guide" to "دليل البرامج", "About" to "حول",
    "NOW PLAYING FROM" to "التشغيل من", "Add a playlist" to "إضافة قائمة", "M3U link" to "رابط M3U",
    "Import file" to "استيراد ملف", "Reload" to "تحديث",
    "Go to number" to "الذهاب لرقم", "No channel with this number" to "لا توجد قناة بهذا الرقم", "So many channels? CH+ / CH− jump 16 at a time, or type the channel number" to "القنوات كثيرة؟ استخدم زرّي ⁨CH+⁩ و⁨CH−⁩ للانتقال ست عشرة قناة في كل مرة، أو اكتب رقم القناة مباشرة", "Want this country first? Hold OK and move it up" to "هل تريد أن تظهر هذه الدولة أولًا؟ اضغط مطولًا على زر ⁨OK⁩ ثم انقلها إلى الأعلى", "Want this language first? Hold OK and move it up" to "هل تريد أن تظهر هذه اللغة أولًا؟ اضغط مطولًا على زر ⁨OK⁩ ثم انقلها إلى الأعلى", "Prefer this section higher? Hold OK and arrange the list your way" to "هل تفضّل أن يكون هذا القسم في موضع أعلى؟ اضغط مطولًا على زر ⁨OK⁩ لترتيب القائمة كما تشاء",
    "Hold OK for channel options" to "اضغط مطولًا على زر ⁨OK⁩ لعرض خيارات القناة", "Hold OK to move or hide this section" to "اضغط مطولًا على زر ⁨OK⁩ لنقل القسم أو إخفائه", "↑↓ change channel · ←→ channel list · Hold OK for options" to "⁨↑↓⁩ لتغيير القناة · ⁨←→⁩ لقائمة القنوات · اضغط مطولًا على ⁨OK⁩ للخيارات",
    "Move section" to "نقل القسم", "Hide this section" to "إخفاء القسم", "↑↓ to move · OK to drop" to "⁨↑↓⁩ للتحريك · ⁨OK⁩ للتثبيت", "Reset list order" to "إعادة الترتيب الافتراضي",
    "Group channels by" to "ترتيب القنوات حسب", "Provider" to "المزوّد", "Country" to "البلد", "Genre" to "النوع", "BY GENRE" to "حسب النوع", "News" to "أخبار", "Sports" to "رياضة", "Kids" to "أطفال", "Music" to "موسيقى", "Documentary" to "وثائقي", "Religious" to "ديني", "Entertainment" to "منوعات", "General" to "عامة", "Business" to "اقتصاد", "Education" to "تعليمي", "Lifestyle" to "لايف ستايل", "Shopping" to "تسوّق", "File" to "ملف", "Your playlists" to "قوائمك",
    "Active" to "مفعّلة", "Remove" to "حذف", "None yet" to "لا يوجد", "Imported file" to "ملف مستورد",
    "XMLTV guide URL" to "رابط دليل XMLTV", "Optional programme guide · not set" to "دليل برامج اختياري · غير محدد",
    "Set guide address" to "تحديد رابط الدليل", "Reload TV guide" to "إعادة تحميل الدليل",
    "programmes loaded" to "برنامج محمّل",
    "Playback engine" to "محرك التشغيل", "Automatic · HLS / DASH" to "تلقائي · HLS / DASH",
    "Version 1.0.0" to "الإصدار 1.0.0",
    "Check for updates" to "التحقق من التحديثات", "Checking for updates…" to "جارٍ البحث عن تحديثات…",
    "Install update" to "تثبيت التحديث", "You're up to date" to "التطبيق محدّث",
    "Update downloaded. Choose Install update to continue." to "تم تنزيل التحديث. اختر تثبيت التحديث للمتابعة.",
    "Allow STV to install updates in the next screen, then return and choose Install update." to "اسمح لـ STV بتثبيت التحديثات في الشاشة التالية، ثم ارجع واختر تثبيت التحديث.",
    "Could not check for updates. Check your internet connection and try again." to "تعذّر التحقق من التحديثات. تحقق من اتصال الإنترنت وحاول مجدداً.",
    "This update is not compatible with this app." to "هذا التحديث غير متوافق مع التطبيق.",
    "Available version" to "الإصدار المتاح",
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
    "Retry" to "إعادة المحاولة", "Next channel" to "القناة التالية",
    // channel options and parental control
    "Add to favorites" to "إضافة إلى المفضلة", "Remove from favorites" to "إزالة من المفضلة",
    "Hide channel" to "إخفاء القناة", "Lock channel" to "قفل القناة", "Unlock channel" to "فتح قفل القناة",
    "Rename" to "تغيير الاسم", "Rename channel" to "تغيير اسم القناة", "Channel name" to "اسم القناة", "Save" to "حفظ",
    "Wrong PIN" to "الرمز السري غير صحيح",
    "Please enter the PIN to lock this channel" to "الرجاء إدخال الرمز السري لإقفال هذه القناة",

    "Watch history" to "سجل المشاهدة",
    "GENRES" to "التصنيفات",
    "Search movies" to "ابحث في الأفلام",
    "Search series" to "ابحث في المسلسلات",
    "No results" to "لا توجد نتائج",
    "Nothing here yet" to "لا شيء هنا بعد",
    "Movie" to "فيلم",
    "Resume" to "متابعة",
    "Start over" to "من البداية",
    "Play" to "تشغيل",
    "left" to "متبقية",
    "More in" to "المزيد من",
    "Can't play this title" to "تعذّر تشغيل هذا العنوان",
    "Back" to "رجوع",
    "Enter the same PIN again to confirm" to "أدخل نفس الرمز السري مرة أخرى للتأكيد",
    "The PINs did not match" to "الرمزان غير متطابقين",
    "Please enter the PIN to remove this playlist" to "الرجاء إدخال الرمز السري لحذف هذه القائمة",
    "Please enter the PIN to hide this channel" to "الرجاء إدخال الرمز السري لإخفاء هذه القناة",
    "Choose a 4-digit PIN to hide this channel" to "اختر رمزاً سرياً من 4 أرقام لإخفاء هذه القناة",
    "Please enter the PIN to unlock this channel" to "الرجاء إدخال الرمز السري لفتح قفل هذه القناة",
    "Please enter the PIN to open this channel" to "الرجاء إدخال الرمز السري لفتح هذه القناة",
    "Choose a 4-digit PIN to lock this channel" to "اختر رمزاً سرياً من 4 أرقام لإقفال هذه القناة",
    "Choose a new 4-digit PIN" to "اختر رمزاً سرياً جديداً من 4 أرقام",
    "Enter your current PIN" to "أدخل الرمز السري الحالي",
    "Please enter the PIN to continue" to "الرجاء إدخال الرمز السري للمتابعة",
    "PIN" to "الرمز السري", "Set" to "محدّد", "Not set" to "غير محدّد", "Set PIN" to "تحديد الرمز السري", "Change PIN" to "تغيير الرمز السري",
    "Locked channels" to "القنوات المقفلة", "Hidden channels" to "القنوات المخفية", "Restore hidden channels" to "استرجاع القنوات المخفية",
    "Remove this playlist?" to "حذف هذه القائمة؟", "Hide this channel?" to "إخفاء هذه القناة؟",
    // welcome
    "All" to "الكل", "space" to "مسافة",
    "Welcome" to "أهلاً بك",
    "Choose how to add your playlist. You can change it any time in Settings." to "اختر طريقة إضافة قائمتك. يمكنك تغييرها في أي وقت من الإعدادات.",
    "Paste the address of an M3U or M3U8 playlist" to "الصق رابط قائمة M3U أو M3U8",
    "Sign in with a server, username and password" to "سجّل الدخول بالسيرفر واسم المستخدم وكلمة السر",
    "Pick an M3U file stored on this TV" to "اختر ملف M3U محفوظاً على هذا التلفزيون",
    "Remove from history" to "إزالة من سجل المشاهدة"
)
