package com.facefit.kiosk.model

import java.util.Locale

enum class FaceShape {
    Undetermined, Oval, Round, Square, Heart, Oblong, Diamond;

    companion object {
        /** Use at the external analyzer boundary; unknown labels never default to Oval. */
        fun fromLabel(value: String?): FaceShape = when (
            value?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-')
        ) {
            "oval" -> Oval
            "round" -> Round
            "square" -> Square
            "heart", "heart-shaped", "heart shaped" -> Heart
            "oblong", "rectangle", "rectangular", "elongated" -> Oblong
            "diamond" -> Diamond
            else -> Undetermined
        }
    }
}