package com.xusb.common

import kotlinx.coroutines.flow.*
import com.xusb.common.ui.theme.AppTheme

/** Preferencias de la app. Las unidades y las operaciones viven en `ui.state` y van por el backend. */
interface IMainViewModel {
    val theme: StateFlow<AppTheme>
    val lang: StateFlow<Int>          // 0 = en, 1 = es
    val isSpanish: Boolean
    fun setTheme(t: AppTheme)
    fun toggleLang()
}

class MockViewModel : IMainViewModel {
    override val theme = MutableStateFlow(AppTheme.FORGE)
    override val lang = MutableStateFlow(1)
    override val isSpanish get() = lang.value == 1
    override fun setTheme(t: AppTheme) { theme.value = t }
    override fun toggleLang() { lang.value = if (lang.value == 0) 1 else 0 }
}
