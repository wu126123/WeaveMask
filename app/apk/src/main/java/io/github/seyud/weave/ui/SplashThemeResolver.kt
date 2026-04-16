package io.github.seyud.weave.ui

import android.os.Build
import io.github.seyud.weave.R
import io.github.seyud.weave.ui.theme.MonetPresetPalette

internal object SplashThemeResolver {

    enum class Kind {
        DEFAULT,
        MONET_SYSTEM,
        MONET_PRESET,
    }

    data class Spec(
        val kind: Kind,
        val dark: Boolean,
        val keyColor: Int,
    )

    fun resolve(
        colorMode: Int,
        keyColor: Int,
        sdkInt: Int = Build.VERSION.SDK_INT,
        isSystemDark: Boolean,
    ): Spec {
        val dark = when (colorMode) {
            2, 5 -> true
            0, 3 -> isSystemDark
            else -> false
        }

        return when (colorMode) {
            3 -> {
                if (MonetPresetPalette.contains(keyColor)) {
                    Spec(kind = Kind.MONET_PRESET, dark = dark, keyColor = keyColor)
                } else if (sdkInt >= Build.VERSION_CODES.S) {
                    Spec(kind = Kind.MONET_SYSTEM, dark = dark, keyColor = 0)
                } else {
                    Spec(kind = Kind.DEFAULT, dark = dark, keyColor = 0)
                }
            }

            4, 5 -> {
                if (MonetPresetPalette.contains(keyColor)) {
                    Spec(kind = Kind.MONET_PRESET, dark = dark, keyColor = keyColor)
                } else {
                    Spec(kind = Kind.DEFAULT, dark = dark, keyColor = 0)
                }
            }

            else -> Spec(kind = Kind.DEFAULT, dark = dark, keyColor = 0)
        }
    }

    fun resolveThemeRes(spec: Spec): Int {
        return when (spec.kind) {
            Kind.DEFAULT -> R.style.Theme_WeaveMagisk_Splash_Default
            Kind.MONET_SYSTEM -> R.style.Theme_WeaveMagisk_Splash_MonetSystem
            Kind.MONET_PRESET -> MonetPresetPalette.splashThemeResFor(spec.keyColor)
                ?: R.style.Theme_WeaveMagisk_Splash_Default
        }
    }

    fun resolveThemeRes(
        colorMode: Int,
        keyColor: Int,
        sdkInt: Int = Build.VERSION.SDK_INT,
        isSystemDark: Boolean,
    ): Int = resolveThemeRes(resolve(colorMode, keyColor, sdkInt, isSystemDark))
}
