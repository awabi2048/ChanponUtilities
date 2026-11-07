package me.awabi2048.chanponutilities.guide

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuideTargetResolverTest {
    @Test fun `only exact nearest selector is resolved as selector`() {
        val nearest = { "nearest" }
        val byName = { name: String -> "name:$name" }
        assertEquals("nearest", GuideTargetResolver.resolve("@p", nearest, byName))
        assertEquals("name:Alice", GuideTargetResolver.resolve("Alice", nearest, byName))
        assertNull(GuideTargetResolver.resolve("@p[distance=..5]", nearest, byName))
        assertNull(GuideTargetResolver.resolve("@a", nearest, byName))
        assertNull(GuideTargetResolver.resolve("@p", { null }, byName))
    }
}
