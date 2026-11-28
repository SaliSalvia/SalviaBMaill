package ir.salisec.autosender.data

/**
 * Builds the opening line of an email deterministically, so the greeting can never be
 * derived from the recipient's mailbox provider (the classic "مدیریت محترم Outlook،"
 * failure for `someone@outlook.com`).
 *
 * Priority: person's name > researched business identity > business domain brand > generic.
 * A mailbox brand is never a valid business identity, so it can never reach the text.
 */
object SalutationBuilder {

    private val greetingTokens = listOf(
        "سلام", "درود", "با سلام", "مدیریت محترم", "جناب", "سرکار",
        "hello", "dear", "hi "
    )

    fun isGreeting(line: String): Boolean {
        if (line.isBlank()) return false
        val lower = line.trim().lowercase()
        return greetingTokens.any { lower.contains(it) }
    }

    /** Deterministic first line for this recipient. */
    fun salutation(contactName: String?, businessDomain: String, identityName: String = ""): String {
        val person = contactName?.trim().orEmpty()
        if (person.isNotBlank() && !MailboxDomains.mentionsBrand(person)) {
            return "سلام $person،"
        }
        val identity = identityName.trim()
            .takeIf { it.length in 2..60 && !MailboxDomains.mentionsBrand(it) }
            .orEmpty()
        if (identity.isNotBlank()) return "مدیریت محترم $identity،"

        val brand = brandFromDomain(businessDomain)
        return if (brand.isNotBlank()) "مدیریت محترم $brand،" else "مدیریت محترم،"
    }

    /** Human-readable brand from a real business domain; "" for mailbox/unknown domains. */
    fun brandFromDomain(domain: String): String {
        val clean = MailboxDomains.normalize(domain)
        if (clean.isBlank() || MailboxDomains.isFree(clean) || !DomainValidator.isValid(clean)) return ""
        val root = MailboxDomains.registrableLabel(clean)
        if (root.length < 3) return ""
        return root.replaceFirstChar { it.uppercase() }
    }

    /**
     * Safety net applied to every generated/edited draft: an opening line that names a
     * mailbox provider is replaced with the deterministic salutation, and when the model
     * produced no greeting at all the deterministic one is prepended.
     */
    fun enforce(body: String, salutation: String, mailboxDomain: String = ""): String {
        if (salutation.isBlank()) return body
        val lines = body.lines()
        val firstIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstIndex < 0) return body

        val first = lines[firstIndex].trim()
        val contaminated = MailboxDomains.mentionsBrand(first) ||
            (mailboxDomain.isNotBlank() && first.contains(mailboxDomain, ignoreCase = true))

        val rebuilt = lines.toMutableList()
        if (isGreeting(first)) {
            if (contaminated) rebuilt[firstIndex] = salutation
            return rebuilt.joinToString("\n")
        }
        if (lines.none { isGreeting(it) }) {
            return (listOf(salutation, "") + lines).joinToString("\n").trimStart()
        }
        return body
    }
}
