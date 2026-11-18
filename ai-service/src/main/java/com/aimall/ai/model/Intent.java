package com.aimall.ai.model;

public enum Intent {
    /** 表达/补充/修改选购需求 */
    SHOPPING,
    /** 在已推荐商品中选择要买的一款 */
    SELECT,
    /** 询问商品参数/说明书等知识 */
    PRODUCT_QA,
    /** 闲聊 */
    CHITCHAT
}
