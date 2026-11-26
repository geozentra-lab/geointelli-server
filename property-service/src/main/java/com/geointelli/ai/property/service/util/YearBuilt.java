package com.geointelli.ai.property.service.util;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.geointelli.ai.property.service.entity.Building;

/**
 * Year built stored on a property. For a property with several buildings or segments, Miami-Dade answers
 * "Multiple (See Building Info.)" instead of a year. GeoZentra then stores the actual year of the first building,
 * which is the year the county gives for properties with a single year: the latest roll year, then the lowest
 * building number, then the lowest segment. Building years outside 1000 to the current year (the county writes
 * 9999 for an unknown year) are skipped. The same rule runs in SQL after a buildings CSV import
 * ({@code CsvImportServiceImpl#FILL_YEAR_BUILT_FROM_BUILDINGS}) and in migration V52 for the existing rows.
 */
public final class YearBuilt {

    /** Lower-case text the county puts in the year built when the year is only given per building. */
    public static final String SEE_BUILDINGS = "see building";

    private static final int EARLIEST_YEAR = 1000;

    private static final Comparator<Building> FIRST_BUILDING = Comparator
        .comparing(Building::getRollYear, Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
        .thenComparing(Building::getBuildingNo, Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
        .thenComparing(Building::getSegNo, Comparator.nullsLast(Comparator.<Integer>naturalOrder()));

    private YearBuilt() {
    }

    /** Whether the county's year built sends the reader to the buildings instead of giving a year. */
    public static boolean pointsToBuildings(String yearBuilt) {
        return yearBuilt != null && yearBuilt.toLowerCase(Locale.ROOT).contains(SEE_BUILDINGS);
    }

    /**
     * The value to store: the first building's year when the county's value points to the buildings, otherwise the
     * county's value unchanged. When no building has a usable year, the county's value is kept as well.
     */
    public static String resolve(String yearBuilt, List<Building> buildings, int currentYear) {
        if (!pointsToBuildings(yearBuilt)) {
            return yearBuilt;
        }
        return firstBuildingYear(buildings, currentYear).map(String::valueOf).orElse(yearBuilt);
    }

    /** Actual year of the first building with a usable year; equal buildings keep their list order. */
    public static Optional<Integer> firstBuildingYear(List<Building> buildings, int currentYear) {
        if (buildings == null) {
            return Optional.empty();
        }
        return buildings.stream()
            .filter(building -> building.getActual() != null
                && building.getActual() >= EARLIEST_YEAR && building.getActual() <= currentYear)
            .min(FIRST_BUILDING)
            .map(Building::getActual);
    }
}
