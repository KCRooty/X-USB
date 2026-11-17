package com.xusb.common

import kotlinx.coroutines.flow.*
import com.xusb.common.usb.*
import com.xusb.common.ui.theme.AppTheme

interface IMainViewModel {
    val connectedDrive: StateFlow<UsbDriveInfo>
    val theme: StateFlow<AppTheme>
    val lang: StateFlow<Int>
    val isSpanish: Boolean
    fun setTheme(t: AppTheme)
    fun toggleLang()
    fun ejectUsb()
}

class MockViewModel : IMainViewModel {
    override val connectedDrive = MutableStateFlow(UsbDriveInfo.NONE)
    override val theme = MutableStateFlow(AppTheme.DARK_PURPLE)
    override val lang = MutableStateFlow(1) // ES por defecto
    override val isSpanish = true
    override fun setTheme(t: AppTheme) { (theme as MutableStateFlow).value = t }
    override fun toggleLang() { (lang as MutableStateFlow).value = if (lang.value == 0) 1 else 0 }
    override fun ejectUsb() {}
}
