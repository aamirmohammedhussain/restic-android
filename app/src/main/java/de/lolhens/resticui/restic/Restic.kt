package de.lolhens.resticui.restic

import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class Restic(
    private val storage: ResticStorage
) {
    private val proot = storage.lib().resolve("proot")
    private val restic = storage.lib().resolve("restic")
    private val loader = storage.lib().resolve("loader")
    private val loader32 = storage.lib().resolve("loader32")

    private fun binds(hostsFile: File): List<Pair<String, String>> = listOf(
        Pair("/system", "/system"),
        Pair("/data", "/data"),
        Pair(hostsFile.absolutePath, "/etc/hosts"),
    ).plus(storage.storage().map {
        val path = it.absolutePath
        Pair(path, path)
    })

    private fun args(binds: List<Pair<String, String>>): List<String> =
        listOf(
            proot.absolutePath,
            "--kill-on-exit",
        ).plus(
            binds.flatMap { (from, to) -> listOf("-b", "$from:$to") }
        ).plus(
            restic.absolutePath
        )

    private fun vars(): List<Pair<String, String>> = listOf(
        Pair("PATH", "/system/bin"),
        Pair("LD_LIBRARY_PATH", storage.lib().absolutePath),
        Pair("PROOT_LOADER", loader.absolutePath),
        Pair("PROOT_LOADER_32", loader32.absolutePath),
        Pair("PROOT_TMP_DIR", storage.cache().absolutePath),
        Pair("RESTIC_CACHE_DIR", storage.cache().resolve("restic").absolutePath)
    )

    private fun hostsFile(hosts: List<String>): CompletableFuture<File> =
        CompletableFuture.supplyAsync {
            val hostsFileContent = hosts.map { host ->
                val address = InetAddress.getByName(host)
                "${address.hostAddress} $host"
            }.joinToString("\n")

            val hostsFile = File.createTempFile("hosts", "", storage.cache())
            try {
                hostsFile.writeText(hostsFileContent, StandardCharsets.UTF_8)
                hostsFile
            } catch (e: Exception) {
                hostsFile.delete()
                throw e
            }
        }

    fun restic(
        args: List<String>,
        vars: List<Pair<String, String>> = emptyList(),
        hosts: List<String> = emptyList(),
        filterOut: ((String) -> Boolean)? = null,
        filterErr: ((String) -> Boolean)? = null
    ): CompletableFuture<Pair<List<String>, List<String>>> =
        hostsFile(hosts).thenCompose { hostsFile ->
            CompletableFuture.supplyAsync {
                Runtime.getRuntime().exec(
                    args(binds(hostsFile)).plus(args).toTypedArray(),
                    vars().plus(vars).map { (key, value) -> "$key=$value" }.toTypedArray()
                )
            }.thenCompose { process ->
                fun InputStream.linesAsync(filter: ((String) -> Boolean)?) =
                    CompletableFuture.supplyAsync {
                        this.bufferedReader().lineSequence()
                            .filter { if (filter == null) true else filter(it) }.toList()
                    }

                val outFuture = process.inputStream.linesAsync(filterOut)
                val errFuture = process.errorStream.linesAsync(filterErr)

                outFuture.thenCompose { out ->
                    errFuture.thenApplyAsync { err ->
                        val exitCode = process.waitFor()
                        if (exitCode == 0) Pair(out, err)
                        else throw ResticException(exitCode, err)
                    }
                }
            }.handle { result, exception ->
                hostsFile.delete()

                if (exception != null) throw exception
                result
            }
        }

    fun version(): CompletableFuture<String> =
        restic(listOf("version")).thenApply { (out, _) -> out[0] }
}