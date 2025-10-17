package rookie.servicedingbot.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rookie.servicedingbot.model.consts.BaseResult
import rookie.servicedingbot.model.entity.ForumAccount
import rookie.servicedingbot.service.ForumService

@RestController
@RequestMapping("/forum")
class ForumCheckController(
    private val forumService: ForumService,
) {

    @GetMapping("/schedule")
    suspend fun processSchedule(): BaseResult<String> {
            return BaseResult.judge(forumService.processSchedule())
    }

    @GetMapping("/log")
    suspend fun queryLoginLog(): String {
            return "ok"
    }

    @PostMapping("/account/add")
    suspend fun addAccount(@RequestBody account: ForumAccount): BaseResult<String> {
            return BaseResult.judge(forumService.addAccount(account))
    }


}