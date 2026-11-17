package com.xusb.desktop

import kotlinx.coroutines.flow.*
import com.xusb.common.ui.theme.AppTheme
import com.xusb.common.IMainViewModel

class DesktopViewModel : IMainViewModel {
    override val theme = MutableStateFlow(AppTheme.FORGE)
    override val lang = MutableStateFlow(1) // ES
    override val isSpanish: Boolean get() = lang.value == 1

    override fun setTheme(t: AppTheme) { theme.value = t }
    override fun toggleLang() { lang.value = if (lang.value == 0) 1 else 0 }
}
