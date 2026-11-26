package com.geointelli.ai.property.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.geointelli.ai.property.service.entity.Building;

/** "Multiple (See Building Info.)" from Miami-Dade becomes the first building's year. */
class YearBuiltTest {
    private static final String MULTIPLE = "Multiple (See Building Info.)";
    private static final int CURRENT_YEAR = 2026;

    private static Building building(Integer rollYear, Integer buildingNo, Integer segNo, Integer actual) {
        Building building = new Building();
        building.setRollYear(rollYear);
        building.setBuildingNo(buildingNo);
        building.setSegNo(segNo);
        building.setActual(actual);
        return building;
    }

    @Test
    @DisplayName("The first building of the latest roll year gives the year, even when another building is older")
    void firstBuildingOfLatestRollYear() {
        List<Building> buildings = List.of(
            building(2025, 2, 1, 1960),
            building(2025, 1, 2, 1994),
            building(2024, 1, 1, 1950),
            building(2025, 1, 1, 1970));

        assertThat(YearBuilt.resolve(MULTIPLE, buildings, CURRENT_YEAR)).isEqualTo("1970");
    }

    @Test
    @DisplayName("The latest roll year comes first; a missing roll year, building or segment number sorts last")
    void latestRollYearFirstAndMissingNumbersLast() {
        assertThat(YearBuilt.resolve(MULTIPLE, List.of(building(2024, 1, 1, 1990), building(2025, 1, 2, 2001)),
            CURRENT_YEAR)).isEqualTo("2001");
        assertThat(YearBuilt.resolve(MULTIPLE, List.of(building(null, 1, 1, 1980), building(2025, 3, 1, 1999)),
            CURRENT_YEAR)).isEqualTo("1999");
        assertThat(YearBuilt.resolve(MULTIPLE, List.of(building(2025, null, 1, 1980), building(2025, 4, null, 1999)),
            CURRENT_YEAR)).isEqualTo("1999");
    }

    @Test
    @DisplayName("Unusable years (9999, the future, before 1000, none) are skipped for the next building")
    void skipsUnusableYears() {
        List<Building> buildings = List.of(
            building(2025, 1, 1, 9999),
            building(2025, 1, 2, 2027),
            building(2025, 1, 3, 999),
            building(2025, 1, 4, null),
            building(2025, 1, 5, 1985));

        assertThat(YearBuilt.resolve(MULTIPLE, buildings, CURRENT_YEAR)).isEqualTo("1985");
        assertThat(YearBuilt.resolve(MULTIPLE, List.of(building(2025, 1, 1, 2026)), CURRENT_YEAR)).isEqualTo("2026");
    }

    @Test
    @DisplayName("Without a usable building year, the county's text is kept")
    void keepsTextWithoutBuildingYear() {
        assertThat(YearBuilt.resolve(MULTIPLE, List.of(building(2025, 1, 1, 9999)), CURRENT_YEAR)).isEqualTo(MULTIPLE);
        assertThat(YearBuilt.resolve(MULTIPLE, List.of(), CURRENT_YEAR)).isEqualTo(MULTIPLE);
        assertThat(YearBuilt.resolve(MULTIPLE, null, CURRENT_YEAR)).isEqualTo(MULTIPLE);
    }

    @Test
    @DisplayName("The text is recognised whatever its case")
    void recognisesTextInAnyCase() {
        assertThat(YearBuilt.pointsToBuildings("MULTIPLE (SEE BUILDING INFO.)")).isTrue();
        assertThat(YearBuilt.pointsToBuildings("see buildings")).isTrue();
        assertThat(YearBuilt.resolve(" multiple (see building info.) ", List.of(building(2025, 1, 1, 1970)),
            CURRENT_YEAR)).isEqualTo("1970");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1999", "0", "199x"})
    @DisplayName("Any other county value is stored as it is, buildings or not")
    void otherValuesUnchanged(String yearBuilt) {
        assertThat(YearBuilt.pointsToBuildings(yearBuilt)).isFalse();
        assertThat(YearBuilt.resolve(yearBuilt, List.of(building(2025, 1, 1, 1970)), CURRENT_YEAR)).isEqualTo(yearBuilt);
    }
}
