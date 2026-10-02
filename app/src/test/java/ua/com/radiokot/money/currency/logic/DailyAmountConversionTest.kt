package ua.com.radiokot.money.currency.logic

import org.junit.Assert
import org.junit.Test
import ua.com.radiokot.money.currency.data.Currency
import ua.com.radiokot.money.currency.data.CurrencyPairMap
import java.math.BigDecimal
import java.math.BigInteger

class DailyAmountConversionTest {

    private val usd = Currency(code = "USD", symbol = "$", precision = 2)
    private val eur = Currency(code = "EUR", symbol = "€", precision = 2)
    private val uah = Currency(code = "UAH", symbol = "₴", precision = 2)

    private val prices = mapOf(
        "2026-09-01" to CurrencyPairMap(
            quoteCode = "USD",
            decimalPriceByBaseCode = mapOf(
                "EUR" to BigDecimal("1.10"),
                "UAH" to BigDecimal("0.025"),
            ),
        ),
    )

    @Test
    fun sameCurrency_NoPricesNeeded() {
        Assert.assertEquals(
            BigInteger("1234"),
            convertDailyAmount("2026-09-05", BigInteger("1234"), eur, eur, emptyMap()),
        )
    }

    @Test
    fun convertsWithTheDayPrice() {
        // 10.00 EUR → 11.00 USD
        Assert.assertEquals(
            BigInteger("1100"),
            convertDailyAmount("2026-09-01", BigInteger("1000"), eur, usd, prices),
        )
        // 400.00 UAH → 10.00 USD
        Assert.assertEquals(
            BigInteger("1000"),
            convertDailyAmount("2026-09-01", BigInteger("40000"), uah, usd, prices),
        )
    }

    @Test
    fun fallsBackToThePreviousDay() {
        Assert.assertEquals(
            BigInteger("1100"),
            convertDailyAmount("2026-09-02", BigInteger("1000"), eur, usd, prices),
        )
    }

    @Test
    fun nullWhenNoPrice() {
        Assert.assertNull(
            convertDailyAmount("2026-09-10", BigInteger("1000"), eur, usd, prices),
        )
    }
}
