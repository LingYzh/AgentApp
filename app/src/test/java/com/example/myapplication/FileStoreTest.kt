package com.example.myapplication

import com.example.myapplication.data.store.FileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: FileStore

    @Before
    fun setUp() {
        store = FileStore(tmp.root)
    }

    @Test
    fun `workspace write and read round trip`() {
        store.writeWorkspace("notes/todo.md", "# 待办\n- 买牛奶")
        assertEquals("# 待办\n- 买牛奶", store.readWorkspace("notes/todo.md"))
        assertEquals(listOf("notes/todo.md"), store.listWorkspace())
    }

    @Test(expected = SecurityException::class)
    fun `path traversal is rejected`() {
        store.workspaceFile("../evil.txt")
    }

    @Test(expected = SecurityException::class)
    fun `nested path traversal is rejected`() {
        store.workspaceFile("a/../../evil.txt")
    }

    @Test
    fun `memory save search delete`() {
        val e = store.saveMemory("偏好", "用户喜欢深色主题")
        assertEquals("用户喜欢深色主题", store.readMemory(e.id))

        val hits = store.searchMemory("深色")
        assertEquals(1, hits.size)
        assertEquals(e.id, hits[0].first.id)

        store.deleteMemory(e.id)
        assertTrue(store.listMemories().isEmpty())
        assertNull(store.readMemory(e.id))
    }

    @Test
    fun `memory update keeps id`() {
        val e = store.saveMemory("标题A", "旧内容")
        val updated = store.saveMemory("标题B", "新内容", id = e.id)
        assertEquals(e.id, updated.id)
        assertEquals("标题B", store.listMemories().first().title)
        assertEquals("新内容", store.readMemory(e.id))
    }

    @Test
    fun `skill save parse list`() {
        store.saveSkill("pdf-report", "生成 PDF 报告", "# 步骤\n1. 收集数据")
        val skills = store.listSkills()
        assertEquals(1, skills.size)
        assertEquals("pdf-report", skills[0].name)
        assertEquals("生成 PDF 报告", skills[0].description)
        assertEquals("# 步骤\n1. 收集数据", store.readSkillBody("pdf-report"))
    }

    @Test
    fun `skill filename is sanitized`() {
        store.saveSkill("a/b:c", "d", "body")
        assertEquals(1, store.listSkills().size)
    }

    @Test
    fun `config round trip`() {
        val c = com.example.myapplication.data.model.AppConfig(maxAgentLoops = 7)
        store.saveConfig(c)
        assertEquals(7, store.loadConfig().maxAgentLoops)
    }

    @Test
    fun `corrupt config falls back to default`() {
        store.configFile.writeText("{ not json")
        assertEquals(10, store.loadConfig().maxAgentLoops)
    }

    @Test
    fun `conversation round trip`() {
        val conv = com.example.myapplication.data.model.Conversation(title = "测试")
        conv.messages += com.example.myapplication.data.model.ChatMessage(role = "user", content = "你好")
        store.saveConversation(conv)
        val loaded = store.loadConversation(conv.id)!!
        assertEquals("测试", loaded.title)
        assertEquals("你好", loaded.messages[0].content)
    }

    @Test
    fun `agents round trip`() {
        val agent = com.example.myapplication.data.model.AgentProfile(
            name = "写手", emoji = "✍️", systemPrompt = "你是作家", model = "m1"
        )
        store.saveAgents(listOf(agent))
        val loaded = store.loadAgents()
        assertEquals(1, loaded.size)
        assertEquals("写手", loaded[0].name)
        assertEquals("你是作家", loaded[0].systemPrompt)
        assertEquals("m1", loaded[0].model)
    }
}
