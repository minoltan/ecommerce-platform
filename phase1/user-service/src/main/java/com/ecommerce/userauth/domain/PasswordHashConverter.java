package com.ecommerce.userauth.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PasswordHashConverter implements AttributeConverter<PasswordHash, String> {

    @Override
    public String convertToDatabaseColumn(PasswordHash attribute) {
        return attribute == null ? null : attribute.hash();
    }

    @Override
    public PasswordHash convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new PasswordHash(dbData);
    }
}
