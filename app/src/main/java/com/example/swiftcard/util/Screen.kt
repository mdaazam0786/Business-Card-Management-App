package com.example.swiftcard.util

sealed class Screen(val route: String) {
    object BusinessCardList : Screen("business_card_list")
    object ScanBusinessCard : Screen("scan_business_card")
    object AddEditBusinessCard : Screen("add_edit_business_card/{businessCardId}") {
        fun createRoute(businessCardId: String?) = "add_edit_business_card/${businessCardId ?: "null"}"
    }
    object BusinessCardDetail : Screen("business_card_detail/{businessCardId}") {
        fun createRoute(businessCardId: String) = "business_card_detail/$businessCardId"
    }
}