package com.geointelli.ai.property.service.mapper;

import com.geointelli.ai.property.service.dto.GeometryDTO;

import org.locationtech.jts.geom.*;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface GeometryMapper {

    @Named("toDTO")
    default GeometryDTO toDTO(MultiPolygon geom) {
        if (geom == null) return null;

        GeometryDTO dto = new GeometryDTO();
        dto.setType(geom.getGeometryType());
        dto.setCoordinates(convertMultiPolygon(geom));
        return dto;
    }

    @Named("fromDTO")
    default MultiPolygon fromDTO(GeometryDTO dto) {
        if (dto == null) return null;

        GeometryFactory factory = new GeometryFactory();

        if (!"MultiPolygon".equals(dto.getType())) {
            throw new IllegalArgumentException("Expected MultiPolygon, but got: " + dto.getType());
        }

        @SuppressWarnings("unchecked")
        List<List<List<double[]>>> polys = (List<List<List<double[]>>>) dto.getCoordinates();

        Polygon[] polygons = new Polygon[polys.size()];
        for (int i = 0; i < polys.size(); i++) {
            List<List<double[]>> polyRings = polys.get(i);
            LinearRing shell = toLinearRing(polyRings.get(0), factory);
            LinearRing[] holes = new LinearRing[polyRings.size() > 1 ? polyRings.size() - 1 : 0];
            for (int j = 1; j < polyRings.size(); j++) {
                holes[j - 1] = toLinearRing(polyRings.get(j), factory);
            }
            polygons[i] = factory.createPolygon(shell, holes);
        }

        return factory.createMultiPolygon(polygons);
    }

    default LinearRing toLinearRing(List<double[]> coords, GeometryFactory factory) {
        Coordinate[] coordinates = coords.stream()
                .map(c -> new Coordinate(c[0], c[1]))
                .toArray(Coordinate[]::new);
        return factory.createLinearRing(coordinates);
    }

    /*
    {
        "type": "MultiPolygon",
        "coordinates": [
            [  // First Polygon
            [  // Exterior ring of first polygon
                [0, 0],
                [4, 0],
                [4, 4],
                [0, 4],
                [0, 0]
            ],
            [  // Interior ring (hole) of first polygon
                [1, 1],
                [2, 1],
                [2, 2],
                [1, 2],
                [1, 1]
            ]
            ],
            [  // Second Polygon
            [  // Exterior ring of second polygon
                [10, 10],
                [12, 10],
                [12, 12],
                [10, 12],
                [10, 10]
            ],
            [  // Interior ring (hole) of second polygon
                [10.5, 10.5],
                [11, 10.5],
                [11, 11],
                [10.5, 11],
                [10.5, 10.5]
            ]
            ],
            [  // Third Polygon (no holes)
            [  // Exterior ring of third polygon
                [20, 20],
                [23, 20],
                [23, 23],
                [20, 23],
                [20, 20]
            ]
            // No interior rings here
            ]
        ]
    } */

    default List<double[]> convertCoordinates(Coordinate[] coordinates) {
        List<double[]> list = new ArrayList<>();
        for (Coordinate c : coordinates) list.add(new double[]{c.getX(), c.getY()});
        return list;
    }

    default List<List<double[]>> convertPolygon(Polygon polygon) {
        List<List<double[]>> rings = new ArrayList<>();
        rings.add(convertCoordinates(polygon.getExteriorRing().getCoordinates()));
        for (int i = 0; i < polygon.getNumInteriorRing(); i++)
            rings.add(convertCoordinates(polygon.getInteriorRingN(i).getCoordinates()));
        return rings;
    }

    default List<List<List<double[]>>> convertMultiPolygon(MultiPolygon multiPolygon) {
        List<List<List<double[]>>> polygons = new ArrayList<>();
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++)
            polygons.add(convertPolygon((Polygon) multiPolygon.getGeometryN(i)));
        return polygons;
    }
}