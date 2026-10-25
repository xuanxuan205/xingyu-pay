package com.wendao.common.utils;

import cn.hutool.core.bean.BeanUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 */
public class StringUtils {

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    /**
     * 格式化 日期 yyyy-MM-dd HH:mm:ss
     * @param date
     * @return
     */
    public static String getTimeStamp(Date date){
        if(date == null){
            return DATE_FORMAT.get().format(new Date());
        } else {
            return DATE_FORMAT.get().format(date);
        }
    }

    /**
     * 格式化 日期 yyyy-MM-dd HH:mm:ss
     * @param time
     * @return
     */
    public static Date getDate(String time){

        try {
            return DATE_FORMAT.get().parse(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new Date();
    }

    /**
     * 判断字符创是否为空
     * @param str
     * @return
     */
    public static boolean isBlank(String str) {
        int strLen;
        if (str != null && (strLen = str.length()) != 0) {
            for(int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(str.charAt(i))) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    /**
     * 判断字符创是否为空
     * @param str
     * @return
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * Bean 转 Map
     * @param bean
     * @param <T>
     * @return
     */
    public static <T> Map<String, Object> beanToMap(T bean) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (bean != null) {
            // Hutool's implementation uses standard bean introspection. The
            // previous Spring CGLIB BeanMap fails on JDK 17+ when generating
            // its accessor class.
            map.putAll(BeanUtil.beanToMap(bean, new LinkedHashMap<>(), false, false));
        }
        return map;
    }

    /**
     * 随机4位数生成
     */
    public static String getRandomNum() {

        Random random = new Random();
        int end2 = random.nextInt(9999);
        //如果不足两位前面补0
        String str = String.format("%04d", end2);
        return str;
    }

    public static void main(String[] args){

    }

}
