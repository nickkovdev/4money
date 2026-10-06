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

package ua.com.radiokot.money.inbox.logic

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.inbox.FakePayeeRuleRepository
import ua.com.radiokot.money.inbox.FakeTransferHistoryRepository
import ua.com.radiokot.money.inbox.data.PayeeRule
import ua.com.radiokot.money.inbox.logic.PayeeRulePatternSuggester.KnownPayee
import ua.com.radiokot.money.inbox.testAccount
import ua.com.radiokot.money.inbox.testCategory
import ua.com.radiokot.money.transfers.data.Transfer
import ua.com.radiokot.money.transfers.data.TransferCounterparty
import java.math.BigInteger

class GetKnownPayeesUseCaseTest {

    private val account = TransferCounterparty.Account(testAccount("card"))
    private val food = TransferCounterparty.Category(testCategory("food"))
    private val salary = TransferCounterparty.Category(testCategory("salary", isIncome = true))

    private fun transfer(
        source: TransferCounterparty,
        destination: TransferCounterparty,
        memo: String?,
    ) = Transfer(
        source = source,
        sourceAmount = BigInteger.TEN,
        destination = destination,
        destinationAmount = BigInteger.TEN,
        dateTime = LocalDateTime(2026, 10, 1, 12, 0),
        memo = memo,
    )

    private val rule = PayeeRule(
        payeePattern = "example cafe",
        matchType = PayeeRule.MatchType.Contains,
        categoryId = "cafe",
        subcategoryId = null,
        accountId = null,
        hits = 1,
        lastUsedAt = null,
    )

    @Test
    fun normalizesMemosAndTakesCategoryFromEitherSide() {
        val useCase = GetKnownPayeesUseCase(
            payeeRuleRepository = FakePayeeRuleRepository(listOf(rule)),
            transferHistoryRepository = FakeTransferHistoryRepository(
                transfers = listOf(
                    transfer(account, food, "MCDONALDS AKROPOLE RIG"),
                    transfer(salary, account, "EXAMPLE EMPLOYER SIA"),
                    transfer(account, food, null),
                    transfer(account, account, "TO MY OTHER CARD"),
                )
            ),
        )

        val known = runBlocking { useCase() }

        Assert.assertEquals(
            setOf(
                KnownPayee("example cafe", "cafe"),
                KnownPayee(PayeeNormalizer.normalize("MCDONALDS AKROPOLE RIG"), "food"),
                KnownPayee(PayeeNormalizer.normalize("EXAMPLE EMPLOYER SIA"), "salary"),
            ),
            known.toSet(),
        )
    }

    @Test
    fun historyFailureFallsBackToRules() {
        val useCase = GetKnownPayeesUseCase(
            payeeRuleRepository = FakePayeeRuleRepository(listOf(rule)),
            transferHistoryRepository = FakeTransferHistoryRepository(
                failWith = IllegalStateException("no history"),
            ),
        )

        val known = runBlocking { useCase() }

        Assert.assertEquals(listOf(KnownPayee("example cafe", "cafe")), known)
    }
}
