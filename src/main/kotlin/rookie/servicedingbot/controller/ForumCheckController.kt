package rookie.servicedingbot.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rookie.servicedingbot.model.consts.BaseResult
import rookie.servicedingbot.service.ForumService

@RestController
@RequestMapping("/forum")
class ForumCheckController(
    private val forumService: ForumService,
) {

    @GetMapping("/schedule")
    fun processSchedule(): BaseResult<Boolean> {
        return BaseResult.success(true)
    }

    @GetMapping("/log")
    fun queryLoginLog(): String {
        return "ok"
    }




}