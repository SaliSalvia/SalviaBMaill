package ir.salisec.autosender.ai

import ir.salisec.autosender.data.AI_PROVIDER_CLOUDFLARE
import ir.salisec.autosender.data.AI_PROVIDER_GOOGLE
import ir.salisec.autosender.data.AI_PROVIDER_GROQ
import ir.salisec.autosender.data.AI_PROVIDER_OPENROUTER
import ir.salisec.autosender.data.Settings
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSettingsTest {
    @Test
    fun selectedProviderUsesItsOwnKey() {
        assertEquals(
            "google-key-placeholder",
            Settings(aiProvider = AI_PROVIDER_GOOGLE, googleAiKey = "google-key-placeholder").aiApiKey()
        )
        assertEquals(
            "groq-key-placeholder",
            Settings(aiProvider = AI_PROVIDER_GROQ, groqKey = "groq-key-placeholder").aiApiKey()
        )
        assertEquals(
            "router-key-placeholder",
            Settings(aiProvider = AI_PROVIDER_OPENROUTER, openRouterKey = "router-key-placeholder").aiApiKey()
        )
    }

    @Test
    fun cloudflareRequiresBothTokenAndAccountId() {
        assertFalse(
            Settings(aiProvider = AI_PROVIDER_CLOUDFLARE, cloudflareKey = "token-placeholder")
                .aiConfigurationReady()
        )
        assertTrue(
            Settings(
                aiProvider = AI_PROVIDER_CLOUDFLARE,
                cloudflareKey = "token-placeholder",
                cloudflareAccountId = "account-id-placeholder"
            ).aiConfigurationReady()
        )
    }

    @Test
    fun freeGoogleModelsRankAboveLightweightFallbacks() {
        val ai = AiClient(OkHttpClient())
        assertTrue(ai.modelCapabilityScore("gemini-4-hydrogen") > ai.modelCapabilityScore("gemini-3.8-flash"))
        assertTrue(ai.modelCapabilityScore("gemini-3.8-flash") > ai.modelCapabilityScore("gemini-3.5-flash-lite"))
    }
}
