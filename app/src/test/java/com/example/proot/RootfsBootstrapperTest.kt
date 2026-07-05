package com.example.proot

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RootfsBootstrapperTest {
    @Test
    fun `buildTarCommand skips ownership metadata for Android extraction`() {
        val archive = File("/tmp/rootfs.tar.gz")
        val destination = File("/tmp/rootfs")

        val command = RootfsBootstrapperTestHelper.buildTarCommand(archive, destination, "tar.gz")

        assertEquals(
            listOf(
                "tar",
                "--no-same-owner",
                "-xzf",
                "/tmp/rootfs.tar.gz",
                "-C",
                "/tmp/rootfs"
            ),
            command
        )
    }
}

object RootfsBootstrapperTestHelper {
    fun buildTarCommand(archive: File, destinationDir: File, ext: String): List<String> {
        val tarFlag = if (ext == "tar.xz") "-xJf" else "-xzf"
        return listOf(
            "tar",
            "--no-same-owner",
            "--no-same-permissions",
            tarFlag,
            archive.absolutePath,
            "-C",
            destinationDir.absolutePath
        )
    }
}
