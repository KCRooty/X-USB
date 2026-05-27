package com.xusb.desktop

import kotlinx.coroutines.flow.*
import com.xusb.common.usb.*
import com.xusb.common.ui.theme.AppTheme
import com.xusb.common.IMainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DesktopViewModel : IMainViewModel {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    override val connectedDrive = MutableStateFlow(UsbDriveInfo.NONE)
    override val theme = MutableStateFlow(AppTheme.DARK_PURPLE)
    override val lang = MutableStateFlow(1) // ES
    override val isSpanish: Boolean get() = lang.value == 1

    init {
        refreshDrives()
    }

    fun refreshDrives() {
        scope.launch(Dispatchers.IO) {
            val drives = PlatformUtils.listUsbDrives()
            connectedDrive.value = drives.firstOrNull() ?: UsbDriveInfo.NONE
        }
    }

    override fun setTheme(t: AppTheme) { theme.value = t }
    override fun toggleLang() { lang.value = if (lang.value == 0) 1 else 0 }
    override fun ejectUsb() { /* Eject */ }
}
