package com.rork.weatherloom.build

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildDependencyPolicyTest {

    @Test
    fun `Kotlin Gradle plugin stays at patched security floor`() {
        val catalog = repoFile("android/gradle/libs.versions.toml").readText()
        val match = Regex("""(?m)^kotlin\s*=\s*\"([^\"]+)\"\s*$""").find(catalog)

        assertNotNull("version catalog must declare the Kotlin plugin version", match)
        val version = requireNotNull(match).groupValues[1]

        assertTrue(
            "Kotlin Gradle plugin $version is below the patched 2.4.20 security floor",
            isStableVersionAtLeast(version, "2.4.20")
        )
    }

    private fun isStableVersionAtLeast(actual: String, floor: String): Boolean {
        if (!actual.matches(Regex("""\d+\.\d+\.\d+"""))) return false
        val actualParts = actual.split('.').map(String::toInt)
        val floorParts = floor.split('.').map(String::toInt)
        return actualParts.zip(floorParts)
            .firstOrNull { (left, right) -> left != right }
            ?.let { (left, right) -> left > right }
            ?: true
    }

    private fun repoFile(relative: String): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val base = current ?: error("Could not locate repository file: $relative")
            val candidate = File(base, relative)
            if (candidate.isFile) return candidate
            current = base.parentFile
        }
        error("Could not locate repository file: $relative from ${System.getProperty("user.dir")}")
    }
}
