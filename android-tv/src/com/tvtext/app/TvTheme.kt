package com.tvtext.app

/** Default to dark; the activity loads and persists the chosen mode. */
object TvTheme {
    var dark = true
        private set
    fun select(value: Boolean) { dark=value }
    val canvas get() = if(dark) 0xff141822.toInt() else 0xfff5f7fc.toInt()
    val surface get() = if(dark) 0xff1f2533.toInt() else 0xffffffff.toInt()
    val inset get() = if(dark) 0xff262d3c.toInt() else 0xfff5f7fc.toInt()
    val border get() = if(dark) 0xff3b4457.toInt() else 0xffe6ebf5.toInt()
    val icon get() = if(dark) 0xff36415b.toInt() else 0xffebf0ff.toInt()
    val ink get() = if(dark) 0xffebeff9.toInt() else 0xff212c42.toInt()
    val muted get() = if(dark) 0xffa4aec3.toInt() else 0xff728096.toInt()
    val accent get() = if(dark) 0xffaab8ff.toInt() else 0xff3d5adb.toInt()
    val primary get() = if(dark) 0xff4f63d5.toInt() else 0xff4969ed.toInt()
    val primaryFocus get() = if(dark) 0xff4455bf.toInt() else 0xff3957da.toInt()
    val primaryPressed get() = if(dark) 0xff3c4fb4.toInt() else 0xff314dcc.toInt()
    val button get() = if(dark) 0xff30394c.toInt() else 0xfff1f4fc.toInt()
    val buttonFocus get() = if(dark) 0xff3d4962.toInt() else 0xffe7edfc.toInt()
    val buttonPressed get() = if(dark) 0xff39445c.toInt() else 0xffe3eafd.toInt()
    val disabled get() = if(dark) 0xff303646.toInt() else 0xffe7ebf7.toInt()
    val green get() = if(dark) 0xff4cbe97.toInt() else 0xff299b77.toInt()
    val amber get() = if(dark) 0xfff4be63.toInt() else 0xffa9701b.toInt()
    val alert get() = if(dark) 0xfff69c90.toInt() else 0xffc84052.toInt()
    val danger get() = 0xffc84052.toInt()
}
