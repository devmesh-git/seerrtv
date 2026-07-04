package ca.devmesh.seerrtv.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

class LocaleContextWrapper(base: Context) : ContextWrapper(base) {
    companion object {
        fun wrap(context: Context): ContextWrapper {
            val language = SharedPreferencesUtil.resolveAppLanguage(context)
            val config = context.resources.configuration
            val locale = Locale.forLanguageTag(language)
            Locale.setDefault(locale)
            
            val newConfig = Configuration(config)
            newConfig.setLocale(locale)
            
            val newContext = context.createConfigurationContext(newConfig)
            return LocaleContextWrapper(newContext)
        }
    }
}
