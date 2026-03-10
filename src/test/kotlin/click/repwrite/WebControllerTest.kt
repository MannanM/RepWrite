package click.repwrite

import click.repwrite.controller.WebController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class WebControllerTest {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(WebController()).build()
    }

    @Test
    fun `should return hello view`() {
        mockMvc.perform(get("/generate")).andExpect(status().isOk).andExpect(view().name("hello"))
    }

    @Test
    fun `should redirect root to generate`() {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound)
                .andExpect(view().name("redirect:/generate"))
    }
}
