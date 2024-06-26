/*
  Copyright (c) 2012, 2023, Werner Keil and others by the @author tag.

  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy of
  the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  License for the specific language governing permissions and limitations under
  the License.
 */
package org.javamoney.moneta.format;

import org.javamoney.moneta.FastMoney;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.RoundedMoney;
import org.javamoney.moneta.spi.MonetaryConfig;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.money.Monetary;
import javax.money.MonetaryContext;
import javax.money.format.AmountFormatContext;
import javax.money.format.AmountFormatContextBuilder;
import javax.money.format.MonetaryAmountFormat;
import javax.money.format.MonetaryParseException;

/**
 * <p>
 * Class to format and parse a text string such as 'EUR 25.25' or vice versa using BigDecimal default formatting.
 * This class will used as default by toString and parse in all implementation on Moneta.
 *
 * By default this formatter formats the amounts as {@code AMOUNT.DECIMAL CURRENCY}, e.g. {code 1234 CHF}.
 * Hereby the currency is represented by its code and the amount formatted as BigDecimal, using its scale.
 * The decimal separator is always a dot.
 * </p>
 * <br>
 * If the amount contains a {@link MonetaryContext} its {@code maxScale} and {@code fixedScale} attributes allow to configure the output,
 * by checking them against the {@code scale} of the BigDecimal.
 * <br>For example:
 * <pre><code>
 * Money money1 = Money.of(1234567.3456, "EUR");
 * System.out.println(money1.toString());</code></pre>
 * prints "EUR 1234567.3456", while:<br>
 * <pre><code>
 * Money money2 = Money.of(1234567.3456, "EUR", MonetaryContextBuilder.of().setMaxScale(2).build());
 * System.out.println(money2.toString());</code></pre>
 * prints "EUR 1234567.34". And:
 * <pre><code>
 * Money money3 = Money.of(1234567.3, "EUR", MonetaryContextBuilder.of().setMaxScale(2).setFixedScale(true).build());
 * System.out.println(money2.toString());</code></pre>
 * prints "EUR 1234567.30".
 * <br><br>
 * You can configure the order of formatting with the {@code org.javamoney.toStringFormatOrder} configuration property:
 *
 * <ul>
 *     <li>Ordering of <b>CURRENCY AMOUNT</b> can be configured with values equal to {@code 'ca', 'c-a', 'c a'}.</li>
 *     <li>Ordering of <b>AMOUNT CURRENCY</b> can be configured with values equal to {@code 'ac', 'a-c', 'a c'} or
 *     any other (default).</li>
 * </ul>
 *
 * Parsing should work either with the currency prefixed or postfixed.
 *
 * <b>Note:</b> This formatter is active by default, but can be replaced with the standard JDK formatter by setting the
 * {@code org.javamoney.moneta.useJDKdefaultFormat} configuration property to {@code true}.
 *
 * {@link Money#toString()}
 * {@link Money#parse(CharSequence)}
 * {@link FastMoney#toString()}
 * {@link FastMoney#parse(CharSequence)}
 * {@link RoundedMoney#toString()}
 * {@link RoundedMoney#parse(CharSequence)}
 * @author Otavio Santana
 * @author Anatole Tresch
 * @author Werner Keil
 * @see MonetaryContext
 * @version 1.8
 */
public final class ToStringMonetaryAmountFormat implements MonetaryAmountFormat {

    private static final String CONTEXT_PREFIX = "ToString_";

    private static final ToStringMonetaryAmountFormat INSTANCE_FASTMONEY = new ToStringMonetaryAmountFormat(ToStringMonetaryAmountFormatStyle.FAST_MONEY);
    private static final ToStringMonetaryAmountFormat INSTANCE_MONEY = new ToStringMonetaryAmountFormat(ToStringMonetaryAmountFormatStyle.MONEY);
    private static final ToStringMonetaryAmountFormat INSTANCE_ROUNDEDMONEY = new ToStringMonetaryAmountFormat(ToStringMonetaryAmountFormatStyle.ROUNDED_MONEY);

    private final ToStringMonetaryAmountFormatStyle style;

    private final AmountFormatContext context;

    private ToStringMonetaryAmountFormat(ToStringMonetaryAmountFormatStyle style) {
        this.style = Objects.requireNonNull(style);
        context = AmountFormatContextBuilder.of(CONTEXT_PREFIX + style).build();
    }

