// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/share/ShareCardScreenTest.kt
// Purpose: The card screen in English, Hindi and Bengali at normal and double text size: the picture it makes is
//          exactly the chosen size, decodes, carries the mark, and the controls stay usable; video mode records the
//          card without its words. Screenshots of the screen and of the made pictures go to CI's "player-screens"
//          artifact.

package io.github.skm0078.audora.ui.share

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList as ComposeLocales
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.share.CardQuote
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareCardScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var recorder: CardRecorder
    private var shared = 0

    private val actions = object : CardScreenActions {
        override fun onBack() = Unit

        override fun onChoices(choices: CardChoices) {
            this@ShareCardScreenTest.choices = choices
        }

        override fun onPickPicture() = Unit

        override fun onShare() {
            shared++
        }

        override fun onMakeVideo() {
            videos++
        }
    }

    private var videos = 0

    private var choices by mutableStateOf(CardChoices())

    private fun show(subject: CardSubject, language: String, fontScale: Float) {
        rule.setContent {
            val config = DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE) then
                DeviceConfigurationOverride.FontScale(fontScale) then
                DeviceConfigurationOverride.Locales(ComposeLocales(language))
            DeviceConfigurationOverride(config) {
                recorder = rememberCardRecorder()
                AudoraTheme { ShareCardScreen(subject, choices, null, busy = false, recorder, actions) }
            }
        }
        rule.waitForIdle()
    }

    /** The picture as Share writes it: the file decodes at exactly the shape's pixels. */
    private fun madePicture(name: String, shape: CardShape) {
        // Drawing the window once makes sure the recorder holds the latest frame.
        rule.onRoot().captureToImage()
        val bitmap = runBlocking { withContext(Dispatchers.Main) { recorder.capture() } }
        assertEquals(shape.widthPx, bitmap.width)
        assertEquals(shape.heightPx, bitmap.height)
        val card = requireNotNull(ShareCardFiles(context).write(bitmap, photo = false)) { "card not written" }
        val decoded = requireNotNull(BitmapFactory.decodeFile(card.file.path)) { "card does not decode" }
        assertEquals(shape.widthPx, decoded.width)
        assertEquals(shape.heightPx, decoded.height)
        assertEquals("image/png", card.mimeType)
        assertTrue("the file is in the share folder", card.file.parentFile == ShareCardFiles(context).folder)
        Screens.save(name, decoded)
    }

    @Test
    fun anEnglishQuoteMakesAStoryPictureAndTheSizeChipsChangeIt() {
        show(subject(ENGLISH), "en", 1f)
        Screens.save("share-card-en", rule.onRoot().captureToImage().asAndroidBitmap())
        madePicture("share-card-en-story", CardShape.STORY)

        rule.onNodeWithText(text(R.string.share_card_square)).performClick()
        rule.waitForIdle()
        assertEquals(CardShape.SQUARE, choices.shape)
        madePicture("share-card-en-square", CardShape.SQUARE)

        rule.onNodeWithText(text(R.string.share_card_send)).performScrollTo().performClick()
        assertEquals(1, shared)
    }

    @Test
    fun aHindiQuoteAtDoubleTextSizeKeepsEveryControlReachable() {
        choices = CardChoices(look = CardLook.PAPER)
        show(subject(HINDI), "hi", 2f)
        Screens.save("share-card-hi-2x", rule.onRoot().captureToImage().asAndroidBitmap())
        madePicture("share-card-hi-story", CardShape.STORY)
        rule.onNodeWithContentDescription(text(R.string.share_card_look_sepia, "hi")).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(CardLook.SEPIA, choices.look)
        rule.onNodeWithText(text(R.string.share_card_send, "hi")).performScrollTo().assertExists()
    }

    @Test
    fun aLongBengaliQuoteIsCutAndSaysSo() {
        choices = CardChoices(shape = CardShape.POST, look = CardLook.NIGHT)
        val long = List(LONG_REPEATS) { BENGALI }.joinToString(" ")
        show(subject(long), "bn", 2f)
        rule.onNodeWithText(text(R.string.share_card_cut_note, "bn")).assertExists()
        Screens.save("share-card-bn-2x", rule.onRoot().captureToImage().asAndroidBitmap())
        madePicture("share-card-bn-post", CardShape.POST)
    }

    @Test
    fun videoModeIsAStoryWithoutTheWordsInTheBackdropAtDoubleBengaliText() {
        choices = CardChoices(shape = CardShape.SQUARE, look = CardLook.NIGHT)
        show(subject(BENGALI), "bn", 2f)
        rule.onNodeWithText(text(R.string.share_card_kind_video, "bn")).performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(choices.video)
        Screens.save("share-card-video-bn-2x", rule.onRoot().captureToImage().asAndroidBitmap())

        // The recorded card is the video's backdrop: a story, and the words (drawn only on screen) are not in it.
        rule.onRoot().captureToImage()
        val backdrop = runBlocking { withContext(Dispatchers.Main) { recorder.capture() } }
        assertEquals(CardShape.STORY.widthPx, backdrop.width)
        assertEquals(CardShape.STORY.heightPx, backdrop.height)
        val row = (backdrop.height * WORDS_ROW).toInt()
        val night = backdrop.getPixel(backdrop.width / 2, row)
        (SIDE_PX until backdrop.width - SIDE_PX).forEach { x ->
            assertEquals("no word at x=$x", night, backdrop.getPixel(x, row))
        }
        Screens.save("share-card-video-backdrop", backdrop)

        rule.onNodeWithText(text(R.string.share_card_video_sharp, "bn")).performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(choices.sharp)
        rule.onNodeWithText(text(R.string.share_card_video_sharp_note, "bn")).assertExists()
        rule.onNodeWithText(text(R.string.share_card_make_video, "bn")).performScrollTo().performClick()
        assertEquals(1, videos)
        assertEquals(0, shared)
    }

    @Test
    fun aBookCardShowsTheBookWithoutAQuote() {
        show(subject(null), "en", 1f)
        rule.onNodeWithText(text(R.string.share_card_book_title)).assertExists()
        madePicture("share-card-book", CardShape.STORY)
    }

    private fun subject(quote: String?): CardSubject {
        val cut = quote?.let { CardQuote.of(it) }
        val page = cut?.let { "Page 12" }
        return CardSubject(
            "The Sample Book",
            "A. Writer",
            cover = null,
            softCover = null,
            quote = cut,
            pageLabel = page,
        )
    }

    private fun text(id: Int, language: String = "en"): String {
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language)))
        return context.createConfigurationContext(config).getString(id)
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        const val LONG_REPEATS = 8

        // Across the middle of the quote's space, inside the card's sides: only the background is there.
        const val WORDS_ROW = 0.4f
        const val SIDE_PX = 120
        const val ENGLISH = "All human beings are born free and equal in dignity and rights."
        const val HINDI = "सभी मनुष्यों को गौरव और अधिकारों के मामले में जन्मजात स्वतंत्रता और समानता प्राप्त है।"
        const val BENGALI = "সমস্ত মানুষ স্বাধীনভাবে সমান মর্যাদা এবং অধিকার নিয়ে জন্মগ্রহণ করে।"
    }
}
