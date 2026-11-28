package ir.salisec.autosender.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the "مدیریت محترم Outlook" class of bug: the recipient's mailbox
 * provider must never be treated as their business identity.
 */
class BusinessIdentityTest {

    @Test
    fun mailboxProvidersAreNeverBusinessDomains() {
        assertTrue(MailboxDomains.isFree("outlook.com"))
        assertTrue(MailboxDomains.isFree("GMAIL.com"))
        assertTrue(MailboxDomains.isFree("yahoo.co.uk"))
        assertTrue(MailboxDomains.isFree("salisec.ir@outlook.com".substringAfter('@')))
        assertFalse(MailboxDomains.isFree("salisec.ir"))
        assertFalse(MailboxDomains.isFree("mail.salisec.ir"))
        assertEquals("Outlook", MailboxDomains.brandOf("outlook.com"))
        assertEquals("", SalutationBuilder.brandFromDomain("outlook.com"))
    }

    @Test
    fun businessDomainResolutionIgnoresTheMailbox() {
        assertEquals(
            "salisec.ir",
            BusinessDomains.resolve("", "salisec.ir", "salisec_ir_2026", "outlook.com")
        )
        assertEquals("", BusinessDomains.resolve("", "", null, "gmail.com"))
        assertEquals(
            "numberland.ir",
            BusinessDomains.resolve("", "", "numberland.ir_2026", "gmail.com")
        )
        // An explicitly stated company domain always wins over the report/folder heuristics.
        assertEquals("sali.ir", BusinessDomains.resolve("sali.ir", "salisec.ir", null, "outlook.com"))
    }

    @Test
    fun salutationNeverNamesTheMailboxProvider() {
        assertEquals("مدیریت محترم،", SalutationBuilder.salutation(null, "", ""))
        assertEquals("مدیریت محترم Salisec،", SalutationBuilder.salutation(null, "salisec.ir"))
        assertEquals(
            "مدیریت محترم SaliSecure،",
            SalutationBuilder.salutation(null, "salisec.ir", "SaliSecure")
        )
        assertEquals("سلام رضایی،", SalutationBuilder.salutation("رضایی", "salisec.ir"))
    }

    @Test
    fun enforceRewritesAGreetingThatNamedTheProvider() {
        val body = "مدیریت محترم مجموعه Outlook،\n\nامتیاز امنیتی شما ۳۳ از ۱۰۰ است."
        val fixed = SalutationBuilder.enforce(body, "مدیریت محترم Salisec،", "outlook.com")
        assertFalse(fixed.contains("Outlook"))
        assertTrue(fixed.startsWith("مدیریت محترم Salisec،"))
        assertTrue(fixed.contains("امتیاز امنیتی شما"))
    }

    @Test
    fun enforceKeepsACleanGreetingUntouched() {
        val body = "سلام آقای رضایی،\n\nمتن ایمیل."
        assertEquals(body, SalutationBuilder.enforce(body, "مدیریت محترم Salisec،", "outlook.com"))
    }
}