    public static ToStringMonetaryAmountFormat of(
            ToStringMonetaryAmountFormatStyle style) {
        switch(style){
        case FAST_MONEY:
            return INSTANCE_FASTMONEY;
            case ROUNDED_MONEY:
                return INSTANCE_ROUNDEDMONEY;
            case MONEY:
            default:
                return INSTANCE_MONEY;
        }
    }

    @Override
    public String queryFrom(MonetaryAmount amount) {
		return Optional.ofNullable(amount).map((m) -> {
            BigDecimal dec = amount.getNumber().numberValue(BigDecimal.class);
            final int maxScale = amount.getContext().getMaxScale();
            int scale = 2;
            if (amount instanceof FastMoney) {
                scale = ((maxScale == -1) ? dec.scale() : ((dec.scale() < maxScale) ? dec.scale() : maxScale));
            } else {
                if (amount.getContext().isFixedScale()) {
                    scale = ((maxScale == -1) ? dec.scale() : maxScale);
                } else {
                    scale = ((maxScale == -1) ? dec.scale() : ((dec.scale() < maxScale) ? dec.scale() : maxScale));
                }
            }
            dec = dec.setScale(scale, RoundingMode.HALF_UP);
            String order = MonetaryConfig.getString("org.javamoney.toStringFormatOrder").orElse("ca");
            switch(order){
                case "amount-currency":
                case "amount currency":
                case "ac":
                case "a c":
                case "a-c":
                    return dec.toPlainString() + " " + m.getCurrency().getCurrencyCode();
                case "currency-amount":
                case "currency amount":
                case "ca":
                case "c a":
                case "c-a":
                default:
                    return m.getCurrency().getCurrencyCode() + " " + dec.toPlainString();
            }
        }).orElse("null");
    }

    @Override
    public AmountFormatContext getContext() {
        return context;
    }

    @Override
    public void print(Appendable appendable, MonetaryAmount amount)
            throws IOException {
        appendable.append(queryFrom(amount));

    }

    @Override
    public MonetaryAmount parse(CharSequence text)
            throws MonetaryParseException {
		try {
			ParserMonetaryAmount amount = parserMonetaryAmount(text);
			return style.to(amount);
		} catch (Exception e) {
			throw new MonetaryParseException(e.getMessage(), text, 0);
		}
    }

    private ParserMonetaryAmount parserMonetaryAmount(CharSequence text) {
        String[] array = Objects.requireNonNull(text).toString().split(" ");
        if(array.length != 2) {
        	throw new MonetaryParseException("An error happened when try to parse the Monetary Amount.",text,0);
        }
        try {
            CurrencyUnit currencyUnit = Monetary.getCurrency(array[1]);
            BigDecimal number = new BigDecimal(array[0]);
            return new ParserMonetaryAmount(currencyUnit, number);
        }catch(Exception e){
            CurrencyUnit currencyUnit = Monetary.getCurrency(array[0]);
            BigDecimal number = new BigDecimal(array[1]);
            return new ParserMonetaryAmount(currencyUnit, number);
        }
    }

    private static class ParserMonetaryAmount {
        ParserMonetaryAmount(CurrencyUnit currencyUnit, BigDecimal number) {
            this.currencyUnit = currencyUnit;
            this.number = number;
        }

        private final CurrencyUnit currencyUnit;
        private final BigDecimal number;
    }

    /**
     * indicates with implementation will used to format or parser in
     * ToStringMonetaryAmountFormat
     */
    public enum ToStringMonetaryAmountFormatStyle {
    	/**
    	 * {@link Money}
    	 */
        MONEY {
            @Override
            MonetaryAmount to(ParserMonetaryAmount amount) {
                return Money.of(amount.number, amount.currencyUnit);
            }
        },
        /**
    	 * {@link FastMoney}
    	 */
        FAST_MONEY {
            @Override
            MonetaryAmount to(ParserMonetaryAmount amount) {
                return FastMoney.of(amount.number, amount.currencyUnit);
            }
        },
        /**
    	 * {@link RoundedMoney}
    	 */
        ROUNDED_MONEY {
            @Override
            MonetaryAmount to(ParserMonetaryAmount amount) {
                return RoundedMoney.of(amount.number, amount.currencyUnit);
            }
        };

        private static final long serialVersionUID = 6606016328162974467L;
        abstract MonetaryAmount to(ParserMonetaryAmount amount);
    }

}
