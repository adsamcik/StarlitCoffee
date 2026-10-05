package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.GrindMemoryDao
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeGrindSettingEntity
import com.adsamcik.starlitcoffee.data.db.entity.PackGrindSettingEntity
import com.adsamcik.starlitcoffee.data.db.entity.TypeGrindSettingEntity
import com.adsamcik.starlitcoffee.data.model.GrindSaveScope
import com.adsamcik.starlitcoffee.data.model.RememberedGrind
import kotlinx.coroutines.flow.combine

class GrindMemoryRepository(private val dao: GrindMemoryDao) {
    fun settings() = combine(dao.typeSettings(), dao.coffeeSettings(), dao.packSettings()) { types, coffees, packs ->
        types.map { RememberedGrind(GrindSaveScope.TYPE, it.coffeeType, it.context, it.value) } +
            coffees.map { RememberedGrind(GrindSaveScope.COFFEE, it.coffeeId.toString(), it.context, it.value) } +
            packs.map { RememberedGrind(GrindSaveScope.PACK, it.packId.toString(), it.context, it.value) }
    }

    suspend fun save(setting: RememberedGrind) {
        when (setting.scope) {
            GrindSaveScope.TYPE -> dao.saveType(TypeGrindSettingEntity(setting.owner, setting.context, setting.value))
            GrindSaveScope.COFFEE -> dao.saveCoffee(CoffeeGrindSettingEntity(setting.owner.toLong(), setting.context, setting.value))
            GrindSaveScope.PACK -> dao.savePack(PackGrindSettingEntity(setting.owner.toLong(), setting.context, setting.value))
            GrindSaveScope.BREW -> error("Temporary values are owned by the brew draft")
        }
    }

    suspend fun remove(setting: RememberedGrind) {
        val c = setting.context
        when (setting.scope) {
            GrindSaveScope.TYPE -> dao.removeType(setting.owner, c.grinderId, c.methodId, c.filterKey)
            GrindSaveScope.COFFEE -> dao.removeCoffee(setting.owner.toLong(), c.grinderId, c.methodId, c.filterKey)
            GrindSaveScope.PACK -> dao.removePack(setting.owner.toLong(), c.grinderId, c.methodId, c.filterKey)
            GrindSaveScope.BREW -> error("Temporary values are owned by the brew draft")
        }
    }
}
