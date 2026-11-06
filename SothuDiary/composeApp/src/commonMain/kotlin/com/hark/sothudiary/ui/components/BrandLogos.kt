package com.hark.sothudiary.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.SourcePlatform

/*
 * Official brand marks as vectors (paths from Simple Icons, CC0 – https://simpleicons.org), drawn in brand colours.
 * Use them small (16–24 dp) as source badges; never recolour.
 */
private const val IG_PATH = "M 7.0301 .084 c -1.2768 .0602 -2.1487 .264 -2.911 .5634 -.7888 .3075 -1.4575 .72 -2.1228 1.3877 -.6652 .6677 -1.075 1.3368 -1.3802 2.127 -.2954 .7638 -.4956 1.6365 -.552 2.914 -.0564 1.2775 -.0689 1.6882 -.0626 4.947 .0062 3.2586 .0206 3.6671 .0825 4.9473 .061 1.2765 .264 2.1482 .5635 2.9107 .308 .7889 .72 1.4573 1.388 2.1228 .6679 .6655 1.3365 1.0743 2.1285 1.38 .7632 .295 1.6361 .4961 2.9134 .552 1.2773 .056 1.6884 .069 4.9462 .0627 3.2578 -.0062 3.668 -.0207 4.9478 -.0814 1.28 -.0607 2.147 -.2652 2.9098 -.5633 .7889 -.3086 1.4578 -.72 2.1228 -1.3881 .665 -.6682 1.0745 -1.3378 1.3795 -2.1284 .2957 -.7632 .4966 -1.636 .552 -2.9124 .056 -1.2809 .0692 -1.6898 .063 -4.948 -.0063 -3.2583 -.021 -3.6668 -.0817 -4.9465 -.0607 -1.2797 -.264 -2.1487 -.5633 -2.9117 -.3084 -.7889 -.72 -1.4568 -1.3876 -2.1228 C 21.2982 1.33 20.628 .9208 19.8378 .6165 19.074 .321 18.2017 .1197 16.9244 .0645 15.6471 .0093 15.236 -.005 11.977 .0014 8.718 .0076 8.31 .0215 7.0301 .0839 m .1402 21.6932 c -1.17 -.0509 -1.8053 -.2453 -2.2287 -.408 -.5606 -.216 -.96 -.4771 -1.3819 -.895 -.422 -.4178 -.6811 -.8186 -.9 -1.378 -.1644 -.4234 -.3624 -1.058 -.4171 -2.228 -.0595 -1.2645 -.072 -1.6442 -.079 -4.848 -.007 -3.2037 .0053 -3.583 .0607 -4.848 .05 -1.169 .2456 -1.805 .408 -2.2282 .216 -.5613 .4762 -.96 .895 -1.3816 .4188 -.4217 .8184 -.6814 1.3783 -.9003 .423 -.1651 1.0575 -.3614 2.227 -.4171 1.2655 -.06 1.6447 -.072 4.848 -.079 3.2033 -.007 3.5835 .005 4.8495 .0608 1.169 .0508 1.8053 .2445 2.228 .408 .5608 .216 .96 .4754 1.3816 .895 .4217 .4194 .6816 .8176 .9005 1.3787 .1653 .4217 .3617 1.056 .4169 2.2263 .0602 1.2655 .0739 1.645 .0796 4.848 .0058 3.203 -.0055 3.5834 -.061 4.848 -.051 1.17 -.245 1.8055 -.408 2.2294 -.216 .5604 -.4763 .96 -.8954 1.3814 -.419 .4215 -.8181 .6811 -1.3783 .9 -.4224 .1649 -1.0577 .3617 -2.2262 .4174 -1.2656 .0595 -1.6448 .072 -4.8493 .079 -3.2045 .007 -3.5825 -.006 -4.848 -.0608 M 16.953 5.5864 A 1.44 1.44 0 1 0 18.39 4.144 a 1.44 1.44 0 0 0 -1.437 1.4424 M 5.8385 12.012 c .0067 3.4032 2.7706 6.1557 6.173 6.1493 3.4026 -.0065 6.157 -2.7701 6.1506 -6.1733 -.0065 -3.4032 -2.771 -6.1565 -6.174 -6.1498 -3.403 .0067 -6.156 2.771 -6.1496 6.1738 M 8 12.0077 a 4 4 0 1 1 4.008 3.9921 A 3.9996 3.9996 0 0 1 8 12.0077"
private const val FB_PATH = "M 9.101 23.691 v -7.98 H 6.627 v -3.667 h 2.474 v -1.58 c 0 -4.085 1.848 -5.978 5.858 -5.978 .401 0 .955 .042 1.468 .103 a 8.68 8.68 0 0 1 1.141 .195 v 3.325 a 8.623 8.623 0 0 0 -.653 -.036 26.805 26.805 0 0 0 -.733 -.009 c -.707 0 -1.259 .096 -1.675 .309 a 1.686 1.686 0 0 0 -.679 .622 c -.258 .42 -.374 .995 -.374 1.752 v 1.297 h 3.919 l -.386 2.103 -.287 1.564 h -3.246 v 8.245 C 19.396 23.238 24 18.179 24 12.044 c 0 -6.627 -5.373 -12 -12 -12 s -12 5.373 -12 12 c 0 5.628 3.874 10.35 9.101 11.647 Z"
private const val YT_PATH = "M 23.498 6.186 a 3.016 3.016 0 0 0 -2.122 -2.136 C 19.505 3.545 12 3.545 12 3.545 s -7.505 0 -9.377 .505 A 3.017 3.017 0 0 0 .502 6.186 C 0 8.07 0 12 0 12 s 0 3.93 .502 5.814 a 3.016 3.016 0 0 0 2.122 2.136 c 1.871 .505 9.376 .505 9.376 .505 s 7.505 0 9.377 -.505 a 3.015 3.015 0 0 0 2.122 -2.136 C 24 15.93 24 12 24 12 s 0 -3.93 -.502 -5.814 z M 9.545 15.568 V 8.432 L 15.818 12 l -6.273 3.568 z"
private const val WA_PATH = "M 17.472 14.382 c -.297 -.149 -1.758 -.867 -2.03 -.967 -.273 -.099 -.471 -.148 -.67 .15 -.197 .297 -.767 .966 -.94 1.164 -.173 .199 -.347 .223 -.644 .075 -.297 -.15 -1.255 -.463 -2.39 -1.475 -.883 -.788 -1.48 -1.761 -1.653 -2.059 -.173 -.297 -.018 -.458 .13 -.606 .134 -.133 .298 -.347 .446 -.52 .149 -.174 .198 -.298 .298 -.497 .099 -.198 .05 -.371 -.025 -.52 -.075 -.149 -.669 -1.612 -.916 -2.207 -.242 -.579 -.487 -.5 -.669 -.51 -.173 -.008 -.371 -.01 -.57 -.01 -.198 0 -.52 .074 -.792 .372 -.272 .297 -1.04 1.016 -1.04 2.479 0 1.462 1.065 2.875 1.213 3.074 .149 .198 2.096 3.2 5.077 4.487 .709 .306 1.262 .489 1.694 .625 .712 .227 1.36 .195 1.871 .118 .571 -.085 1.758 -.719 2.006 -1.413 .248 -.694 .248 -1.289 .173 -1.413 -.074 -.124 -.272 -.198 -.57 -.347 m -5.421 7.403 h -.004 a 9.87 9.87 0 0 1 -5.031 -1.378 l -.361 -.214 -3.741 .982 .998 -3.648 -.235 -.374 a 9.86 9.86 0 0 1 -1.51 -5.26 c .001 -5.45 4.436 -9.884 9.888 -9.884 2.64 0 5.122 1.03 6.988 2.898 a 9.825 9.825 0 0 1 2.893 6.994 c -.003 5.45 -4.437 9.884 -9.885 9.884 m 8.413 -18.297 A 11.815 11.815 0 0 0 12.05 0 C 5.495 0 .16 5.335 .157 11.892 c 0 2.096 .547 4.142 1.588 5.945 L .057 24 l 6.305 -1.654 a 11.882 11.882 0 0 0 5.683 1.448 h .005 c 6.554 0 11.89 -5.335 11.893 -11.893 a 11.821 11.821 0 0 0 -3.48 -8.413 Z"

