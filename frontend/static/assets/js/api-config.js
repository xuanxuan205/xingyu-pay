// API配置文件

window.API_CUSTOM_CONFIG = window.API_CUSTOM_CONFIG || {};

const API_CONFIG = {
    // 动态获取基础URL
    get BASE_URL() {
        // 固定同源。禁止通过 ?apiBase= 或 localStorage 把请求改到别的域名。
        if (typeof window !== 'undefined' && window.location) {
            return window.location.origin;
        }

        // 兜底默认值
        return 'https://pay.example.com';
    },

    // API端点配置
    ENDPOINTS: {
        RECHARGE: '/order/add',
        STATUS: '/order/state',
        PRODUCT_DETAIL: '/product/get',
        PRODUCT_LIST: '/product/getListByType',
        PUBLIC_LIST: '/order/publicList',
        ADMIN_LIST: '/order/admin/list'
    },

    // 请求超时配置
    TIMEOUT: 10000,

    // 请求头配置
    HEADERS: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
    }
};
// 导出配置
if (typeof module !== 'undefined' && module.exports) {
    module.exports = API_CONFIG;
} else {
    window.API_CONFIG = API_CONFIG;
}
