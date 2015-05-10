package org.javamoney.moneta.spi;

import static org.javamoney.moneta.function.MonetaryOperators.rounding;
import static org.testng.Assert.assertEquals;

import java.util.Locale;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RoundedMoneyProducerTest {

	private MonetaryAmountProducer producer;

	private CurrencyUnit currency;

	@BeforeMethod
	public void setup() {
		producer = new RoundedMoneyProducer(rounding());
		currency = Monetary.getCurrency(Locale.getDefault());
	}

	@Test(expectedExceptions = NullPointerException.class)
	public void shouldReturnErrorWhenCurrencyIsNull() {
		producer.create(null, 10);
	}

	@Test(expectedExceptions = NullPointerException.class)
	public void shouldReturnErrorWhenNumberIsNull() {
		producer.create(currency, null);
	}

	@Test
	public void shouldCreateMonetaryAmount() {
		Long value = 10L;
		MonetaryAmount amount = producer.create(currency, value);
		assertEquals(amount.getCurrency(), currency);
		assertEquals(Long.valueOf(amount.getNumber().longValue()), value);

	}

}
