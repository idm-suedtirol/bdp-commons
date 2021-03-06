package it.bz.idm.bdp.augeg4.fun.convert.tohub;

import org.junit.Test;

import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class StationMappingsTest {

    @Test
    public void test_mapping_from_a_known_control_unit_id() {
        // given
        StationMappings mapper = new StationMappings();

        // when
        Optional<StationMapping> mappingContainer = mapper.getMapping("AIRQ01");

        // then
        assertTrue(mappingContainer.isPresent());

        StationMapping mapping = mappingContainer.get();
        assertEquals(mapping.getName(), "103.700_APPA BZ");
    }

    @Test
    public void test_mapping_from_a_unknown_id() {
        // given
        StationMappings mapper = new StationMappings();

        // when
        Optional<StationMapping> mappingContainer = mapper.getMapping("abc");

        // then
        assertFalse(mappingContainer.isPresent());
    }
}
