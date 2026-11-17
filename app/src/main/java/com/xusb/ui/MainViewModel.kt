package com.xusb.ui

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xusb.usb.UsbDriveInfo
import com.xusb.usb.XUsbManager
import com.xusb.ui.theme.AppTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Context

private val Context.dataStore by preferencesDataStore("xusb_prefs")
private val THEME_KEY   = intPreferencesKey("theme")
private val LANG_KEY    = intPreferencesKey("lang")  // 0=en, 1=es

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val usbManager = XUsbManager(app)

    val connectedDrive: StateFlow<UsbDriveInfo> = usbManager.selectedDrive
    val allDrives: StateFlow<List<UsbDriveInfo>> = usbManager.connectedDrives

    private val _theme = MutableStateFlow(AppTheme.DARK_PURPLE)
    val theme: StateFlow<AppTheme> = _theme

    private val _lang = MutableStateFlow(0) // 0=en, 1=es
    val lang: StateFlow<Int> = _lang

    val isSpanish: Boolean get() = _lang.value == 1

    init {
        usbManager.register()
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { prefs ->
                _theme.value = AppTheme.entries.getOrElse(prefs[THEME_KEY] ?: 0) { AppTheme.DARK_PURPLE }
                _lang.value  = prefs[LANG_KEY] ?: 0
            }
        }
    }

    fun setTheme(t: AppTheme) {
        _theme.value = t
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[THEME_KEY] = t.ordinal }
        }
    }

    fun toggleLang() {
        val next = if (_lang.value == 0) 1 else 0
        _lang.value = next
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[LANG_KEY] = next }
        }
    }

    fun ejectUsb() = usbManager.eject()
    fun selectDrive(drive: com.xusb.usb.UsbDriveInfo) = usbManager.selectDrive(drive)

    override fun onCleared() {
        super.onCleared()
        usbManager.unregister()
    }
}
