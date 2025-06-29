package com.example.swiftcard.util

object Routes {
    const val HomeScreen = "homeScreen"
    const val AddBusinessCardScreen = "addBusinessCardScreen/{businessCardId}"
    const val EditBusinessCardScreen = "editBusinessCardScreen"
    const val ViewBusinessCardScreen = "viewBusinessCardScreen/{businessCardId}"
    const val ScanBusinessCardScreen = "scanBusinessCardScreen"

    fun createAddEditBusinessCardRoute(businessCardId: String?) =
        "addBusinessCardScreen/${businessCardId ?: "null"}"

    fun createBusinessCardDetailRoute(businessCardId: String) =
        "viewBusinessCardScreen/$businessCardId"
}