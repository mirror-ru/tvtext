package com.tvtext.mobile.app

/** The sender's explicit day/night palette; independent of the system theme. */
data class Palette(val dark: Boolean) {
    val ink = if (dark) 0xffebeff9.toInt() else 0xff212c42.toInt()
    val muted = if (dark) 0xffa4aec3.toInt() else 0xff728096.toInt()
    val accent = if (dark) 0xffaab8ff.toInt() else 0xff4969ed.toInt()
    val buttonInk = if (dark) accent else 0xff3d5adb.toInt()
    val primary = if (dark) 0xff4f63d5.toInt() else 0xff4969ed.toInt()
    val canvas = if (dark) 0xff141822.toInt() else 0xfff5f7fc.toInt()
    val surface = if (dark) 0xff1f2533.toInt() else 0xffffffff.toInt()
    val input = if (dark) 0xff191e2a.toInt() else 0xffffffff.toInt()
    val border = if (dark) 0xff3b4457.toInt() else 0xffe6ebf5.toInt()
    val selectedBorder = if (dark) 0xff7185d8.toInt() else 0xffa6b8f9.toInt()
    val row = if (dark) 0xff262d3c.toInt() else 0xfff9fafd.toInt()
    val selected = if (dark) 0xff2d385b.toInt() else 0xffebf0ff.toInt()
    val icon = if (dark) 0xff36415b.toInt() else 0xffebf0fd.toInt()
    val art = if (dark) 0xff242d44.toInt() else 0xffe8eeff.toInt()
    val green = if (dark) 0xff4cbe97.toInt() else 0xff299b77.toInt()
    val button = if (dark) 0xff30394c.toInt() else 0xfff1f4fc.toInt()
    val error = if (dark) 0xfff69c90.toInt() else 0xffba5342.toInt()
    val ripple = if (dark) 0x33aab8ff else 0x224969ed
}
