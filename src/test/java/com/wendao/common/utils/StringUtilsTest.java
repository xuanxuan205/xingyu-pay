package com.wendao.common.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilsTest {

    @Test
    void beanToMapUsesStandardBeanIntrospection() {
        SampleBean bean = new SampleBean();
        bean.setName("paypro");
        bean.setAmount(5);

        Map<String, Object> map = StringUtils.beanToMap(bean);

        assertEquals("paypro", map.get("name"));
        assertEquals(5, map.get("amount"));
    }

    @Test
    void randomPayNumIsFourDigits() {
        String value = StringUtils.getRandomNum();
        assertTrue(value.matches("\\d{4}"));
    }

    public static class SampleBean {
        private String name;
        private Integer amount;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAmount() {
            return amount;
        }

        public void setAmount(Integer amount) {
            this.amount = amount;
        }
    }
}
