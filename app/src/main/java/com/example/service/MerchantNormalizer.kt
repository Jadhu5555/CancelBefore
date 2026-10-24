package com.example.service

import java.util.Locale

object MerchantNormalizer {

    private val PREFIX_REGEX = Regex(
        """^(?:GOOGLE\s*\*|GOOGLE\*|AMZN\s*\*|AMZN\*|AMAZON\s*\*|PAYTM\s*\*|RAZORPAY\s*\*|BILLDESK\s*\*|PAYU\s*\*|CC\s*\*|POS\s*\*|ACH\s*\*|UPI\s*[-/]\s*|MSFT\s*\*|OPENAI\s*\*|APPLE\s*\*)""",
        RegexOption.IGNORE_CASE
    )

    private val SUFFIX_REGEX = Regex(
        """\b(?:INC|LLC|LTD|PVT\s+LTD|PRIVATE\s+LIMITED|LIMITED|GMBH|CORP|CORPORATION|CO|ENTERTAINMENT|TECHNOLOGIES|TECH|SERVICES|DIGITAL|MEDIA|INDIA|UK|US|USA|GLOBAL|AB)\b\.?""",
        RegexOption.IGNORE_CASE
    )

    private val KNOWN_MERCHANT_MAPPINGS = listOf(
        Regex("""(?i)\b(?:netflix|nflx)\b""") to "Netflix",
        Regex("""(?i)\b(?:spotify)\b""") to "Spotify",
        Regex("""(?i)\b(?:amazon\s*prime|prime\s*video|amzn\s*prime)\b""") to "Amazon Prime",
        Regex("""(?i)\b(?:hotstar|disney\+?\s*hotstar)\b""") to "Disney+ Hotstar",
        Regex("""(?i)\b(?:youtube\s*(?:premium|music)?)\b""") to "YouTube Premium",
        Regex("""(?i)\b(?:google\s*one|google\s*storage)\b""") to "Google One",
        Regex("""(?i)\b(?:google\s*play(?:\s*apps)?)\b""") to "Google Play",
        Regex("""(?i)\b(?:apple(?:\.com)?(?:/bill)?|apple\s*services|itunes)\b""") to "Apple Services",
        Regex("""(?i)\b(?:microsoft|msft|office)\s*[*_/\-]?\s*365\b|\b(?:xbox\s*game\s*pass)\b""") to "Microsoft 365",
        Regex("""(?i)\b(?:chatgpt|openai)\b""") to "ChatGPT Plus",
        Regex("""(?i)\b(?:canva)\b""") to "Canva",
        Regex("""(?i)\b(?:dropbox)\b""") to "Dropbox",
        Regex("""(?i)\b(?:adobe|adobe\s*systems)\b""") to "Adobe Creative Cloud",
        Regex("""(?i)\b(?:github)\b""") to "GitHub",
        Regex("""(?i)\b(?:swiggy\s*one|swiggy)\b""") to "Swiggy One",
        Regex("""(?i)\b(?:zomato\s*gold|zomato)\b""") to "Zomato Gold",
        Regex("""(?i)\b(?:uber\s*one|uber)\b""") to "Uber One",
        Regex("""(?i)\b(?:times\s*prime)\b""") to "Times Prime",
        Regex("""(?i)\b(?:duolingo)\b""") to "Duolingo",
        Regex("""(?i)\b(?:playstation(?:\s*plus)?|ps\s*plus)\b""") to "PlayStation Plus"
    )

    /**
     * Scans an entire text block (subject, body, notification content) to see if a known
     * canonical subscription merchant is mentioned.
     */
    fun findKnownMerchant(text: String): String? {
        if (text.isBlank()) return null
        for ((regex, canonical) in KNOWN_MERCHANT_MAPPINGS) {
            if (regex.containsMatchIn(text)) {
                return canonical
            }
        }
        return null
    }

    fun normalize(rawMerchant: String): String {
        if (rawMerchant.isBlank()) return "Unknown Merchant"

        var cleaned = rawMerchant.trim()

        // 1. Direct scan against known canonical mappings
        findKnownMerchant(cleaned)?.let { return it }

        // 2. Strip prefix aggregator (e.g. GOOGLE *, MSFT *, AMZN *)
        cleaned = PREFIX_REGEX.replace(cleaned, "")

        // 3. Strip legal suffixes (INC, LLC, PVT LTD, etc.)
        cleaned = SUFFIX_REGEX.replace(cleaned, "")

        // 4. Check known mappings again after cleaning
        findKnownMerchant(cleaned)?.let { return it }

        // 5. Clean extra punctuation and spaces
        cleaned = cleaned.replace(Regex("""[._\-*#]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.isBlank()) return "Unknown Merchant"

        // 6. Capitalize words properly
        return cleaned.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }
    }
}
