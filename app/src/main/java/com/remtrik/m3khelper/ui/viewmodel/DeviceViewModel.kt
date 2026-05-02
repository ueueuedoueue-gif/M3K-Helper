package com.remtrik.m3khelper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remtrik.m3khelper.M3KApp
import com.remtrik.m3khelper.R.string
import com.remtrik.m3khelper.util.variables.DeviceStrings
import com.remtrik.m3khelper.util.variables.device
import com.remtrik.m3khelper.util.variables.BootIsPresent
import com.remtrik.m3khelper.util.variables.WindowsIsPresent
import com.remtrik.m3khelper.util.variables.dynamicVars
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceViewModel : ViewModel() {
    val uiState: StateFlow<DeviceStrings?> = combine(
        BootIsPresent,
        WindowsIsPresent,
        device.panelType,
        device.overrideDeviceCard
    ) { boot, windows, panel, _ ->
        val card = device.currentDeviceCard

        DeviceStrings(
            woa = M3KApp.getString(string.woa),
            model = M3KApp.getString(string.model, card.deviceName, card.deviceCodename[0]),
            ram = M3KApp.getString(string.ramvalue, device.ram),
            panel = M3KApp.getString(string.paneltype, panel),
            bootState = if (!card.noBoot && !card.noMount) {
                M3KApp.getString(string.backup_boot_state, M3KApp.getString(boot))
            } else null,
            slot = if (device.slot.isNotEmpty()) {
                M3KApp.getString(string.slot, device.slot.drop(1).uppercase())
            } else null,
            windowsStatus = if (!card.noMount) {
                M3KApp.getString(string.windows_status, M3KApp.getString(windows))
            } else null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun refreshStatus() {
        viewModelScope.launch {
            dynamicVars()
        }
    }
}