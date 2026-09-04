package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import javax.inject.Inject

/** 切换当前学期（事务原子操作）*/
class SwitchSemesterUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    suspend operator fun invoke(name: String) {
        repository.switchSemester(name)
    }
}