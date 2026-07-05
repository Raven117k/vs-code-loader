package com.example.install

import android.content.Context
import com.example.proot.CommandRunner
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class PackageInstaller(private val context: Context) {
    private val commandRunner = CommandRunner(context)

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    fun isInstalled(): Boolean {
        // Check if code-server binary or config exists inside the rootfs
        val codeServerBin = File(context.filesDir, "rootfs/usr/local/bin/code-server")
        val altBin = File(context.filesDir, "rootfs/usr/bin/code-server")
        val npmCS = File(context.filesDir, "rootfs/usr/local/lib/node_modules/code-server")
        return codeServerBin.exists() || altBin.exists() || npmCS.exists()
    }

    suspend fun installPackages(distro: String, useFastInstall: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        _isInstalling.value = true
        _progress.value = 0.05f
        AppLogger.log("Installer", "Starting installation of requirements inside $distro...")

        try {
            // 1. Update package lists & install git + curl + ca-certificates
            _status.value = "Installing system dependencies (git, curl, ca-certificates)..."
            _progress.value = 0.15f
            
            val baseInstallCmd = if (distro == "Alpine") {
                "apk update && apk add --no-cache git curl ca-certificates bash"
            } else {
                "apt update && apt install -y git curl ca-certificates"
            }

            val baseExitCode = commandRunner.runCommand(baseInstallCmd) { line ->
                _status.value = "Deps: $line"
            }

            if (baseExitCode != 0) {
                AppLogger.log("Installer", "Failed to install base dependencies, exit code: $baseExitCode")
                _status.value = "Failed installing basic packages"
                _isInstalling.value = false
                return@withContext false
            }

            _progress.value = 0.4f

            // 2. Install Code-Server
            if (useFastInstall) {
                _status.value = "Downloading & extracting precompiled code-server release..."
                AppLogger.log("Installer", "Using precompiled fast installation for code-server...")
                
                // Determine architecture suffix inside guest
                val fastInstallScript = """
                    set -e
                    ARCH_NAME=""
                    UNAME_M=$(uname -m)
                    if [ "${'$'}UNAME_M" = "aarch64" ] || [ "${'$'}UNAME_M" = "arm64" ]; then
                        ARCH_NAME="arm64"
                    else
                        ARCH_NAME="amd64"
                    fi
                    
                    VERSION="4.92.2"
                    TARBALL="code-server-${'$'}VERSION-linux-${'$'}ARCH_NAME.tar.gz"
                    URL="https://github.com/coder/code-server/releases/download/v${'$'}VERSION/${'$'}TARBALL"
                    
                    echo "Selected URL: ${'$'}URL"
                    curl -fL -o /tmp/code-server.tar.gz "${'$'}URL"
                    
                    echo "Extracting code-server to /usr/local/lib..."
                    mkdir -p /usr/local/lib/code-server
                    tar -xzf /tmp/code-server.tar.gz -C /usr/local/lib/code-server --strip-components=1
                    
                    echo "Creating symlink to /usr/local/bin..."
                    mkdir -p /usr/local/bin
                    ln -sf /usr/local/lib/code-server/bin/code-server /usr/local/bin/code-server
                    
                    echo "Cleanup temp files..."
                    rm -f /tmp/code-server.tar.gz
                    
                    echo "Fast Install completed!"
                """.trimIndent()

                val fastExitCode = commandRunner.runCommand(fastInstallScript) { line ->
                    _status.value = "FastInstall: $line"
                }

                if (fastExitCode != 0) {
                    AppLogger.log("Installer", "Fast installation failed, falling back to Node NPM...")
                } else {
                    _progress.value = 0.9f
                    _status.value = "Verifying installation..."
                    
                    val versionExitCode = commandRunner.runCommand("code-server --version") { line ->
                        _status.value = "VS Code: $line"
                    }
                    
                    if (versionExitCode == 0) {
                        AppLogger.log("Installer", "VS Code Server successfully installed and verified!")
                        _status.value = "Installed successfully!"
                        _progress.value = 1.0f
                        _isInstalling.value = false
                        return@withContext true
                    }
                }
            }

            // Fallback: Install via Node.js NPM
            _status.value = "NPM Method: Installing Node.js & NPM..."
            _progress.value = 0.5f

            val nodeInstallCmd = if (distro == "Alpine") {
                "apk add --no-cache nodejs npm"
            } else {
                "apt install -y nodejs npm"
            }

            val nodeExit = commandRunner.runCommand(nodeInstallCmd) { line ->
                _status.value = "Node: $line"
            }

            if (nodeExit != 0) {
                AppLogger.log("Installer", "Failed to install Node.js/NPM")
                _status.value = "Node/NPM install failed"
                _isInstalling.value = false
                return@withContext false
            }

            _progress.value = 0.7f
            _status.value = "NPM Method: Installing code-server (may take a few minutes)..."

            val npmInstallCmd = "npm install -g --unsafe-perm code-server"
            val npmExit = commandRunner.runCommand(npmInstallCmd) { line ->
                _status.value = "NPM: $line"
            }

            if (npmExit != 0) {
                AppLogger.log("Installer", "NPM installation of code-server failed")
                _status.value = "NPM install of VS Code failed"
                _isInstalling.value = false
                return@withContext false
            }

            _progress.value = 0.9f
            _status.value = "Verifying installation..."
            commandRunner.runCommand("code-server --version") { line ->
                _status.value = "VS Code: $line"
            }

            _status.value = "Installed successfully!"
            _progress.value = 1.0f
            _isInstalling.value = false
            true

        } catch (e: Exception) {
            AppLogger.log("Installer", "Installation failed: ${e.message}")
            _status.value = "Error: ${e.message}"
            _isInstalling.value = false
            false
        }
    }

    fun stopInstallation() {
        commandRunner.stopActiveCommand()
        _isInstalling.value = false
        _status.value = "Installation stopped"
    }
}
