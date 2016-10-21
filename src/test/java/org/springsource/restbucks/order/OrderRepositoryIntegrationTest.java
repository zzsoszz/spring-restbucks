/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springsource.restbucks.order;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springsource.restbucks.core.Currencies.*;
import static org.springsource.restbucks.order.Order.Status.*;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.javamoney.moneta.Money;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springsource.restbucks.AbstractIntegrationTest;
import org.springsource.restbucks.Restbucks;
import org.springsource.restbucks.payment.OrderPaidEvent;

/**
 * Integration tests for Spring Data based {@link OrderRepository}.
 * 
 * @author Oliver Gierke
 */
public class OrderRepositoryIntegrationTest extends AbstractIntegrationTest {

	@Autowired OrderRepository repository;
	@Autowired CollectingEventListener listener;

	@Configuration
	@Import(Restbucks.class)
	public static class Config {

		@Bean
		CollectingEventListener listener() {
			return new CollectingEventListener();
		}
	}

	@Test
	public void findsAllOrders() {

		Iterable<Order> orders = repository.findAll();
		assertThat(orders, is(not(emptyIterable())));
	}

	@Test
	public void createsNewOrder() {

		Long before = repository.count();

		Order order = repository.save(createOrder());

		Iterable<Order> result = repository.findAll();
		assertThat(result, is(Matchers.<Order>iterableWithSize(before.intValue() + 1)));
		assertThat(result, hasItem(order));
	}

	@Test
	public void findsOrderByStatus() {

		int paidBefore = repository.findByStatus(PAID).size();
		int paymentExpectedBefore = repository.findByStatus(PAYMENT_EXPECTED).size();

		Order order = repository.save(createOrder());
		assertThat(repository.findByStatus(PAYMENT_EXPECTED).size(), is(paymentExpectedBefore + 1));
		assertThat(repository.findByStatus(PAID).size(), is(paidBefore));

		order.markPaid();
		order = repository.save(order);

		assertThat(repository.findByStatus(PAYMENT_EXPECTED), hasSize(paymentExpectedBefore));
		assertThat(repository.findByStatus(PAID), hasSize(paidBefore + 1));
	}

	@Test
	public void throwsOrderPaidEvent() {

		Order order = repository.save(createOrder()).markPaid();
		assertThat(order.getDomainEvents(), hasItem(instanceOf(OrderPaidEvent.class)));

		repository.save(order);
		assertThat(listener.getEvents(), hasItem(instanceOf(OrderPaidEvent.class)));
	}

	public static Order createOrder() {
		return new Order(new LineItem("English breakfast", Money.of(2.70, EURO)));
	}

	private static class CollectingEventListener {

		private final @Getter List<Object> events = new ArrayList<>();

		@EventListener
		public void on(Object event) {
			this.events.add(event);
		}
	}
}
