package click.repwrite.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HelloController {

    @GetMapping("/")
    fun index(): String {
        return "redirect:/generate"
    }

    @GetMapping("/generate")
    fun hello(model: Model): String {
        model.addAttribute("message", "Hello World")
        return "hello"
    }

    @GetMapping("/about")
    fun about(): String {
        return "about"
    }
}
