package com.example.personalassistant.core.holiday.model

/**
 * Phase 8 Sub-step 7：节假日类型
 *
 * - [HOLIDAY]   法定假日（如国庆、春节、端午）→ 用户级「默认跳过提醒」
 * - [WORKDAY]   调休补班（如国庆前补班的周六）→ 视为工作日，不避让
 * - [OBSERVANCE] 传统节日（非假日，如七夕、重阳、腊八）→ 不避让，只展示
 *
 * 字段持久化用 name（String）落库；类型变化时记得在数据库层做迁移。
 */
enum class HolidayType {
    HOLIDAY,
    WORKDAY,
    OBSERVANCE,
}