package ir.salisec.autosender.data

/**
 * Single source of truth for consumer mailbox providers (Gmail / Outlook / Yahoo / …).
 *
 * A mailbox domain is where an inbox lives, it is NOT the business a message is about:
 * `salisec.ir@outlook.com` is the business `salisec.ir` on an Outlook mailbox. Email
 * identity, report matching, enrichment and salutations must never treat the mailbox
 * domain as the recipient's company.
 */
object MailboxDomains {

    /** Exact consumer mailbox domains (case-insensitive). */
    private val exact = setOf(
        "gmail.com", "googlemail.com", "gimail.com",
        "outlook.com", "outlook.ir", "outlook.de", "outlook.fr",
        "hotmail.com", "hotmail.co.uk", "hotmail.fr", "hotmail.de", "hotmail.ir",
        "live.com", "live.co.uk", "live.fr", "live.de", "live.ir",
        "msn.com", "yahoo.com", "yahoo.co.uk", "yahoo.fr", "yahoo.de", "yahoo.co.in",
        "yahoo.com.br", "ymail.com", "rocketmail.com", "aol.com", "aol.co.uk",
        "icloud.com", "me.com", "mac.com", "gmx.com", "gmx.de", "gmx.net",
        "mail.com", "email.com", "proton.me", "protonmail.com", "pm.me",
        "tutanota.com", "tuta.com", "tuta.io", "zoho.com", "zohomail.com",
        "yandex.com", "yandex.ru", "yandex.ir", "mail.ru", "inbox.ru", "list.ru", "bk.ru"
    )

    /** Root labels that are always a mailbox provider, whatever the TLD is. */
    private val rootLabels = setOf(
        "gmail", "googlemail", "gimail", "outlook", "hotmail", "yahoo", "ymail",
        "icloud", "protonmail", "tutanota", "gmx", "zoho", "yandex", "aol", "msn"
    )

    /** Brand tokens that must never be written as the recipient's business name. */
    private val brandTokens = listOf(
        "Gmail", "Googlemail", "Outlook", "Hotmail", "Yahoo", "Ymail", "iCloud",
        "AOL", "MSN", "GMX", "ProtonMail", "Tutanota", "Zoho", "Yandex",
        "جیمیل", "اوت‌لوک", "هوتمیل", "یاهو"
    )

    private val publicSuffixLabels = setOf("co", "com", "net", "org", "ac", "gov", "sch")

    fun normalize(domain: String): String =
        domain.trim().lowercase().removePrefix("www.").trimEnd('.')

    fun isFree(domain: String): Boolean {
        val d = normalize(domain)
        if (d.isBlank()) return false
        if (d in exact) return true
        return registrableLabel(d) in rootLabels
    }

    /** Display name of the mailbox provider, or "" when the domain is a real business domain. */
    fun brandOf(domain: String): String {
        val d = normalize(domain)
        if (!isFree(d)) return ""
        return when (val root = d.substringBefore('.')) {
            "googlemail", "gimail" -> "Gmail"
            "ymail", "rocketmail" -> "Yahoo"
            "hotmail" -> "Hotmail"
            "live" -> "Outlook"
            "msn" -> "MSN"
            "me", "mac" -> "iCloud"
            "protonmail", "pm", "proton" -> "Proton"
            "tuta" -> "Tutanota"
            "zohomail" -> "Zoho"
            "inbox", "list", "bk" -> "Mail.ru"
            else -> root.replaceFirstChar { it.uppercase() }
        }
    }

    /** Returns the mailbox brand mentioned in [text] (e.g. "Outlook"), or "" if none. */
    fun brandIn(text: String): String {
        if (text.isBlank()) return ""
        val lower = text.lowercase()
        return brandTokens.firstOrNull { lower.contains(it.lowercase()) }.orEmpty()
    }

    fun mentionsBrand(text: String): Boolean = brandIn(text).isNotBlank()

    /** The label that identifies the registrable domain, skipping suffixes like co.uk / com.ir. */
    fun registrableLabel(domain: String): String {
        val labels = normalize(domain).split('.').filter { it.isNotBlank() }
        if (labels.size < 2) return ""
        var index = labels.size - 2
        while (index > 0 && labels[index] in publicSuffixLabels) index--
        return labels.getOrNull(index).orEmpty()
    }
}

/** Resolves the recipient's real business identity without ever borrowing a mailbox domain. */
object BusinessDomains {

    /**
     * Priority: explicit user-provided domain > the domain recorded inside the report bundle
     * (sidecar/PDF) > a sitest folder name that is clearly a domain > the email domain when it
     * is a real business domain. "" means "unknown" — callers must not guess a company then.
     */
    fun resolve(
        explicit: String,
        reportDomain: String,
        folderName: String?,
        emailDomain: String
    ): String {
        val candidates = listOf(
            MailboxDomains.normalize(explicit),
            MailboxDomains.normalize(reportDomain),
            domainFromFolder(folderName)
        )
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            if (!DomainValidator.isValid(candidate)) continue
            if (MailboxDomains.isFree(candidate)) continue
            return candidate
        }
        val email = MailboxDomains.normalize(emailDomain)
        return if (email.isNotBlank() && DomainValidator.isValid(email) && !MailboxDomains.isFree(email)) email else ""
    }

    /** Folder names are free-form ("numberland.ir_2026", "SaliSecure"); accept only real domains. */
    fun domainFromFolder(name: String?): String {
        val raw = name?.trim()?.lowercase().orEmpty()
        if (raw.isBlank()) return ""
        val asDomain = raw.replace('_', '.').replace(' ', '.').replace(Regex("""\.\d{4}$"""), "")
        val candidate = asDomain.trim('.')
        return if (DomainValidator.isValid(candidate)) candidate else ""
    }
}
