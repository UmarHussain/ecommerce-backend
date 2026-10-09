package com.umar.ecommerce.payment.service;

import com.umar.ecommerce.payment.dto.PaymentAttemptResponse;
import com.umar.ecommerce.payment.entity.PaymentAttempt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PaymentMapperConfig.class, componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    PaymentAttemptResponse toResponse(PaymentAttempt attempt);
}
