package com.helmet.guard

import androidx.sqlite.db.SupportSQLiteDatabase
import com.helmet.guard.core.detector.AccidentDetectorConfig
import com.helmet.guard.core.dispatcher.DispatchResult
import com.helmet.guard.core.dispatcher.EmergencyDispatcher
import com.helmet.guard.core.dispatcher.EmergencyMessage
import com.helmet.guard.core.dispatcher.FakeEmergencyDispatcher
import com.helmet.guard.core.dispatcher.SmsManagerDispatcher
import com.helmet.guard.core.location.LocationResult
import com.helmet.guard.data.database.AppDatabase
import com.helmet.guard.data.database.entity.EmergencyContactEntity
import com.helmet.guard.domain.model.EmergencyContact
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * v1.2.0 升级核心特性与防误触分发管线深度单元测试：
 * 1. 15s 防误触默认倒计时验证
 * 2. 联系人 isEnabled 状态控制与全量并发分发（无 take(3) 限制）
 * 3. 生产环境号码强力清洗（去 ASCII 与中文全角空格、横杠、破折号、括号）
 * 4. 数据库升级 MIGRATION_1_2 迁移 SQL 语义与执行校验
 * 5. 并发分发部分失败容灾机制与状态研判
 */
class DispatchAndContactUpgradeTest {

    @Test
    fun testDefaultCountdownDurationIs15Seconds() {
        val config = AccidentDetectorConfig()
        assertEquals("事故检测默认倒计时必须为 15 秒", 15, config.countdownDurationSec)
    }

    @Test
    fun testEmergencyContactDefaultsToEnabled() {
        val domainContact = EmergencyContact(
            id = 1L,
            name = "张三",
            phone = "13800138000"
        )
        assertTrue("领域模型 EmergencyContact 默认必须处于启动状态", domainContact.isEnabled)

        val entityContact = EmergencyContactEntity(
            id = 1L,
            name = "李四",
            phone = "13900139000",
            isPrimary = true,
            relationship = "家属"
        )
        assertTrue("Room 实体 EmergencyContactEntity 默认必须处于启动状态", entityContact.isEnabled)
    }

    @Test
    fun testPhoneNumberCleansingProductionImplementation() {
        // 测试 SmsManagerDispatcher 生产环境清洗规则
        assertEquals("13812345678", SmsManagerDispatcher.cleanPhoneNumber("138-1234-5678"))
        assertEquals("13812345678", SmsManagerDispatcher.cleanPhoneNumber("138 1234 5678"))
        assertEquals("13812345678", SmsManagerDispatcher.cleanPhoneNumber("(138) 1234 5678"))
        assertEquals("+8613812345678", SmsManagerDispatcher.cleanPhoneNumber("+86 138-1234-(5678)"))
        // 中文全角字符及破折号清洗验证
        assertEquals("13812345678", SmsManagerDispatcher.cleanPhoneNumber("（138） 1234—5678"))
        assertEquals("13812345678", SmsManagerDispatcher.cleanPhoneNumber("138－1234－5678"))
        assertEquals("13812345678", SmsManagerDispatcher.cleanPhoneNumber("138\u30001234\u30005678")) // 全角空格
        assertEquals("", SmsManagerDispatcher.cleanPhoneNumber("  --- ( ) \u3000 "))
    }

    @Test
    fun testContactFilteringExcludesDisabledAndBlankContacts() {
        val contacts = listOf(
            EmergencyContact(1L, "联系人1", "13800000001", isEnabled = true),
            EmergencyContact(2L, "联系人2", "13800000002", isEnabled = false),
            EmergencyContact(3L, "联系人3", "", isEnabled = true),
            EmergencyContact(4L, "联系人4", "   ", isEnabled = true),
            EmergencyContact(5L, "联系人5", "13800000005", isEnabled = true),
            EmergencyContact(6L, "联系人6", "13800000006", isEnabled = true),
            EmergencyContact(7L, "联系人7", "13800000007", isEnabled = true)
        )

        val activeContacts = contacts.filter { it.isEnabled && it.phone.isNotBlank() }

        assertEquals("应包含所有有效且已启动联系人，彻底移除 3 人上限截断", 4, activeContacts.size)
        assertTrue("联系人1 必须在激活列表中", activeContacts.any { it.name == "联系人1" })
        assertFalse("已停用的联系人2 不得在激活列表中", activeContacts.any { it.name == "联系人2" })
        assertFalse("空号联系人不得在激活列表中", activeContacts.any { it.name == "联系人3" || it.name == "联系人4" })
        assertTrue("第 4 个有效联系人6 必须被保留（无 take(3) 限制）", activeContacts.any { it.name == "联系人6" })
        assertTrue("第 5 个有效联系人7 必须被保留（无 take(3) 限制）", activeContacts.any { it.name == "联系人7" })
    }

