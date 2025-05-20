package com.pdf.pdfreader.utiles

fun main() {
    val fonts = arrayOf(
        Font("TextViewUltralight100", "sf_pro_ultralight_100"),
        Font("TextViewThin200", "sf_pro_thin_200"),
        Font("TextViewLight300", "sf_pro_light_300"),
        Font("TextViewRegular400", "sf_pro_regular_400"),
        Font("TextViewMedium500", "sf_pro_medium_500"),
        Font("TextViewSemiBold600", "sf_pro_semibold_600"),
        Font("TextViewBold700", "sf_pro_bold_700"),
        Font("TextViewHeavy800", "sf_pro_heavy_800"),
        Font("TextViewBlack900", "sf_pro_black_900"),
    )
    val colors = arrayOf(StyleColor("Black", "black"), StyleColor("Blue", "blue"))
    val output = StringBuilder()

    fonts.forEach { font ->
        generateFontStyles(output, font)
        generateTextSizeStyles(output, font)
        generateColorStyles(output, font, colors)
    }

    println(output.toString())
}

fun generateFontStyles(output: StringBuilder, font: Font) {
    output.appendLine("<style name=\"${font.name}\" parent=\"Widget.AppCompat.TextView\">")
    output.appendLine("    <item name=\"android:fontFamily\">@font/${font.fontResourceName}</item>")
    output.appendLine("    <item name=\"android:includeFontPadding\">false</item>")
    output.appendLine("</style>")
    output.appendLine()
}

fun generateTextSizeStyles(output: StringBuilder, font: Font) {
    (12..32).forEach { size ->
        output.appendLine("<style name=\"${font.name}.$size\">")
        output.appendLine("    <item name=\"android:textSize\">@dimen/sp_$size</item>")
        output.appendLine("</style>")
        output.appendLine()
    }
}

fun generateColorStyles(output: StringBuilder, font: Font, colors: Array<StyleColor>) {
    (12..32).forEach { size ->
        colors.forEach { color ->
            output.appendLine("<style name=\"${font.name}.$size.${color.name}\">")
            output.appendLine("    <item name=\"android:textColor\">@color/${color.colorResourceName}</item>")
            output.appendLine("</style>")
            output.appendLine()
        }
    }
}

data class Font(val name: String, val fontResourceName: String)
data class StyleColor(val name: String, val colorResourceName: String)
