package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import javax.inject.Inject

/** 删除课程（按 id）*/
class DeleteCourseUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteCourse(id)
    }
}