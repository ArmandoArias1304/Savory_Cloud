package com.aatechsolutions.elgransazon.domain.entity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemConfigurationPaymentMethodsTest {

    @Test
    void deliveryNeverEnablesCashEvenIfStoredTrue() {
        SystemConfiguration config = new SystemConfiguration();
        Map<PaymentMethodType, Boolean> delivery = new HashMap<>();
        delivery.put(PaymentMethodType.CASH, true);
        delivery.put(PaymentMethodType.CREDIT_CARD, true);
        config.setDeliveryPaymentMethods(delivery);

        assertFalse(config.isDeliveryPaymentMethodEnabled(PaymentMethodType.CASH));
        assertTrue(config.isDeliveryPaymentMethodEnabled(PaymentMethodType.CREDIT_CARD));
        assertTrue(config.hasAnyDeliveryPaymentMethodEnabled());
    }

    @Test
    void deliveryHasNoneWhenOnlyCashWasStored() {
        SystemConfiguration config = new SystemConfiguration();
        Map<PaymentMethodType, Boolean> delivery = new HashMap<>();
        delivery.put(PaymentMethodType.CASH, true);
        config.setDeliveryPaymentMethods(delivery);

        assertFalse(config.hasAnyDeliveryPaymentMethodEnabled());
    }

    @Test
    void creatingAnyOrderTypeUsesRestaurantMethodsNotDeliveryMethods() {
        SystemConfiguration config = new SystemConfiguration();
        Map<PaymentMethodType, Boolean> restaurant = new HashMap<>();
        restaurant.put(PaymentMethodType.CASH, true);
        restaurant.put(PaymentMethodType.CREDIT_CARD, false);
        config.setPaymentMethods(restaurant);

        Map<PaymentMethodType, Boolean> delivery = new HashMap<>();
        delivery.put(PaymentMethodType.CASH, false);
        delivery.put(PaymentMethodType.CREDIT_CARD, true);
        config.setDeliveryPaymentMethods(delivery);

        assertTrue(config.isPaymentMethodEnabledForOrderType(PaymentMethodType.CASH, OrderType.DELIVERY));
        assertTrue(config.isPaymentMethodEnabledForOrderType(PaymentMethodType.CASH, OrderType.TAKEOUT));
        assertTrue(config.isPaymentMethodEnabledForOrderType(PaymentMethodType.CASH, OrderType.DINE_IN));
        assertFalse(config.isPaymentMethodEnabledForOrderType(PaymentMethodType.CREDIT_CARD, OrderType.DELIVERY));
        assertFalse(config.isDeliveryPaymentMethodEnabled(PaymentMethodType.CASH));
        assertTrue(config.isDeliveryPaymentMethodEnabled(PaymentMethodType.CREDIT_CARD));
    }
}
