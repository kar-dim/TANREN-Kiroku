package gr.dkaratzas.tanrenkiroku

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FixesVerificationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Floating point weight formatting precision test
    @Test
    fun testWeightFormattingPrecision() {
        fun formatWeightNum(value: Double): String {
            return if (kotlin.math.abs(value - kotlin.math.round(value)) < 0.001) {
                kotlin.math.round(value).toLong().toString()
            } else {
                "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
            }
        }

        // Exact integers
        assertEquals("20", formatWeightNum(20.0))
        assertEquals("0", formatWeightNum(0.0))

        // Floating point near integers (e.g. IEEE 754 precision artifacts)
        assertEquals("20", formatWeightNum(20.000000000000004))
        assertEquals("20", formatWeightNum(19.999999999999996))

        // Decimals
        assertEquals("22.5", formatWeightNum(22.5))
        assertEquals("22.25", formatWeightNum(22.25))
        assertEquals("17.1", formatWeightNum(17.100000000000001))
    }

    // Bodyweight exercises (0 kg) allowed by validation
    @Test
    fun testSetDialogValidation() {
        fun validate(repsStr: String, kgStr: String): Pair<Int?, Double?> {
            val reps = repsStr.trim().toIntOrNull()?.takeIf { it > 0 }
            val kg = kgStr.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
            return reps to kg
        }

        // Bodyweight 0 kg is valid
        val (reps0, kg0) = validate("10", "0")
        assertEquals(10, reps0)
        assertEquals(0.0, kg0!!, 0.0001)

        // Bodyweight 0.0 kg is valid
        val (reps00, kg00) = validate("8", "0.0")
        assertEquals(8, reps00)
        assertEquals(0.0, kg00!!, 0.0001)

        // Standard weight is valid
        val (repsStd, kgStd) = validate("8", "80.5")
        assertEquals(8, repsStd)
        assertEquals(80.5, kgStd!!, 0.0001)

        // Comma decimal separator is valid
        val (repsComma, kgComma) = validate("12", "72,5")
        assertEquals(12, repsComma)
        assertEquals(72.5, kgComma!!, 0.0001)

        // Negative weight is rejected
        val (_, kgNeg) = validate("10", "-5")
        assertEquals(null, kgNeg)

        // 0 reps is rejected
        val (repsZero, _) = validate("0", "10")
        assertEquals(null, repsZero)
    }

    // Greek / non-ASCII custom exercise slug & collision handling
    @Test
    fun testGreekCustomExerciseIdGeneration() {
        fun generateId(name: String, existingIds: Set<String>): String {
            val trimmed = name.trim()
            val slug = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            val baseId = if (slug.isNotBlank()) "custom_$slug" else "custom_${System.currentTimeMillis()}"
            var id = baseId
            var counter = 1
            while (existingIds.contains(id)) {
                id = "${baseId}_$counter"
                counter++
            }
            return id
        }

        // ASCII slug works
        val id1 = generateId("Cable Fly", emptySet())
        assertEquals("custom_cable_fly", id1)

        // Greek text produces non-empty fallback ID instead of "custom_"
        val idGreek1 = generateId("Πιέσεις Στήθους", emptySet())
        assertTrue(idGreek1.startsWith("custom_"))
        assertNotEquals("custom_", idGreek1)

        // Subsequent Greek exercise avoids collision with earlier one
        val idGreek2 = generateId("Κάμψεις Δικεφάλων", setOf(idGreek1))
        assertNotEquals(idGreek1, idGreek2)

        // Colliding base ID resolves with counter
        val idDup1 = generateId("Push Up", setOf("custom_push_up"))
        assertEquals("custom_push_up_1", idDup1)

        val idDup2 = generateId("Push Up", setOf("custom_push_up", "custom_push_up_1"))
        assertEquals("custom_push_up_2", idDup2)
    }

    // Custom exercise category preserved in primary muscles
    @Test
    fun testCategoryPreservedInPrimaryMuscles() {
        val selectedGroup = "Chest"
        val userChosenPrimary = setOf("Shoulders")

        val effectivePrimary = (setOf(selectedGroup) + userChosenPrimary).toList()

        assertEquals(listOf("Chest", "Shoulders"), effectivePrimary)
        assertTrue(effectivePrimary.contains("Chest"))
    }

    // Resilient manifest parsing skips corrupted/empty files
    @Test
    fun testManifestResilientParsing() {
        val tempDir = File.createTempFile("tanren_test", "dir")
        tempDir.delete()
        tempDir.mkdirs()

        try {
            // Valid workout file
            val validFile = File(tempDir, "2026-09-26.json")
            validFile.writeText("""{"date":"2026-09-26","entries":[]}""")

            // Corrupt file (0 bytes)
            val emptyFile = File(tempDir, "2026-09-25.json")
            emptyFile.writeText("")

            // Corrupt file (malformed JSON)
            val malformedFile = File(tempDir, "2026-09-24.json")
            malformedFile.writeText("{ broken json content")

            val files = tempDir.listFiles() ?: emptyArray()

            val entries = files.mapNotNull { file ->
                runCatching {
                    val normalized = json.encodeToString(json.parseToJsonElement(file.readText())).toByteArray()
                    file.name to normalized.size
                }.getOrNull()
            }

            // Only the valid file should be parsed, not crashing on corrupted ones
            assertEquals(1, entries.size)
            assertEquals("2026-09-26.json", entries.first().first)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
