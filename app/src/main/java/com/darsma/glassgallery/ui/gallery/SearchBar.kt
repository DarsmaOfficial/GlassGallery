package com.darsma.glassgallery.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.liquidGlassBorder

private val FieldShape = RoundedCornerShape(16.dp)

/**
 * An expanding search field. Collapsed it's nothing; expanded it springs open
 * to full width with a glass body, auto-focuses, and offers a clear button.
 */
@Composable
fun GallerySearchBar(
    expanded: Boolean,
    query: String,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        enter   = expandHorizontally(Motion.expressive(), expandFrom = Alignment.End) +
                  fadeIn(Motion.standard()),
        exit    = shrinkHorizontally(Motion.standard(), shrinkTowards = Alignment.End) +
                  fadeOut(Motion.snappy()),
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(FieldShape)
                .background(Color.White.copy(alpha = 0.07f))
                .liquidGlassBorder(FieldShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Rounded.Search,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.55f),
                modifier           = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text     = "Search videos…",
                        color    = Color.White.copy(alpha = 0.40f),
                        fontSize = 15.sp,
                    )
                }
                BasicTextField(
                    value         = query,
                    onValueChange = onQueryChange,
                    singleLine    = true,
                    textStyle     = LocalTextStyle.current.copy(
                        color    = Color.White,
                        fontSize = 15.sp,
                    ),
                    cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier      = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            // Live result count.
            if (query.isNotEmpty()) {
                Text(
                    text       = "$resultCount",
                    color      = MaterialTheme.colorScheme.primary,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(horizontal = 6.dp),
                )
            }
            BouncyIconButton(
                onClick    = onClose,
                size       = 28.dp,
                background = Color.White.copy(alpha = 0.10f),
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Close,
                    contentDescription = "Close search",
                    tint               = Color.White,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}
