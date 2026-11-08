package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropertyImageObjectKeyTest {

    @Test
    void bucketUrlUsesItsPath() {
        assertThat(PropertyImageServiceImpl.objectKey("https://bucket.s3.amazonaws.com/1_abc.png"))
                .isEqualTo("1_abc.png");
    }

    @Test
    void windowsFilePathUsesTheFileName() {
        assertThat(PropertyImageServiceImpl.objectKey(
                "C:\\Users\\admin\\geointelliai-server\\property-service\\images\\1_abc.png"))
                .isEqualTo("1_abc.png");
    }

    @Test
    void unixFilePathAndBareKeyUseTheFileName() {
        assertThat(PropertyImageServiceImpl.objectKey("/srv/app/images/2_def.jpg")).isEqualTo("2_def.jpg");
        assertThat(PropertyImageServiceImpl.objectKey("3_ghi.png")).isEqualTo("3_ghi.png");
    }
}
