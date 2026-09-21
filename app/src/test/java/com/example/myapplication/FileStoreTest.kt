package com.example.myapplication

import com.example.myapplication.data.store.FileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

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
    fun `managed memory rejects traversal ids and unknown reads`() {
        assertNull(store.readMemory("../../outside"))
        try {
            store.saveMemory("x", "y", "../../outside")
            fail("expected invalid memory id rejection")
        } catch (_: IllegalArgumentException) {
        }
        try {
            store.deleteMemory("../../outside")
            fail("expected invalid memory id rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `managed skills reject dot directory names`() {
        try {
            store.saveSkill("..", "x", "y")
            fail("expected dot skill rejection")
        } catch (_: IllegalArgumentException) {
        }
        try {
            store.deleteSkill(".")
            fail("expected dot skill rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `managed roots reject symbolic link children`() {
        val outsideMemory = tmp.newFile("outside-memory.md")
        try {
            Files.createSymbolicLink(store.memoryDir.toPath().resolve("linked.md"), outsideMemory.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }
        try {
            store.readMemory("linked")
            fail("expected linked memory rejection")
        } catch (_: IllegalStateException) {
        }

        val outsideSkill = tmp.newFolder("outside-skill")
        try {
            Files.createSymbolicLink(store.skillsDir.toPath().resolve("linked-skill"), outsideSkill.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }
        try {
            store.readSkill("linked-skill")
            fail("expected linked skill rejection")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `managed roots allow app root alias but reject redirected storage roots`() {
        val actualRoot = tmp.newFolder("actual-app-root")
        val alias = tmp.root.toPath().resolve("app-root-alias")
        try {
            Files.createSymbolicLink(alias, actualRoot.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }
        val aliasedStore = FileStore(alias.toFile())
        assertEquals("y", aliasedStore.saveMemory("x", "y").let { aliasedStore.readMemory(it.id) })

        val outsideMemory = tmp.newFolder("outside-memory-root")
        assertTrue(aliasedStore.memoryDir.deleteRecursively())
        try {
            Files.createSymbolicLink(aliasedStore.memoryDir.toPath(), outsideMemory.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }
        try {
            aliasedStore.saveMemory("x", "y")
            fail("expected redirected memory root rejection")
        } catch (_: IllegalStateException) {
        }

        val outsideSkills = tmp.newFolder("outside-skills-root")
        assertTrue(aliasedStore.skillsDir.delete())
        try {
            Files.createSymbolicLink(aliasedStore.skillsDir.toPath(), outsideSkills.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }
        try {
            aliasedStore.saveSkill("x", "x", "y")
            fail("expected redirected skills root rejection")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `managed writes replace hard links without changing their other names`() {
        val memory = store.saveMemory("before", "before", "memory-a")
        val memoryBody = store.memoryDir.resolve("${memory.id}.md")
        val outsideMemory = tmp.newFile("outside-memory-hard-link.md").apply { writeText("keep-memory") }
        val index = store.memoryDir.resolve("index.json")
        val outsideIndex = tmp.newFile("outside-index-hard-link.json").apply { writeText("keep-index") }
        val skillName = "hard-link-skill"
        store.saveSkill(skillName, "before", "before")
        val skillFile = store.skillDir(skillName).resolve("SKILL.md")
        val outsideSkill = tmp.newFile("outside-skill-hard-link.md").apply { writeText("keep-skill") }
        try {
            assertTrue(memoryBody.delete())
            Files.createLink(memoryBody.toPath(), outsideMemory.toPath())
            assertTrue(index.delete())
            Files.createLink(index.toPath(), outsideIndex.toPath())
            assertTrue(skillFile.delete())
            Files.createLink(skillFile.toPath(), outsideSkill.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }

        store.saveMemory("after", "after", memory.id)
        store.saveSkill(skillName, "after", "after")

        assertEquals("keep-memory", outsideMemory.readText())
        assertEquals("keep-index", outsideIndex.readText())
        assertEquals("keep-skill", outsideSkill.readText())
        assertEquals("after", store.readMemory(memory.id))
        assertEquals("after", store.readSkillBody(skillName))
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
