package com.example.facefit.data.shops

/** Fictional display-only fixtures. Never written to Firebase or used for real directions. */
object DemoNearbyShops {
    const val latitude=10.3157
    const val longitude=123.8854
    val shops=listOf(
        OpticalShop("demo-clearview","ClearView Optical","Ground Flr, Gaisano Mall",latitude+.0036,longitude,4.8),
        OpticalShop("demo-visionary","Visionary Eyewear","Rizal St, Downtown",latitude+.0108,longitude,4.6),
        OpticalShop("demo-opticare","OptiCare Center","SM City, 2nd Floor",latitude+.0189,longitude,4.5),
        OpticalShop("demo-lens","Lens & Co.","Magsaysay Avenue",latitude+.0269,longitude,4.7)
    )
}
