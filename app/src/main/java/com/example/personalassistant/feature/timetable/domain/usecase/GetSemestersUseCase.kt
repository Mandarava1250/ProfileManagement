package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 获取全部学期（按 startDate 倒序）*/
class GetSemestersUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    operator fun invoke(): Flow<List<Semester>> = repository.observeAllSemesters()
}