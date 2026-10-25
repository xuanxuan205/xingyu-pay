package com.wendao.common.utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 固定金额二维码定位器。
 *
 * 微信二维码约定：
 * {qrDir}/wechat/{金额两位小数}/{编号}.png
 *
 * 只返回真实存在、可读、且不是符号链接的文件编号。
 */
public final class QrCodeResolver {

    private static final Pattern PAY_TYPE_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d{1,9}\\.\\d{2}");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000.00");

    private QrCodeResolver() {
    }

    public static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * @return 按升序排列的可用二维码编号；目录或文件不存在时返回空列表
     */
    public static List<Integer> findAvailableNumbers(String qrDir,
                                                     String payType,
                                                     BigDecimal amount,
                                                     int maxNumbers) {
        if (qrDir == null || qrDir.trim().isEmpty() || maxNumbers < 1) {
            return Collections.emptyList();
        }
        String type = payType == null ? "" : payType.trim().toLowerCase(Locale.ROOT);
        if (!PAY_TYPE_PATTERN.matcher(type).matches()) {
            return Collections.emptyList();
        }
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(MAX_AMOUNT) > 0) {
            return Collections.emptyList();
        }

        String amountText = formatAmount(amount);
        if (amountText == null || !AMOUNT_PATTERN.matcher(amountText).matches()) {
            return Collections.emptyList();
        }

        Path root = Paths.get(qrDir).toAbsolutePath().normalize();
        Path amountDir = root.resolve(type).resolve(amountText).normalize();
        // 双重保护：即使输入被篡改，也不允许跳出 QR_DIR。
        if (!amountDir.startsWith(root)) {
            return Collections.emptyList();
        }

        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= maxNumbers; i++) {
            Path file = amountDir.resolve(i + ".png");
            try {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(file)) {
                    numbers.add(i);
                }
            } catch (SecurityException ignored) {
                // 不可读的路径按不存在处理
            }
        }
        return numbers;
    }
}
