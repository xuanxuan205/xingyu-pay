package com.wendao.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void findsOnlyExistingQrNumbersInAscendingOrder() throws IOException {
        Path amountDir = tempDir.resolve("wechat").resolve("5.00");
        Files.createDirectories(amountDir);
        Files.createFile(amountDir.resolve("2.png"));

        assertEquals(Collections.singletonList(2),
                QrCodeResolver.findAvailableNumbers(tempDir.toString(), "wechat",
                        new BigDecimal("5.00"), 2));
    }

    @Test
    void returnsBothNumbersWhenBothExist() throws IOException {
        Path amountDir = tempDir.resolve("wechat").resolve("29.00");
        Files.createDirectories(amountDir);
        Files.createFile(amountDir.resolve("1.png"));
        Files.createFile(amountDir.resolve("2.png"));

        assertEquals(Arrays.asList(1, 2),
                QrCodeResolver.findAvailableNumbers(tempDir.toString(), "wechat",
                        new BigDecimal("29.00"), 2));
    }

    @Test
    void returnsEmptyWhenDirectoryMissing() {
        assertTrue(QrCodeResolver.findAvailableNumbers(tempDir.toString(), "wechat",
                new BigDecimal("10.00"), 2).isEmpty());
    }

    @Test
    void rejectsInvalidPayTypeAndAmount() {
        assertTrue(QrCodeResolver.findAvailableNumbers(tempDir.toString(), "../wechat",
                new BigDecimal("5.00"), 2).isEmpty());
        assertTrue(QrCodeResolver.findAvailableNumbers(tempDir.toString(), "wechat",
                new BigDecimal("-1.00"), 2).isEmpty());
    }

    @Test
    void formatsAmountWithTwoDecimals() {
        assertEquals("5.00", QrCodeResolver.formatAmount(new BigDecimal("5")));
        assertEquals("29.90", QrCodeResolver.formatAmount(new BigDecimal("29.9")));
    }
}
