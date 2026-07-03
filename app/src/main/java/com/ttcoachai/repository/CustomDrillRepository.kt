package com.ttcoachai.repository

import com.ttcoachai.db.CustomDrillDao
import com.ttcoachai.models.CustomDrillEntity

class CustomDrillRepository(private val dao: CustomDrillDao) {

    suspend fun save(entity: CustomDrillEntity) = dao.upsert(entity)

    suspend fun getAll(): List<CustomDrillEntity> = dao.getAll()

    suspend fun get(drillType: String): CustomDrillEntity? = dao.getByDrillType(drillType)

    suspend fun count(): Int = dao.count()

    suspend fun delete(drillType: String) = dao.deleteByDrillType(drillType)
}
