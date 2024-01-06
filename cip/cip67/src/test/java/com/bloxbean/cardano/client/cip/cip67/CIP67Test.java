package com.bloxbean.cardano.client.cip.cip67;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CIP67Test {

    @Test
    void labelToPrefixTest() {
        byte[] prefixAsBytes = CIP67.labelToPrefix(222);
        byte[] expectedValue = {0, 13, -31, 64};
        assertTrue(Arrays.equals(prefixAsBytes, expectedValue)); // corresponding to 0x000de140
    }

    @Test
    void prefixToLabelTest() {
        int label = CIP67.prefixToLabel(new byte[]{0, 13, -31, 64});
        assertEquals(222, label);
    }

    @Test
    void isValidAssetNameTestCorrectName() {
        boolean isValid = CIP67.isValidAssetName(0x000de140);
        assertEquals(true, isValid);
    }

    @Test
    void isValidAssetNameTestWrongPadding() {
        boolean isValid = CIP67.isValidAssetName(0x000de141);
        assertEquals(false, isValid);

        isValid = CIP67.isValidAssetName(0x100de140);
        assertEquals(false, isValid);
    }

    @Test
    void isValidAssetNameWrongChecksum() {
        boolean isValid = CIP67.isValidAssetName(0x000de130);
        assertEquals(false, isValid);
    }
}