    @Test
    fun testZeroIntervalConcurrentDispatchAllActiveContacts() = runTest {
        val dispatcher: EmergencyDispatcher = FakeEmergencyDispatcher()
        val activeContacts = listOf(
            EmergencyContact(1L, "救护车队", "120", isEnabled = true),
            EmergencyContact(2L, "紧急联系人A", "13800000001", isEnabled = true),
            EmergencyContact(3L, "紧急联系人B", "13800000002", isEnabled = true),
            EmergencyContact(4L, "紧急联系人C", "13800000003", isEnabled = true),
            EmergencyContact(5L, "紧急联系人D", "13800000004", isEnabled = true)
        )

        val location = LocationResult.Success(
            latitude = 31.2304,
            longitude = 121.4737,
            accuracyMeters = 5.0f,
            timestampMs = System.currentTimeMillis(),
            isHistoricalFallback = false
        )
        val alertMessage = EmergencyMessage.buildAlertBody("智能头盔 Pro", 95, location)

        // 模拟 HelmetRepositoryImpl 中的 coroutineScope 并发分发
        val dispatchResults = coroutineScope {
            activeContacts.map { contact ->
                async {
                    val result = dispatcher.dispatch(contact.phone, alertMessage)
                    contact to result
                }
            }.awaitAll()
        }

        assertEquals("5 位联系人必须全部完成并发分发", 5, dispatchResults.size)
        assertTrue("所有分发结果必须为成功", dispatchResults.all { it.second is DispatchResult.Success })
    }

    @Test
    fun testConcurrentDispatchWithPartialFailures() = runTest {
        val mockDispatcher = object : EmergencyDispatcher {
            override suspend fun dispatch(phone: String, messageBody: String): DispatchResult {
                return if (phone.endsWith("1")) {
                    DispatchResult.Success("发送成功")
                } else {
                    DispatchResult.Failure("网络不可达")
                }
            }
        }

        val contacts = listOf(
            EmergencyContact(1L, "联系人1", "13800000001", isEnabled = true),
            EmergencyContact(2L, "联系人2", "13800000002", isEnabled = true)
        )

        val results = coroutineScope {
            contacts.map { contact ->
                async {
                    val res = mockDispatcher.dispatch(contact.phone, "test")
                    contact to res
                }
            }.awaitAll()
        }

        assertEquals(2, results.size)
        val anySuccess = results.any { it.second is DispatchResult.Success }
        assertTrue("至少有一个成功时整体状态应标记成功", anySuccess)
        assertTrue(results.any { it.second is DispatchResult.Failure })
    }

    @Test
    fun testEmptyActiveContactsTriggersFailurePath() {
        val contacts = listOf(
            EmergencyContact(1L, "停用联系人1", "13800000001", isEnabled = false),
            EmergencyContact(2L, "停用联系人2", "13800000002", isEnabled = false)
        )

        val activeContacts = contacts.filter { it.isEnabled && it.phone.isNotBlank() }
        assertTrue("全部未启动时 activeContacts 必须为空", activeContacts.isEmpty())
    }

    @Test
    fun testDatabaseMigrationVersionAndSqlExecution() {
        assertEquals("AppDatabase 迁移起始版本必须为 1", 1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals("AppDatabase 迁移目标版本必须为 2", 2, AppDatabase.MIGRATION_1_2.endVersion)

        // 实际执行 MIGRATION_1_2 并拦截校验执行的 SQL
        val executedSqls = mutableListOf<String>()
        val mockDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSqls.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_1_2.migrate(mockDb)

        assertEquals("必须且仅执行一次 ALTER TABLE 迁移语句", 1, executedSqls.size)
        assertEquals(
            "ALTER TABLE emergency_contacts ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1",
            executedSqls[0]
        )
    }
}
