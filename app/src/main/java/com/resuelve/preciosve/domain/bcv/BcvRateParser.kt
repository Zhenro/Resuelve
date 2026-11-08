package com.resuelve.preciosve.domain.bcv

import com.resuelve.preciosve.domain.util.LocalizedNumberParser

/** Extrae la tasa BCV desde el texto del banner de Gama: "BCV | Ref. 1 = Bs. XXX,XX". */
object BcvRateParser {

    private val BANNER_REGEX = Regex(
        pattern = "BCV\\s*\\|\\s*Ref\\.?\\s*1\\s*=\\s*Bs\\.?\\s*([0-9][0-9.,]*)",
        option = RegexOption.IGNORE_CASE
    )

    /** Devuelve los bolívares por Ref. o null si el texto no contiene el banner o el valor es inválido. */
    fun parseBanner(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val normalized = text.replace('\u00A0', ' ')
        val raw = BANNER_REGEX.find(normalized)?.groupValues?.get(1) ?: return null
        return LocalizedNumberParser.parse(raw)?.takeIf { it > 0.0 }
    }
}
