// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/delivery/SentenceFeelingsTest.kt
// Purpose: Table of sentences in English, Hindi and Bengali and the feeling their own words ask for (ADR-056).

package io.github.skm0078.audora.speech.delivery

import io.github.skm0078.audora.core.model.Feeling
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceFeelingsTest {
    @Test
    fun `each sentence gets the feeling its words ask for`() {
        listOf(
            "“Get out!” he shouted." to Feeling.LOUD,
            "“चले जाओ!” वह चिल्लाया।" to Feeling.LOUD,
            "“বেরিয়ে যাও!” সে চেঁচিয়ে উঠল।" to Feeling.LOUD,
            // A verb beats the exclamation mark: a whispered "Run!" is soft.
            "“Run!” she whispered." to Feeling.SOFT,
            "उसने धीरे से कहा, “मत जाओ।”" to Feeling.SOFT,
            "সে ফিসফিস করে বলল, “যেও না।”" to Feeling.SOFT,
            "She sobbed into her hands." to Feeling.SAD,
            "माँ रो पड़ी।" to Feeling.SAD,
            "মেয়েটি কেঁদে ফেলল।" to Feeling.SAD,
            "“Who's there?” she asked, trembling." to Feeling.SCARED,
            "वह डरकर पीछे हट गया।" to Feeling.SCARED,
            "ভয়ে তার গলা শুকিয়ে গেল।" to Feeling.SCARED,
            "He gasped. “You!”" to Feeling.SURPRISED,
            "वह चौंककर बोली, “तुम यहाँ!”" to Feeling.SURPRISED,
            "সে অবাক হয়ে বলল, “তুমি এখানে!”" to Feeling.SURPRISED,
            "“We won!” cried Tom." to Feeling.LIVELY,
            "वह हँसकर बोली, “आ गए तुम।”" to Feeling.LIVELY,
            "সে হেসে বলল, “এসেছ তাহলে।”" to Feeling.LIVELY,
            "What a day it was!" to Feeling.LIVELY,
            "And then the door opened…" to Feeling.TRAILING,
            "He waited for an answer that never came..." to Feeling.TRAILING,
            "“Is anyone there?”" to Feeling.ASKING,
            "क्या तुम आओगे?" to Feeling.ASKING,
            "তুমি কি আসবে?" to Feeling.ASKING,
            "The house was empty now." to Feeling.NEUTRAL,
            "घर अब खाली था।" to Feeling.NEUTRAL,
            "বাড়িটা এখন ফাঁকা।" to Feeling.NEUTRAL,
        ).forEach { (text, feeling) -> assertEquals(text, feeling, SentenceFeelings.of(text)) }
    }

    @Test
    fun `a closing quote or bracket after the mark does not hide it`() {
        assertEquals(Feeling.ASKING, SentenceFeelings.of("He asked (as always) “why?”  "))
        assertEquals(Feeling.TRAILING, SentenceFeelings.of("“If only…”"))
    }

    @Test
    fun `a Bengali cue matches whichever way the PDF encodes its nukta letters`() {
        val composed = "\u09DF" // য় as one code point, which NFC splits into য and the nukta
        assertEquals(Feeling.SCARED, SentenceFeelings.of("ভ" + composed + "ে সে কাঁপছিল।"))
        assertEquals(Feeling.SCARED, SentenceFeelings.of("ভয়ে সে কাঁপছিল।"))
    }
}
