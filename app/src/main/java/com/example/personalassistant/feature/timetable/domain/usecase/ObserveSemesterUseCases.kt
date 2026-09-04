package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 观察当前学期 */
class ObserveCurrentSemesterUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    operator fun invoke(): Flow<com.example.personalassistant.feature.timetable.domain.model.Semester?> =
        repository.observeCurrentSemester()
}

/** 观察指定学期的全部课程 */
class ObserveCoursesBySemesterUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    operator fun invoke(semester: String): Flow<List<Course>> =
        repository.observeCoursesBySemester(semester)
}