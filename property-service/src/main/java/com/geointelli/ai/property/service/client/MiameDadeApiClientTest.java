package com.geointelli.ai.property.service.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.geointelli.ai.property.service.client.dto.PropertyApiResponse;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.mapper.PropertyMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalPropertyMapper;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class MiameDadeApiClientTest {
    public static void main(String[] args) throws Exception {
        // ExternalPropertyMapper externalPropertyMapper = new ExternalPropertyMapperImpl();
        // PropertyMapper propertyMapper = new PropertyMapperImpl();
        // MiameDadeApiClient miameDadeApiClient = new MiameDadeApiClient();
        // String response = miameDadeApiClient.importMiameDadePropertyDetails("0131330131540")
        //                     .block();
        // ObjectMapper mapper = new ObjectMapper();
        // PropertyApiResponse property = mapper.readValue(response, PropertyApiResponse.class);
        // Property property2 = propertyMapper.toEntity(externalPropertyMapper.toDTO(property.getPropertyInfo()));
        // String jsonFormat = mapper
        //         .enable(SerializationFeature.INDENT_OUTPUT)
        //         .writeValueAsString(json);

        // System.out.println(property);
    }
}