private fun logo(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

object BrandLogos {
    val YouTube: ImageVector by lazy {
        logo("YouTube") {
            // white play triangle under the red body (the triangle is a hole in the official mark)
            addPath(addPathNodes("M8.6 7.2 L17 12 L8.6 16.8 Z"), fill = SolidColor(Color.White))
            addPath(addPathNodes(YT_PATH), fill = SolidColor(Color(0xFFFF0000)))
        }
    }
    val Instagram: ImageVector by lazy {
        logo("Instagram") {
            addPath(
                addPathNodes(IG_PATH),
                fill = Brush.radialGradient(
                    0f to Color(0xFFFFD600), 0.25f to Color(0xFFFF7A00), 0.5f to Color(0xFFFF0069), 0.75f to Color(0xFFD300C5), 1f to Color(0xFF7638FA),
                    center = Offset(4f, 24f), radius = 30f,
                ),
            )
        }
    }
    val Facebook: ImageVector by lazy {
        logo("Facebook") {
            // white disc behind the blue mark so the cut-out "f" reads white (the f is cut out of the official mark)
            addPath(addPathNodes("M12 0.2 A11.8 11.8 0 1 0 12 23.8 A11.8 11.8 0 1 0 12 0.2 Z"), fill = SolidColor(Color.White))
            addPath(addPathNodes(FB_PATH), fill = SolidColor(Color(0xFF0866FF)))
        }
    }
    val WhatsApp: ImageVector by lazy {
        logo("WhatsApp") {
            addPath(addPathNodes("M12 2.2 A9.8 9.8 0 1 0 12 21.8 A9.8 9.8 0 1 0 12 2.2 Z"), fill = SolidColor(Color.White))
            addPath(addPathNodes(WA_PATH), fill = SolidColor(Color(0xFF25D366)))
        }
    }
}

/** The brand mark of a source, or null for "Others" / none. */
fun brandLogo(p: SourcePlatform): ImageVector? = when (p) {
    SourcePlatform.YOUTUBE -> BrandLogos.YouTube
    SourcePlatform.INSTAGRAM -> BrandLogos.Instagram
    SourcePlatform.FACEBOOK -> BrandLogos.Facebook
    SourcePlatform.WHATSAPP -> BrandLogos.WhatsApp
    else -> null
}
