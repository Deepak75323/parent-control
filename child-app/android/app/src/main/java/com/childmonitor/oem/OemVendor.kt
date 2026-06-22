package com.childmonitor.oem

import android.os.Build

/** Supported OEM vendors for certification and workaround routing. */
enum class OemVendor(val id: String, val displayName: String) {
    XIAOMI("xiaomi", "Xiaomi / HyperOS / MIUI"),
    SAMSUNG("samsung", "Samsung One UI"),
    OPPO("oppo", "Oppo ColorOS"),
    VIVO("vivo", "Vivo Funtouch OS"),
    REALME("realme", "Realme UI"),
    ONEPLUS("oneplus", "OnePlus OxygenOS"),
    MOTOROLA("motorola", "Motorola"),
    PIXEL("pixel", "Google Pixel"),
    GENERIC("generic", "Generic Android"),
    ;

    companion object {
        fun detect(): OemVendor {
            val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
            val brand = Build.BRAND?.lowercase().orEmpty()
            return when {
                manufacturer.contains("xiaomi") ||
                    manufacturer.contains("redmi") ||
                    manufacturer.contains("poco") ||
                    brand.contains("redmi") ||
                    brand.contains("poco") -> XIAOMI
                manufacturer.contains("samsung") -> SAMSUNG
                manufacturer.contains("oppo") -> OPPO
                manufacturer.contains("vivo") -> VIVO
                manufacturer.contains("realme") -> REALME
                manufacturer.contains("oneplus") -> ONEPLUS
                manufacturer.contains("motorola") || manufacturer.contains("lenovo") -> MOTOROLA
                manufacturer.contains("google") || brand.contains("pixel") -> PIXEL
                else -> GENERIC
            }
        }
    }
}
