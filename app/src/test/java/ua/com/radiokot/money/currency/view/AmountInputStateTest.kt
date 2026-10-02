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

package ua.com.radiokot.money.currency.view

import org.junit.Assert
import org.junit.Test
import java.math.BigInteger
import java.util.Locale

class AmountInputStateTest {

    private val usd = ViewCurrency(symbol = "$", precision = 2)
    private val divide = AmountInputState.Operator.Divide.symbol
    private val multiply = AmountInputState.Operator.Multiply.symbol
    private val minus = AmountInputState.Operator.Minus.symbol
    private val plus = AmountInputState.Operator.Plus.symbol

    private fun state() = AmountInputState(
        currency = usd,
        initialValue = BigInteger.ZERO,
        format = ViewAmountFormat(Locale.ENGLISH),
    )

    private fun AmountInputState.type(vararg symbols: Char) = apply {
        symbols.forEach(::acceptInput)
    }

    @Test
    fun plus() {
        Assert.assertEquals("15 ", state().type('1', '2', plus, '3', '=').inputText)
    }

    @Test
    fun minus_CanGoNegative() {
        Assert.assertEquals("-15 ", state().type('1', '0', minus, '2', '5', '=').inputText)
    }

    @Test
    fun multiply_RespectsPrecision() {
        Assert.assertEquals("3 ", state().type('1', '.', '5', multiply, '2', '=').inputText)
    }

    @Test
    fun divide_RespectsPrecision() {
        Assert.assertEquals("2.5 ", state().type('1', '0', divide, '4', '=').inputText)
    }

    @Test
    fun divideByZero_IsZero() {
        Assert.assertEquals("0 ", state().type('5', divide, '0', '=').inputText)
    }

    @Test
    fun nextOperator_EvaluatesThePrevious() {
        val state = state().type('2', plus, '3', multiply)
        Assert.assertEquals("5 $multiply  ", state.inputText)
        Assert.assertTrue(state.isEvaluationNeeded)
    }

    @Test
    fun backspace_RemovesTheOperator() {
        val state = state().type('7', plus, '⌫')
        Assert.assertEquals("7 ", state.inputText)
        Assert.assertFalse(state.isEvaluationNeeded)
    }
}
