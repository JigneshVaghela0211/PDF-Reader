package com.pdf.pdfreader.utiles

fun main() {


    val font = arrayOf(
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
    val color = arrayOf(StyleColor("Black", "black"), StyleColor("Blue", "blue"))




    font.forEach { i ->
        println("<style name=\"${i.name}\" parent=\"Widget.AppCompat.TextView\">")
        println("    <item name=\"android:fontFamily\">@font/${i.fontResourceName}</item>")
        println("    <item name=\"android:includeFontPadding\">false</item>")
        println("</style>")
        println()

        (12..33).forEachIndexed { _, j ->
            if (j < 33) {
                println("<style name=\"${i.name}.$j\">")
                println("    <item name=\"android:textSize\">@dimen/sp_${j}</item>")
                println("</style>")
                println()
                return@forEachIndexed

            } else {
                (12..33).forEach { k ->
                    if (k < 33) {
                        color.forEach {color ->
                            println("<style name=\"${i.name}.${k}.${color.name}\">")
                            println("    <item name=\"android:textColor\">@color/${color.colorResourceName}</item>")
                            println("</style>")
                            println()
                        }
                    }
                }

            }
        }
    }
}

data class Font(val name: String, val fontResourceName: String)
data class StyleColor(val name: String, val colorResourceName: String)