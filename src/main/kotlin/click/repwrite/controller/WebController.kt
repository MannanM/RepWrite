package click.repwrite.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class WebController {

    @GetMapping("/")
    fun index(): String {
        return "redirect:/generate"
    }

    @GetMapping("/generate")
    fun generate(): String {
        return "generate"
    }

    @GetMapping("/about")
    fun about(): String {
        return "about"
    }

    @GetMapping("/refresh-senators")
    fun refreshSenatorsPage(): String {
        return "refresh-senators"
    }
}
