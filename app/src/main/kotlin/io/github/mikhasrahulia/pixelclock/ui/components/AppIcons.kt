package io.github.mikhasrahulia.pixelclock.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight local Material Rounded vector icons.
 * Eliminates the multi-MB material-icons-extended dependency for faster R8 builds and smaller APK.
 */
object AppIcons {
    private val black = SolidColor(Color.Black)

    private inline fun icon(
        name: String,
        autoMirror: Boolean = false,
        block: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror
    ).apply {
        path(fill = black, pathFillType = PathFillType.NonZero, pathBuilder = block)
    }.build()

    val Add: ImageVector by lazy {
        icon("Add") {
            moveTo(18f, 13f); horizontalLineToRelative(-5f); verticalLineToRelative(5f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f); reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
            verticalLineToRelative(-5f); horizontalLineTo(6f)
            curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f); reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
            horizontalLineToRelative(5f); verticalLineTo(6f)
            curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f); reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
            verticalLineToRelative(5f); horizontalLineToRelative(5f)
            curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f); reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
            close()
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            moveTo(18.3f, 5.71f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
            lineTo(12f, 10.59f); lineTo(7.11f, 5.7f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
            lineTo(10.59f, 12f); lineTo(5.7f, 16.89f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
            lineTo(12f, 13.41f); lineToRelative(4.89f, 4.89f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
            lineTo(13.41f, 12f); lineToRelative(4.89f, -4.89f)
            curveToRelative(0.38f, -0.38f, 0.38f, -1.02f, 0f, -1.4f)
            close()
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft", autoMirror = true) {
            moveTo(14.71f, 15.88f); lineTo(10.83f, 12f); lineToRelative(3.88f, -3.88f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
            lineTo(8.71f, 11.3f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
            lineToRelative(4.59f, 4.59f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
            curveToRelative(0.38f, -0.39f, 0.39f, -1.03f, 0f, -1.42f)
            close()
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight", autoMirror = true) {
            moveTo(9.29f, 15.88f); lineTo(13.17f, 12f); lineTo(9.29f, 8.12f)
            curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0f, -1.41f)
            curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0f)
            lineToRelative(4.59f, 4.59f)
            curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0f, 1.41f)
            lineTo(10.7f, 17.3f)
            curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0f)
            curveToRelative(-0.38f, -0.39f, -0.39f, -1.03f, 0f, -1.42f)
            close()
        }
    }

    val Alarm: ImageVector by lazy {
        icon("Alarm") {
            moveTo(12f, 4f); curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            reflectiveCurveToRelative(4.03f, 9f, 9f, 9f); reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
            reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f); close()
            moveTo(12f, 20f); curveToRelative(-3.86f, 0f, -7f, -3.14f, -7f, -7f)
            reflectiveCurveToRelative(3.14f, -7f, 7f, -7f); reflectiveCurveToRelative(7f, 3.14f, 7f, 7f)
            reflectiveCurveToRelative(-3.14f, 7f, -7f, 7f); close()
            moveTo(12.5f, 8f); horizontalLineTo(11f); verticalLineToRelative(6f)
            lineToRelative(4.75f, 2.85f); lineToRelative(0.75f, -1.23f); lineToRelative(-4f, -2.37f); close()
            moveTo(7.88f, 3.39f); lineTo(6.6f, 1.86f); lineTo(2f, 5.71f); lineToRelative(1.29f, 1.53f); close()
            moveTo(22f, 5.72f); lineToRelative(-4.6f, -3.86f); lineToRelative(-1.29f, 1.53f); lineToRelative(4.6f, 3.86f); close()
        }
    }

    val Schedule: ImageVector by lazy {
        icon("Schedule") {
            moveTo(11.99f, 2f); curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f); curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 11.99f, 2f); close()
            moveTo(12f, 20f); curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
            reflectiveCurveToRelative(3.58f, -8f, 8f, -8f); reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
            reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f); close()
            moveTo(12.5f, 7f); horizontalLineTo(11f); verticalLineToRelative(6f)
            lineToRelative(5.25f, 3.15f); lineToRelative(0.75f, -1.23f); lineToRelative(-4.5f, -2.67f); close()
        }
    }

    val Keyboard: ImageVector by lazy {
        icon("Keyboard") {
            moveTo(20f, 5f); horizontalLineTo(4f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(10f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            moveTo(11f, 8f); horizontalLineToRelative(2f); verticalLineToRelative(2f); horizontalLineToRelative(-2f); close()
            moveTo(11f, 11f); horizontalLineToRelative(2f); verticalLineToRelative(2f); horizontalLineToRelative(-2f); close()
            moveTo(8f, 8f); horizontalLineToRelative(2f); verticalLineToRelative(2f); horizontalLineTo(8f); close()
            moveTo(8f, 11f); horizontalLineToRelative(2f); verticalLineToRelative(2f); horizontalLineTo(8f); close()
            moveTo(7f, 13f); horizontalLineTo(5f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
            moveTo(7f, 10f); horizontalLineTo(5f); verticalLineTo(8f); horizontalLineToRelative(2f); close()
            moveTo(16f, 17f); horizontalLineTo(8f); verticalLineToRelative(-2f); horizontalLineToRelative(8f); close()
            moveTo(16f, 13f); horizontalLineToRelative(-2f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
            moveTo(16f, 10f); horizontalLineToRelative(-2f); verticalLineTo(8f); horizontalLineToRelative(2f); close()
            moveTo(19f, 13f); horizontalLineToRelative(-2f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
            moveTo(19f, 10f); horizontalLineToRelative(-2f); verticalLineTo(8f); horizontalLineToRelative(2f); close()
        }
    }

    val CalendarMonth: ImageVector by lazy {
        icon("CalendarMonth") {
            moveTo(19f, 4f); horizontalLineToRelative(-1f); verticalLineTo(2f); horizontalLineToRelative(-2f)
            verticalLineToRelative(2f); horizontalLineTo(8f); verticalLineTo(2f); horizontalLineTo(6f)
            verticalLineToRelative(2f); horizontalLineTo(5f); curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f); curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            moveTo(19f, 20f); horizontalLineTo(5f); verticalLineTo(10f); horizontalLineToRelative(14f); close()
            moveTo(9f, 14f); horizontalLineTo(7f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
            moveTo(13f, 14f); horizontalLineToRelative(-2f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
            moveTo(17f, 14f); horizontalLineToRelative(-2f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
            moveTo(9f, 18f); horizontalLineTo(7f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
            moveTo(13f, 18f); horizontalLineToRelative(-2f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
            moveTo(17f, 18f); horizontalLineToRelative(-2f); verticalLineToRelative(-2f); horizontalLineToRelative(2f); close()
        }
    }

    val PauseCircle: ImageVector by lazy {
        icon("PauseCircle") {
            moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f); reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f); close()
            moveTo(10f, 16f); curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
            verticalLineTo(9f); curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
            reflectiveCurveToRelative(1f, 0.45f, 1f, 1f); verticalLineToRelative(6f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f); close()
            moveTo(14f, 16f); curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
            verticalLineTo(9f); curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
            reflectiveCurveToRelative(1f, 0.45f, 1f, 1f); verticalLineToRelative(6f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f); close()
        }
    }

    val Label: ImageVector by lazy {
        icon("Label", autoMirror = true) {
            moveTo(17.63f, 5.84f); curveTo(17.27f, 5.33f, 16.67f, 5f, 16f, 5f)
            lineTo(5f, 5.01f); curveTo(3.9f, 5.01f, 3f, 5.9f, 3f, 7f)
            verticalLineToRelative(10f); curveToRelative(0f, 1.1f, 0.9f, 1.99f, 2f, 1.99f)
            lineTo(16f, 19f); curveToRelative(0.67f, 0f, 1.27f, -0.33f, 1.63f, -0.84f)
            lineToRelative(3.96f, -5.58f); curveToRelative(0.25f, -0.35f, 0.25f, -0.81f, 0f, -1.16f)
            lineToRelative(-3.96f, -5.58f); close()
        }
    }

    val Vibration: ImageVector by lazy {
        icon("Vibration") {
            moveTo(1f, 15f); curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-4f); curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f); verticalLineToRelative(4f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f); close()
            moveTo(4f, 17f); curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineTo(8f); curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f); verticalLineToRelative(8f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f); close()
            moveTo(23f, 9f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            verticalLineToRelative(4f); curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); verticalLineToRelative(-4f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f); close()
            moveTo(20f, 7f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            verticalLineToRelative(8f); curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); verticalLineTo(8f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f); close()
            moveTo(16.5f, 3f); horizontalLineToRelative(-9f); curveTo(6.67f, 3f, 6f, 3.67f, 6f, 4.5f)
            verticalLineToRelative(15f); curveToRelative(0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
            horizontalLineToRelative(9f); curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
            verticalLineToRelative(-15f); curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f); close()
            moveTo(16f, 19f); horizontalLineTo(8f); verticalLineTo(5f); horizontalLineToRelative(8f); close()
        }
    }

    val Edit: ImageVector by lazy {
        icon("Edit") {
            moveTo(3f, 17.46f); verticalLineToRelative(3.04f); curveToRelative(0f, 0.28f, 0.22f, 0.5f, 0.5f, 0.5f)
            horizontalLineToRelative(3.04f); curveToRelative(0.13f, 0f, 0.26f, -0.05f, 0.35f, -0.15f)
            lineTo(17.81f, 9.94f); lineToRelative(-3.75f, -3.75f); lineTo(3.15f, 17.1f)
            curveToRelative(-0.1f, 0.1f, -0.15f, 0.22f, -0.15f, 0.36f); close()
            moveTo(20.71f, 7.04f); curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
            lineToRelative(-2.34f, -2.34f); curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
            lineToRelative(-1.83f, 1.83f); lineToRelative(3.75f, 3.75f); lineToRelative(1.83f, -1.83f); close()
        }
    }

    val PlayArrow: ImageVector by lazy {
        icon("PlayArrow") {
            moveTo(8f, 6.82f); verticalLineToRelative(10.36f)
            curveToRelative(0f, 0.79f, 0.87f, 1.27f, 1.54f, 0.84f)
            lineToRelative(8.14f, -5.18f)
            curveToRelative(0.62f, -0.39f, 0.62f, -1.29f, 0f, -1.69f)
            lineTo(9.54f, 5.98f)
            curveTo(8.87f, 5.55f, 8f, 6.03f, 8f, 6.82f); close()
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            moveTo(8f, 19f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f); verticalLineToRelative(10f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); close()
            moveTo(14f, 7f); verticalLineToRelative(10f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f); verticalLineTo(7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f); close()
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            moveTo(8f, 6f); horizontalLineToRelative(8f); curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
            verticalLineToRelative(8f); curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
            horizontalLineTo(8f); curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
            verticalLineTo(8f); curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f); close()
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8f)
            reflectiveCurveToRelative(3.57f, 8f, 7.99f, 8f)
            curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
            horizontalLineToRelative(-2.08f)
            curveToRelative(-0.82f, 2.33f, -3.04f, 4f, -5.65f, 4f)
            curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
            reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
            curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
            lineTo(13f, 11f); horizontalLineToRelative(7f); verticalLineTo(4f); lineToRelative(-2.35f, 2.35f); close()
        }
    }

    val Backspace: ImageVector by lazy {
        icon("Backspace", autoMirror = true) {
            moveTo(22f, 3f); horizontalLineTo(7f); curveToRelative(-0.69f, 0f, -1.23f, 0.35f, -1.59f, 0.88f)
            lineTo(0f, 12f); lineToRelative(5.41f, 8.11f); curveToRelative(0.36f, 0.53f, 0.9f, 0.89f, 1.59f, 0.89f)
            horizontalLineToRelative(15f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(5f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            moveTo(18.3f, 16.3f); curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0f)
            lineTo(14f, 13.41f); lineToRelative(-2.89f, 2.89f)
            curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0f)
            curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0f, -1.41f)
            lineTo(12.59f, 12f); lineTo(9.7f, 9.11f)
            curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0f, -1.41f)
            curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0f)
            lineTo(14f, 10.59f); lineToRelative(2.89f, -2.89f)
            curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0f)
            curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0f, 1.41f)
            lineTo(15.41f, 12f); lineToRelative(2.89f, 2.89f)
            curveToRelative(0.38f, 0.38f, 0.38f, 1.02f, 0f, 1.41f); close()
        }
    }

    val HourglassBottom: ImageVector by lazy {
        icon("HourglassBottom") {
            moveTo(16f, 22f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineToRelative(-3.17f); curveToRelative(0f, -0.53f, -0.21f, -1.04f, -0.59f, -1.42f)
            lineTo(14f, 12f); lineToRelative(3.41f, -3.41f)
            curveToRelative(0.38f, -0.38f, 0.59f, -0.89f, 0.59f, -1.42f)
            verticalLineTo(4f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineTo(8f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(3.17f); curveToRelative(0f, 0.53f, 0.21f, 1.04f, 0.59f, 1.42f)
            lineTo(10f, 12f); lineToRelative(-3.41f, 3.41f)
            curveToRelative(-0.38f, 0.38f, -0.59f, 0.89f, -0.59f, 1.42f)
            verticalLineTo(20f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); close()
            moveTo(8f, 7.5f); verticalLineTo(4f); horizontalLineToRelative(8f); verticalLineToRelative(3.5f)
            lineToRelative(-4f, 4f); close()
        }
    }

    val Snooze: ImageVector by lazy {
        icon("Snooze") {
            moveTo(7.88f, 3.39f); lineTo(6.6f, 1.86f); lineTo(2f, 5.71f); lineToRelative(1.29f, 1.53f); close()
            moveTo(22f, 5.72f); lineToRelative(-4.6f, -3.86f); lineToRelative(-1.29f, 1.53f); lineToRelative(4.6f, 3.86f); close()
            moveTo(12f, 4f); curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            reflectiveCurveToRelative(4.02f, 9f, 9f, 9f); curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
            reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f); close()
            moveTo(12f, 20f); curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
            reflectiveCurveToRelative(3.13f, -7f, 7f, -7f); reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
            reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f); close()
            moveTo(9f, 11f); horizontalLineToRelative(3.63f); lineTo(9f, 15.2f); verticalLineTo(17f)
            horizontalLineToRelative(6f); verticalLineToRelative(-2f); horizontalLineToRelative(-3.63f)
            lineTo(15f, 10.8f); verticalLineTo(9f); horizontalLineTo(9f); close()
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            moveTo(9f, 16.17f); lineTo(5.53f, 12.7f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
            lineToRelative(4.18f, 4.18f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
            lineTo(20.29f, 7.71f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
            lineTo(9f, 16.17f); close()
        }
    }

    val Delete: ImageVector by lazy {
        icon("Delete") {
            moveTo(6f, 19f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(8f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(9f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineTo(8f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(10f); close()
            moveTo(18f, 4f); horizontalLineToRelative(-2.5f); lineToRelative(-0.71f, -0.71f)
            curveToRelative(-0.18f, -0.18f, -0.44f, -0.29f, -0.7f, -0.29f)
            horizontalLineTo(9.91f); curveToRelative(-0.26f, 0f, -0.52f, 0.11f, -0.7f, 0.29f)
            lineTo(8.5f, 4f); horizontalLineTo(6f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); horizontalLineToRelative(12f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
            close()
        }
    }

    val Repeat: ImageVector by lazy {
        icon("Repeat") {
            moveTo(7f, 7f); horizontalLineToRelative(10f); verticalLineToRelative(1.79f)
            curveToRelative(0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
            lineToRelative(2.79f, -2.79f); curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0f, -0.71f)
            lineToRelative(-2.79f, -2.79f); curveToRelative(-0.31f, -0.31f, -0.85f, -0.09f, -0.85f, 0.36f)
            verticalLineTo(5f); horizontalLineTo(6f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
            verticalLineToRelative(4f); curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); verticalLineTo(7f); close()
            moveTo(17f, 17f); horizontalLineTo(7f); verticalLineToRelative(-1.79f)
            curveToRelative(0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
            lineToRelative(-2.79f, 2.79f); curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0f, 0.71f)
            lineToRelative(2.79f, 2.79f); curveToRelative(0.31f, 0.31f, 0.85f, 0.09f, 0.85f, -0.36f)
            verticalLineTo(19f); horizontalLineToRelative(11f); curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-4f); curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f); verticalLineToRelative(3f); close()
        }
    }

    val MoreVert: ImageVector by lazy {
        icon("MoreVert") {
            moveTo(12f, 8f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f); reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f); close()
            moveTo(12f, 10f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f); reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f); close()
            moveTo(12f, 16f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f); reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f); close()
        }
    }
}
