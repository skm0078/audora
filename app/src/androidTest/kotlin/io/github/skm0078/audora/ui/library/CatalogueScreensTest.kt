// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/CatalogueScreensTest.kt
// Purpose: The catalogue (ADR-069) on a phone-sized screen: a filter applies to the list the moment it is picked,
//          collection chips narrow the library and count their books, a book joins a collection from its details,
//          and screenshots of the filter and collection sheets for review in CI's "player-screens" artifact.

package io.github.skm0078.audora.ui.library

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.LibraryFacets
import io.github.skm0078.audora.core.library.LibraryIndex
import io.github.skm0078.audora.core.library.LibraryNarrow
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogueScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aFilterPickedInTheSheetNarrowsTheListAtOnce() {
        var narrow by mutableStateOf(LibraryNarrow())
        rule.setContent {
            Phone {
                Column {
                    LibraryQuery(narrow = narrow).apply(BOOKS, { it }, { null }, INDEX, MEMBERS).forEach {
                        Text("Row ${it.title}")
                    }
                }
                FilterSheet(FACETS, COLLECTIONS, COUNTS, narrow, { narrow = it }, {}, Modifier.testTag(SHEET))
            }
        }
        rule.onNodeWithText("Row Gitanjali").assertExists()
        save("filter-sheet", rule.onNodeWithTag(SHEET).captureToImage().asAndroidBitmap())

        rule.onNodeWithText(shelf("Premchand", 1)).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(LibraryNarrow(author = "Premchand"), narrow)
        rule.onNodeWithText("Row Godaan").assertExists()
        rule.onNodeWithText("Row Gitanjali").assertDoesNotExist()

        val clear = context.getString(R.string.catalogue_clear)
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(clear))
        rule.onNodeWithText(clear).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Row Gitanjali").assertExists()
    }

    @Test
    fun aCollectionChipCountsItsBooksAndNarrowsTheLibrary() {
        var query by mutableStateOf(LibraryQuery())
        rule.setContent {
            Phone {
                val items = BOOKS.map { LibraryItem(it, availability = null) }
                val list = LibraryListState.Loaded(
                    items = query.apply(items, { it.book }, { it.progress }, INDEX, MEMBERS),
                    all = items,
                    query = query,
                    collections = COLLECTIONS,
                    members = MEMBERS,
                    facets = FACETS,
                )
                Column(Modifier.testTag(SHEET)) {
                    LibraryChipRow(list, onQuery = { query = it })
                    list.items.forEach { Text("Row ${it.book.title}") }
                }
            }
        }
        // One chip row for shelves and collections: a collection waits past the shelves, a sideways scroll away.
        rule.onNodeWithText(shelf("For school", 2)).performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText(shelf("For school", 2)).assertIsSelected()
        rule.onNodeWithText(context.getString(R.string.catalogue_filters_count, 1)).assertExists()
        rule.onNodeWithText("Row Godaan").assertExists()
        rule.onNodeWithText("Row Gora").assertDoesNotExist()
        save("catalogue-row", rule.onNodeWithTag(SHEET).captureToImage().asAndroidBitmap())
    }

    @Test
    fun theCollectionsSheetListsEachWithItsBooksAndAsksBeforeDeleting() {
        val deleted = mutableListOf<String>()
        rule.setContent {
            Phone {
                CollectionsSheet(
                    COLLECTIONS,
                    COUNTS,
                    emptyMap(),
                    { _, _ -> },
                    { _, _, _, _ -> },
                    { deleted += it },
                    {},
                    Modifier.testTag(SHEET),
                )
            }
        }
        rule.onNodeWithText("For school").assertExists()
        val books = context.resources.getQuantityString(R.plurals.collections_book_count, 2, 2)
        rule.onAllNodes(hasText(books)).onFirst().assertExists()
        save("collections-sheet", rule.onNodeWithTag(SHEET).captureToImage().asAndroidBitmap())

        rule.onNode(hasContentDescription(context.getString(R.string.collections_delete_named, "Bedtime")))
            .performClick()
        rule.onNodeWithText(context.getString(R.string.collections_delete_body)).assertExists()
        // The sheet is a dialog too, and its rows' Delete buttons say which collection: the confirm button does not.
        val rowDelete =
            hasContentDescription(context.getString(R.string.collections_delete_named, ""), substring = true)
        rule.onNode(
            hasText(context.getString(R.string.collections_delete)) and hasAnyAncestor(isDialog()) and !rowDelete,
        )
            .performClick()
        rule.waitForIdle()
        assertEquals(listOf("bed"), deleted)
    }

    @Test
    fun aBookJoinsACollectionFromItsDetailsAtOnce() {
        var memberOf by mutableStateOf(setOf<String>())
        rule.setContent {
            Phone {
                BookCollectionChips(
                    COLLECTIONS,
                    memberOf,
                    onToggle = { id, member -> memberOf = if (member) memberOf + id else memberOf - id },
                    onCreate = {},
                )
            }
        }
        rule.onNodeWithText("Bedtime").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Bedtime").assertIsSelected()
        assertEquals(setOf("bed"), memberOf)
    }

    private fun shelf(name: String, count: Int) = context.getString(R.string.library_shelf, name, count)

    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TEST_PHONE_SIZE)) {
            AudoraTheme { content() }
        }
    }

    /** Into Downloads/ListenLock-screens, which outlives the test app's uninstall; CI pulls it into an artifact. */
    private fun save(name: String, bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, SCREENS_FOLDER)
        }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "No $name" }
        requireNotNull(resolver.openOutputStream(uri)) { "Cannot write $name" }.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG of $name failed" }
        }
    }

    private companion object {
        val TEST_PHONE_SIZE = DpSize(411.dp, 914.dp)
        const val PNG_QUALITY = 100
        const val SCREENS_FOLDER = "Download/ListenLock-screens"
        const val SHEET = "sheet"

        fun book(title: String, author: String?, format: DocumentFormat, language: String) = Book(
            BookId(title), "content://test/$title", title, 1_000, "f-$title", 10, 1, null,
            author = author, language = language, format = format,
        )

        val BOOKS = listOf(
            book("Gitanjali", "Rabindranath Tagore", DocumentFormat.EPUB, "bn"),
            book("Godaan", "Premchand", DocumentFormat.PDF, "hi"),
            book("Gora", "Rabindranath Tagore", DocumentFormat.PDF, "bn"),
        )
        val INDEX = LibraryIndex.of(BOOKS)
        val FACETS = LibraryFacets.of(BOOKS, INDEX)
        val COLLECTIONS = listOf(BookCollection("bed", "Bedtime", 2), BookCollection("school", "For school", 1))
        val MEMBERS = mapOf(BookId("Gitanjali") to setOf("school", "bed"), BookId("Godaan") to setOf("school"))
        val COUNTS = collectionCounts(MEMBERS)
    }
}
