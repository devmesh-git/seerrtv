package ca.devmesh.seerrtv.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.ui.state.FocusArea
import ca.devmesh.seerrtv.viewmodel.SeerrViewModel

/**
 * Action buttons section for MediaDetails screen
 * Displays Play, Request, Manage, and Trailer buttons based on media state
 */
@Composable
fun MediaDetailsActionButtons(
    media: MediaDetails,
    viewModel: SeerrViewModel,
    currentFocusArea: Int,
    actionButtonStates: Map<String, ActionButtonState>,
    canRequestMedia: Boolean,
    has4kCapability: Boolean,
    onFocusChange: (Int) -> Unit,
    context: Context
) {
    Column {
        // Play Button
        if (actionButtonStates["play"]?.isVisible == true) {
            ActionPlayButton(
                isFocused = (currentFocusArea == FocusArea.PLAY),
                onEnter = {
                    onFocusChange(FocusArea.PLAY)
                },
                context = context
            )
        }

        // Request Button - can be single or split based on tier availability
        if (actionButtonStates["request"]?.isVisible == true) {
            Spacer(modifier = Modifier.height(8.dp))
            ActionRequestButton(
                media = media,
                viewModel = viewModel,
                isFocused = (currentFocusArea == FocusArea.REQUEST_HD ||
                        currentFocusArea == FocusArea.REQUEST_4K ||
                        currentFocusArea == FocusArea.REQUEST_SINGLE),
                canRequest = canRequestMedia,
                has4kCapability = has4kCapability,
                currentFocusArea = currentFocusArea,
                context = context,
                leftTier = actionButtonStates["request"]?.leftTier,
                rightTier = actionButtonStates["request"]?.rightTier,
                singleTier = actionButtonStates["request"]?.singleTier
            )
        }

        // Manage Button - can be single or split based on existing requests
        if (actionButtonStates["manage"]?.isVisible == true) {
            Spacer(modifier = Modifier.height(8.dp))
            ActionManageButton(
                isFocused = (currentFocusArea == FocusArea.MANAGE_HD ||
                        currentFocusArea == FocusArea.MANAGE_4K ||
                        currentFocusArea == FocusArea.MANAGE_SINGLE),
                has4kCapability = has4kCapability,
                currentFocusArea = currentFocusArea,
                context = context,
                leftTier = actionButtonStates["manage"]?.leftTier,
                rightTier = actionButtonStates["manage"]?.rightTier,
                singleTier = actionButtonStates["manage"]?.singleTier
            )
        }

        // Trailer Button - always single button when trailer is available
        if (actionButtonStates["trailer"]?.isVisible == true) {
            Spacer(modifier = Modifier.height(8.dp))
            ActionTrailerButton(
                isFocused = (currentFocusArea == FocusArea.TRAILER),
                context = context
            )
        }
    }
}

