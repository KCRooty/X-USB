package com.xusb.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xusb.common.IMainViewModel
import com.xusb.common.ui.theme.AppTheme
import com.xusb.common.usb.AndroidBackend
import com.xusb.common.usb.UsbDriveInfo
import com.xusb.common.usb.UsbPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("xusb_prefs")
private val THEME_KEY = intPreferencesKey("theme")
private val LANG_KEY = intPreferencesKey("lang") // 0=en, 1=es

/**
 * Estado de la app en Android. Los USB se descubren con el backend (USB Host, sin root);
 * el sistema avisa de conexiones, desconexiones y permisos concedidos para volver a listarlos.
 */
class AndroidViewModel2(app: Application) : AndroidViewModel(app), IMainViewModel {

    /** Se incrementa con cada conexión/desconexión; la UI lo observa para volver a listar. */
    val usbEvents = MutableStateFlow(0)
    private val _theme = MutableStateFlow(AppTheme.FORGE)
    override val theme: StateFlow<AppTheme> = _theme
    private val _lang = MutableStateFlow(1)
    override val lang: StateFlow<Int> = _lang
    override val isSpanish: Boolean get() = _lang.value == 1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) { usbEvents.value += 1 }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(AndroidBackend.ACTION_USB_PERMISSION) // al aceptar el aviso de permiso, se vuelve a listar
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            app.registerReceiver(receiver, filter)

        viewModelScope.launch {
            app.dataStore.data.collect { prefs ->
                _theme.value = AppTheme.entries.getOrElse(prefs[THEME_KEY] ?: 0) { AppTheme.FORGE }
                _lang.value = prefs[LANG_KEY] ?: 1
            }
        }
    }

    override fun setTheme(t: AppTheme) {
        _theme.value = t
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[THEME_KEY] = t.ordinal } }
    }

    override fun toggleLang() {
        val next = if (_lang.value == 0) 1 else 0
        _lang.value = next
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[LANG_KEY] = next } }
    }

    override fun onCleared() {
        try { getApplication<Application>().unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onCleared()
    }
}
