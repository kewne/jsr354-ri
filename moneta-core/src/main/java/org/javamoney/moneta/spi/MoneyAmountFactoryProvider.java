/*
  Copyright (c) 2012, 2020, Anatole Tresch, Werner Keil and others by the @author tag.

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
package org.javamoney.moneta.spi;

import javax.annotation.Priority;
import javax.money.MonetaryAmountFactory;
import javax.money.MonetaryContext;
import javax.money.spi.MonetaryAmountFactoryProviderSpi;

import org.javamoney.moneta.Money;

/**
 * Implementation of {@link MonetaryAmountFactoryProviderSpi} creating instances of
 * {@link MoneyAmountFactory}.
 *
 * @author Anatole Tresch
 */
@Priority(10)
public final class MoneyAmountFactoryProvider implements MonetaryAmountFactoryProviderSpi<Money>{

    @Override
    public Class<Money> getAmountType(){
        return Money.class;
    }

    @Override
    public MonetaryAmountFactory<Money> createMonetaryAmountFactory(){
        return new MoneyAmountFactory();
    }

    /*
     * (non-Javadoc)
     * @see javax.money.spi.MonetaryAmountFactoryProviderSpi#getQueryInclusionPolicy()
     */
    @Override
    public QueryInclusionPolicy getQueryInclusionPolicy(){
        return QueryInclusionPolicy.ALWAYS;
    }

    @Override
    public MonetaryContext getDefaultMonetaryContext(){
        return MoneyAmountFactory.DEFAULT_CONTEXT;
    }

    @Override
    public MonetaryContext getMaximalMonetaryContext(){
        return MoneyAmountFactory.MAX_CONTEXT;
    }
}
