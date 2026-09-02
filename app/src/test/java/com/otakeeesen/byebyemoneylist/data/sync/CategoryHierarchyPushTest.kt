package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryHierarchyPushTest {

    private fun cat(id: Long, name: String, parentId: Long? = null, serverId: String? = null) =
        CategoryEntity(id = id, name = name, parentId = parentId, serverId = serverId)

    private fun dtoByTempId(dtos: List<NextcloudCategoryDto>): Map<String, NextcloudCategoryDto> =
        dtos.associateBy { it.tempId!! }

    @Test
    fun `full unsynced hierarchy is sent parents first with tempId parent references`() {
        val food = cat(1, "Food")
        val bakery = cat(2, "Bakery", parentId = 1)
        val dairy = cat(3, "Dairy", parentId = 1)
        val croissant = cat(4, "Croissant", parentId = 2)
        val all = listOf(food, bakery, dairy, croissant)

        val dtos = buildHierarchicalPushDtos(all, all)
        val byId = dtoByTempId(dtos)

        assertEquals(setOf("1", "2", "3", "4"), byId.keys)
        assertNull(byId.getValue("1").parentId)
        assertEquals("1", byId.getValue("2").parentId)
        assertEquals("1", byId.getValue("3").parentId)
        assertEquals("2", byId.getValue("4").parentId)

        // parents are created before their children
        assertOrdered(dtos, listOf("1", "2", "4"))
        assertOrdered(dtos, listOf("1", "3"))
    }

    @Test
    fun `pushing a leaf auto includes unsynced ancestors in order`() {
        val food = cat(1, "Food")
        val bakery = cat(2, "Bakery", parentId = 1)
        val croissant = cat(4, "Croissant", parentId = 2)
        val all = listOf(food, bakery, croissant)

        val dtos = buildHierarchicalPushDtos(listOf(croissant), all)
        val byId = dtoByTempId(dtos)

        assertEquals(listOf("1", "2", "4"), dtos.map { it.tempId })
        assertNull(byId.getValue("1").parentId)
        assertEquals("1", byId.getValue("2").parentId)
        assertEquals("2", byId.getValue("4").parentId)
    }

    @Test
    fun `synced parent is referenced by server id and not re-pushed`() {
        val food = cat(1, "Food", serverId = "uuid-food")
        val bakery = cat(2, "Bakery", parentId = 1)
        val croissant = cat(4, "Croissant", parentId = 2)
        val all = listOf(food, bakery, croissant)

        val dtos = buildHierarchicalPushDtos(listOf(croissant), all)
        val byId = dtoByTempId(dtos)

        assertEquals(listOf("2", "4"), dtos.map { it.tempId })
        assertEquals("uuid-food", byId.getValue("2").parentId)
        assertEquals("2", byId.getValue("4").parentId)
    }

    @Test
    fun `three level hierarchy keeps grandparent references intact`() {
        val root = cat(10, "Root")
        val child = cat(11, "Child", parentId = 10)
        val grandchild = cat(12, "Grandchild", parentId = 11)
        val unrelated = cat(20, "Other", serverId = "uuid-other")
        val leaf = cat(21, "Leaf", parentId = 20)
        val all = listOf(root, child, grandchild, unrelated, leaf)

        val dtos = buildHierarchicalPushDtos(listOf(grandchild, leaf), all)
        val byId = dtoByTempId(dtos)

        assertOrdered(dtos, listOf("10", "11", "12"))
        assertNull(byId.getValue("10").parentId)
        assertEquals("10", byId.getValue("11").parentId)
        assertEquals("11", byId.getValue("12").parentId)
        assertEquals("uuid-other", byId.getValue("21").parentId)
    }

    @Test
    fun `parent with stale serverId that is being re-pushed is referenced by tempId`() {
        val food = cat(1, "Food", serverId = "stale-uuid-food")
        val bakery = cat(2, "Bakery", parentId = 1)
        val all = listOf(food, bakery)

        // Both are unmatched this run, so food is re-created on the server.
        val dtos = buildHierarchicalPushDtos(listOf(food, bakery), all)
        val byId = dtoByTempId(dtos)

        assertEquals(listOf("1", "2"), dtos.map { it.tempId })
        assertNull(byId.getValue("1").parentId)
        assertEquals("1", byId.getValue("2").parentId)
    }

    private fun assertOrdered(dtos: List<NextcloudCategoryDto>, tempIdsInOrder: List<String>) {
        var lastIndex = -1
        for (tempId in tempIdsInOrder) {
            val index = dtos.indexOfFirst { it.tempId == tempId }
            if (index < 0) throw AssertionError("DTO with tempId $tempId not present")
            if (index <= lastIndex) throw AssertionError("$tempId is not after ${tempIdsInOrder[lastIndex]}")
            lastIndex = index
        }
    }
}
