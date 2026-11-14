package com.example.malttuk.integration

import org.junit.Assert.*
import org.junit.Test

class VehicleWebPolicyTest {
    @Test fun onlyPackagedAssetsCanUseNativeBridgeOrigin() {
        assertEquals("vehicle-ui/native-vehicle.html",VehicleWebPolicy.assetPath(VehicleWebPolicy.HOME))
        assertEquals("vehicle-ui/assets/main-123.js",VehicleWebPolicy.assetPath("https://appassets.androidplatform.net/assets/vehicle-ui/assets/main-123.js"))
        for(url in listOf(
            "https://untrusted.invalid/assets/vehicle-ui/native-vehicle.html",
            "http://appassets.androidplatform.net/assets/vehicle-ui/native-vehicle.html",
            "https://appassets.androidplatform.net.attacker.invalid/assets/vehicle-ui/native-vehicle.html",
            "https://appassets.androidplatform.net@attacker.invalid/assets/vehicle-ui/native-vehicle.html",
            "https://appassets.androidplatform.net/assets/vehicle-ui/../../secret.html",
            "https://appassets.androidplatform.net/assets/vehicle-ui/%2e%2e/secret.html",
            "https://appassets.androidplatform.net/assets/vehicle-ui/main.js?redirect=elsewhere",
            "https://appassets.androidplatform.net/assets/vehicle-ui/main.js#other",
            "file:///data/data/com.example.malttuk/shared_prefs/session.xml",
            "javascript:alert(1)")) assertNull(url,VehicleWebPolicy.assetPath(url))
    }
}
