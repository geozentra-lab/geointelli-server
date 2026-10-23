package com.geointelli.ai.property.service.backfill;

import com.geointelli.ai.property.service.integration.mapillary.PanoramaProjection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanoramaProjectionTest {
    private JsonNode northPose() throws Exception {
        return new ObjectMapper().readTree("""
            {"computed_rotation":[1.5707963267948966,0,0],"computed_compass_angle":0,"exif_orientation":1}
            """);
    }
    private BufferedImage longitudeGradient() {
        var panorama=new BufferedImage(2048,1024,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<1024;y++)for(int x=0;x<2048;x++)panorama.setRGB(x,y,((x*255/2047)<<16)|(y*255/1023));
        return panorama;
    }
    @Test void centersOnWorldBearingAndKeepsHorizonLevel() throws Exception {
        var north=PanoramaProjection.extract(longitudeGradient(),northPose(),0).view();
        var east=PanoramaProjection.extract(longitudeGradient(),northPose(),90).view();
        int northPixel=north.getRGB(north.getWidth()/2,north.getHeight()/2);
        int eastPixel=east.getRGB(east.getWidth()/2,east.getHeight()/2);
        assertEquals(128,(northPixel>>16)&255,2);
        assertEquals(191,(eastPixel>>16)&255,2);
        assertEquals(128,northPixel&255,2);
        assertEquals(128,eastPixel&255,2);
        assertEquals(north.getWidth()*3,north.getHeight()*4);
    }
    @Test void limitsResolutionToNativeDetailRatherThanUpscaling() throws Exception {
        var source=new BufferedImage(4064,2032,BufferedImage.TYPE_INT_RGB);
        var result=PanoramaProjection.extract(source,northPose(),0);
        assertEquals(744,result.view().getWidth());assertEquals(558,result.view().getHeight());
        assertEquals(false,result.assessment().get("upscaled"));
        assertEquals("unsuitable",result.assessment().get("qualityDecision"));
        assertEquals("small-preview", result.assessment().get("qualityTier"));
        assertTrue(result.assessment().get("qualityWarnings").toString().contains("below-detail-view-resolution"));
        assertFalse(result.assessment().get("unsuitableReasons").toString().contains("insufficient-native-preview-resolution"));
        assertTrue(result.assessment().get("unsuitableReasons").toString().contains("blur-suspected"));
    }
    @Test void rejectsViewsBelowTheMinimumPreviewResolution() throws Exception {
        var result=PanoramaProjection.extract(longitudeGradient(),northPose(),0);
        assertNotNull(result.view());
        assertEquals(false,result.assessment().get("upscaled"));
        assertEquals("unsuitable",result.assessment().get("qualityDecision"));
        assertTrue(result.assessment().get("unsuitableReasons").toString().contains("insufficient-native-preview-resolution"));
    }
    @Test void refusesUntrustworthyOrientationRatherThanShowingRawPanorama() throws Exception {
        var json=new ObjectMapper();var source=longitudeGradient();
        assertNull(PanoramaProjection.extract(source,json.readTree("{\"exif_orientation\":1}"),0).view());
        var conflicting=json.readTree("""
            {"computed_rotation":[1.5707963267948966,0,0],"computed_compass_angle":90,"exif_orientation":1}
            """);
        var result=PanoramaProjection.extract(source,conflicting,0);
        assertNull(result.view());assertEquals("unsuitable",result.assessment().get("qualityDecision"));
        assertTrue(result.assessment().get("unsuitableReasons").toString().contains("rotation-and-compass-conflict"));
    }
    @Test void wrapsPanoramaSeamWithoutSamplingOutsideImage() throws Exception {
        var result=PanoramaProjection.extract(longitudeGradient(),northPose(),180);
        var view=result.view();int y=view.getHeight()/2,x=view.getWidth()/2;
        assertTrue(((view.getRGB(x-2,y)>>16)&255)>245);
        assertTrue(((view.getRGB(x+2,y)>>16)&255)<10);
    }
    @Test void sharpnessMetricSeparatesFlatFromDetailedImages() {
        var flat=new BufferedImage(64,48,BufferedImage.TYPE_INT_RGB);
        var detailed=new BufferedImage(64,48,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<48;y++)for(int x=0;x<64;x++)detailed.setRGB(x,y,(x+y)%2==0?0xffffff:0);
        assertEquals(0,PanoramaProjection.laplacianVariance(flat));
        assertTrue(PanoramaProjection.laplacianVariance(detailed)>1000);
    }

    @Test void validationGalleryNeverUsesOriginalPanoramaAsCandidateImage() {
        Map<String,Object> candidate=new HashMap<>();
        candidate.put("file","candidate_1_perspective.png");candidate.put("originalFile","candidate_1_original_panorama.jpg");
        candidate.put("imageId","123");candidate.put("panorama",true);candidate.put("qualityDecision","unsuitable");
        Map<String,Object> row=new HashMap<>();row.put("folio","1234");row.put("folder","1234");
        row.put("candidates",List.of(candidate));
        String report=MapillaryPreview.gallery(List.of(row));
        assertTrue(report.contains("src='1234/candidate_1_perspective.png'"));
        assertFalse(report.contains("src='1234/candidate_1_original_panorama.jpg'"));
        assertTrue(report.contains("href='1234/candidate_1_original_panorama.jpg'"));
        candidate.put("file",null);
        report=MapillaryPreview.gallery(List.of(row));
        assertFalse(report.contains("<img"));
        assertTrue(report.contains("No trustworthy perspective view"));
    }
}
