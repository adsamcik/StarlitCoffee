package com.adsamcik.starlitcoffee.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = CoffeeBrown,
    onPrimary = OnCoffeeBrown,
    primaryContainer = CoffeeCream,
    onPrimaryContainer = OnCoffeeCream,
    secondary = Caramel,
    onSecondary = OnCaramel,
    secondaryContainer = LightGold,
    onSecondaryContainer = OnLightGold,
    tertiary = WarmRose,
    onTertiary = OnWarmRose,
    tertiaryContainer = SoftPink,
    onTertiaryContainer = OnSoftPink,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = WarmOffWhite,
    onBackground = OnWarmOffWhite,
    surface = WarmOffWhite,
    onSurface = OnWarmOffWhite,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = CoffeeBrownLight,
    onPrimary = OnCoffeeBrownDark,
    primaryContainer = CoffeeCreamDark,
    onPrimaryContainer = OnCoffeeCreamDark,
    secondary = CaramelLight,
    onSecondary = OnCaramelDark,
    secondaryContainer = LightGoldDark,
    onSecondaryContainer = OnLightGoldDark,
    tertiary = WarmRoseLight,
    onTertiary = OnWarmRoseDark,
    tertiaryContainer = SoftPinkDark,
    onTertiaryContainer = OnSoftPinkDark,
    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = DarkCoffee,
    onBackground = OnDarkCoffee,
    surface = DarkCoffee,
    onSurface = OnDarkCoffee,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
)

@Composable
fun StarlitCoffeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
