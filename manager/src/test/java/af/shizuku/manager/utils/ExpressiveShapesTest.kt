package af.shizuku.manager.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExpressiveShapesTest {
    @Test
    fun everyShapeIsCenteredInTheUnitSquare() {
        for (key in ExpressiveShapes.KEYS.filter { it != ExpressiveShapes.OFF } + "circle") {
            val p = ExpressiveShapes.polygon(key)
            // The icon is drawn at the drawable's center; the shape's own center must match.
            assertEquals(0.5f, p.centerX, 0.002f, "$key centerX")
            assertEquals(0.5f, p.centerY, 0.002f, "$key centerY")
            val b = p.calculateBounds(approximate = false)
            assertTrue(b[0] >= -0.001f && b[1] >= -0.001f && b[2] <= 1.001f && b[3] <= 1.001f, "$key fits: ${b.toList()}")
        }
    }
}
