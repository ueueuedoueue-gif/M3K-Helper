package com.remtrik.m3khelper.util.funcs

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.remtrik.m3khelper.R
import com.remtrik.m3khelper.M3KApp
import com.remtrik.m3khelper.util.variables.commandHandler
import com.remtrik.m3khelper.util.variables.device
import com.remtrik.m3khelper.util.variables.SDCARD_PATH
import com.remtrik.m3khelper.util.variables.bootBackupStatus
import com.remtrik.m3khelper.util.variables.commandError
import com.remtrik.m3khelper.util.variables.commandResult
import com.remtrik.m3khelper.util.variables.showBootBackupErrorDialog
import com.remtrik.m3khelper.util.variables.showMountErrorDialog
import com.remtrik.m3khelper.util.variables.showQuickBootErrorDialog
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

internal val BlockWindowsPath by lazy {
    ShellUtils.fastCmd("readlink -fn /dev/block/bootdevice/by-name/win")
}

private const val TAG = "M3K: Commands"

object RootCommandExecutor {
    suspend fun exec(command: String): Shell.Result = withContext(Dispatchers.IO) {
        try {
            Shell.cmd(command).exec()
        } catch (t: Throwable) {
            Log.e(TAG, "RootCommandExecutor failed: ", t)
            try {
                Shell.cmd("echo 'ERROR' 1>&2").exec()
            } catch (_: Throwable) {
                throw t
            }
        }
    }
}

abstract class Commands {
    lateinit var internalLastCommandResult: Shell.Result
    lateinit var internalCommandResult: Shell.Result

    private fun toCommandResult(res: Shell.Result?): CommandResult {
        return if (res != null) {
            CommandResult(
                res.isSuccess,
                res.out.toMutableList(),
                res.err.toMutableList()
            )
        } else {
            CommandResult(
                false,
                mutableListOf(R.string.mount_error_default.string()),
                mutableListOf(R.string.mount_error_default.string())
            )
        }
    }

    fun dumpBoot(type: ErrorType, where: BootBackupState): CommandResult = runBlocking(Dispatchers.IO) {
            dumpBootSuspend(type, where)
        }

    protected suspend fun dumpBootSuspend(type: ErrorType, where: BootBackupState): CommandResult {
        when (where) {
            BootBackupState.WINDOWS -> {
                val ok = withMountedWindowsSuspend(type) {
                    val target = File("$SDCARD_PATH/Windows/boot.img").canonicalFile
                    internalLastCommandResult =
                        RootCommandExecutor.exec("dd if=/dev/block/bootdevice/by-name/boot${device.slot} of=${target.path} bs=32M")
                }
                if (!ok) return CommandResult(
                    false,
                    mutableListOf(R.string.mount_error_default.string()),
                    mutableListOf(R.string.mount_error_default.string())
                )
            }

            BootBackupState.ANDROID -> {
                val target = File("$SDCARD_PATH/boot.img").canonicalFile
                internalLastCommandResult =
                    RootCommandExecutor.exec("dd if=/dev/block/bootdevice/by-name/boot${device.slot} of=${target.path}")
            }

            else -> return CommandResult(
                false,
                mutableListOf("Invalid 'where' arg"),
                mutableListOf("Invalid 'where' arg")
            )
        }
        bootBackupStatus()
        return toCommandResult(internalLastCommandResult)
    }

    fun mountWindows(): CommandResult = runBlocking(Dispatchers.IO) {
        mountWindowsSuspend()
    }

    protected suspend fun mountWindowsSuspend(): CommandResult {
        RootCommandExecutor.exec("mkdir -p ${SDCARD_PATH}/Windows")
        internalCommandResult =
            RootCommandExecutor.exec("su -mm -c mount.ntfs /dev/block/by-name/win ${SDCARD_PATH}/Windows")
        return toCommandResult(internalCommandResult)
    }

    fun umountWindows(): CommandResult = runBlocking(Dispatchers.IO) {
        umountWindowsSuspend()
    }

