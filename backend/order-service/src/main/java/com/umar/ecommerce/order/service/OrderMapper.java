package com.umar.ecommerce.order.service;

import com.umar.ecommerce.order.dto.response.AddressSnapshotResponse;
import com.umar.ecommerce.order.dto.response.OrderLineResponse;
import com.umar.ecommerce.order.dto.response.OrderResponse;
import com.umar.ecommerce.order.dto.response.QuoteLineResponse;
import com.umar.ecommerce.order.dto.response.QuoteResponse;
import com.umar.ecommerce.order.entity.CustomerOrder;
import com.umar.ecommerce.order.entity.CustomerQuote;
import com.umar.ecommerce.order.entity.OrderAddress;
import com.umar.ecommerce.order.entity.OrderLine;
import com.umar.ecommerce.order.entity.QuoteLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = OrderMapperConfig.class)
public interface OrderMapper {

    @Mapping(target = "shippingPolicy", constant = "LOCAL_DEMO_FREE_SHIPPING")
    @Mapping(target = "taxPolicy", constant = "LOCAL_DEMO_TAX_NOT_CALCULATED")
    @Mapping(target = "address.addressId", source = "addressId")
    @Mapping(target = "address.label", source = "addressLabel")
    @Mapping(target = "address.line1", source = "addressLine1")
    @Mapping(target = "address.line2", source = "addressLine2")
    @Mapping(target = "address.city", source = "addressCity")
    @Mapping(target = "address.region", source = "addressRegion")
    @Mapping(target = "address.postalCode", source = "addressPostalCode")
    @Mapping(target = "address.countryCode", source = "addressCountryCode")
    QuoteResponse toQuote(CustomerQuote quote);

    QuoteLineResponse toQuoteLine(QuoteLine line);

    @Mapping(target = "shippingPolicy", constant = "LOCAL_DEMO_FREE_SHIPPING")
    @Mapping(target = "taxPolicy", constant = "LOCAL_DEMO_TAX_NOT_CALCULATED")
    @Mapping(target = "paymentSimulated", constant = "true")
    OrderResponse toOrder(CustomerOrder order);

    OrderLineResponse toLine(OrderLine line);

    AddressSnapshotResponse toAddress(OrderAddress address);
}
