package dev.jdtech.jellyfin.player.local.domain

import android.os.Build
import java.util.Locale

private val ISO3_TO_ISO1 =
    mapOf(
        "eng" to "en",
        "jpn" to "ja",
        "chi" to "zh",
        "zho" to "zh",
        "ger" to "de",
        "deu" to "de",
        "fre" to "fr",
        "fra" to "fr",
        "spa" to "es",
        "kor" to "ko",
        "rus" to "ru",
        "ita" to "it",
        "por" to "pt",
        "dut" to "nl",
        "nld" to "nl",
        "pol" to "pl",
        "tur" to "tr",
        "ukr" to "uk",
        "cze" to "cs",
        "ces" to "cs",
        "slo" to "sk",
        "slk" to "sk",
        "slv" to "sl",
        "hun" to "hu",
        "rum" to "ro",
        "ron" to "ro",
        "bul" to "bg",
        "swe" to "sv",
        "dan" to "da",
        "fin" to "fi",
        "nor" to "no",
        "gre" to "el",
        "ell" to "el",
        "heb" to "he",
        "ara" to "ar",
        "hin" to "hi",
        "vie" to "vi",
        "tha" to "th",
        "ind" to "id",
        "may" to "ms",
        "msa" to "ms",
        "cat" to "ca",
        "baq" to "eu",
        "eus" to "eu",
        "glg" to "gl",
        "srp" to "sr",
        "hrv" to "hr",
        "bos" to "bs",
        "alb" to "sq",
        "sqi" to "sq",
        "mac" to "mk",
        "mkd" to "mk",
        "lit" to "lt",
        "lav" to "lv",
        "est" to "et",
        "ice" to "is",
        "isl" to "is",
        "wel" to "cy",
        "cym" to "cy",
        "bel" to "be",
        "aze" to "az",
        "kaz" to "kk",
        "uzb" to "uz",
        "tgl" to "tl",
        "fil" to "tl",
        "yue" to "zh",
    )

private val DEFAULT_MARKERS =
    setOf(
        "default",
        "??",
        "??",
        "??",
        "??",
        "d?faut",
        "standard",
        "predeterminado",
        "predeterminada",
        "predefinido",
        "padr?o",
        "vorgabe",
        "?? ?????????",
        "???????????",
        "domy?lny",
        "predvolen?",
        "privzeto",
        "varsay?lan",
        "??",
        "?????",
        "??",
        "???????",
        "????? ????",
        "m?c ??nh",
        "predeterminat",
        "??????????",
        "????????????",
        "oletus",
        "forvalt",
        "standart",
        "?? ?????????????",
    )

fun buildStreamLabel(
    title: String?,
    language: String,
    codec: String,
    isSubtitle: Boolean,
): String {
    val localizedLanguage = languageName(language)
    val englishLanguage = englishLanguageName(language)
    val languageTag = languageTag(language)
    val codecLower = codec.trim().lowercase()

    val segments = title?.split(" - ")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    val remaining = segments.filterNot { segment ->
        val lower = segment.lowercase()
        lower in DEFAULT_MARKERS ||
            lower == codecLower ||
            lower == language.lowercase() ||
            lower == languageTag?.lowercase() ||
            (englishLanguage != null && lower == englishLanguage.lowercase()) ||
            (localizedLanguage != null && lower == localizedLanguage.lowercase())
    }

    val languageLabel =
        localizedLanguage ?: remaining.firstOrNull() ?: language.uppercase().ifBlank { "Track" }
    val rest = if (localizedLanguage != null) remaining else remaining.drop(1)

    return if (isSubtitle) {
        val displayCodec = codec.trim().uppercase()
        if (displayCodec.isNotEmpty()) "$languageLabel - $displayCodec" else languageLabel
    } else {
        (listOf(languageLabel) + rest).joinToString(" - ")
    }
}

private fun languageTag(code: String): String? {
    if (code.isBlank()) return null
    val lower = code.trim().lowercase()
    return ISO3_TO_ISO1[lower] ?: lower
}

private fun languageName(code: String): String? {
    val tag = languageTag(code) ?: return null
    val locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Locale.of(tag)
        } else {
            @Suppress("DEPRECATION") Locale(tag)
        }
    val name = locale.displayLanguage
    return name.takeIf { it.isNotBlank() && !it.equals(tag, true) && !it.equals(code, true) }
}

private fun englishLanguageName(code: String): String? {
    val tag = languageTag(code) ?: return null
    val locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Locale.of(tag)
        } else {
            @Suppress("DEPRECATION") Locale(tag)
        }
    return locale.getDisplayLanguage(Locale.ENGLISH)
}