    protected suspend fun umountWindowsSuspend(): CommandResult {
        internalCommandResult = RootCommandExecutor.exec("su -mm -c umount ${SDCARD_PATH}/Windows")
        RootCommandExecutor.exec("rmdir ${SDCARD_PATH}/Windows || true")
        return toCommandResult(internalCommandResult)
    }

    fun isMounted(): MountStatus {
        val result = Shell.cmd("mount | grep $BlockWindowsPath").exec()
        return if (result.isSuccess && result.out.isNotEmpty() && result.out[0].contains("Windows")) MountStatus.MOUNTED else MountStatus.NOT_MOUNTED
    }

    private fun checkSensors(): Boolean {
        if (!device.currentDeviceCard.sensors) return true
        var check = false
        runBlocking(Dispatchers.IO) {
            withMountedWindowsSuspend(ErrorType.QUICKBOOT_ERROR) {
                val out =
                    ShellUtils.fastCmd("find ${SDCARD_PATH}/Windows/Windows/System32/Drivers/DriverData/QUALCOMM/fastRPC/vendor/etc/sensors/*")
                check = out.isNotEmpty()
            }
        }
        return check
    }

    private fun dumpSensors(): CommandResult = runBlocking(Dispatchers.IO) {
        dumpSensorsSuspend()
    }

    protected suspend fun dumpSensorsSuspend(): CommandResult {
        withMountedWindowsSuspend(ErrorType.QUICKBOOT_ERROR) {
            internalLastCommandResult =
                RootCommandExecutor.exec("cp -r /vendor/etc/sensors/* ${SDCARD_PATH}/Windows/Windows/System32/Drivers/DriverData/QUALCOMM/fastRPC/vendor/etc/sensors")
        }
        return toCommandResult(internalLastCommandResult)
    }

    private fun dumpModem(): CommandResult = runBlocking(Dispatchers.IO) {
        dumpModemSuspend()
    }

    protected suspend fun dumpModemSuspend(): CommandResult {
        withMountedWindowsSuspend(ErrorType.QUICKBOOT_ERROR) {
            val path =
                ShellUtils.fastCmd("find ${SDCARD_PATH}/Windows/Windows/System32/DriverStore/FileRepository -name qcremotefs8150.inf_arm64_*")
            if (path.isEmpty()) {
                internalLastCommandResult =
                    Shell.cmd("echo 'modem path not found'").exec()
            } else {
                internalLastCommandResult =
                    RootCommandExecutor.exec("dd if=/dev/block/bootdevice/by-name/modemst1 of=$path/bootmodem_fs1 bs=8388608 && dd if=/dev/block/bootdevice/by-name/modemst2 of=$path/bootmodem_fs2 bs=8388608")
            }
        }
        return toCommandResult(internalLastCommandResult)
    }

    private fun uefitell(uefiFile: File): Shell.Result {
        return runBlocking(Dispatchers.IO) {
            RootCommandExecutor.exec("dd if=${uefiFile.path} of=/dev/block/bootdevice/by-name/boot${device.slot} bs=32M")
        }
    }

