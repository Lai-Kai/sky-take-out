package com.sky.constant;

public class FlashSaleConstant {

    public static final String EXCHANGE = "sky.flash.sale.exchange";
    public static final String ORDER_QUEUE = "sky.flash.sale.order.queue";
    public static final String ORDER_ROUTING_KEY = "flash.sale.order";

    public static final String STOCK_KEY_PREFIX = "flash_sale:stock:";
    public static final String USERS_KEY_PREFIX = "flash_sale:users:";
    public static final String LOCK_KEY_PREFIX = "lock:flash_sale:";

    public static final String SUBMIT_SUCCESS = "flash sale request accepted";
    public static final String ACTIVITY_NOT_FOUND = "flash sale activity not found";
    public static final String ACTIVITY_NOT_STARTED = "flash sale activity not started";
    public static final String ACTIVITY_ENDED = "flash sale activity ended";
    public static final String ACTIVITY_DISABLED = "flash sale activity disabled";
    public static final String OUT_OF_STOCK = "flash sale setmeal sold out";
    public static final String REPEAT_ORDER = "duplicate flash sale order";
    public static final String REQUEST_BUSY = "request too busy";
}
