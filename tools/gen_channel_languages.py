"""
Builds app/src/main/resources/channel_languages.tsv from the iptv-org database
(https://github.com/iptv-org/database): the language of every channel and feed whose
language is not the one the app would guess from its country ("RTArabic.ru" is Arabic,
not Russian; "MBC1USA.us" is Arabic, not English).

Usage: python3 tools/gen_channel_languages.py PATH/TO/iptv-org/database
Run from the repository root; it reads the country -> language table from ChannelGrouping.kt.
"""
import csv
import re
import sys

db = sys.argv[1].rstrip("/") + "/data"
grouping = open("app/src/main/java/com/streamtv/ui/ChannelGrouping.kt", encoding="utf-8").read()
block = grouping[grouping.index("private val CountryLanguage"):grouping.index("/** A language named")]
country_language = {}
for m in re.finditer(r'for \(c in listOf\(([^)]*)\)\) put\(c, "(\w+)"\)', block):
    for c in re.findall(r'"(\w+)"', m.group(1)):
        country_language[c] = m.group(2)
for c, lang in re.findall(r'put\("(\w+)", "(\w+)"\)', block):
    country_language[c] = lang

# ISO 639-1 (the app's table) -> ISO 639-3 (the database's codes), for the languages in that table.
to3 = {"ar": "ara", "fr": "fra", "en": "eng", "de": "deu", "es": "spa", "pt": "por", "it": "ita", "nl": "nld",
       "tr": "tur", "ru": "rus", "zh": "zho", "fa": "fas", "ur": "urd", "hi": "hin", "el": "ell", "hr": "hrv",
       "ro": "ron", "pl": "pol", "uk": "ukr", "cs": "ces", "sk": "slk", "hu": "hun", "bg": "bul", "sr": "srp",
       "sv": "swe", "no": "nor", "da": "dan", "fi": "fin", "ja": "jpn", "ko": "kor", "vi": "vie", "th": "tha",
       "id": "ind", "ms": "msa", "he": "heb", "sq": "sqi", "hy": "hye", "ka": "kat", "az": "aze", "bn": "ben",
       "si": "sin", "ne": "nep", "mn": "mon", "sl": "slv", "bs": "bos", "mk": "mkd", "lt": "lit", "lv": "lav",
       "et": "est", "is": "isl"}
missing = set(country_language.values()) - set(to3)
assert not missing, f"add these to to3: {missing}"

countries = {r["id"]: r["country"] for r in csv.DictReader(open(f"{db}/channels.csv", encoding="utf-8"))}
feeds = [f for f in csv.DictReader(open(f"{db}/feeds.csv", encoding="utf-8")) if f["channel"] in countries]


def language_of(feed):
    """The feed's language(s) as the app should file it, or None when the app's own guess is right."""
    langs = [l for l in feed["languages"].split(";") if l]
    guess = to3.get(country_language.get(countries[feed["channel"]], ""))
    if not langs or langs == [guess]:
        return None
    # A feed that lists the country's language beside another one ("eng;ara" for MBC 1 USA) is
    # filed under the other one: the country's language there is subtitles or the broadcast area.
    return ";".join([l for l in langs if l != guess] or langs)


# A channel id stands for its main feed; another feed ("France24.fr@Arabic") gets a line of its own
# only when its language differs from the main one (the app falls back to the channel id).
main = {f["channel"]: language_of(f) for f in feeds if f["is_main"].upper() == "TRUE"}
lines = {channel: value for channel, value in main.items() if value}
for feed in feeds:
    value = language_of(feed)
    if feed["is_main"].upper() != "TRUE" and value != main.get(feed["channel"]):
        lines[f"{feed['channel']}@{feed['id']}"] = value or "-"

with open("app/src/main/resources/channel_languages.tsv", "w", encoding="utf-8") as out:
    for key in sorted(lines):
        out.write(f"{key}\t{lines[key]}\n")
print(len(lines), "entries")
