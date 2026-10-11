package vn.gov.drvn.kcht.greentree;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.gov.drvn.kcht.entity.GreenTreeRecordEntity;
import vn.gov.drvn.kcht.mapper.GreenTreeMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreenTreeMapperTest {

    @Test
    void typedAndProtectedFieldsWinOverConflictingAttributes() {
        GreenTreeRecordEntity entity = new GreenTreeRecordEntity();
        entity.setId(12L);
        entity.setRecordId("TREE-12");
        entity.setName("Typed name");
        entity.setRoadClass("Class A");
        entity.setMaintainValue(new BigDecimal("1500.00"));
        entity.setVersion(7);
        entity.setAttributes("""
                {"id":"spoofed","dataset_code":"other_assets","asset_type":"OTHER",
                 "name":"Attribute name","road_class":"Class B","custom_key":"kept"}
                """);

        var result = new GreenTreeMapper(new ObjectMapper()).toRecordItemDto(entity);

        assertEquals("green_trees", result.getDatasetKey());
        assertEquals("TREE-12", result.getRecordKey());
        assertEquals("TREE-12", result.getPayload().path("id").asText());
        assertEquals("green_trees", result.getPayload().path("dataset_code").asText());
        assertEquals("TREE", result.getPayload().path("asset_type").asText());
        assertEquals("Typed name", result.getPayload().path("name").asText());
        assertEquals("Class A", result.getPayload().path("road_class").asText());
        assertEquals(0, new BigDecimal("1500.00")
                .compareTo(result.getPayload().path("maintain_value").decimalValue()));
        assertEquals(7, result.getPayload().path("version").asInt());
        assertEquals("kept", result.getPayload().path("custom_key").asText());
    }
}
