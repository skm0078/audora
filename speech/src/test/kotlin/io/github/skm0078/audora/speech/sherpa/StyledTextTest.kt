// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/StyledTextTest.kt
// Purpose: A styled model's sentence ends without the mark sherpa-onnx would cut into a noise (ADR-057).

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class StyledTextTest {
    @Test
    fun `a final dot, question or exclamation becomes a danda in Bengali and Hindi`() {
        assertEquals("তুমি কোথায় যাচ্ছ।", StyledText.ending("তুমি কোথায় যাচ্ছ?", Lang.BN))
        assertEquals("যাও।", StyledText.ending("যাও!!", Lang.BN))
        assertEquals("सुनो।", StyledText.ending("सुनो.", Lang.HI))
    }

    @Test
    fun `closing quotes stay after the danda, and a danda already there is not doubled`() {
        assertEquals("সে বলল, “যাও।”", StyledText.ending("সে বলল, “যাও!”", Lang.BN))
        assertEquals("সে এল।", StyledText.ending("সে এল।", Lang.BN))
        assertEquals("সে এল।", StyledText.ending("সে এল।.", Lang.BN))
    }

    @Test
    fun `marks inside the sentence stay, and English just drops the end mark`() {
        assertEquals("যাও! এখনই।", StyledText.ending("যাও! এখনই!", Lang.BN))
        assertEquals("Come here", StyledText.ending("Come here?", Lang.EN))
        assertEquals("কোনো চিহ্ন নেই", StyledText.ending("কোনো চিহ্ন নেই", Lang.BN))
    }
}
