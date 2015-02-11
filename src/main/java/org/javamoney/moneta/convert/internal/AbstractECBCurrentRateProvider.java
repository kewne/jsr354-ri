/**
 * Copyright (c) 2012, 2014, Credit Suisse (Anatole Tresch), Werner Keil and others by the @author tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.javamoney.moneta.convert.internal;

import java.io.InputStream;
import java.math.MathContext;
import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.money.CurrencyUnit;
import javax.money.MonetaryCurrencies;
import javax.money.convert.ConversionContextBuilder;
import javax.money.convert.ConversionQuery;
import javax.money.convert.CurrencyConversionException;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ProviderContext;
import javax.money.convert.RateType;
import javax.money.spi.Bootstrap;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.javamoney.moneta.ExchangeRateBuilder;
import org.javamoney.moneta.spi.AbstractRateProvider;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.javamoney.moneta.spi.LoaderService;
import org.javamoney.moneta.spi.LoaderService.LoaderListener;

/**
 * Base to all Europe Central Bank implementation.
 * @author otaviojava
 */
abstract class AbstractECBCurrentRateProvider extends AbstractRateProvider implements
		LoaderListener {

    static final String BASE_CURRENCY_CODE = "EUR";

    /**
     * Base currency of the loaded rates is always EUR.
     */
    public static final CurrencyUnit BASE_CURRENCY = MonetaryCurrencies.getCurrency(BASE_CURRENCY_CODE);

    /**
     * Historic exchange rates, rate timestamp as UTC long.
     */
	private final Map<Long, Map<String, ExchangeRate>> historicRates = new ConcurrentHashMap<>();
    /**
     * Parser factory.
     */
    private SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

    private Long recentKey;

    public AbstractECBCurrentRateProvider(ProviderContext context) throws MalformedURLException{
        super(context);
        saxParserFactory.setNamespaceAware(false);
        saxParserFactory.setValidating(false);
        LoaderService loader = Bootstrap.getService(LoaderService.class);
        loader.addLoaderListener(this, getDataId());
        loader.loadDataAsync(getDataId());
    }

    public abstract String getDataId();

    @Override
    public void newDataLoaded(String data, InputStream is){
        final int oldSize = this.historicRates.size();
        try{
            SAXParser parser = saxParserFactory.newSAXParser();
            parser.parse(is, new RateReadingHandler(historicRates, getProviderContext()));
            recentKey = null;
        }
        catch(Exception e){
            LOGGER.log(Level.FINEST, "Error during data load.", e);
        }
        int newSize = this.historicRates.size();
        LOGGER.info("Loaded " + getDataId() + " exchange rates for days:" + (newSize - oldSize));
    }


    @Override
	public ExchangeRate getExchangeRate(ConversionQuery query){
    	Objects.requireNonNull(query);
    	if(historicRates.isEmpty()){
            return null;
        }

		Long timeStampMillis = getMillisSeconds(query);
        ExchangeRateBuilder builder = getBuilder(query, timeStampMillis);


		Map<String, ExchangeRate> targets = this.historicRates
				.get(timeStampMillis);
        if(Objects.isNull(targets)){
            return null;
        }
		ExchangeRate sourceRate = targets.get(query.getBaseCurrency()
				.getCurrencyCode());
		ExchangeRate target = targets
				.get(query.getCurrency().getCurrencyCode());
        return createExchangeRate(query, builder, sourceRate, target);
    }

	private ExchangeRate createExchangeRate(ConversionQuery query,
			ExchangeRateBuilder builder, ExchangeRate sourceRate,
			ExchangeRate target) {

		if(areBothBaseCurrencies(query)){
            builder.setFactor(DefaultNumberValue.ONE);
            return builder.build();
        } else if(BASE_CURRENCY_CODE.equals(query.getCurrency().getCurrencyCode())){
            if(Objects.isNull(sourceRate)){
                return null;
            }
            return reverse(sourceRate);
		} else if (BASE_CURRENCY_CODE.equals(query.getBaseCurrency()
				.getCurrencyCode())) {
            return target;
        } else{
            // Get Conversion base as derived rate: base -> EUR -> term
            ExchangeRate rate1 = getExchangeRate(
                    query.toBuilder().setTermCurrency(MonetaryCurrencies.getCurrency(BASE_CURRENCY_CODE)).build());
            ExchangeRate rate2 = getExchangeRate(
                    query.toBuilder().setBaseCurrency(MonetaryCurrencies.getCurrency(BASE_CURRENCY_CODE))
                            .setTermCurrency(query.getCurrency()).build());
            if(Objects.nonNull(rate1) && Objects.nonNull(rate2)){
                builder.setFactor(multiply(rate1.getFactor(), rate2.getFactor()));
                builder.setRateChain(rate1, rate2);
                return builder.build();
            }
			throw new CurrencyConversionException(query.getBaseCurrency(),
					query.getCurrency(), sourceRate.getConversionContext());
        }
	}

	private boolean areBothBaseCurrencies(ConversionQuery query) {
		return BASE_CURRENCY_CODE.equals(query.getBaseCurrency().getCurrencyCode()) &&
                BASE_CURRENCY_CODE.equals(query.getCurrency().getCurrencyCode());
	}

	private Long getMillisSeconds(ConversionQuery query) {
		if (Objects.nonNull(query.getTimestamp())) {
			LocalDate timeStamp = query.getTimestamp().toLocalDate();

			Date date = Date.from(timeStamp.atStartOfDay()
					.atZone(ZoneId.systemDefault()).toInstant());
			Long timeStampMillis = date.getTime();
			return timeStampMillis;
		} else {
			return getRecentKey();
		}
	}

	private Long getRecentKey() {
		if(Objects.isNull(recentKey)) {
			Comparator<Long> reversed = Comparator.<Long>naturalOrder().reversed();
			recentKey =  historicRates.keySet().stream().sorted(reversed).findFirst().get();
		}
		return recentKey;
	}

	private ExchangeRateBuilder getBuilder(ConversionQuery query,
			Long timeStampMillis) {
		ExchangeRateBuilder builder = new ExchangeRateBuilder(
                ConversionContextBuilder.create(getProviderContext(), RateType.HISTORIC)
						.setTimestampMillis(timeStampMillis).build());
        builder.setBase(query.getBaseCurrency());
        builder.setTerm(query.getCurrency());
		return builder;
	}

    private ExchangeRate reverse(ExchangeRate rate){
        if(Objects.isNull(rate)){
            throw new IllegalArgumentException("Rate null is not reversable.");
        }
        return new ExchangeRateBuilder(rate).setRate(rate).setBase(rate.getCurrency()).setTerm(rate.getBaseCurrency())
                .setFactor(divide(DefaultNumberValue.ONE, rate.getFactor(), MathContext.DECIMAL64)).build();
    }

}