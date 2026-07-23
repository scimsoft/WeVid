package com.scimsoft.wevid.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.scimsoft.wevid.R

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Display / brand — geometric Syne. */
@OptIn(ExperimentalTextApi::class)
val WeVidDisplay = FontFamily(
    variableFont(R.font.syne, FontWeight.Medium),
    variableFont(R.font.syne, FontWeight.Bold),
    variableFont(R.font.syne, FontWeight.Black),
)

/** Body / UI chrome — Figtree. */
@OptIn(ExperimentalTextApi::class)
val WeVidBody = FontFamily(
    variableFont(R.font.figtree, FontWeight.Normal),
    variableFont(R.font.figtree, FontWeight.Medium),
    variableFont(R.font.figtree, FontWeight.SemiBold),
    variableFont(R.font.figtree, FontWeight.Bold),
)
