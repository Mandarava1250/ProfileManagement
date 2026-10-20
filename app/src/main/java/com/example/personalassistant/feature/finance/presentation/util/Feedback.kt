package com.example.personalassistant.feature.finance.presentation.util

/**
 * C 重构 D-β 阶段 1：记账模块统一的用户反馈类型。
 *
 * 取代之前各处 `MutableStateFlow<String?>` 的反馈字段。
 *
 * ## 三档语义
 * - [Success]：操作成功（例如"已添加分类：奶茶"）
 * - [Warning]：业务允许但带警示（例如金额格式不对、分类被引用不可删）
 * - [Error]：真正的失败（DB 写入异常、依赖未就绪等）
 *
 * ## 用法
 * ViewModel 持有 `MutableStateFlow<Feedback?>`，UI 用 `LaunchedEffect(feedback)` 监听并触发 snackbar。
 * `cause` 仅在错误排查时使用，不应在 snackbar 文案里展示。
 */
sealed interface Feedback {
    val message: String

    /** 操作成功 */
    data class Success(override val message: String) : Feedback

    /** 业务允许但带警示（如金额格式不对、被引用无法删除） */
    data class Warning(
        override val message: String,
        val cause: Throwable? = null,
    ) : Feedback

    /** 真正的失败（写入异常、依赖未就绪等） */
    data class Error(
        override val message: String,
        val cause: Throwable? = null,
    ) : Feedback
}