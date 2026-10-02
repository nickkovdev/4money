/* Copyright 2025 Oleg Koretsky

   This file is part of the 4Money,
   a budget tracking Android app.

   4Money is free software: you can redistribute it
   and/or modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation, either version 3 of the License,
   or (at your option) any later version.

   4Money is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
   See the GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with 4Money. If not, see <http://www.gnu.org/licenses/>.
*/

package ua.com.radiokot.money.uikit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import kotlinx.coroutines.launch
import ua.com.radiokot.money.uikit.theme.LocalMoneyColors

/**
 * Tints a pressed element with a faint ink overlay: the default indication
 * for rows and tiles. Animates only while pressed.
 */
object PressOverlayIndication : IndicationNodeFactory {

    private const val PRESSED_ALPHA = 0.07f

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        Node(interactionSource)

    override fun hashCode(): Int = -2

    override fun equals(other: Any?): Boolean = other === this

    private class Node(
        private val interactionSource: InteractionSource,
    ) : Modifier.Node(),
        DrawModifierNode,
        CompositionLocalConsumerModifierNode {

        private val alpha = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                val presses = mutableSetOf<PressInteraction.Press>()
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> presses.add(interaction)
                        is PressInteraction.Release -> presses.remove(interaction.press)
                        is PressInteraction.Cancel -> presses.remove(interaction.press)
                    }
                    val target = if (presses.isNotEmpty()) PRESSED_ALPHA else 0f
                    if (alpha.targetValue != target) {
                        launch {
                            alpha.animateTo(
                                targetValue = target,
                                animationSpec = tween(
                                    durationMillis =
                                        if (target > 0f)
                                            60
                                        else
                                            180,
                                ),
                            )
                        }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (alpha.value > 0f) {
                drawRect(
                    color = currentValueOf(LocalMoneyColors).ink.copy(alpha = alpha.value),
                )
            }
        }
    }
}
