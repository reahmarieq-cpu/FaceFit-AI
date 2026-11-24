package com.example.facefit.data.shops

import java.util.Locale

/**
 * A frame suits the user's face when its shape (or type) has a compatibility score above the
 * neutral midpoint: the assessors rated that frame category as suitable for the face shape.
 */
internal const val COMPATIBLE_FRAME_SCORE = 50.0

/** [scoreFor] returns the compatibility score (0–100) of a frame category for the user's face. */
internal fun ShopFrame.suitsFace(scoreFor: (String) -> Double?): Boolean =
    listOf(shape, type).filter { it.isNotBlank() }.any { (scoreFor(it) ?: return@any false) > COMPATIBLE_FRAME_SCORE }

/**
 * Nearby-shop order:
 * 1. subscribed shops first (a subscription past its end date no longer counts),
 * 2. then shops that have a frame suiting the user's face,
 * 3. then the nearest shop,
 * 4. non-subscribed shops last (ordered by the same rules among themselves).
 * Name breaks any remaining tie so the list never jumps around.
 */
internal fun rankShops(
    shops: List<OpticalShop>,
    compatibleShopIds: Set<String>,
    nowMs: Long,
    distanceKm: (OpticalShop) -> Double?
): List<OpticalShop> = shops.sortedWith(
    compareBy<OpticalShop>(
        { !it.isSubscribedAt(nowMs) },
        { it.id !in compatibleShopIds },
        { distanceKm(it) ?: Double.POSITIVE_INFINITY },
        { it.name.lowercase(Locale.ROOT) },
        { it.id }
    )
)
