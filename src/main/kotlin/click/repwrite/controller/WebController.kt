package click.repwrite.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class WebController {

    @GetMapping("/")
    fun index() = "redirect:/generate"

    @GetMapping("/generate")
    fun generate() = "generate"

    @GetMapping("/about")
    fun about() = "about"

    @GetMapping("/refresh-politicians")
    fun refreshPoliticiansPage() = "refresh-politicians"
}