    fun flashUEFI(uefiPath: String): CommandResult {
        return runBlocking(Dispatchers.IO) {
            val file = File(uefiPath).canonicalFile
            /*if (!file.exists()) {
                return@runBlocking CommandResult(
                    false,
                    mutableListOf("UEFI file not found".toString()),
                    mutableListOf("UEFI file not found".toString())
                )
            }*/
            val res = uefitell(file)
            toCommandResult(res)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun quickBoot(uefiPath: String) {
        runBlocking(Dispatchers.IO) {
            var manualReboot = false
            if (!device.currentDeviceCard.noMount) {
                if (ShellUtils.fastCmd("find ${SDCARD_PATH}/Windows/boot.img").isEmpty()) {
                    commandResult = dumpBoot(ErrorType.QUICKBOOT_ERROR, BootBackupState.WINDOWS)
                    if (!commandResult.isSuccess) {
                        handleErrorType(ErrorType.QUICKBOOT_ERROR, commandResult)
                        manualReboot = true
                    }
                }

                if (!device.currentDeviceCard.noModem) {
                    commandResult = dumpModem()
                    if (!commandResult.isSuccess) {
                        handleErrorType(ErrorType.QUICKBOOT_ERROR, commandResult)
                        manualReboot = true
                    }
                }

                if (device.currentDeviceCard.sensors && !checkSensors()) {
                    commandResult = dumpSensors()
                    if (!commandResult.isSuccess) {
                        handleErrorType(ErrorType.QUICKBOOT_ERROR, commandResult)
                        manualReboot = true
                    }
                }
            }
            if (ShellUtils.fastCmd("find ${SDCARD_PATH}/boot.img").isEmpty()) {
                commandResult = dumpBoot(ErrorType.QUICKBOOT_ERROR, BootBackupState.ANDROID)
                if (!commandResult.isSuccess) {
                    handleErrorType(ErrorType.QUICKBOOT_ERROR, commandResult)
                    return@runBlocking
                }
            }

            commandResult = flashUEFI(uefiPath)
            if (!commandResult.isSuccess) {
                handleErrorType(ErrorType.QUICKBOOT_ERROR, commandResult)
                return@runBlocking
            }

            // reboot: use RootCommandExecutor
            if (!manualReboot) {
                RootCommandExecutor.exec("svc power reboot")
            } else {
                GlobalScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        M3KApp,
                        R.string.manual_reboot_toast.string(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * this gay shitter is used for backwards compatibility and is visible from outside
     */
    fun withMountedWindows(
        type: ErrorType,
        block: () -> Unit
    ): Boolean {
        return runBlocking {
            withMountedWindowsSuspend(type) {
                block()
            }
        }
    }

    protected suspend fun withMountedWindowsSuspend(
        type: ErrorType,
        block: suspend () -> Unit
    ): Boolean {
        val wasMounted = isMounted()
        if (wasMounted == MountStatus.NOT_MOUNTED && !device.currentDeviceCard.noMount) {
            commandResult = commandHandler.mountWindows()
            val isOk = try {
                internalCommandResult.isSuccess
            } catch (_: UninitializedPropertyAccessException) {
                false
            }
            if (!isOk) {
                handleErrorType(type, commandResult)
                if (type != ErrorType.MOUNT_ERROR) {
                    return false
                }
            }
        }
        try {
            block()
        } finally {
            if (wasMounted == MountStatus.NOT_MOUNTED && !device.currentDeviceCard.noMount) {
                commandResult = commandHandler.umountWindows()
                val isOk2 = try {
                    internalCommandResult.isSuccess
                } catch (_: UninitializedPropertyAccessException) {
                    false
                }
                if (!isOk2) {
                    handleErrorType(type, commandResult)
                }
            }
        }
        return true
    }

    private fun handleErrorType(type: ErrorType, result: CommandResult) {
        commandError.value = result.output.firstOrNull() ?: ""
        when (type) {
            ErrorType.MOUNT_ERROR -> showMountErrorDialog.value = true
            ErrorType.BOOTBACKUP_ERROR -> showBootBackupErrorDialog.value = true
            ErrorType.QUICKBOOT_ERROR -> showQuickBootErrorDialog.value = true
        }
    }
}

fun Context.restart() {
    runCatching {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(Intent.makeRestartActivityTask(it.component))
        }
    }.onFailure { e -> Log.e("M3K Helper", "restart failed", e) }
}

data class CommandResult(
    val isSuccess: Boolean,
    val output: MutableList<String>,
    val error: MutableList<String>
)

enum class ErrorType { MOUNT_ERROR, BOOTBACKUP_ERROR, QUICKBOOT_ERROR }

enum class MountStatus { NOT_MOUNTED, MOUNTED }

enum class BootBackupState { NONE, ANDROID, WINDOWS, BOTH }